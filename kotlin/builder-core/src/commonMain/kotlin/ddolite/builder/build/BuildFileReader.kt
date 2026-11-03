package ddolite.builder.build

import ddolite.builder.data.BuilderData
import ddolite.builder.model.Alignment
import ddolite.builder.model.CharacterClass
import ddolite.builder.model.Skill
import ddolite.builder.model.Stat
import ddolite.builder.model.Tree
import ddolite.builder.model.TreeStyle
import ddolite.builder.model.SelectorStyle
import ddolite.dataformat.Vb
import ddolite.dataformat.Windows1252

/**
 * A line VB would stop on with "Unexpected error … Ignore error and load anyway?"
 * (`LoadFileLite`'s error handler). Loading carries on as if the user chose Yes: the rest of that
 * line is skipped (VB's `Resume Next` returns to the line loop).
 *
 * @property lineNumber 1-based, as VB reports it.
 * @property message VB's error description ("Subscript out of range", "Overflow", …).
 */
data class BuildLoadProblem(
    val lineNumber: Int,
    val line: String,
    val section: String,
    val field: String,
    val message: String,
)

/** The result of opening a .build file: the build with its rules, and what didn't match the data. */
class BuildLoadResult(
    val build: Build,
    val rules: BuildRules,
    val deprecations: Deprecations,
    val problems: List<BuildLoadProblem>,
)

/** A VB run-time error, raised where VB's would be and turned into a [BuildLoadProblem]. */
private class VbRuntimeError(message: String) : Exception(message)

private const val SUBSCRIPT_OUT_OF_RANGE = "Subscript out of range"
private const val OVERFLOW = "Overflow"
private const val INVALID_CALL = "Invalid procedure call or argument"

/** Sections of a .build file. Ported from `SectionEnum` (basFormatLite.bas:4). */
private enum class Section(val displayName: String) {
    UNKNOWN("Unknown"),
    OVERVIEW("Overview"),
    STATS("Stats"),
    SKILLS("Skills"),
    FEATS("Feats"),
    SPELLS("Spells"),
    ENHANCEMENTS("Enhancements"),
    LEVELING_GUIDE("Leveling Guide"),
    DESTINY("Destiny"),
    TWISTS("Twists"),
}

/**
 * Opens a .build file (Windows-1252 bytes, as VB writes them). See [readBuildText].
 */
fun readBuildFile(bytes: ByteArray, data: BuilderData, preferred: BuildPoints): BuildLoadResult =
    readBuildText(Windows1252.decode(bytes), data, preferred)

/**
 * Opens a build from the text of a .build file. Ported from `basFormatLite.LoadFileLite`
 * (basFormatLite.bas:649) followed by what `basDocuments.LoadBuild` (basDocuments.bas:520) does
 * after it: `CascadeChanges cceAll`.
 *
 * [preferred] is VB's `cfg.BuildPoints` setting, used when the file has no "Preferred:" line.
 *
 * Differences from VB, both only for malformed files:
 * - VB keeps the section (and field) of the previously opened file until the first section
 *   heading, so lines before it are read as part of that file's last section. Here they're ignored.
 * - VB's feat list starts as the previously open build's; here it starts empty. It only matters
 *   to warlock pact spells, which this load leaves empty either way (see below).
 *
 * VB quirk, kept: after loading, a warlock's pact spell slots are empty. `InitBuildSpells` runs
 * before `InitBuildFeats` lists the feats, so it sees the list made before the [Feats] section was
 * read, finds no Pact feat, and clears the slots. VB fills them again when the Spells screen opens
 * or a feat changes.
 */
fun readBuildText(text: String, data: BuilderData, preferred: BuildPoints): BuildLoadResult {
    val reader = BuildFileReader(data, preferred)
    reader.load(text)
    reader.rules.cascadeAll()
    return BuildLoadResult(reader.build, reader.rules, reader.deprecations, reader.problems)
}

/** A feat slot: its kind and 0-based position among that kind. */
private class SlotRef(val type: BuildFeatType, val index: Int)

/** "text number" split at the last space (SplitCombo). */
private class Combo(val text: String, val number: Int)

/** An ability found in its tier: its 0-based position, and its selector (numbered from 1; 0 for none). */
private class ResolvedAbility(val ability: Int, val selector: Int)

