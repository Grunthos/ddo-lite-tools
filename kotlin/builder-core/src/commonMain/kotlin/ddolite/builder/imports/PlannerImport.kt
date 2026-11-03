package ddolite.builder.imports

import ddolite.builder.build.Build
import ddolite.builder.build.BuildFeatType
import ddolite.builder.build.BuildLoadResult
import ddolite.builder.build.BuildPoints
import ddolite.builder.build.BuildRules
import ddolite.builder.build.Deprecations
import ddolite.builder.build.HEROIC_LEVELS
import ddolite.builder.build.MAX_LEVELUPS
import ddolite.builder.build.RACE_DROW
import ddolite.builder.data.BuilderData
import ddolite.builder.data.FeatMapKey
import ddolite.builder.model.Alignment
import ddolite.builder.model.CharacterClass
import ddolite.builder.model.Race
import ddolite.builder.model.Skill
import ddolite.builder.model.Stat
import ddolite.dataformat.Vb

/**
 * Imports from Ron's Character Planner (.txt) and DDO Builder (.ddocp). Ported from
 * basFormatRon.ImportFileRon (basFormatRon.bas:333) and basFormatBuilder.ImportFileBuilder
 * (basFormatBuilder.bas:286). Each starts a new build (ClearBuild, SetBuildDefaults), reads what VB
 * reads, then does what BuildWasImported does to the build (CascadeChanges cceAll); the caller
 * shows it as an unsaved build with no file.
 *
 * The text is the file's, read as VB's xp.File.LoadToString does (Windows-1252, trailing CRLFs dropped).
 *
 * VB crashes, not copied (VB-BUGS 6b): where VB indexes past an array (an out-of-range level-up or
 * class number, a DDO Builder feat at a level with no slot of its kind, more than 30 levels), this
 * skips the value.
 */
object PlannerImport {
    /** GetImportStatRaise (basFormat.bas:114): the other planners count 7-10 raises; ours are 8/10/13/16 points. */
    fun importStatRaise(points: Int): Int = when (points) {
        7 -> 8
        8 -> 10
        9 -> 13
        10 -> 16
        else -> points
    }

    /** ImportFileRon: [fileName] names the build when the file has no name (FindBuildName). */
    fun ron(text: String, fileName: String, data: BuilderData, preferred: BuildPoints): BuildLoadResult {
        val reader = RonReader(data, preferred, lines(text))
        reader.read(fileName)
        reader.rules.cascadeAll()
        return BuildLoadResult(reader.build, reader.rules, Deprecations(), emptyList())
    }

    /** ImportFileBuilder. */
    fun builder(text: String, data: BuilderData, preferred: BuildPoints): BuildLoadResult {
        val reader = BuilderReader(data, preferred, loaded(text))
        reader.read()
        reader.rules.cascadeAll()
        return BuildLoadResult(reader.build, reader.rules, Deprecations(), emptyList())
    }

    /** xp.File.LoadToString: drops trailing CRLFs. */
    private fun loaded(text: String): String {
        var trimmed = text
        while (trimmed.endsWith("\r\n")) {
            trimmed = trimmed.substring(0, trimmed.length - 2)
        }
        return trimmed
    }

    /** LoadArray (basFormatRon.bas:111): the lines, split on CRLF. */
    private fun lines(text: String): List<String> = Vb.split(loaded(text), "\r\n")
}

/** Val, then the Long conversion VB does on assignment; 0 where VB would overflow. */
private fun number(s: String): Int {
    try {
        return Vb.cLng(Vb.valOf(s))
    } catch (e: ArithmeticException) {
        return 0
    }
}

/** The data's race with this VB race number, or null. */
private fun raceByIndex(data: BuilderData, vbIndex: Int): Race? {
    for (r in data.races) {
        if (r.vbIndex == vbIndex) {
            return r
        }
    }
    return null
}

