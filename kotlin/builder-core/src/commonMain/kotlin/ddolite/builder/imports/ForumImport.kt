package ddolite.builder.imports

import ddolite.builder.build.Build
import ddolite.builder.build.BuildAbility
import ddolite.builder.build.BuildFeat
import ddolite.builder.build.BuildFeatType
import ddolite.builder.build.BuildLoadResult
import ddolite.builder.build.BuildPoints
import ddolite.builder.build.BuildRules
import ddolite.builder.build.BuildTree
import ddolite.builder.build.Deprecations
import ddolite.builder.build.HEROIC_LEVELS
import ddolite.builder.build.MAX_LEVEL
import ddolite.builder.build.MAX_LEVELUPS
import ddolite.builder.build.RACE_DROW
import ddolite.builder.build.SpellSlotType
import ddolite.builder.data.BuilderData
import ddolite.builder.data.KNOWN_RACES
import ddolite.builder.model.Ability
import ddolite.builder.model.Alignment
import ddolite.builder.model.CharacterClass
import ddolite.builder.model.Feat
import ddolite.builder.model.Skill
import ddolite.builder.model.Stat
import ddolite.builder.model.Tree
import ddolite.builder.model.TreeStyle
import ddolite.builder.output.BuildOutput
import ddolite.builder.output.OutputFormat
import ddolite.builder.output.OutputSettings
import ddolite.dataformat.Vb

/** A run-time error where VB raises one; frmImport's error handler turns any into "Invalid or Incomplete build text". */
private class ImportError(message: String) : Exception(message)

/**
 * File › Import › from Forums: a build read back from the text of an exported build, as copied
 * from a forum post. Ported from frmImport.frm: BeginTranslate (:268) with InitBuild, Translate,
 * FinishBuild and CascadeChanges cceAll; the window and its links are the screen's part.
 *
 * Returns null where VB's error handler shows "Invalid or Incomplete build text…": any run-time
 * error while translating. VB raises many, and they're kept, being what VB users see (VB-BUGS 18j-18m):
 * - "Destinies" isn't a section heading (VB looks for "Destiny"), so the destinies are read as more
 *   tiers of the last enhancement tree, which runs past its tiers;
 * - a spell line with fewer spells than the level has slots, or a free spell (VB's ReDim inside a
 *   `With` on the same array, error 10);
 * - a destiny's abilities when its number among all destinies is more than the build's destinies.
 *
 * [settings] are the output settings, whose feat options ValidFeats uses when guessing the build points.
 *
 * Differences from VB: the text's lines may end in LF (the platform's clipboard) as well as CRLF;
 * and VB's form keeps the last destiny read for the next translation (until the window is closed),
 * where this starts each translation with none.
 */
class ForumImport(private val data: BuilderData, preferred: BuildPoints, private val settings: OutputSettings) {
    private val build = Build()
    private val rules = BuildRules(data, build)

    private enum class Section { HEADER, LEVEL_ORDER, STATS, SKILLS, FEATS, SPELLS, ENHANCEMENTS, LEVELING_GUIDE, DESTINY, TWISTS }

    /** StatColumnEnum: a build points column (the BuildPoints ordinals), then Tome and Level. */
    private val tomeColumn = 4
    private val levelupColumn = 5

    private var heroic = 0
    private var epic = 0
    private var twentyOrMax = false
    private var max = false
    private var columns = 0
    private var column = IntArray(0)

    /** menProcess: before, between or after the skills' dashed lines. */
    private var process = 0

    private var classSpell: CharacterClass? = null
    private var spellLevel = 0
    private var tree: Tree? = null
    private var tier = 0
    private var destiny = 0
    private var destinyTier = 0

    private class Guess(val points: BuildPoints) {
        var skills = BuildOutput.Valid.SKIP
        var feats = BuildOutput.Valid.SKIP
    }

    private val guesses = ArrayList<Guess>()

    /** Why the last [translate] gave null: the run-time error VB would have raised (for tests and diagnosis). */
    var failure = ""
        private set

    init {
        // InitBuild (:293): ClearBuild, SetBuildDefaults, Erase build.IncludePoints.
        rules.setBuildDefaults(preferred)
        for (i in build.includePoints.indices) {
            build.includePoints[i] = false
        }
        build.buildName = "Imported Build"
    }

    /** BeginTranslate: the build, or null for VB's "Invalid or Incomplete build text". */
    fun translate(text: String): BuildLoadResult? {
        try {
            translateLines(text)
            finishBuild()
            rules.cascadeAll()
        } catch (e: Exception) {
            failure = e.message ?: e.toString()
            return null
        }
        return BuildLoadResult(build, rules, Deprecations(), emptyList())
    }