/** The state of `LoadFileLite` while it reads one file (VB's module-level variables in basFormatLite). */
private class BuildFileReader(private val data: BuilderData, preferred: BuildPoints) {
    val build = Build()
    val rules = BuildRules(data, build)
    val deprecations = Deprecations()
    val problems = ArrayList<BuildLoadProblem>()

    private var section = Section.UNKNOWN
    private var field = ""
    private var value = ""
    private var list: List<String> = emptyList()

    /** `mlngSpellSlot(class, spellLevel)`: spells read so far for each class and spell level. */
    private val spellSlotsRead = HashMap<CharacterClass, IntArray>()

    /** `mtypLookup`: leveling guide tree names seen so far, matching [BuildGuide.trees] one for one. */
    private val guideLookups = ArrayList<GuideLookup>()

    private class GuideLookup(val tree: Tree, val characterClass: CharacterClass?, val display: String)

    init {
        // ClearBuild False, SetBuildDefaults
        rules.setBuildDefaults(preferred)
    }

    fun load(text: String) {
        // xp.File.LoadToString drops trailing CRLFs; LoadArray splits on CRLF.
        var trimmed = text
        while (trimmed.endsWith("\r\n")) {
            trimmed = trimmed.substring(0, trimmed.length - 2)
        }
        val lines = Vb.split(trimmed, "\r\n")
        for (i in lines.indices) {
            val line = lines[i]
            try {
                if (parseLine(line)) {
                    when (section) {
                        Section.OVERVIEW -> loadOverview()
                        Section.STATS -> loadStats()
                        Section.SKILLS -> loadSkills()
                        Section.FEATS -> loadFeats()
                        Section.SPELLS -> loadSpells()
                        Section.ENHANCEMENTS -> loadEnhancements()
                        Section.LEVELING_GUIDE -> loadLevelingGuide()
                        Section.DESTINY -> loadDestiny()
                        else -> {}
                    }
                }
            } catch (e: VbRuntimeError) {
                problems.add(BuildLoadProblem(i + 1, line, section.displayName, field, e.message ?: ""))
            } catch (e: ArithmeticException) {
                problems.add(BuildLoadProblem(i + 1, line, section.displayName, field, OVERFLOW)) // Vb.cLng
            }
        }
    }

    /**
     * Section headings, comments and "Field: value" lines. Returns true for a field line, leaving
     * [field] (trimmed, lower case), [value] and its tab-separated [list]. Ported from `ParseLine`
     * (basFormatLite.bas:717).
     *
     * VB quirk, kept: an unknown [heading] is read as [Skills].
     */
    private fun parseLine(raw: String): Boolean {
        val first = if (raw.isEmpty()) "" else raw.substring(0, 1)
        when (first) {
            ";", " " -> return false
            "[" -> {
                if (!raw.endsWith("]")) {
                    return false
                }
                if (section == Section.OVERVIEW) {
                    finalizeOverview()
                }
                section = when (raw.lowercase()) {
                    "[overview]" -> Section.OVERVIEW
                    "[stats]" -> Section.STATS
                    "[skills]" -> Section.SKILLS
                    "[feats]" -> Section.FEATS
                    "[spells]" -> Section.SPELLS
                    "[enhancements]" -> Section.ENHANCEMENTS
                    "[levelingguide]" -> Section.LEVELING_GUIDE
                    "[destiny]" -> Section.DESTINY
                    "[twists]" -> Section.TWISTS
                    else -> Section.SKILLS
                }
                return false
            }
        }
        val pos = raw.indexOf(": ")
        if (pos < 0) {
            return false
        }
        field = Vb.trim(raw.substring(0, pos)).lowercase()
        value = raw.substring(pos + 2)
        list = Vb.split(value, "\t")
        return true
    }

    /** `UBound(mstrList)`. */
    private val listMax: Int get() = list.size - 1

    // ---------------------------------------------------------------- overview