/** The class kind of feat slot for [c]: CLASS1-3 by its place in the build's classes, or null. GetClassType (basFormatBuilder.bas:565). */
private fun classType(build: Build, c: CharacterClass?): BuildFeatType? {
    if (c == null) {
        return null
    }
    if (build.buildClasses[0] == c) {
        return BuildFeatType.CLASS1
    }
    if (build.buildClasses[1] == c) {
        return BuildFeatType.CLASS2
    }
    if (build.buildClasses[2] == c) {
        return BuildFeatType.CLASS3
    }
    return null
}

/** The module state of basFormatRon while it imports one file (mstrLine, mlngLines). */
private class RonReader(private val data: BuilderData, preferred: BuildPoints, private val line: List<String>) {
    val build = Build()
    val rules = BuildRules(data, build)

    /** mlngLines: the last line's index. */
    private val lastLine = line.size - 1

    init {
        rules.setBuildDefaults(preferred)
    }

    fun read(fileName: String) {
        build.buildName = findBuildName(fileName)
        build.race = findRace()
        build.alignment = findAlignment()
        identifyClasses()
        identifyBuildPoints()
        identifyLevelups()
        identifyTomes()
        identifySkills()
        identifyFeats()
    }

    /** FindValue (:354): the text after the field, without a trailing ";"; "" if there's no such line. */
    private fun findValue(field: String): String {
        val i = findLine(field)
        if (i > lastLine) {
            return ""
        }
        var value = Vb.trim(line[i].substring(field.length))
        if (value.endsWith(";")) {
            value = Vb.trim(value.substring(0, value.length - 1))
        }
        return value
    }

    /**
     * FindLine (:365): the first (or, [backwards], last) line starting with [field]. Not found is one
     * past the end: lastLine + 1 forwards, -1 backwards.
     */
    private fun findLine(field: String, backwards: Boolean = false): Int {
        if (backwards) {
            var i = lastLine
            while (i >= 0) {
                if (line[i].startsWith(field)) {
                    return i
                }
                i--
            }
            return -1
        }
        for (i in 0..lastLine) {
            if (line[i].startsWith(field)) {
                return i
            }
        }
        return lastLine + 1
    }

    /** FindBuildName (:386): "First, Last" as "First Last", else the file's name. */
    private fun findBuildName(fileName: String): String {
        var name = findValue("NAME:")
        name = name.replace(",", " ")
        name = Vb.trim(name)
        name = name.replace("  ", " ")
        if (name.isEmpty()) {
            name = nameFromFile(fileName)
        }
        return name
    }

    /** GetNameFromFilespec (basUtils.bas:44): the name without its folder or extension. */
    private fun nameFromFile(file: String): String {
        var name = file.substring(file.lastIndexOf('\\') + 1)
        val dot = name.lastIndexOf('.')
        if (dot >= 0) {
            name = name.substring(0, dot)
        }
        return name
    }

    /** FindRace (:397): Ron's race numbers; anything else is Human. */
    private fun findRace(): Race? {
        val vbIndex = when (number(findValue("RACE:"))) {
            5 -> 2 // Drow
            3 -> 3 // Dwarf
            1 -> 4 // Elf
            2 -> 5 // Halfling
            6 -> 6 // Half-Elf
            7 -> 7 // Half-Orc
            4 -> 8 // Warforged
            8 -> 9 // Bladeforged
            10 -> 10 // Purple Dragon Knight
            9 -> 11 // Morninglord
            11 -> 12 // Shadar-kai
            12 -> 13 // Gnome
            13 -> 14 // Deep Gnome
            14 -> 15 // Dragonborn
            15 -> 16 // Aasimar
            16 -> 17 // Scourge
            17 -> 18 // Wood Elf
            else -> 1 // Human
        }
        return raceByIndex(data, vbIndex)
    }