    // ---------------------------------------------------------------- VB string functions

    /** Mid$(s, start) and Mid$(s, start, length), 1-based. */
    private fun mid(s: String, start: Int, length: Int = Int.MAX_VALUE): String {
        val from = start - 1
        if (from >= s.length) {
            return ""
        }
        val to = if (length >= s.length - from) s.length else from + length
        return s.substring(from, to)
    }

    private fun left(s: String, n: Int): String = if (n >= s.length) s else s.substring(0, n)

    private fun right(s: String, n: Int): String = if (n >= s.length) s else s.substring(s.length - n)

    private fun number(s: String): Int = Vb.cLng(Vb.valOf(s))

    // ---------------------------------------------------------------- translate

    /** Translate (:308). */
    private fun translateLines(text: String) {
        val line = ArrayList(Vb.split(text.replace("\r\n", "\n"), "\n"))
        trimLeadingSpaces(line)
        var section = Section.HEADER
        for (i in line.indices) {
            line[i] = when (section) {
                Section.STATS -> cleanStatsLine(line[i])
                Section.FEATS -> line[i].replace(".", " ")
                else -> Vb.trim(line[i]).replace(".", " ")
            }
            if (line[i].isNotEmpty()) {
                val next = parseSection(line[i], section)
                if (next != null) {
                    section = next
                } else {
                    when (section) {
                        Section.HEADER -> parseHeader(line[i])
                        Section.LEVEL_ORDER -> parseLevelOrder(line[i])
                        Section.STATS -> parseStats(line[i])
                        Section.SKILLS -> parseSkills(line[i])
                        Section.FEATS -> parseFeats(line[i])
                        Section.SPELLS -> parseSpells(line[i])
                        Section.ENHANCEMENTS -> parseEnhancements(line[i])
                        Section.DESTINY -> parseDestiny(line[i])
                        else -> {}
                    }
                }
            }
        }
    }

    /**
     * TrimLeadingSpaces (:340): when every line but the first starts with spaces, the fewest are removed.
     * VB quirk, kept: the first line isn't looked at or trimmed.
     */
    private fun trimLeadingSpaces(line: ArrayList<String>) {
        var spaces = 9999
        for (i in 1 until line.size) {
            val s = line[i]
            if (s.isNotEmpty()) {
                var j = 1
                while (j <= s.length && s[j - 1] == ' ') {
                    j++
                }
                if (j == 1) {
                    return
                }
                if (spaces > j) {
                    spaces = j
                }
            }
        }
        for (i in 1 until line.size) {
            line[i] = mid(line[i], spaces)
        }
    }

    /** CleanStatsLine (:360): a line naming a stat after a space loses its leading spaces; dots become spaces. */
    private fun cleanStatsLine(raw: String): String {
        var s = raw
        for (name in listOf(" Strength", " Dexterity", " Constitution", " Intelligence", " Wisdom", " Charisma")) {
            if (s.contains(name)) {
                s = s.trimStart(' ')
                break
            }
        }
        return s.replace(".", " ")
    }

    /**
     * ParseSection (:378): the section a heading starts, finishing the one before; null if the line
     * isn't a heading. Feats and Spells set up their slots as their headings are met.
     */
    private fun parseSection(raw: String, current: Section): Section? {
        val s = Vb.trim(raw)
        val next: Section
        if (left(s, 11) == "Level Order") {
            next = Section.LEVEL_ORDER
        } else if (left(s, 5) == "Stats") {
            next = Section.STATS
        } else if (s == "Skills") {
            next = Section.SKILLS
        } else if (left(s, 5) == "Feats") {
            next = Section.FEATS
            rules.initBuildFeats()
        } else if (s == "Spells") {
            rules.initBuildSpells()
            next = Section.SPELLS
        } else if (left(s, 12) == "Enhancements") {
            next = Section.ENHANCEMENTS
        } else if (left(s, 14) == "Leveling Guide") {
            next = Section.LEVELING_GUIDE
        } else if (left(s, 7) == "Destiny") {
            next = Section.DESTINY
        } else if (left(s, 14) == "Twists of Fate") {
            next = Section.TWISTS
        } else {
            return null
        }
        when (current) {
            Section.HEADER -> rules.calculateBab()
            Section.FEATS -> {
                rules.initBuildFeats()
                if (twentyOrMax && !max) {
                    build.maxLevels = 20
                }
            }
            else -> {}
        }
        process = 0
        when (next) {
            Section.SPELLS -> {
                // The first class, in VB's class order, with spell levels.
                classSpell = null
                for (c in classesInVbOrder()) {
                    val spells = build.spellsFor(c)
                    if (spells != null && spells.maxSpellLevel > 0) {
                        classSpell = c
                        break
                    }
                }
            }
            Section.DESTINY -> {
                tree = null
                tier = 1
            }
            else -> {}
        }
        return next
    }