    /** Ported from `LoadOverviewText` (basFormatLite.bas:770). */
    private fun loadOverview() {
        when (field) {
            "name" -> build.buildName = value
            "race" -> build.race = data.findRace(value)
            "alignment" -> build.alignment = Alignment.find(value)
            "maxlevels" -> build.maxLevels = vbByte(Vb.valOf(value))
            "notes" -> build.notes = if (build.notes.isEmpty()) value else build.notes + "\r\n" + value
            "class" -> setBuildClass()
            "level" -> setBuildLevel()
        }
    }

    /** The first empty one of the three build classes. Ported from `SetBuildClass` (basFormatLite.bas:782). */
    private fun setBuildClass() {
        val c = data.findClass(value) ?: return
        for (i in 0 until 3) {
            if (build.buildClasses[i] == null) {
                build.buildClasses[i] = c
                return
            }
        }
    }

    /** "Level: n<tab>Class". Ported from `SetBuildLevel` (basFormatLite.bas:794). */
    private fun setBuildLevel() {
        if (listMax != 1) {
            return
        }
        val level = Vb.cLng(Vb.valOf(list[0]))
        if (level < 1 || level > HEROIC_LEVELS) {
            return
        }
        val c = data.findClass(list[1])
        if (c != null) {
            build.levelClasses[level - 1] = c
        }
    }

    /**
     * Fills in missing build classes from the levels and missing levels from the first class,
     * then sets up BAB, feat slots and spell slots for the sections that follow. Ported from
     * `FinalizeOverview` (basFormatLite.bas:805).
     */
    private fun finalizeOverview() {
        if (build.buildClasses[0] == null) {
            // Classes in order of first appearance become build classes 1-3.
            val order = ArrayList<CharacterClass>()
            for (level in 1..HEROIC_LEVELS) {
                val c = build.levelClass(level) ?: continue
                if (!order.contains(c)) {
                    order.add(c)
                }
                val position = order.indexOf(c) + 1
                if (position in 1..3) {
                    build.buildClasses[position - 1] = c
                }
            }
        }
        val first = build.buildClasses[0]
        if (first != null) {
            for (level in 1..HEROIC_LEVELS) {
                if (build.levelClasses[level - 1] == null) {
                    build.levelClasses[level - 1] = first
                }
            }
        }
        rules.calculateBab()
        rules.initBuildFeats()
        rules.initBuildSpells()
    }

    // ---------------------------------------------------------------- stats

    /** Ported from `LoadStatsText` (basFormatLite.bas:844). */
    private fun loadStats() {
        when (field) {
            "preferred" -> build.buildPoints = BuildPoints.find(value)
            "levelup" -> {
                if (listMax == 1) {
                    val index = Vb.cLng(Vb.valOf(list[0])) / 4 // VB's \ rounds, then truncates
                    if (index in 0..MAX_LEVELUPS) {
                        build.levelups[index] = Stat.find(list[1])
                    }
                }
            }
            else -> {
                for (points in BuildPoints.entries) {
                    if (field == points.displayName.lowercase()) {
                        if (Vb.trim(value).lowercase() == "no") {
                            build.includePoints[points.ordinal] = false
                        }
                        return
                    }
                }
                for (stat in Stat.entries) {
                    if (field == stat.abbreviation.lowercase()) {
                        parseStatLine(stat)
                        return
                    }
                }
            }
        }
    }

    /**
     * Points in each column (fixed width; VB uses MAX_STATS, 6, as the column width) and the tome.
     * Ported from `ParseStatLine` (basFormatLite.bas:884).
     */
    private fun parseStatLine(stat: Stat) {
        for (column in 0 until 4) {
            val points = vbByte(Vb.valOf(Vb.trim(mid(value, column * 6 + 1, 4))))
            build.statPoints[column][stat.ordinal] = points
            build.statTotals[column] = vbByte((build.statTotals[column] + points).toDouble())
        }
        var tome = vbByte(Vb.valOf(Vb.trim(mid(value, 25, 4))))
        if (tome > data.tomes.stat.max) {
            tome = data.tomes.stat.max
        }
        build.tomes[stat.ordinal] = tome
    }

    // ---------------------------------------------------------------- skills