    /** FindAlignment (:425): Ron's alignment numbers; anything else is none. */
    private fun findAlignment(): Alignment? = when (number(findValue("ALIGNMENT:"))) {
        3 -> Alignment.TRUE_NEUTRAL
        2 -> Alignment.NEUTRAL_GOOD
        1 -> Alignment.LAWFUL_NEUTRAL
        0 -> Alignment.LAWFUL_GOOD
        5 -> Alignment.CHAOTIC_NEUTRAL
        4 -> Alignment.CHAOTIC_GOOD
        else -> null
    }

    /**
     * IdentifyClasses (:442): the 20 lines after CLASSRECORD:; an unknown class is the first class.
     * The first three classes met become the build's classes. Then the slots: CalculateBAB,
     * InitBuildFeats, InitBuildSpells, InitBuildTrees, InitLevelingGuide.
     */
    private fun identifyClasses() {
        val start = findLine("CLASSRECORD:")
        if (start > lastLine - 20) {
            return
        }
        for (i in 1..HEROIC_LEVELS) {
            var name = Vb.trim(line[start + i])
            if (name.endsWith(",")) {
                name = Vb.trim(name.substring(0, name.length - 1))
            }
            var c = data.findClass(name)
            if (c == null && build.buildClasses[0] != null) {
                c = build.buildClasses[0]
            }
            for (k in 0..2) {
                if (build.buildClasses[k] == c) {
                    break
                }
                if (build.buildClasses[k] == null) {
                    build.buildClasses[k] = c
                    break
                }
            }
            build.levelClasses[i - 1] = c
        }
        rules.calculateBab()
        rules.initBuildFeats()
        rules.initBuildSpells()
        rules.initBuildTrees()
        rules.initLevelingGuide()
    }

    /**
     * IdentifyBuildPoints (:472): the build points from the past lives (none: Champion if the file
     * has ABILITYFAVORBONUS: and the race isn't Drow, else Adventurer; one: Hero; more: Legend), only
     * that column included, and the stat raises from ABILITYRAISE:.
     */
    private fun identifyBuildPoints() {
        var lives = 0
        val pastLife = findLine("PASTLIFE:", backwards = true)
        if (pastLife >= 0 && pastLife < lastLine) {
            for (s in Vb.split(line[pastLife + 1], ",")) {
                lives += number(Vb.trim(s))
            }
        }
        val favorBonus = findLine("ABILITYFAVORBONUS:") <= lastLine
        build.buildPoints = when (lives) {
            0 -> if (build.race?.vbIndex == RACE_DROW || !favorBonus) BuildPoints.ADVENTURER else BuildPoints.CHAMPION
            1 -> BuildPoints.HERO
            else -> BuildPoints.LEGEND
        }
        for (i in build.includePoints.indices) {
            build.includePoints[i] = false
        }
        build.includePoints[build.buildPoints.ordinal] = true
        val raises = Vb.split(findValue("ABILITYRAISE:"), ",")
        if (raises.size == 6) {
            val column = build.buildPoints.ordinal
            for (i in 0..5) {
                val points = PlannerImport.importStatRaise(number(Vb.trim(raises[i])))
                build.statPoints[column][i] = points
                build.statTotals[column] = build.statTotals[column] + points
            }
        }
    }

    /**
     * IdentifyLevelups (:510): ABILITY4: to ABILITY36:, Ron's 0-5 for Strength to Charisma; the "all"
     * level-up is the stat if every one is the same.
     *
     * VB quirk, kept: a missing line reads as 0, Strength. A number past Charisma, which VB stores as
     * is, is no level-up here.
     */
    private fun identifyLevelups() {
        var mixed = false
        for (i in 1..MAX_LEVELUPS) {
            val stat = Stat.entries.getOrNull(number(findValue("ABILITY" + (i * 4) + ":")))
            build.levelups[i] = stat
            if (build.levelups[0] == null) {
                build.levelups[0] = stat
            }
            if (build.levelups[0] != stat) {
                mixed = true
            }
        }
        if (mixed) {
            build.levelups[0] = null
        }
    }