    private fun classesInVbOrder(): List<CharacterClass> = data.classes.sortedBy { it.vbIndex }

    /** FinishBuild (:431): the "all" level-up if every one is the same, then the build points. */
    private fun finishBuild() {
        build.levelups[0] = build.levelups[1]
        for (i in 2..MAX_LEVELUPS) {
            if (build.levelups[i] != build.levelups[0]) {
                build.levelups[0] = null
                break
            }
        }
        guessBuildPoints()
    }

    // ---------------------------------------------------------------- header

    /** ParseHeader (:457): a class split, an alignment and race, or else the build's name. */
    private fun parseHeader(text: String) {
        if (parseClassSplit(text)) {
            return
        }
        if (parseAlignRace(text)) {
            return
        }
        build.buildName = text
    }

    /**
     * ParseClassSplit (:463): "Class 20" or "12/6/2 Class1/Class2/Class3", each optionally followed by
     * ", Epic 10". A single class fills all 20 levels; a split only sets the build's classes (the
     * Level Order section places them).
     */
    private fun parseClassSplit(raw: String): Boolean {
        var text = raw
        heroic = 0
        epic = 0
        val epicAt = text.indexOf(", Epic ")
        if (epicAt >= 0) {
            val level = mid(text, text.lastIndexOf(' ') + 2)
            if (!Vb.isNumeric(level)) {
                return false
            }
            epic = number(level)
            text = text.substring(0, epicAt)
        }
        for (c in classesInVbOrder()) {
            if (text.startsWith(c.name)) {
                val space = text.lastIndexOf(' ')
                if (space < 0) {
                    return false
                }
                val level = text.substring(space + 1)
                if (!Vb.isNumeric(level)) {
                    return false
                }
                heroic = number(level)
                build.buildClasses[0] = c
                setMaxLevel()
                for (i in 1..HEROIC_LEVELS) {
                    build.levelClasses[i - 1] = c
                }
                rules.calculateBab()
                return true
            }
        }
        if (!text.contains("/")) {
            return false
        }
        val space = text.indexOf(' ')
        if (space < 0) {
            return false
        }
        val levels = Vb.split(text.substring(0, space), "/")
        val classes = Vb.split(text.substring(space + 1), "/")
        if (levels.size - 1 < 1 || levels.size != classes.size) {
            return false
        }
        heroic = 0
        // enClass: after the pure-class loop it's past the last class, so "not any" until a class is read.
        var anyClass = false
        for (i in levels.indices) {
            if (!Vb.isNumeric(levels[i])) {
                break
            }
            heroic += number(levels[i])
            val c = data.findClass(classes[i])
            if (c == null) {
                anyClass = true
                break
            }
            build.buildClasses[i] = c
        }
        if (heroic == 0 || anyClass) {
            for (i in build.buildClasses.indices) {
                build.buildClasses[i] = null
            }
            return false
        }
        setMaxLevel()
        return true
    }

    /** SetMaxLevel (:522): 20 heroic levels and no epic means the highest level. */
    private fun setMaxLevel() {
        if (heroic == 20 && epic == 0) {
            build.maxLevels = MAX_LEVEL
            twentyOrMax = true
        } else {
            build.maxLevels = heroic + epic
        }
    }

    /**
     * ParseAlignRace (:531): an alignment the line starts with and a race it ends with, each the first
     * found in VB's order.
     *
     * VB quirk, kept (VB-BUGS 18n): races are tried in VB's order, so "Half-Elf" and "Wood Elf" are
     * read as Elf and "Deep Gnome" as Gnome. And once an alignment or race is known, every later
     * header line counts as this line, so it can't be the build's name.
     */
    private fun parseAlignRace(text: String): Boolean {
        for (a in Alignment.entries) {
            if (text.startsWith(a.displayName)) {
                build.alignment = a
                break
            }
        }
        for (known in KNOWN_RACES) {
            if (text.endsWith(known.name)) {
                for (r in data.races) {
                    if (r.vbIndex == known.vbIndex) {
                        build.race = r
                    }
                }
                break
            }
        }
        return build.alignment != null || build.race != null
    }

    // ---------------------------------------------------------------- level order