    /** Ranks per level in 4-character columns, then the tome. Ported from `LoadSkillsText` (basFormatLite.bas:899). */
    private fun loadSkills() {
        for (skill in Skill.entries) {
            if (field == skill.abbreviation.lowercase()) {
                for (level in 1..HEROIC_LEVELS) {
                    build.skills[skill.ordinal][level - 1] = vbByte(Vb.valOf(mid(value, (level - 1) * 4 + 1, 3)))
                }
                var tome = vbByte(Vb.valOf(mid(value, 82, 3)))
                if (tome > data.tomes.skill.max) {
                    tome = data.tomes.skill.max
                }
                build.skillTomes[skill.ordinal] = tome
                return
            }
        }
    }

    // ---------------------------------------------------------------- feats

    /**
     * "Kind: Slot n<tab>Feat", "Alternate: Slot n<tab>Feat" or "Exchange: Slot n<tab>level<tab>Feat".
     * Ported from `LoadFeatsText` (basFormatLite.bas:920).
     */
    private fun loadFeats() {
        if (listMax < 1) {
            return
        }
        var childType: BuildFeatType? = null
        var childIndex = 0
        val type: BuildFeatType
        val index: Int
        if (field == "alternate" || field == "exchange") {
            type = if (field == "alternate") BuildFeatType.ALTERNATE else BuildFeatType.EXCHANGE
            val child = identifySlot(list[0]) ?: return
            childType = child.type
            childIndex = child.index
            index = addSlot(type)
            if (index < 0) {
                throw VbRuntimeError(SUBSCRIPT_OUT_OF_RANGE) // AddSlot returned 0
            }
            val slot = build.feats(type)[index]
            val childSlot = build.feats(child.type)[child.index]
            slot.childType = child.type
            slot.child = child.index
            slot.level = if (type == BuildFeatType.EXCHANGE) vbByte(Vb.valOf(list[1])) else childSlot.level
            slot.classLevel = childSlot.classLevel
            slot.source = childSlot.source
        } else {
            val found = identifySlot(list[0]) ?: return
            type = found.type
            index = found.index
        }
        val raw = list[listMax]
        val found = findFeat(raw)
        if (found.result != 1) {
            deprecations.feats.add(DeprecatedFeat(type, index, found.featName, found.selectorName, childType, childIndex))
            return
        }
        val slot = build.feats(type)[index]
        slot.featName = found.featName
        slot.selector = found.selector
    }

    private class FoundFeat(val result: Int, val featName: String, val selectorName: String, val selector: Int)

    /**
     * Splits "Feat: Selector" and finds both, applying NameChange.txt renames. Returns result 0 if the
     * feat isn't found, 2 if it needs a selector that isn't found, 1 if all is well. Ported from
     * `FindFeat` (basFormatLite.bas:970) and `basDeprecate.DeprecateGetFeatID` (basDeprecate.bas:86).
     *
     * The feat name is matched ignoring case (see [BuilderData.findFeatIgnoringCase]); a rename's old
     * name and new name must match exactly. Selector names are matched ignoring case.
     */
    private fun findFeat(raw: String): FoundFeat {
        var featName: String
        var selectorName: String
        val pos = raw.indexOf(":")
        if (pos < 0) {
            featName = raw
            selectorName = ""
        } else {
            featName = raw.substring(0, pos)
            selectorName = Vb.trim(raw.substring(pos + 1))
            // VB: "2020.10.07 - hack to fix AchStudies which has a ":" in the feat name"
            if (featName == "Alchemical Studies") {
                featName = Vb.trim(raw)
            }
        }
        var feat = data.findFeatIgnoringCase(featName)
        if (feat == null) {
            val change = data.findNameChange("Feat", featName)
            if (change != null) {
                feat = data.findFeat(change.newName)
                if (feat != null) {
                    deprecations.addNameChange(change)
                }
            }
        }
        if (feat == null) {
            return FoundFeat(0, featName, selectorName, 0)
        }
        featName = feat.name
        var selector = feat.selectors.size
        while (selector >= 1) {
            if (feat.selectors[selector - 1].name.lowercase() == selectorName.lowercase()) {
                break
            }
            selector--
        }
        val result = if (selector == 0 && feat.selectors.isNotEmpty()) 2 else 1
        return FoundFeat(result, featName, selectorName, selector)
    }