    /** IdentifyTomes (:526): the 6 lines after TOMERAISE:, each level's tomes added up, to the tome limit. */
    private fun identifyTomes() {
        val start = findLine("TOMERAISE:")
        if (start == 0 || start > lastLine - 6) {
            return
        }
        for (stat in 1..6) {
            var tome = 0
            for (s in Vb.split(line[start + stat], ",")) {
                tome += number(Vb.trim(s))
            }
            if (tome > data.tomes.stat.max) {
                tome = data.tomes.stat.max
            }
            build.tomes[stat - 1] = tome
        }
    }

    /** IdentifySkills (:546): the 20 lines after SKILLRAISE:, each with the 21 skills' points. */
    private fun identifySkills() {
        val start = findLine("SKILLRAISE:")
        if (start == 0 || start > lastLine - 20) {
            return
        }
        for (level in 1..HEROIC_LEVELS) {
            val values = Vb.split(line[start + level], ",")
            if (values.size - 1 >= 20) {
                for (i in 0..20) {
                    build.skills[i][level - 1] = number(Vb.trim(values[i]))
                }
            }
        }
    }

    /**
     * IdentifyFeats (:564): each line after FEATLIST: up to one with ";" is "name, level, kind,";
     * the name is looked up in the feat map, and the feat goes in the last slot of that kind at that level.
     *
     * VB quirk, kept: no feats are read when FEATLIST: is the first line.
     */
    private fun identifyFeats() {
        val start = findLine("FEATLIST:")
        if (start == 0) {
            return
        }
        data.featMapOrder.sort(FeatMapKey.RON)
        for (i in start + 1..lastLine) {
            if (line[i].contains(";")) {
                break
            }
            val feat = splitFeat(line[i]) ?: continue
            val slot = findSlot(line[i]) ?: continue
            slot.featName = feat.first
            slot.selector = feat.second
        }
    }

    /** RonSplitFeat (:587): our feat name and selector number for the line's feat, or null. */
    private fun splitFeat(raw: String): Pair<String, Int>? {
        val comma = raw.indexOf(',')
        if (comma < 0) {
            return null
        }
        val entry = data.featMapOrder.seek(raw.substring(0, comma)) ?: return null
        var name = entry.lite
        var selectorName = ""
        val colon = name.indexOf(':')
        if (colon >= 0) {
            selectorName = Vb.trim(name.substring(colon + 1))
            name = name.substring(0, colon)
        }
        val feat = rules.findFeatOrNull(name) ?: return null
        var selector = 0
        if (selectorName.isNotEmpty()) {
            var s = feat.selectors.size
            while (s >= 1) {
                if (feat.selectors[s - 1].name == selectorName) {
                    break
                }
                s--
            }
            selector = s
        }
        return Pair(name, selector)
    }

    /** RonFindSlot (:617): the slot for the line's level and Ron's slot kind (RonSlotEnum, :52). */
    private fun findSlot(raw: String): ddolite.builder.build.BuildFeat? {
        val token = Vb.split(raw, ",")
        if (token.size - 1 < 2) {
            return null
        }
        var className = ""
        var type: BuildFeatType? = null
        when (number(Vb.trim(token[2]))) {
            1, 18 -> type = BuildFeatType.STANDARD // Standard, Destiny
            9 -> type = BuildFeatType.DEITY
            20 -> type = BuildFeatType.LEGEND
            2, 15, 21 -> type = BuildFeatType.RACE // Human, Dilettante, Dragonborn
            3 -> className = "Fighter"
            4 -> className = "Wizard"
            5 -> className = "Ranger" // Favored Enemy
            6 -> className = "Rogue"
            7, 8 -> className = "Monk" // Bonus, Path
            10 -> className = "Favored Soul"
            16 -> className = "Artificer"
            17 -> className = "Druid"
            19 -> className = "Warlock" // Pact
            22 -> className = "Cleric" // Domain
        }
        if (className.isNotEmpty()) {
            type = classType(build, data.findClass(className))
        }
        if (type == null) {
            return null
        }
        val level = number(Vb.trim(token[1]))
        val slots = build.feats(type)
        var i = slots.size - 1
        while (i >= 0) {
            if (slots[i].level == level) {
                return slots[i]
            }
            i--
        }
        return null
    }
}