    /** ParseLevelOrder (:557): four columns of "nn. Class". */
    private fun parseLevelOrder(raw: String) {
        parseLevelOrderEntry(raw, 1, 18)
        parseLevelOrderEntry(raw, 19, 37)
        parseLevelOrderEntry(raw, 38, 56)
        parseLevelOrderEntry(raw, 57, 76)
        rules.calculateBab()
    }

    /** ParseLevelOrderEntry (:565). */
    private fun parseLevelOrderEntry(raw: String, start: Int, endColumn: Int) {
        var end = endColumn
        if (raw.length < start - 2) {
            return
        }
        val levelText = Vb.trim(mid(raw, start, 2))
        if (!Vb.isNumeric(levelText)) {
            return
        }
        val level = number(levelText)
        if (level < 1 || level > 20) {
            return
        }
        if (raw.length < end) {
            end = raw.length
        }
        if (end < start + 5) {
            return
        }
        val c = data.findClass(Vb.trim(mid(raw, start + 3, end - start - 2))) ?: return
        build.levelClasses[level - 1] = c
    }

    // ---------------------------------------------------------------- stats

    /**
     * ParseStats (:590): the first line names the columns (28pt…36pt, Tome, Level); each stat line
     * then has its scores (less the race's starting score, as points), tomes ("+2") and level-ups ("4:STR").
     *
     * VB quirks, kept: an unnamed column counts as 28pt; "30pt" is Hero (Drow's 30 points), and
     * "32pt" is Legend for a Drow; a level-up sets the stat for the rest of its line.
     */
    private fun parseStats(rawLine: String) {
        var raw = rawLine.trimEnd(' ')
        if (raw.contains("-")) {
            return
        }
        raw = raw.replace(": ", ":")
        val col = stripSpaces(raw)
        if (columns == 0) {
            columns = col.size - 1
            column = IntArray(columns + 1)
            for (i in 1 until col.size) {
                var include = true
                when (col[i]) {
                    "28pt" -> column[i] = BuildPoints.ADVENTURER.ordinal
                    "30pt" -> column[i] = BuildPoints.HERO.ordinal
                    "32pt" -> column[i] = if (build.race?.vbIndex == RACE_DROW) BuildPoints.LEGEND.ordinal else BuildPoints.CHAMPION.ordinal
                    "34pt" -> column[i] = BuildPoints.HERO.ordinal
                    "36pt" -> column[i] = BuildPoints.LEGEND.ordinal
                    "Tome" -> {
                        column[i] = tomeColumn
                        include = false
                    }
                    "Level" -> {
                        column[i] = levelupColumn
                        include = false
                    }
                    else -> include = false
                }
                if (include) {
                    build.includePoints[column[i]] = true
                }
            }
            return
        }
        var stat = Stat.find(col[0])
        for (i in 1 until col.size) {
            if (col[i].contains(":")) {
                val levelup = Vb.split(col[i], ":")
                if (levelup.size != 2 || !Vb.isNumeric(levelup[0])) {
                    return
                }
                val level = number(levelup[0])
                if (level != 4 && level != 8 && level != 12 && level != 16 && level != 20 && level != 24 && level != 28) {
                    return
                }
                stat = Stat.find(levelup[1]) ?: return
                build.levelups[level / 4] = stat
            } else if (columnAt(i) == tomeColumn) {
                if (col[i].length != 2 || !col[i].startsWith("+")) {
                    return
                }
                val tome = right(col[i], 1)
                if (!Vb.isNumeric(tome)) {
                    return
                }
                if (stat != null) {
                    build.tomes[stat.ordinal] = number(tome)
                }
            } else {
                val s = stat ?: return
                if (!Vb.isNumeric(col[i])) {
                    return
                }
                val race = build.race ?: throw ImportError("Subscript out of range") // db.Race(reAny)
                var points = number(col[i]) - race.stat(s)
                points = PlannerImport.importStatRaise(points)
                val c = columnAt(i)
                if (c > BuildPoints.LEGEND.ordinal) {
                    throw ImportError("Subscript out of range") // StatPoints(scTome or scLevelup, …)
                }
                build.statPoints[c][s.ordinal] = points
                build.statTotals[c] = build.statTotals[c] + points
            }
        }
    }

    /** menColumn(i): past the header's columns is VB's subscript out of range. */
    private fun columnAt(i: Int): Int {
        if (i >= column.size) {
            throw ImportError("Subscript out of range")
        }
        return column[i]
    }