    /**
     * The feat slot "Kind n" names: its kind and 0-based index, or null. Standard slots are found by
     * character level, class and deity slots by class level. Ported from `IdentifySlot`
     * (basFormatLite.bas:999) and `IdentifySlotType` (:1034).
     */
    private fun identifySlot(raw: String): SlotRef? {
        val combo = splitCombo(raw) ?: return null
        val text = combo.text
        val number = combo.number
        val type = slotType(text) ?: return null
        val slots = build.feats(type)
        var index = 0
        when (type) {
            BuildFeatType.ALTERNATE, BuildFeatType.EXCHANGE -> index = 0
            BuildFeatType.CLASS1, BuildFeatType.CLASS2, BuildFeatType.CLASS3, BuildFeatType.DEITY -> {
                while (index < slots.size && slots[index].classLevel != number) {
                    index++
                }
            }
            else -> {
                while (index < slots.size && slots[index].level != number) {
                    index++
                }
            }
        }
        if (index >= slots.size) {
            return null
        }
        return SlotRef(type, index)
    }

    /** Ported from `IdentifySlotType` (basFormatLite.bas:1034). */
    private fun slotType(text: String): BuildFeatType? {
        when (text) {
            "standard", "heroic", "epic", "destiny" -> return BuildFeatType.STANDARD
            "legend" -> return BuildFeatType.LEGEND
            "race" -> return BuildFeatType.RACE
            "deity" -> return BuildFeatType.DEITY
            "alternate" -> return BuildFeatType.ALTERNATE
            "exchange" -> return BuildFeatType.EXCHANGE
        }
        val c = data.findClass(text)
        if (c != null) {
            // VB compares the class with each build class in turn; the first match wins.
            for (i in 0 until 3) {
                if (build.buildClasses[i] == c) {
                    return BuildFeatType.entries[BuildFeatType.CLASS1.ordinal + i]
                }
            }
        }
        if (data.findRace(text) != null) {
            return BuildFeatType.RACE
        }
        return null
    }

    /**
     * Adds a slot to the alternate or exchange list and returns its 0-based index; -1 (VB 0) for an
     * exchange line without exactly three fields. Ported from `AddSlot` (basFormatLite.bas:1063).
     */
    private fun addSlot(type: BuildFeatType): Int {
        if (type == BuildFeatType.EXCHANGE && listMax != 2) {
            return -1
        }
        val slots = build.feats(type)
        slots.add(BuildFeat(type))
        return slots.size - 1
    }

    // ---------------------------------------------------------------- spells

    /**
     * "Spell: Class level<tab>Spell", filling that class's slots of that level in order. A free
     * spell adds a free slot. Ported from `LoadSpellsText` (basFormatLite.bas:1077).
     *
     * A spell is only put in an empty slot, so the cleric's cure and (if already set) pact spells
     * stay. More spells than slots is a VB "Subscript out of range" problem.
     */
    private fun loadSpells() {
        if (field != "spell" || listMax != 1) {
            return
        }
        val combo = splitCombo(list[0]) ?: return
        val c = data.findClass(combo.text) ?: return
        val spellLevel = combo.number
        val spells = build.spellsFor(c) ?: throw VbRuntimeError(SUBSCRIPT_OUT_OF_RANGE) // build.Spell not set up
        if (spellLevel < 1 || spellLevel > spells.maxSpellLevel) {
            return
        }
        val counts = spellSlotsRead.getOrPut(c) { IntArray(HEROIC_LEVELS) }
        val slotNumber = counts[spellLevel - 1] + 1
        counts[spellLevel - 1] = slotNumber
        val spell = list[1]
        val level = spells.level(spellLevel)
        if (rules.isFreeSpell(c, spell)) {
            level.freeSlots++
            level.slots.add(BuildSpellSlot()) // ReDim Preserve .Slot(1 To .Slots + 1)
        }
        if (slotNumber > level.slots.size) {
            throw VbRuntimeError(SUBSCRIPT_OUT_OF_RANGE)
        }
        val slot = level.slots[slotNumber - 1]
        if (slot.spell.isEmpty()) {
            slot.spell = spell
        }
    }

    // ---------------------------------------------------------------- trees