/** A feat read from a DDO Builder file (LevelFeatType): its name there, its slot kind and level. */
private class BuilderFeat(val featName: String, val level: Int) {
    var featType = ""
}

/** The module state of basFormatBuilder while it imports one file (mstrXML, mstrTrail, mlngLevel, mtypFeat). */
private class BuilderReader(private val data: BuilderData, preferred: BuildPoints, private val xml: String) {
    val build = Build()
    val rules = BuildRules(data, build)

    /** mstrTrail: the open tags above the current one, as "<A><B>", leaving out DDOCharacterData and Character. */
    private var trail = ""

    /** mlngLevel: the LevelTraining block being read (1-based). */
    private var level = 0

    /** mtypFeat(1 To 30): each level's feats. */
    private val levelFeats = Array(31) { ArrayList<BuilderFeat>() }

    init {
        rules.setBuildDefaults(preferred)
    }

    /** ParseXML (:297): the tags, then the feats. */
    fun read() {
        parseTags(1, xml.lastIndexOf('>') + 1)
        processFeats()
    }

    /** InStr(start, mstrXML, text), 1-based; 0 if not found. */
    private fun inStr(start: Int, text: String): Int = xml.indexOf(text, start - 1) + 1

    /** FindText (:352): [text] found from [left] at or before [right]; the position, or 0. */
    private fun findText(text: String, left: Int, right: Int): Int {
        val at = inStr(left, text)
        if (at > 0 && at <= right) {
            return at
        }
        return 0
    }

    /** Mid$(mstrXML, start, length), 1-based. */
    private fun mid(start: Int, length: Int): String {
        val from = start - 1
        val to = minOf(from + length, xml.length)
        if (from >= to) {
            return ""
        }
        return xml.substring(from, to)
    }

    /**
     * ParseTags (:313), recursive: each tag pair between [left] and [right] (1-based); a pair with
     * tags inside is read into, else its text is the value. AutomaticFeats, EnhancementSpendInTree,
     * EpicDestinySpendInTree, TwistOfFate and TrainedSpell are skipped.
     *
     * VB quirk, kept: a tag with attributes, a comment or an `<?xml ?>` declaration has no matching
     * close tag, which ends the reading of that level: a file that starts with a declaration
     * imports nothing.
     */
    private fun parseTags(start: Int, right: Int) {
        var left = start
        do {
            val openBegin = findText("<", left, right)
            if (openBegin == 0) {
                break
            }
            val openEnd = findText(">", openBegin, right)
            if (openEnd == 0) {
                break
            }
            val closeEnd: Int
            if (mid(openEnd - 1, 1) == "/") {
                closeEnd = openEnd // a null tag
            } else {
                val tag = mid(openBegin + 1, openEnd - openBegin - 1)
                val closeBegin = findText("</$tag>", openEnd, right)
                if (closeBegin == 0) {
                    break
                }
                closeEnd = closeBegin + tag.length + 2
                when (tag) {
                    "AutomaticFeats", "EnhancementSpendInTree", "EpicDestinySpendInTree", "TwistOfFate", "TrainedSpell" -> {}
                    else -> {
                        if (findText("<", openEnd, closeBegin - 1) != 0) {
                            advance(tag)
                            parseTags(openEnd + 1, closeBegin - 1)
                            retreat(tag)
                        } else {
                            processValue(tag, mid(openEnd + 1, closeBegin - openEnd - 1))
                        }
                    }
                }
            }
            left = closeEnd + 1
        } while (left < right)
    }

    /** Advance (:357). */
    private fun advance(tag: String) {
        if (tag != "DDOCharacterData" && tag != "Character") {
            trail = "$trail<$tag>"
        }
    }