    /** StripSpaces (:1252): runs of spaces made single, then split at spaces. */
    private fun stripSpaces(raw: String): List<String> {
        var s = raw
        while (s.contains("  ")) {
            s = s.replace("  ", " ")
        }
        return Vb.split(s, " ")
    }

    /**
     * GuessBuildPoints (:665): the first included column whose skills and feats are both complete
     * (or empty); else the best fit; else the first column.
     */
    private fun guessBuildPoints() {
        guesses.clear()
        for (points in BuildPoints.entries) {
            if (addGuess(points)) {
                return
            }
        }
        val order = listOf(
            Pair(BuildOutput.Valid.COMPLETE, BuildOutput.Valid.INCOMPLETE),
            Pair(BuildOutput.Valid.INCOMPLETE, BuildOutput.Valid.COMPLETE),
            Pair(BuildOutput.Valid.INCOMPLETE, BuildOutput.Valid.INCOMPLETE),
            Pair(BuildOutput.Valid.COMPLETE, BuildOutput.Valid.ERRORS),
            Pair(BuildOutput.Valid.ERRORS, BuildOutput.Valid.COMPLETE),
            Pair(BuildOutput.Valid.INCOMPLETE, BuildOutput.Valid.ERRORS),
            Pair(BuildOutput.Valid.ERRORS, BuildOutput.Valid.INCOMPLETE),
        )
        for (o in order) {
            if (bestGuess(o.first, o.second)) {
                return
            }
        }
        if (guesses.isNotEmpty()) {
            build.buildPoints = guesses[0].points
        }
    }

    /** AddGuess (:686): tries [points] if its column is included; true if skills and feats come out complete. */
    private fun addGuess(points: BuildPoints): Boolean {
        if (!build.includePoints[points.ordinal]) {
            return false
        }
        val guess = Guess(points)
        guesses.add(guess)
        build.buildPoints = points
        val valid = BuildOutput(rules, OutputFormat.DEFAULT, settings).importValidity()
        guess.skills = if (valid.first == BuildOutput.Valid.EMPTY) BuildOutput.Valid.COMPLETE else valid.first
        guess.feats = if (valid.second == BuildOutput.Valid.EMPTY) BuildOutput.Valid.COMPLETE else valid.second
        return guess.skills == BuildOutput.Valid.COMPLETE && guess.feats == BuildOutput.Valid.COMPLETE
    }

    /**
     * BestGuess (:703).
     * VB bug, kept (VB-BUGS 18o): it looks at the last guess every time round the loop, and then
     * takes the first guess, so a match on the last guess picks the first column.
     */
    private fun bestGuess(skills: BuildOutput.Valid, feats: BuildOutput.Valid): Boolean {
        for (i in guesses.indices) {
            val last = guesses[guesses.size - 1]
            if (last.skills == skills && last.feats == feats) {
                build.buildPoints = guesses[i].points
                return true
            }
        }
        return false
    }

    // ---------------------------------------------------------------- skills

    /**
     * ParseSkills (:725): between the dashed lines, each line starting with a skill's short name has
     * its ranks in 3-character columns, one per level.
     */
    private fun parseSkills(raw: String) {
        if (left(raw.trimStart(' '), 3) == "---") {
            process = if (process == 0) 1 else 2
            return
        }
        if (process != 1) {
            return
        }
        var skill: Skill? = null
        for (s in Skill.entries) {
            if (raw.startsWith(s.abbreviation)) {
                skill = s
                break
            }
        }
        if (skill == null) {
            return
        }
        for (level in 1..HEROIC_LEVELS) {
            val pos = level * 3 + 7
            if (raw.length < pos + 2) {
                break
            }
            val ranks = Vb.trim(mid(raw, pos, 3))
            if (ranks.isNotEmpty()) {
                parseRanks(skill, level, ranks)
            }
        }
    }

    /** ParseRanks (:753): ranks as points, two per rank for a cross-class skill, plus one for "½". */
    private fun parseRanks(skill: Skill, level: Int, raw: String) {
        val c = build.levelClass(level) ?: return
        val native = c.nativeSkills.contains(skill)
        var ranks = raw
        var half = false
        if (ranks.endsWith("½")) {
            half = true
            ranks = if (ranks.length == 1) "0" else ranks.substring(0, ranks.length - 1)
        }
        if (!Vb.isNumeric(ranks)) {
            return
        }
        var points = number(ranks)
        if (!native) {
            points *= 2
        }
        if (half) {
            points++
        }
        build.skills[skill.ordinal][level - 1] = points
    }

    // ---------------------------------------------------------------- feats