    /** Ported from `LoadEnhancementsText` (basFormatLite.bas:1110). */
    private fun loadEnhancements() {
        val current = if (build.trees.isEmpty()) null else build.trees[build.trees.size - 1]
        when (field) {
            "tier5" -> build.tier5 = value
            "racialap" -> build.racialAP = vbByte(Vb.valOf(value))
            "universalap" -> build.universalAP = vbByte(Vb.valOf(value))
            "tree" -> build.trees.add(BuildTree(value))
            "type" -> if (current != null) current.treeType = TreeStyle.find(value)
            "source" -> if (current != null) current.source = data.findClass(value)
            "classlevels" -> if (current != null) current.classLevels = vbByte(Vb.valOf(value))
            "ability" -> if (current != null) addTreeAbility(value, current, false)
        }
    }

    /**
     * Ported from `LoadDestinyText` (basFormatLite.bas:1134), including its fix for builds that
     * name the destiny "Macrotechnic" (4.4 and earlier). An ability line before any "Destiny:" line
     * is a VB "Subscript out of range" problem.
     *
     * VB bug, kept: there's no case for "DestinyTier5", which the writer saves, so the tier 5
     * destiny is lost each time a build is opened (VB-BUGS 18f).
     */
    private fun loadDestiny() {
        when (field) {
            "destinyap" -> build.destinyAP = vbByte(Vb.valOf(value))
            "permdestinypoints" -> build.permDestinyPoints = vbByte(Vb.valOf(value))
            "destiny" -> {
                var name = value
                if (name.lowercase() == "macrotechnic") {
                    name = "Machrotechnic"
                }
                val tree = BuildTree(name)
                tree.treeType = TreeStyle.DESTINY
                build.destinies.add(tree)
            }
            "ability" -> {
                if (build.destinies.isEmpty()) {
                    throw VbRuntimeError(SUBSCRIPT_OUT_OF_RANGE)
                }
                addTreeAbility(value, build.destinies[build.destinies.size - 1], true)
            }
        }
    }

    /** Ported from `AddTreeAbility` (basFormatLite.bas:1154). */
    private fun addTreeAbility(raw: String, buildTree: BuildTree, isDestiny: Boolean) {
        val ability = parseAbility(buildTree.treeName + " " + raw, isDestiny)
        val resolved = if (ability.parsed) resolveAbility(ability) else null
        if (resolved == null) {
            deprecateAbility(ability)
            return
        }
        buildTree.abilities.add(BuildAbility(ability.tier, resolved.ability, ability.rank, resolved.selector))
    }

    /**
     * Records an ability that didn't resolve. Ported from `basDeprecate.DeprecateAbility`
     * (basDeprecate.bas:444).
     *
     * VB bug, reported as a problem: for a destiny ability VB writes to an array it never sized,
     * so it stops with "Subscript out of range" (the ability is still counted as deprecated).
     */
    private fun deprecateAbility(ability: DeprecatedAbility) {
        if (ability.isDestiny) {
            deprecations.destinies.add(ability)
            throw VbRuntimeError(SUBSCRIPT_OUT_OF_RANGE)
        }
        deprecations.enhancements.add(ability)
    }

    /**
     * Splits "[Tree ]Tier n: Ability[: Selector][ (Rank r)]". Ported from `ParseAbility`
     * (basFormatLite.bas:1167). Only one digit is read for the tier and the rank.
     */
    private fun parseAbility(text: String, isDestiny: Boolean): DeprecatedAbility {
        var treeName = ""
        var tier = 0
        var rank = 0
        var abilityName = ""
        var selectorName = ""
        var display = ""
        fun result(parsed: Boolean) =
            DeprecatedAbility(isDestiny, text, treeName, tier, abilityName, selectorName, rank, display, parsed)

        val pos = vbInStr(text, "Tier ")
        if (pos == 0) {
            return result(false)
        }
        var number = mid(text, pos + 5, 1)
        if (!Vb.isNumeric(number)) {
            return result(false)
        }
        tier = Vb.cLng(Vb.valOf(number))
        if (pos > 1) {
            treeName = left(text, pos - 2)
            display = treeName
        }
        var rest = midFrom(text, pos + 8)
        val rankPos = vbInStr(rest, "(Rank ")
        if (rankPos == 0) {
            rank = 1
        } else {
            number = mid(rest, rankPos + 6, 1)
            if (!Vb.isNumeric(number)) {
                return result(false)
            }
            rank = Vb.cLng(Vb.valOf(number))
            rest = left(rest, rankPos - 2)
        }
        val colon = vbInStr(rest, ": ")
        if (colon == 0) {
            abilityName = rest
        } else {
            abilityName = left(rest, colon - 1)
            selectorName = midFrom(rest, colon + 2)
        }
        return result(true)
    }