    /** Retreat (:364): drops the last tag's length from the trail (also for the two Advance leaves out). */
    private fun retreat(tag: String) {
        val length = trail.length - tag.length - 2
        trail = if (length < 1) "" else trail.substring(0, length)
    }

    /** ProcessValue (:371). */
    private fun processValue(tag: String, value: String) {
        if (trail.isEmpty()) {
            processOverview(tag, value)
        } else if (trail == "<AbilitySpend>") {
            processStats(tag, value)
        } else if (trail == "<SkillTomes>") {
            val skill = Skill.find(tag)
            if (skill != null) {
                build.skillTomes[skill.ordinal] = number(value)
            }
        } else if (trail.startsWith("<LevelTraining>")) {
            levelTraining(tag, value)
        }
    }

    /** ProcessOverview (:388): level-ups, stat tomes, the three classes, name, alignment, race. */
    private fun processOverview(tag: String, value: String) {
        if (tag.startsWith("Level")) {
            val index = number(tag.substring(5)) / 4
            if (index < 0 || index > MAX_LEVELUPS) {
                return // VB: subscript out of range
            }
            build.levelups[index] = Stat.find(value)
            if (index == 7) {
                build.levelups[0] = build.levelups[1]
                for (i in 2..7) {
                    if (build.levelups[0] != build.levelups[i]) {
                        build.levelups[0] = null
                        break
                    }
                }
            }
        } else if (tag.endsWith("Tome")) {
            val stat = Stat.find(tag.substring(0, minOf(3, tag.length)))
            if (stat != null) {
                build.tomes[stat.ordinal] = number(value)
            }
        } else if (tag.startsWith("Class")) {
            val i = number(tag.substring(tag.length - 1))
            if (i in 1..3) { // VB: Class0 is out of range
                build.buildClasses[i - 1] = data.findClass(value)
            }
        } else {
            when (tag) {
                "Name" -> build.buildName = value
                "Alignment" -> build.alignment = if (value == "Neutral") Alignment.TRUE_NEUTRAL else Alignment.find(value)
                "Race" -> build.race = data.findRace(value)
            }
        }
    }

    /** ProcessStats (:426): AvailableSpend picks the build points; the others are the stat raises. */
    private fun processStats(tag: String, value: String) {
        if (tag == "AvailableSpend") {
            filterPoints(number(value))
            return
        }
        val stat = Stat.find(tag.substring(0, minOf(3, tag.length))) ?: return
        val column = build.buildPoints.ordinal
        val points = PlannerImport.importStatRaise(number(value))
        build.statPoints[column][stat.ordinal] = points
        build.statTotals[column] = build.statTotals[column] + points
    }

    /**
     * FilterPoints (:446): 28 Adventurer, 30 Champion, 32 Legend for Drow else Champion, 34 Hero,
     * 36 Legend; anything else Adventurer. Only that column is included.
     *
     * VB quirk, kept (VB-BUGS 18q): the export writes Champion as 32 and Hero as 34 (28 and 30 for
     * Drow), so a non-Drow Champion comes back right, but a Drow's Champion comes back as
     * Adventurer and Hero as Champion.
     */
    private fun filterPoints(value: Int) {
        val points = when (value) {
            28 -> BuildPoints.ADVENTURER
            30 -> BuildPoints.CHAMPION
            32 -> if (build.race?.vbIndex == RACE_DROW) BuildPoints.LEGEND else BuildPoints.CHAMPION
            34 -> BuildPoints.HERO
            36 -> BuildPoints.LEGEND
            else -> BuildPoints.ADVENTURER
        }
        build.buildPoints = points
        for (i in build.includePoints.indices) {
            build.includePoints[i] = i == points.ordinal
        }
    }