    /**
     * ParseFeats (:777): "lv Source : Feat" in fixed columns. "Swap" lines are exchanges; otherwise
     * the feat goes in the slot with that source and level, and " OR " adds alternates.
     */
    private fun parseFeats(raw: String) {
        val levelText = Vb.trim(left(raw, 2))
        if (!Vb.isNumeric(levelText)) {
            return
        }
        val level = number(levelText)
        if (mid(raw, 11, 2) != ": ") {
            return
        }
        val source = Vb.trim(mid(raw, 4, 7))
        val display = mid(raw, 13)
        if (source == "Swap") {
            addExchangeFeat(display, level)
        } else {
            val list = rules.featList
            var index = -1
            for (i in list.indices) {
                val t = list[i].actualType
                if (t == BuildFeatType.GRANTED || t == BuildFeatType.ALTERNATE || t == BuildFeatType.EXCHANGE) {
                    continue
                }
                if (list[i].sourceOutput == source && list[i].level == level) {
                    index = i
                    break
                }
            }
            if (index < 0) {
                return
            }
            if (display.contains(" OR ")) {
                val parts = Vb.split(display, " OR ")
                val first = parseFeat(parts[0])
                if (first != null) {
                    applyFeat(index, first)
                    for (i in 1 until parts.size) {
                        var alternate = parseFeat(parts[i])
                        if (alternate == null) {
                            alternate = parseFeat(first.first.name + ": " + parts[i])
                        }
                        if (alternate != null) {
                            addAlternateFeat(index, alternate)
                        }
                    }
                }
            } else {
                applyFeat(index, parseFeat(display))
            }
        }
        if (twentyOrMax && level > 20) {
            max = true
        }
    }

    /**
     * ParseFeat (:831): a feat by name, "Feat: Selector", or a selector-only feat's selector; with
     * its selector number (0 if none or not found). Null if no feat.
     */
    private fun parseFeat(display: String): Pair<Feat, Int>? {
        var selector = 0
        var selectorName = ""
        var feat = rules.findFeatOrNull(display)
        if (feat == null) {
            val colon = display.indexOf(": ")
            if (colon >= 0) {
                selectorName = display.substring(colon + 2)
                feat = rules.findFeatOrNull(display.substring(0, colon)) ?: return null
            } else {
                for (f in data.feats) {
                    if (f.selectorOnly) {
                        for (s in f.selectors.indices) {
                            if (f.selectors[s].name == display) {
                                selector = s + 1
                                break
                            }
                        }
                        if (selector != 0) {
                            feat = f
                            selectorName = display
                            break
                        }
                    }
                }
            }
        }
        if (feat == null) {
            return null
        }
        if (selector == 0 && selectorName.isNotEmpty()) {
            for (s in feat.selectors.indices) {
                if (feat.selectors[s].name == selectorName) {
                    selector = s + 1
                    break
                }
            }
        }
        return Pair(feat, selector)
    }

    /**
     * ApplyFeat (:882): the feat in the listed slot, then the feat list again. A feat that wasn't
     * found empties the slot (VB's db.Feat(0) has no name).
     */
    private fun applyFeat(index: Int, found: Pair<Feat, Int>?) {
        val entry = rules.featList[index]
        entry.slot.featName = found?.first?.name ?: ""
        entry.slot.selector = found?.second ?: 0
        rules.initBuildFeats()
    }

    /** AddAlternateFeat (:898): an alternate slot copied from the listed one. */
    private fun addAlternateFeat(index: Int, found: Pair<Feat, Int>) {
        val entry = rules.featList[index]
        val slot = entry.slot.copy()
        slot.type = BuildFeatType.ALTERNATE
        slot.childType = entry.actualType
        slot.child = entry.index
        slot.featName = found.first.name
        slot.selector = found.second
        build.feats(BuildFeatType.ALTERNATE).add(slot)
    }

    /** AddExchangeFeat (:918): "New replaces Old" at [level], for the listed slot holding Old. */
    private fun addExchangeFeat(raw: String, level: Int) {
        if (raw.isEmpty()) {
            return
        }
        val parts = Vb.split(raw, " replaces ")
        if (parts.size != 2) {
            return
        }
        val newFeat = parseFeat(parts[0]) ?: return
        val oldFeat = parseFeat(parts[1]) ?: return
        val list = rules.featList
        for (entry in list) {
            if (entry.feat == oldFeat.first && entry.selector == oldFeat.second) {
                val slot = BuildFeat(BuildFeatType.EXCHANGE)
                slot.childType = entry.actualType
                slot.child = entry.index
                slot.featName = newFeat.first.name
                slot.selector = newFeat.second
                slot.level = level
                build.feats(BuildFeatType.EXCHANGE).add(slot)
                return
            }
        }
    }