    /**
     * The 0-based ability index and 1-based selector (0 for none), or null. Ported from
     * `ResolveAbility` (basFormatLite.bas:1202), `ResolveAbilityTree` (:1213) and
     * `FindAbilityOnTier` (:1218).
     *
     * An ability name with ": " in it (Warpriest's "Inflame: Energy Absorption") is found from its
     * two parsed halves.
     */
    private fun resolveAbility(ability: DeprecatedAbility): ResolvedAbility? {
        val tree = (if (ability.isDestiny) data.findDestiny(ability.treeName) else data.findTree(ability.treeName))
            ?: return null
        if (ability.tier > tree.style.tierCount || ability.tier >= tree.tiers.size) {
            return null
        }
        val abilities = tree.tiers[ability.tier]
        var a = 0
        var selector = 0
        while (a < abilities.size) {
            val candidate = abilities[a]
            if (candidate.name == ability.abilityName) {
                if (candidate.selectorStyle == SelectorStyle.NONE) {
                    if (ability.selectorName.isNotEmpty()) {
                        return null
                    }
                    break
                }
                if (ability.selectorName.isEmpty()) {
                    return null
                }
                selector = 1
                while (selector <= candidate.selectors.size) {
                    if (candidate.selectors[selector - 1].name == ability.selectorName) {
                        break
                    }
                    selector++
                }
                if (selector > candidate.selectors.size) {
                    return null
                }
                break
            } else if (candidate.name == ability.abilityName + ": " + ability.selectorName) {
                break
            }
            a++
        }
        if (a >= abilities.size) {
            return null
        }
        if (ability.rank > abilities[a].ranks) {
            return null
        }
        return ResolvedAbility(a, selector)
    }

    // ---------------------------------------------------------------- leveling guide

    /**
     * "Guide: <tree> Tier n: Ability…", "Reset Tree: <tree>", "Reset All Trees" or "Bank AP".
     * Ported from `LoadLevelingGuideText` (basFormatLite.bas:1263). After the first line that
     * doesn't resolve, the rest of the guide is ignored.
     */
    private fun loadLevelingGuide() {
        if (deprecations.levelingGuide != null || field != "guide") {
            return
        }
        if (value == "Bank AP") {
            build.guide.enhancements.add(BuildGuideEnhancement(200))
        } else if (value == "Reset All Trees") {
            build.guide.enhancements.add(BuildGuideEnhancement(100))
        } else if (value.startsWith("Reset Tree: ")) {
            val display = value.substring(12)
            val lookup = guideTreeLookup(display)
            if (lookup == 0) {
                deprecations.levelingGuide =
                    DeprecatedAbility(false, "", "", 0, "", "", 0, display, false)
            } else {
                build.guide.enhancements.add(BuildGuideEnhancement(100 + lookup))
            }
        } else {
            val parsed = parseAbility(value, false)
            if (!parsed.parsed) {
                deprecations.levelingGuide = parsed
                return
            }
            val lookup = guideTreeLookup(parsed.guideTreeDisplay)
            if (lookup == 0) {
                deprecations.levelingGuide = parsed
                return
            }
            val ability = parsed.copy(treeName = guideLookups[lookup - 1].tree.name)
            val resolved = resolveAbility(ability)
            if (resolved == null) {
                deprecations.levelingGuide = ability
                return
            }
            val step = BuildGuideEnhancement(lookup)
            step.tier = ability.tier
            step.ability = resolved.ability
            step.selector = resolved.selector
            step.rank = ability.rank
            build.guide.enhancements.add(step)
        }
    }