    /** LevelTraining (:463): each Class tag starts the next level; its feats (name, type) and skill points. */
    private fun levelTraining(tag: String, value: String) {
        if (tag == "Class") {
            level++
        } else if (level == 0) {
            return
        }
        when (tag) {
            "Class" -> if (level < 21) {
                build.levelClasses[level - 1] = data.findClass(value)
            }
            "FeatName" -> if (level <= 30) { // VB: mtypFeat(1 To 30)
                levelFeats[level].add(BuilderFeat(value, level))
            }
            "Type" -> if (level <= 30 && levelFeats[level].isNotEmpty()) {
                levelFeats[level][levelFeats[level].size - 1].featType = value
            }
            "Skill" -> if (level in 1..20) {
                val skill = Skill.find(value)
                if (skill != null) {
                    build.skills[skill.ordinal][level - 1] = build.skills[skill.ordinal][level - 1] + 1
                }
            }
        }
    }

    /** ProcessFeats (:493): the slots (InitBuildFeats), then each level's feats in order. */
    private fun processFeats() {
        rules.initBuildFeats()
        data.featMapOrder.sort(FeatMapKey.BUILDER)
        for (lvl in 1..30) {
            for (f in levelFeats[lvl]) {
                processFeat(f)
            }
        }
    }

    /**
     * ProcessFeat (:507): the slot kind from DDO Builder's type, the first slot of that kind at the
     * level, and our feat and selector from the feat map.
     *
     * VB quirk, kept: the slot is skipped if its number among its kind is more than its level
     * (`If lngIndex > .Feat(lngIndex).Level`, meant as a not-found check); with no slot at the level
     * VB crashes, which isn't copied.
     */
    private fun processFeat(f: BuilderFeat) {
        val type: BuildFeatType = when (f.featType) {
            "Standard", "EpicFeat", "EpicDestinyFeat" -> BuildFeatType.STANDARD
            "Legendary" -> BuildFeatType.LEGEND
            "FollowerOf", "ChildOf", "Deity", "BelovedOf", "DamageReduction" -> BuildFeatType.DEITY
            "HumanBonus", "Dilettante", "PDKBonus", "DragonbornRacial", "AasimarBond" -> BuildFeatType.RACE
            "ArtificerBonus" -> classType(build, data.findClass("Artificer"))
            "DruidWildShape" -> classType(build, data.findClass("Druid"))
            "FighterBonus" -> classType(build, data.findClass("Fighter"))
            "FavoredEnemy" -> classType(build, data.findClass("Ranger"))
            "RogueSpecialAbility" -> classType(build, data.findClass("Rogue"))
            "WarlockPact" -> classType(build, data.findClass("Warlock"))
            "Metamagic" -> classType(build, data.findClass("Wizard"))
            "EnergyResistance", "FavoredSoulBattle", "FavoredSoulHeart" -> classType(build, data.findClass("Favored Soul"))
            "Domain" -> classType(build, data.findClass("Cleric"))
            "MonkBonus", "MonkPhilosophy", "MonkBonus6" -> classType(build, data.findClass("Monk"))
            else -> null
        } ?: return
        val slots = build.feats(type)
        var index = -1
        for (i in slots.indices) {
            if (slots[i].level == f.level) {
                index = i
                break
            }
        }
        if (index < 0) {
            return // VB: subscript out of range
        }
        if (index + 1 > slots[index].level) {
            return
        }
        val entry = data.featMapOrder.seek(f.featName) ?: return
        var name = entry.lite
        var selectorName = ""
        val colon = name.indexOf(':')
        if (colon > 0) {
            selectorName = Vb.trim(name.substring(colon + 1)).lowercase()
            name = name.substring(0, colon)
        }
        val feat = rules.findFeatOrNull(name) ?: return
        var selector = 0
        if (selectorName.isNotEmpty()) {
            var s = feat.selectors.size
            while (s >= 1) {
                if (feat.selectors[s - 1].name.lowercase() == selectorName) {
                    break
                }
                s--
            }
            selector = s
        }
        slots[index].featName = name
        slots[index].selector = selector
    }
}