    // ---------------------------------------------------------------- spells

    /**
     * ParseSpells (:955): a class name starts that class's spells; each line after is the next spell
     * level's spells, one per slot, "<Any>" for none. The mandatory slots (cleric cures, warlock
     * pacts) are left alone.
     *
     * VB quirks, kept: the spells are taken as written, so "Cure Light Wounds (3)" (the level the
     * output adds) is stored as the spell's name; fewer spells than slots, or a free spell, is a
     * run-time error (VB-BUGS 18l).
     */
    private fun parseSpells(raw: String) {
        val text = stripBrowserFormatting(raw)
        if (text.isEmpty()) {
            return
        }
        val c = data.findClass(text)
        if (c != null) {
            classSpell = c
            spellLevel = 0
            return
        }
        val current = classSpell ?: return
        spellLevel++
        val spell = Vb.split(text, ",")
        val spells = build.spellsFor(current) ?: throw ImportError("Subscript out of range")
        if (spellLevel > spells.levels.size) {
            throw ImportError("Subscript out of range")
        }
        val level = spells.level(spellLevel)
        for (i in 1..level.slots.size) {
            val slot = level.slots[i - 1]
            if (i - 1 >= spell.size) {
                throw ImportError("Subscript out of range") // strSpell(i - 1)
            }
            var name = Vb.trim(spell[i - 1])
            if (name == "<Any>") {
                name = ""
            }
            if (name.isNotEmpty() && (slot.type == SpellSlotType.STANDARD || slot.type == SpellSlotType.FREE)) {
                if (rules.isFreeSpell(current, name)) {
                    throw ImportError("This array is fixed or temporarily locked") // AddFreeSpellSlot inside With .Slot(i)
                }
                slot.spell = name
            }
        }
    }

    // ---------------------------------------------------------------- enhancements and destinies

    /**
     * ParseEnhancements (:1005): a tree's name (with " (n AP)") starts the tree; each line after is
     * its next tier, cores first: comma-separated abilities.
     */
    private fun parseEnhancements(raw: String) {
        var text = stripBrowserFormatting(raw)
        if (text.isEmpty()) {
            return
        }
        if (text.endsWith(" AP)")) {
            val at = text.lastIndexOf(" (")
            if (at < 0) {
                return
            }
            text = text.substring(0, at)
        }
        val found = seekTree(data.trees, text)
        if (found != null) {
            tree = found
            tier = 0
            addBuildTree(found)
            return
        }
        val current = tree ?: return
        val tierAbilities = tierOf(current, tier)
        for (item in Vb.split(text, ",")) {
            parseAbility(item, tierAbilities, build.trees[build.trees.size - 1])
        }
        if (tier == 5) {
            build.tier5 = current.name
        }
        tier++
    }

    /** db.Tree(n).Tier(t): past the tree's tiers is VB's subscript out of range. */
    private fun tierOf(t: Tree, n: Int): List<Ability> {
        if (n < 0 || n >= t.tiers.size) {
            throw ImportError("Subscript out of range")
        }
        return t.tiers[n]
    }

    /** SeekTree (basDataLoad.bas:1966): a binary search by name (the lists are sorted by name). */
    private fun seekTree(list: List<Tree>, name: String): Tree? {
        var first = 0
        var last = list.size - 1
        while (first <= last) {
            val m = (first + last) / 2
            val c = list[m].name.compareTo(name)
            if (c < 0) {
                first = m + 1
            } else if (c > 0) {
                last = m - 1
            } else {
                return list[m]
            }
        }
        return null
    }

    /**
     * AddBuildTree (:1034): the tree, with the class that has it and that class's heroic levels.
     * VB quirk, kept: a class tree no class in the split has gets the split's last class, with 0 levels.
     */
    private fun addBuildTree(t: Tree) {
        var source: CharacterClass? = null
        var levels = 0
        if (t.style == TreeStyle.CLASS) {
            var found = false
            for (split in rules.classSplit()) {
                source = split.characterClass
                if (split.characterClass.trees.contains(t.name)) {
                    found = true
                    break
                }
            }
            if (found) {
                for (i in 1..rules.heroicLevels()) {
                    if (build.levelClass(i) == source) {
                        levels++
                    }
                }
            }
        }
        val bt = BuildTree(t.name)
        bt.treeType = t.style
        bt.source = source
        bt.classLevels = levels
        build.trees.add(bt)
    }