    /**
     * The 1-based guide tree for [display] (a tree abbreviation, with " (<class initials>)" when two
     * classes share it), adding it if new; 0 if it can't be matched. Ported from `GuideTreeLookup`
     * (basFormatLite.bas:1309) and `AddTreeLookup` (:1321).
     */
    private fun guideTreeLookup(display: String): Int {
        for (i in guideLookups.size - 1 downTo 0) {
            if (guideLookups[i].display == display) {
                return i + 1
            }
        }
        var tree = findTreeByAbbreviation(display)
        var c: CharacterClass? = null
        if (tree == null) {
            // Mid$(display, Len - 5, 2): VB stops with an error for text shorter than 6 characters.
            if (mid(display, display.length - 5, 2) != " (" || !display.endsWith(")")) {
                return 0
            }
            tree = findTreeByAbbreviation(left(display, display.length - 6)) ?: return 0
            c = findClassByInitials(mid(display, display.length - 3, 3)) ?: return 0
        }
        if (tree.style == TreeStyle.CLASS && c == null) {
            c = guideTreeClass(tree) ?: return 0
        }
        guideLookups.add(GuideLookup(tree, c, display))
        build.guide.trees.add(BuildGuideTree(tree.name, c))
        return guideLookups.size
    }

    /** The last tree with this abbreviation. Ported from `FindTreeByAbbreviation` (basFormatLite.bas:1349). */
    private fun findTreeByAbbreviation(abbreviation: String): Tree? {
        for (i in data.trees.size - 1 downTo 0) {
            if (data.trees[i].abbreviation == abbreviation) {
                return data.trees[i]
            }
        }
        return null
    }

    /** The last class whose fourth initials match. Ported from `FindClassByInitials` (basFormatLite.bas:1358). */
    private fun findClassByInitials(initials: String): CharacterClass? {
        for (i in data.classes.size - 1 downTo 0) {
            val c = data.classes[i]
            if (c.initials.size < 4) {
                throw VbRuntimeError(SUBSCRIPT_OUT_OF_RANGE) // only Initial(0) exists
            }
            if (c.initials[3] == initials) {
                return c
            }
        }
        return null
    }

    /**
     * For a class tree named by abbreviation alone: the build's class (most levels first) that has
     * the tree. Ported from `GetGuideTreeClass` (basFormatLite.bas:1367).
     */
    private fun guideTreeClass(tree: Tree): CharacterClass? {
        for (split in rules.classSplit()) {
            if (split.characterClass.trees.contains(tree.name)) {
                return split.characterClass
            }
        }
        return null
    }

    // ---------------------------------------------------------------- VB helpers

    /**
     * "text number" split at the last space: the text in lower case and the number, or null.
     * Ported from `SplitCombo` (basFormatLite.bas:753).
     */
    private fun splitCombo(combo: String): Combo? {
        val pos = combo.lastIndexOf(' ') + 1 // InStrRev, 1-based
        if (pos < 2) {
            return null
        }
        val number = Vb.trim(combo.substring(pos))
        if (!Vb.isNumeric(number)) {
            return null
        }
        return Combo(combo.substring(0, pos - 1).lowercase(), Vb.cLng(Vb.valOf(number)))
    }

    /** Assignment to a VB Byte field: rounds like CByte and fails with "Overflow" outside 0-255. */
    private fun vbByte(value: Double): Int {
        val rounded = Vb.cLng(value)
        if (rounded < 0 || rounded > 255) {
            throw VbRuntimeError(OVERFLOW)
        }
        return rounded
    }

    /** VB's `InStr`: 1-based position, 0 if not found. */
    private fun vbInStr(text: String, find: String): Int = text.indexOf(find) + 1

    /** VB's `Mid$(text, start, length)`: start is 1-based and must be at least 1. */
    private fun mid(text: String, start: Int, length: Int): String {
        if (start < 1 || length < 0) {
            throw VbRuntimeError(INVALID_CALL)
        }
        if (start > text.length) {
            return ""
        }
        return text.substring(start - 1, minOf(text.length, start - 1 + length))
    }

    /** VB's `Mid$(text, start)`. */
    private fun midFrom(text: String, start: Int): String = mid(text, start, text.length)

    /** VB's `Left$(text, length)`: a negative length is an error. */
    private fun left(text: String, length: Int): String {
        if (length < 0) {
            throw VbRuntimeError(INVALID_CALL)
        }
        return text.substring(0, minOf(length, text.length))
    }
}