    /**
     * ParseAbility (:1073): the ability as written; else without a rank (" I", " II", " III");
     * else split at ":" into ability and selector.
     */
    private fun parseAbility(raw: String, abilities: List<Ability>, target: BuildTree) {
        var name = raw
        var selector = ""
        var ranks = 1
        var found = findAbility(abilities, name, selector)
        if (found != null) {
            addAbility(target, found, ranks)
            return
        }
        val rank = getRanks(name)
        if (rank != null) {
            name = rank.first
            ranks = rank.second
            found = findAbility(abilities, name, selector)
            if (found != null) {
                addAbility(target, found, ranks)
                return
            }
        }
        val colon = name.indexOf(':')
        if (colon >= 0) {
            selector = mid(name, colon + 3)
            name = name.substring(0, colon)
            found = findAbility(abilities, name, selector)
            if (found != null) {
                addAbility(target, found, ranks)
            }
        }
    }

    /** FindAbility (:1107): (ability, selector), both 1-based, by abbreviation, or by a selector's name. */
    private fun findAbility(abilities: List<Ability>, name: String, selectorName: String): Pair<Int, Int>? {
        for (i in abilities.indices) {
            if (abilities[i].abbreviation == name) {
                var selector = 0
                if (selectorName.isNotEmpty()) {
                    for (s in abilities[i].selectors.indices) {
                        if (abilities[i].selectors[s].name == selectorName) {
                            selector = s + 1
                            break
                        }
                    }
                }
                return Pair(i + 1, selector)
            }
        }
        for (i in abilities.indices) {
            for (s in abilities[i].selectors.indices) {
                if (abilities[i].selectors[s].name == name) {
                    return Pair(i + 1, s + 1)
                }
            }
        }
        return null
    }

    /**
     * AddAbility (:1143): at the enhancement tier being read.
     * VB bug, kept (VB-BUGS 18m): destinies' abilities get this tier too, which ParseSection sets
     * to 1 at the Destiny heading.
     */
    private fun addAbility(target: BuildTree, found: Pair<Int, Int>, ranks: Int) {
        target.abilities.add(BuildAbility(tier, found.first - 1, ranks, found.second))
    }

    /** GetRanks (:1156): the name without " I", " II" or " III", and the ranks; null if none. */
    private fun getRanks(name: String): Pair<String, Int>? {
        if (name.endsWith(" I")) {
            return Pair(name.substring(0, name.length - 2), 1)
        }
        if (name.endsWith(" II")) {
            return Pair(name.substring(0, name.length - 3), 2)
        }
        if (name.endsWith(" III")) {
            return Pair(name.substring(0, name.length - 4), 3)
        }
        return null
    }

    /**
     * ParseDestiny (:1185): as [parseEnhancements], for destinies.
     * VB bug, kept (VB-BUGS 18m): the abilities go to the build's destiny numbered as the destiny is
     * among all destinies (`build.Destiny(mlngDestiny)`), which is usually past the build's own.
     */
    private fun parseDestiny(raw: String) {
        var text = stripBrowserFormatting(raw)
        if (text.isEmpty()) {
            return
        }
        if (text.endsWith(" AP)")) {
            val at = text.lastIndexOf(" (")
            if (at < 0) {
                return
            }
            text = text.substring(0, at)
        }
        val found = seekTree(data.destinies, text)
        if (found != null) {
            destiny = data.destinies.indexOf(found) + 1
            destinyTier = 0
            val bt = BuildTree(found.name)
            bt.treeType = found.style
            build.destinies.add(bt)
            return
        }
        if (destiny == 0) {
            return
        }
        val tierAbilities = tierOf(data.destinies[destiny - 1], destinyTier)
        for (item in Vb.split(text, ",")) {
            if (destiny > build.destinies.size) {
                throw ImportError("Subscript out of range")
            }
            parseAbility(item, tierAbilities, build.destinies[destiny - 1])
        }
        destinyTier++
    }

    /**
     * StripBrowserFormatting (:1235): trimmed; two spaces (a "." the import turned into a space) back
     * to ". "; no space after commas; no leading "•" or "n " list marker. Lines under 3 characters are dropped.
     */
    private fun stripBrowserFormatting(raw: String): String {
        var s = Vb.trim(raw)
        if (s.length < 3) {
            return ""
        }
        s = s.replace("  ", ". ")
        s = s.replace(", ", ",")
        if (s.startsWith("•")) {
            s = s.substring(1)
        }
        if (Vb.isNumeric(left(s, 1)) && mid(s, 2, 1) == " ") {
            s = s.substring(2)
        }
        return Vb.trim(s)
    }
}
