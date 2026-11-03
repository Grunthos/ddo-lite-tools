package ddolite.builder.output

import ddolite.builder.build.BuildFeatType
import ddolite.builder.build.BuildRules
import ddolite.builder.build.BuildTree
import ddolite.builder.build.EnhancementRules
import ddolite.builder.build.FeatTab
import ddolite.builder.build.GuideStepStyle
import ddolite.builder.build.GuideTree
import ddolite.builder.build.HEROIC_LEVELS
import ddolite.builder.build.MAX_LEVEL
import ddolite.builder.build.MAX_LEVELUPS
import ddolite.builder.build.SpellSlotType
import ddolite.builder.build.StatRules
import ddolite.builder.build.BLANK_ABILITY
import ddolite.builder.model.CharacterClass
import ddolite.builder.model.ClassColor
import ddolite.builder.model.RaceType
import ddolite.builder.model.Skill
import ddolite.builder.model.Stat
import ddolite.builder.model.Tree
import ddolite.builder.model.TreeColor
import ddolite.builder.model.TreeStyle

/**
 * The output colours, by the theme's Output group names (VB's `cfg.GetColor(cgeOutput, …)`).
 * The screen and the export look them up in the theme.
 */
enum class OutputColor { BACKGROUND, TEXT, TEXT_ERROR, TEXT_DIM, LIGHT_GRAY, RED, YELLOW, BLUE, PURPLE, GREEN, ORANGE }

/**
 * Which output: the main window for one window's sections (VB passes the window that's active),
 * or the export. Ported from `OutputEnum` (clsConfig.cls:178) without oeGear, oeRemember (the
 * caller remembers) and oeNone.
 */
enum class OutputView { ALL, OVERVIEW, STATS, SKILLS, FEATS, SPELLS, ENHANCEMENTS, DESTINY, DESTINY2, EXPORT }

/** `FeatOrderOutputEnum` (clsConfig.cls:173). */
enum class FeatOrderOutput { LEVEL, CHANNEL }

/** `SkillOrderOutputEnum` (clsConfig.cls:194). */
enum class SkillOrderOutput { ALPHABETICAL, RANKS_SPENT }

/**
 * The options the output reads (clsConfig). Defaults as `SetDefaults` (clsConfig.cls:428).
 *
 * @property section `cfg.OutputSection`: which section an export writes (ALL, or one of the
 *   windows' sections); the screen ignores it.
 * @property guideFirst the Enhancements window is open on its Leveling Guide tab, so the guide
 *   comes before the trees (OutputEnhancements reads `frmEnhancements.CurrentTab`).
 */
class OutputSettings(
    val featOrder: FeatOrderOutput = FeatOrderOutput.CHANNEL,
    val featShowAll: Boolean = false,
    val skillOrder: SkillOrderOutput = SkillOrderOutput.RANKS_SPENT,
    val section: OutputView = OutputView.ALL,
    val guideFirst: Boolean = false,
)

/** A piece of text on the screen in one style. */
class DisplayRun(val text: String, val color: OutputColor, val courier: Boolean, val bold: Boolean, val underline: Boolean)

/**
 * One line of the screen output. A list item's [prefix] ("1. ", "• ") ends at [listLevel] times
 * the list indent (VB's `CurrentX = level * TextWidth("XXXXX") - TextWidth(prefix)`).
 */
class DisplayLine(val runs: List<DisplayRun>, val listLevel: Int, val prefix: DisplayRun?)

/**
 * The build as text: the main window's view and the export. Ported from Source/Modules/Build/basOutput.bas
 * (GenerateOutput :154 and every routine it calls); [display] is VB's `mblnDisplay`.
 *
 * VB writes the screen output straight onto a PictureBox; here it's a list of [DisplayLine]s for
 * the window to draw. The export is the same text VB builds, joined with CRLF.
 *
 * Not copied: VB's export holds at most 256 lines (`ReDim mstrJoin(255)`) and would stop with
 * "Subscript out of range" past that.
 */
class BuildOutput(private val rules: BuildRules, private val format: OutputFormat, private val settings: OutputSettings) {
    private val build = rules.build
    private val data = rules.data

    private var view = OutputView.ALL
    private var display = true
    private var reddit = false
    private var sectionLines = 2
    private var colorHex: (OutputColor) -> String = { "" }
    private var hideColor = ""

    // The export's lines (mstrJoin, mlngCurrent).
    private val join = ArrayList<String>()
    private var current = 0

    // The screen's lines, and the PictureBox's state: colour, font and the line being written.
    private val lines = ArrayList<DisplayLine>()
    private var lineRuns = ArrayList<DisplayRun>()
    private var lineLevel = 0
    private var linePrefix: DisplayRun? = null
    private var foreColor = OutputColor.TEXT
    private var courier = false

    // mtypList: the stack of open lists (numbered?, items so far).
    private val listNumbered = ArrayList<Boolean>()
    private val listCount = ArrayList<Int>()
    private var colorOverride = false

    private class FeatOutput(val level: Int, val source: String, val featName: String, val tab: FeatTab, val replaces: String) {
        val alternates = ArrayList<String>()
    }

    private val featOutput = ArrayList<FeatOutput>()

    // ---------------------------------------------------------------- entry points

    /** The main window's output while [view]'s window is active. GenerateOutput (:154) for the screen. */
    fun display(view: OutputView): List<DisplayLine> {
        start(view, displayMode = true)
        generate()
        if (lineRuns.isNotEmpty() || linePrefix != null) {
            endLine()
        }
        return lines
    }

    /**
     * The export text: GenerateOutput(oeExport). [colorHex] gives a theme colour as "RRGGBB" for
     * the colour tags (`xp.ColorToHex(cfg.GetColor(cgeOutput, …))`).
     */
    fun export(colorHex: (OutputColor) -> String): String {
        this.colorHex = colorHex
        start(OutputView.EXPORT, displayMode = false)
        if (format.codes && format.color.enabled) {
            hideColor = format.color.open.replace("$", colorHex(OutputColor.BACKGROUND))
        }
        generate()
        // TrimBlanks, then each line right-trimmed and dotted, the wrapper, and a final line break.
        trimBlanks()
        if (current < 0) {
            return ""
        }
        while (join.size > current + 1) {
            join.removeAt(join.size - 1)
        }
        ensureLine()
        for (i in 0..current) {
            var line = rtrim(join[i])
            if (format.dots) {
                line = addDots(line)
            }
            join[i] = line
        }
        if (format.codes && format.wrapper.enabled) {
            join[0] = format.wrapper.open + join[0]
            join[current] = join[current] + format.wrapper.close
        }
        join[current] = join[current] + "\r\n"
        return join.joinToString("\r\n")
    }

    private fun start(view: OutputView, displayMode: Boolean) {
        this.view = view
        display = displayMode
        reddit = format.reddit && !display
        sectionLines = if (reddit) 1 else 2
        current = 0
        join.clear()
        lines.clear()
        lineRuns = ArrayList()
        lineLevel = 0
        linePrefix = null
        foreColor = OutputColor.TEXT
        courier = false
        listNumbered.clear()
        listCount.clear()
        colorOverride = false
    }

    private fun generate() {
        outputHeader()
        outputClassSplit()
        outputStats()
        outputSkills()
        outputFeats()
        outputSpells()
        outputEnhancements()
        outputDestinies()
    }

    /** True if an export of this section is wanted (`cfg.OutputSection` is All or [section]). */
    private fun exportSection(section: OutputView): Boolean = settings.section == OutputView.ALL || settings.section == section

    // ---------------------------------------------------------------- header

    /** OutputHeader (:291): the name, the class split and epic levels, the alignment and race. */
    private fun outputHeader() {
        if (!display && !exportSection(OutputView.OVERVIEW)) {
            return
        }
        val pipe = if (reddit) "|" else ""
        if (build.buildName.isNotEmpty()) {
            outputText(pipe + build.buildName + pipe, bold = !reddit)
            if (reddit) {
                outputText("|:--|:--")
            }
        }
        val split = classSplit()
        if (split.isNotEmpty()) {
            var levels = split[0].levels.toString()
            var names = split[0].characterClass.name
            for (i in 1 until split.size) {
                levels = levels + "/" + split[i].levels
                names = names + "/" + split[i].characterClass.name
            }
            var text = if (split.size == 1) "$names $levels" else "$levels $names"
            if (build.maxLevels != MAX_LEVEL) {
                val epic = build.maxLevels - 20
                if (epic > 0) {
                    text = "$text, Epic $epic"
                }
            }
            if (reddit) {
                outputText("|$text|")
                if (build.buildName.isEmpty()) {
                    outputText("|:--|:--")
                }
            } else {
                outputText(text)
            }
        }
        val alignment = build.alignment?.displayName ?: ""
        val race = build.race?.name ?: ""
        val text = pipe + Vbx.trim("$alignment $race") + pipe
        if (text.isNotEmpty()) {
            outputText(text)
        }
        // VB: If frmMain.picBuild.CurrentY <> 0. An export reads the screen's last drawing, which has
        // something on it whenever a build is open.
        if (!display || lines.isNotEmpty() || lineRuns.isNotEmpty()) {
            blankLine(sectionLines)
        }
    }

    // ---------------------------------------------------------------- class split

    private class SplitEntry(val characterClass: CharacterClass, val levels: Int) {
        var color: OutputColor? = null
        var initial = ""
        var buildClass = 0
    }

    /**
     * The classes taken, most levels first, with the colours for the level order and skills and
     * the initials for the skills header. Ported from `GetClassSplit` (basBuild.bas:139) with
     * `ComplementColor` (:214): the main class has no colour; with three classes the splash with
     * the later Overview slot gets a colour that goes with the other's.
     */
    private fun classSplit(): List<SplitEntry> {
        val result = ArrayList<SplitEntry>()
        for (s in rules.classSplit()) {
            val e = SplitEntry(s.characterClass, s.levels)
            e.color = classColor(s.characterClass.color)
            result.add(e)
        }
        if (result.isEmpty()) {
            return result
        }
        result[0].color = null
        if (result.size == 3) {
            for (i in 0..2) {
                for (j in 0..2) {
                    if (result[i].characterClass == build.buildClasses[j]) {
                        result[i].buildClass = j
                        break
                    }
                }
            }
            val splash = if (result[1].buildClass > result[2].buildClass) 1 else 2
            result[splash].color = complementColor(result[3 - splash].color, result[splash].color)
        }
        var initial = 0
        if (result.size > 1) {
            if (initialOf(result[0].characterClass, 0) == initialOf(result[1].characterClass, 0)) {
                initial = 1
            }
        }
        if (result.size == 3 && initial == 0) {
            val third = initialOf(result[2].characterClass, 0)
            if (third == initialOf(result[0].characterClass, 0) || third == initialOf(result[1].characterClass, 0)) {
                initial = 1
            }
        }
        for (e in result) {
            e.initial = initialOf(e.characterClass, initial)
        }
        return result
    }

    private fun initialOf(c: CharacterClass, index: Int): String = c.initials.getOrNull(index) ?: ""

    /** A class's `Color:`; VB's default (0) for none is cveBackground. */
    private fun classColor(color: ClassColor?): OutputColor = when (color) {
        ClassColor.RED -> OutputColor.RED
        ClassColor.GREEN -> OutputColor.GREEN
        ClassColor.BLUE -> OutputColor.BLUE
        ClassColor.YELLOW -> OutputColor.YELLOW
        ClassColor.PURPLE -> OutputColor.PURPLE
        ClassColor.ORANGE -> OutputColor.ORANGE
        null -> OutputColor.BACKGROUND
    }

    /**
     * ComplementColor (basBuild.bas:214). VB returns 0 (cveBackground) for a first colour that
     * isn't one of the six.
     */
    private fun complementColor(color: OutputColor?, preferred: OutputColor?): OutputColor? {
        return when (color) {
            OutputColor.RED -> if (preferred == OutputColor.RED || preferred == OutputColor.ORANGE || preferred == OutputColor.PURPLE) OutputColor.BLUE else preferred
            OutputColor.BLUE -> if (preferred == OutputColor.BLUE || preferred == OutputColor.PURPLE) OutputColor.RED else preferred
            OutputColor.GREEN -> if (preferred == OutputColor.GREEN || preferred == OutputColor.YELLOW) OutputColor.ORANGE else preferred
            OutputColor.ORANGE -> if (preferred == OutputColor.ORANGE || preferred == OutputColor.RED || preferred == OutputColor.YELLOW) OutputColor.GREEN else preferred
            OutputColor.YELLOW -> if (preferred == OutputColor.YELLOW || preferred == OutputColor.ORANGE || preferred == OutputColor.GREEN) OutputColor.PURPLE else preferred
            OutputColor.PURPLE -> if (preferred == OutputColor.PURPLE || preferred == OutputColor.BLUE || preferred == OutputColor.RED) OutputColor.YELLOW else preferred
            else -> OutputColor.BACKGROUND
        }
    }

    /** OutputClassSplit (:339): the level order, in four columns of five, for multiclass builds. */
    private fun outputClassSplit() {
        if (!display && !exportSection(OutputView.OVERVIEW)) {
            return
        }
        if (!validClassSplit()) {
            return
        }
        val split = classSplit()
        if (split.size < 2) {
            return
        }
        val colors = HashMap<CharacterClass, OutputColor?>()
        for (e in split) {
            colors[e.characterClass] = e.color
        }
        outputTitle("Level Order")
        blankLine()
        val lastLevel = rules.heroicLevels()
        if (reddit) {
            var columns = 1 + (build.maxLevels - 1) / 5
            if (columns > 4) {
                columns = 4
            }
            outputText("|||||".substring(0, columns + 1))
            outputText("|:--|:--|:--|:--|:--".substring(0, (columns + 1) * 4))
        } else {
            outputText("", newLine = false, special = Special.COURIER_BEGIN)
        }
        for (i in 1..5) {
            var j = 0
            while (j <= 15) {
                val level = i + j
                if (build.maxLevels >= level) {
                    val c = levelClass(level)
                    if (reddit) {
                        outputText("|" + level + ") " + (c?.name ?: ""), newLine = false)
                    } else {
                        var text = if (j == 0) level.toString() else alignText(level.toString(), 2, Align.RIGHT)
                        outputText("$text. ", newLine = false)
                        text = c?.name ?: ""
                        if (level + 5 <= lastLevel) {
                            text = alignText(text, MAX_CLASS_NAME.length + 1, Align.LEFT)
                        }
                        outputText(text, newLine = false, color = colors[c])
                    }
                }
                j += 5
            }
            if (i == 5 || i >= lastLevel) {
                if (reddit) {
                    outputText("|")
                } else {
                    outputText("", special = Special.COURIER_END)
                }
                break
            } else {
                if (reddit) {
                    outputText("|")
                } else {
                    outputText("")
                }
            }
        }
        blankLine(sectionLines)
    }

    /** A heroic level's class, or null past level 20 or with none chosen (GetClassName(ceAny) is ""). */
    private fun levelClass(level: Int): CharacterClass? {
        if (level < 1 || level > HEROIC_LEVELS) {
            return null
        }
        return build.levelClass(level)
    }

    /**
     * ValidClassSplit (:406).
     *
     * VB quirk, kept: the Destinies window passes oeDestiny2, which no section accepts, so while
     * it's active the main window shows only the header.
     */
    private fun validClassSplit(): Boolean {
        if (display && view != OutputView.ALL) {
            return view == OutputView.OVERVIEW || view == OutputView.STATS || view == OutputView.SKILLS ||
                view == OutputView.FEATS || view == OutputView.SPELLS || view == OutputView.ENHANCEMENTS
        }
        return true
    }

    // ---------------------------------------------------------------- stats

    /** OutputStats (:420): the point-buy columns, tomes and level-ups. */
    private fun outputStats() {
        if (!display && !exportSection(OutputView.STATS)) {
            return
        }
        if (!validStats()) {
            return
        }
        val race = build.race ?: return
        val show = BooleanArray(6)
        var columns = 0
        for (i in 0..3) {
            show[i] = build.includePoints[i] && build.statTotals[i] == rules.buildPointsFor(ddolite.builder.build.BuildPoints.entries[i])
            if (show[i]) {
                columns++
            }
        }
        for (stat in Stat.entries) {
            if (capTome(build.tomes[stat.ordinal]) > 0) {
                show[4] = true
                columns++
                break
            }
        }
        for (i in 1..MAX_LEVELUPS) {
            if (build.levelups[i] != null) {
                if (capLevelup(i * 4) > 0) {
                    show[5] = true
                    columns++
                }
                break
            }
        }
        if (columns == 0) {
            return
        }
        outputTitle("Stats")
        if (reddit) {
            outputStatsReddit(show, columns + 2, race.stats)
            return
        }
        // Header
        outputText(spaces(15), newLine = false, special = Special.COURIER_BEGIN)
        for (points in 0..3) {
            if (show[points]) {
                outputText(rules.buildPointsFor(ddolite.builder.build.BuildPoints.entries[points]).toString() + "pt     ", newLine = false, color = statColumnColor(points), bold = statColumnBold(points))
            }
        }
        if (show[4]) {
            outputText("Tome     ", newLine = false)
        }
        if (show[5]) {
            outputText("Level Up", newLine = false)
        }
        outputText("")
        // Underlines
        outputText(spaces(15), newLine = false)
        for (points in 0..4) {
            if (show[points]) {
                outputText("----     ", newLine = false, color = statColumnColor(points), bold = statColumnBold(points))
            }
        }
        if (show[5]) {
            outputText("--------", newLine = false)
        }
        outputText("")
        // Body
        for (stat in Stat.entries) {
            val number = stat.ordinal + 1
            outputText((stat.displayName + spaces(15)).substring(0, 15), newLine = false)
            for (points in 0..3) {
                if (show[points]) {
                    val item = StatRules(rules).baseStat(race.stats[stat.ordinal], build.statPoints[points][stat.ordinal]).toString()
                    outputText(alignText(item, 3, Align.RIGHT) + "      ", newLine = false, color = statColumnColor(points), bold = statColumnBold(points))
                }
            }
            if (show[4]) {
                if (build.tomes[stat.ordinal] != 0) {
                    outputText(" +" + capTome(build.tomes[stat.ordinal]) + "      ", newLine = false)
                } else {
                    outputText("         ", newLine = false)
                }
            }
            val levelup = build.levelups[number]
            if (levelup != null && build.maxLevels >= number * 4) {
                val item = (number * 4).toString() + ": " + levelup.abbreviation.uppercase()
                outputText(alignText(item, 7, Align.RIGHT), newLine = false, color = levelupColor(number))
            }
            outputText("")
        }
        // Level-ups from 28 on, one line each
        var i = 28
        while (i <= build.maxLevels) {
            outputText(spaces(15), newLine = false)
            for (column in 0..4) {
                if (show[column]) {
                    outputText(spaces(9), newLine = false, bold = build.buildPoints.ordinal == column)
                }
            }
            val levelup = build.levelups[i / 4]
            val text = i.toString() + ": " + (levelup?.abbreviation?.uppercase() ?: "")
            outputText(text, color = levelupColor(i / 4))
            i += 4
        }
        blankLine()
    }

    /** OutputStatsReddit (:525). */
    private fun outputStatsReddit(show: BooleanArray, columns: Int, raceStats: List<Int>) {
        blankLine()
        var text = "||"
        for (points in 0..3) {
            if (show[points]) {
                val bold = if (build.buildPoints.ordinal == points) "**" else ""
                text = text + bold + rules.buildPointsFor(ddolite.builder.build.BuildPoints.entries[points]) + "pt" + bold + "|"
            }
        }
        if (show[4]) {
            text = "${text}Tome|"
        }
        if (show[5]) {
            text = "${text}Level Up|"
        }
        outputText(text)
        text = "|:--"
        for (i in 0..3) {
            if (show[i]) {
                text = "$text|--:"
            }
        }
        if (show[4]) {
            text = "$text|:--:"
        }
        if (show[5]) {
            text = "$text|--:"
        }
        text = "$text|:--"
        outputText(text)
        val mainStat = identifyMainStat()
        for (stat in Stat.entries) {
            val number = stat.ordinal + 1
            text = "|" + stat.displayName + "|"
            for (points in 0..3) {
                if (show[points]) {
                    val bold = if (build.buildPoints.ordinal == points) "**" else ""
                    val item = StatRules(rules).baseStat(raceStats[stat.ordinal], build.statPoints[points][stat.ordinal]).toString()
                    text = "$text$bold$item$bold|"
                }
            }
            if (show[4]) {
                if (build.tomes[stat.ordinal] != 0) {
                    text = text + "+" + capTome(build.tomes[stat.ordinal])
                }
                text = "$text|"
            }
            if (show[5]) {
                val levelup = build.levelups[number]
                if (levelup != null && build.maxLevels >= number * 4) {
                    val bold = if (levelup == mainStat) "" else "**"
                    text = text + bold + (number * 4) + ": " + levelup.abbreviation.uppercase() + bold
                }
                text = "$text|"
            }
            outputText(text)
        }
        var i = 28
        while (i <= build.maxLevels) {
            val levelup = build.levelups[i / 4]
            val bold = if (levelup == mainStat) "" else "**"
            text = bold + i + ": " + (levelup?.abbreviation?.uppercase() ?: "") + bold
            outputText("||||||||".substring(0, columns - 1) + text + "|")
            i += 4
        }
        blankLine()
    }

    /**
     * The stat levelled most (ties: the first). IdentifyMainStat (:601).
     *
     * VB quirk, kept: level-ups not chosen count towards stat 0, which isn't a stat, so they never win.
     */
    private fun identifyMainStat(): Stat? {
        val counts = IntArray(Stat.entries.size)
        for (i in 1..MAX_LEVELUPS) {
            val s = build.levelups[i]
            if (s != null) {
                counts[s.ordinal]++
            }
        }
        var high = 0
        var main: Stat? = null
        for (stat in Stat.entries) {
            if (high < counts[stat.ordinal]) {
                high = counts[stat.ordinal]
                main = stat
            }
        }
        return main
    }

    /** StatColumnColor (:619): the preferred column (and tomes, level-ups) in text colour, the rest dim. */
    private fun statColumnColor(column: Int): OutputColor =
        if (build.buildPoints.ordinal == column || column > 3) OutputColor.TEXT else OutputColor.TEXT_DIM

    /** StatColumnBold (:627): the preferred column is bold when colours aren't shown. */
    private fun statColumnBold(column: Int): Boolean =
        build.buildPoints.ordinal == column && !(format.textColors && format.color.enabled)

    /** ValidStats (:631). */
    private fun validStats(): Boolean {
        if (display && view != OutputView.ALL) {
            return view == OutputView.OVERVIEW || view == OutputView.STATS || view == OutputView.SKILLS || view == OutputView.FEATS
        }
        return true
    }

    /** CapTome (:641). */
    private fun capTome(tome: Int): Int {
        val cap = StatRules(rules).tomeLevel(build.maxLevels)
        return if (tome > cap) cap else tome
    }

    /** CapLevelup (:648). */
    private fun capLevelup(levelup: Int): Int {
        val cap = build.maxLevels / 4
        return if (levelup > cap) cap else levelup
    }

    /**
     * LevelupColor (:655): each stat's colour, unless "All Levels" is set (then none).
     * VB returns 0 (cveBackground's index, read as black) for a level-up not chosen.
     */
    private fun levelupColor(index: Int): OutputColor? {
        if (build.levelups[0] != null) {
            return null
        }
        return when (build.levelups[index]) {
            Stat.STR -> OutputColor.ORANGE
            Stat.DEX -> OutputColor.RED
            Stat.CON -> OutputColor.GREEN
            Stat.INT -> OutputColor.BLUE
            Stat.WIS -> OutputColor.YELLOW
            Stat.CHA -> OutputColor.PURPLE
            null -> null
        }
    }

    // ---------------------------------------------------------------- skills

    /** VB's `Skill` grid as InitBuildSkills (basBuild.bas:394) fills it, for the output. */
    private inner class SkillOutput {
        val levels = rules.heroicLevels()
        val native = Array(Skill.entries.size) { IntArray(levels + 1) }
        val maxRanks = Array(Skill.entries.size) { IntArray(levels + 1) }
        val rowRanks = IntArray(Skill.entries.size)
        val colPoints = IntArray(levels + 1)
        val colMax = IntArray(levels + 1)
        val colInitial = Array(levels + 1) { "" }
        val colColor = arrayOfNulls<OutputColor>(levels + 1)

        init {
            val split = classSplit()
            val initials = HashMap<CharacterClass, String>()
            val colors = HashMap<CharacterClass, OutputColor?>()
            for (e in split) {
                initials[e.characterClass] = e.initial
                colors[e.characterClass] = e.color
            }
            for (level in 1..levels) {
                val c = build.levelClass(level)
                colInitial[level] = if (c == null) "" else initials[c] ?: ""
                colColor[level] = if (c == null) null else colors[c]
            }
            val everNative = BooleanArray(Skill.entries.size)
            for (skill in Skill.entries) {
                for (level in 1..levels) {
                    val c = build.levelClass(level)
                    val n = if (c != null && c.isNativeSkill(skill)) 2 else 1
                    native[skill.ordinal][level] = n
                    if (n == 2) {
                        everNative[skill.ordinal] = true
                    }
                    var max = level + 3
                    if (everNative[skill.ordinal]) {
                        max *= 2
                    }
                    maxRanks[skill.ordinal][level] = max
                }
            }
            val stats = StatRules(rules)
            for (level in 1..levels) {
                var points = 0
                for (skill in Skill.entries) {
                    points += build.skills[skill.ordinal][level - 1]
                }
                colPoints[level] = points
                var max = (build.levelClass(level)?.skillPoints ?: 0) + (build.race?.skillPoints ?: 0) + stats.stat(Stat.INT, level, modifier = true)
                if (max < 1) {
                    max = 1
                }
                if (level == 1) {
                    max *= 4
                }
                colMax[level] = max
            }
            for (skill in Skill.entries) {
                var ranks = 0
                for (level in 1..levels) {
                    ranks += build.skills[skill.ordinal][level - 1] * native[skill.ordinal][level]
                }
                rowRanks[skill.ordinal] = ranks
            }
        }

        fun ranks(skill: Skill, level: Int): Int = build.skills[skill.ordinal][level - 1] * native[skill.ordinal][level]
    }

    /** OutputSkills (:674): the skill points grid for the skills with ranks. */
    private fun outputSkills() {
        if (!display && !exportSection(OutputView.SKILLS)) {
            return
        }
        val grid = SkillOutput()
        val result = validSkills(grid)
        val valid = result.first
        if (valid == Valid.SKIP || valid == Valid.EMPTY) {
            return
        }
        val classRow = result.second
        val rows = result.third
        val levels = rules.heroicLevels()
        val dashLine = "         " + "-".repeat(levels * 3)
        outputTitle("Skills", error = if (valid == Valid.ERRORS) "(Errors)" else "")
        var font = Special.COURIER_BEGIN
        if (reddit) {
            outputSkillsReddit(grid, rows, classRow, levels, valid == Valid.COMPLETE)
            return
        }
        if (classRow) {
            outputText(spaces(8), newLine = false, special = font)
            font = Special.NONE
            for (col in 1..levels) {
                outputText(alignText(grid.colInitial[col], 3, Align.RIGHT), newLine = col == levels, color = grid.colColor[col])
            }
        }
        outputText(spaces(8), newLine = false, special = font)
        for (col in 1..levels) {
            outputText(alignText(col.toString(), 3, Align.RIGHT), newLine = col == levels, color = grid.colColor[col])
        }
        outputText(dashLine)
        for (skill in rows) {
            outputText(alignText(skill.abbreviation, 9, Align.LEFT), newLine = false)
            for (col in 1..levels) {
                outputText(formatRanks(grid.ranks(skill, col)), newLine = false, color = grid.colColor[col])
            }
            outputText(" " + formatRanks(grid.rowRanks[skill.ordinal]))
        }
        outputText(dashLine)
        outputText(spaces(8), newLine = false)
        for (col in 1 until levels) {
            outputText(alignText(grid.colPoints[col].toString(), 3, Align.RIGHT), newLine = false, color = grid.colColor[col])
        }
        // VB passes Skill.Col(lngCol).Color after its loop, so the last column takes level 20's colour.
        if (valid == Valid.COMPLETE) {
            outputText(alignText(grid.colPoints[levels].toString(), 3, Align.RIGHT), color = grid.colColor[levels], special = Special.COURIER_END)
        } else {
            outputText(alignText(grid.colPoints[levels].toString(), 3, Align.RIGHT), color = grid.colColor[levels])
            outputText(alignText("Max", 8, Align.LEFT), newLine = false)
            for (col in 1 until levels) {
                outputText(alignText(grid.colMax[col].toString(), 3, Align.RIGHT), newLine = false, color = grid.colColor[col])
            }
            outputText(alignText(grid.colMax[levels].toString(), 3, Align.RIGHT), color = grid.colColor[levels], special = Special.COURIER_END)
        }
        blankLine(2)
    }

    /** OutputSkillsReddit (:751). */
    private fun outputSkillsReddit(grid: SkillOutput, rows: List<Skill>, classRow: Boolean, levels: Int, complete: Boolean) {
        blankLine()
        var cells = Array(levels + 4) { "" }
        for (i in 1..levels) {
            cells[i + 1] = i.toString()
        }
        outputText(cells.joinToString("|"))
        cells = Array(levels + 4) { "" }
        outputText("|:--" + cells.joinToString("|--:"))
        if (classRow) {
            cells = Array(levels + 4) { "" }
            for (i in 1..levels) {
                cells[i + 1] = grid.colInitial[i]
            }
            outputText(cells.joinToString("|"))
        }
        for (skill in rows) {
            cells[1] = skill.abbreviation
            for (col in 1..levels) {
                cells[col + 1] = Vbx.trim(formatRanks(grid.ranks(skill, col)))
            }
            cells[levels + 2] = Vbx.trim(formatRanks(grid.rowRanks[skill.ordinal]))
            outputText(cells.joinToString("|"))
        }
        cells = Array(levels + 4) { "" }
        for (i in 1..levels) {
            cells[i + 1] = grid.colPoints[i].toString()
        }
        outputText(cells.joinToString("|"))
        if (!complete) {
            cells[1] = "Max"
            for (i in 1..levels) {
                cells[i + 1] = grid.colMax[i].toString()
            }
            outputText(cells.joinToString("|"))
        }
        blankLine()
    }

    /**
     * ValidSkills (:804): skip, empty, errors (a level over its points, or a skill over its rank cap
     * at any level), incomplete (points left), or complete; the class row when multiclassed; and the
     * skills with ranks, in the output order.
     */
    private fun validSkills(grid: SkillOutput): Triple<Valid, Boolean, List<Skill>> {
        if (display && view != OutputView.ALL && view != OutputView.OVERVIEW && view != OutputView.SKILLS) {
            return Triple(Valid.SKIP, false, emptyList())
        }
        val splitSize = classSplit().size
        if (splitSize == 0) {
            return Triple(Valid.EMPTY, false, emptyList())
        }
        val classRow = splitSize == 2 || splitSize == 3
        val rows = ArrayList<Skill>()
        for (row in 1..Skill.entries.size) {
            val skill = initialSkillOrder(row)
            if (grid.rowRanks[skill.ordinal] > 0) {
                rows.add(skill)
            }
        }
        if (rows.isEmpty()) {
            return Triple(Valid.EMPTY, classRow, rows)
        }
        var result = Valid.COMPLETE
        for (col in 1..rules.heroicLevels()) {
            if (grid.colPoints[col] > grid.colMax[col]) {
                result = Valid.ERRORS
                break
            } else if (grid.colPoints[col] < grid.colMax[col]) {
                result = Valid.INCOMPLETE
            }
        }
        if (result != Valid.ERRORS) {
            check@ for (skill in Skill.entries) {
                var ranks = 0
                for (col in 1..rules.heroicLevels()) {
                    ranks += grid.ranks(skill, col)
                    if (ranks > grid.maxRanks[skill.ordinal][col]) {
                        result = Valid.ERRORS
                        break@check
                    }
                }
            }
        }
        if (settings.skillOrder == SkillOrderOutput.RANKS_SPENT) {
            sortRows(rows, grid)
        }
        return Triple(result, classRow, rows)
    }

    /**
     * InitialSkillOrder (:873): alphabetical, or (for "ranks spent") an order that keeps related
     * skills together when their ranks are equal.
     */
    private fun initialSkillOrder(row: Int): Skill {
        if (settings.skillOrder == SkillOrderOutput.ALPHABETICAL) {
            return Skill.entries[row - 1]
        }
        return when (row) {
            1 -> Skill.PERFORM
            2 -> Skill.CONCENTRATION
            3 -> Skill.HEAL
            4 -> Skill.REPAIR
            5 -> Skill.SPELLCRAFT
            6 -> Skill.DISABLE_DEVICE
            7 -> Skill.OPEN_LOCK
            8 -> Skill.SEARCH
            9 -> Skill.SPOT
            10 -> Skill.LISTEN
            11 -> Skill.BLUFF
            12 -> Skill.DIPLOMACY
            13 -> Skill.INTIMIDATE
            14 -> Skill.BALANCE
            15 -> Skill.JUMP
            16 -> Skill.HIDE
            17 -> Skill.MOVE_SILENTLY
            18 -> Skill.SWIM
            19 -> Skill.TUMBLE
            20 -> Skill.HAGGLE
            else -> Skill.UMD
        }
    }

    /** SortRows (:906): a stable insertion sort, most ranks first. */
    private fun sortRows(rows: ArrayList<Skill>, grid: SkillOutput) {
        for (i in 1 until rows.size) {
            val hold = rows[i]
            var j = i
            while (j >= 1) {
                if (grid.rowRanks[hold.ordinal] > grid.rowRanks[rows[j - 1].ordinal]) {
                    rows[j] = rows[j - 1]
                } else {
                    break
                }
                j--
            }
            rows[j] = hold
        }
    }

    /** FormatRanks (:920): half ranks as " 3½", three characters wide. */
    private fun formatRanks(ranks: Int): String {
        val trail = if (ranks % 2 == 0) " " else "½"
        return when {
            ranks < 1 -> "   "
            ranks == 1 -> " ½ "
            ranks <= 19 -> " " + (ranks / 2) + trail
            else -> (ranks / 2).toString() + trail
        }
    }

    // ---------------------------------------------------------------- feats

    /** OutputFeats (:936): by level, or by channel (General first, Deity or Granted last). */
    private fun outputFeats() {
        if (!display && !exportSection(OutputView.FEATS)) {
            return
        }
        val valid = validFeats()
        if (valid == Valid.SKIP) {
            return
        }
        if (valid == Valid.EMPTY && view != OutputView.FEATS) {
            return
        }
        outputTitle("Feats", error = if (valid == Valid.ERRORS) "(Errors)" else "")
        blankLine()
        if (reddit) {
            outputText("|||")
            outputText("|:--|:--|:--")
        }
        if (settings.featOrder == FeatOrderOutput.LEVEL) {
            for (i in featOutput.indices) {
                if (reddit) outputFeatReddit(i) else outputFeat(i)
            }
            blankLine()
        } else {
            val last = if (settings.featShowAll) FeatTab.GRANTED else FeatTab.DEITY
            var started = false
            for (tab in FeatTab.entries) {
                if (tab.ordinal < FeatTab.GENERAL.ordinal || tab.ordinal > last.ordinal) {
                    continue
                }
                var count = 0
                var found = featOutput.size
                if (tab != FeatTab.GENERAL) {
                    for (i in featOutput.indices) {
                        if (featOutput[i].tab == tab && featOutput[i].featName.isNotEmpty()) {
                            found = i
                            break
                        }
                    }
                } else {
                    found = 0
                }
                if (found < featOutput.size) {
                    if (reddit && started) {
                        outputText("|||")
                    }
                    started = true
                    for (i in featOutput.indices) {
                        if (featOutput[i].tab == tab) {
                            if (reddit) outputFeatReddit(i) else outputFeat(i)
                            count++
                        }
                    }
                    if (count != 0 && !reddit) {
                        blankLine()
                    }
                }
            }
        }
        blankLine()
    }

    /** OutputFeat (:1004): "12 Fighter: Feat", "OR" alternates, or "Swap: Feat replaces Feat". */
    private fun outputFeat(index: Int) {
        val f = featOutput[index]
        val exchange = f.source == "Swap"
        val level = alignText(f.level.toString(), 2, Align.RIGHT)
        val source = alignText(f.source, 7, Align.LEFT)
        if (exchange) {
            outputText("$level ", newLine = false, special = Special.COURIER_BEGIN)
            outputText(source, newLine = false, color = OutputColor.BLUE)
            outputText(": ", newLine = false, special = Special.COURIER_END)
        } else {
            outputText("$level $source: ", newLine = false, special = Special.COURIER)
        }
        if (f.alternates.isEmpty()) {
            if (exchange) {
                outputText(f.featName, newLine = false)
                outputText(" replaces ", newLine = false, color = OutputColor.TEXT_DIM)
                outputText(f.replaces)
            } else {
                outputText(f.featName)
            }
        } else {
            outputText(f.featName, newLine = false)
            for (i in f.alternates.indices) {
                outputText(" OR ", newLine = false, color = OutputColor.TEXT_DIM)
                outputText(f.alternates[i], newLine = i == f.alternates.size - 1)
            }
        }
    }

    /** OutputFeatReddit (:1039). */
    private fun outputFeatReddit(index: Int) {
        val f = featOutput[index]
        val exchange = f.source == "Swap"
        var source = f.level.toString()
        if (f.source.isNotEmpty()) {
            source = if (exchange) "$source **Swap**" else source + " " + f.source
        }
        var feat = f.featName
        if (exchange) {
            feat = feat + " **replaces** " + f.replaces
        } else if (f.alternates.isNotEmpty()) {
            for (alternate in f.alternates) {
                feat = "$feat **OR** $alternate"
            }
        }
        outputText("|$source|$feat|")
    }

    /** ValidFeats (:1063) with AddFeatOutput (:1109): the feat list (rebuilt) gathered for output. */
    private fun validFeats(): Valid {
        if (display && view != OutputView.ALL && view != OutputView.FEATS) {
            return Valid.SKIP
        }
        if (build.levelClass(1) == null) {
            return Valid.SKIP
        }
        rules.initFeatList()
        var empty = true
        var errors = false
        var complete = true
        featOutput.clear()
        val list = rules.featList
        for (entry in list) {
            if (!settings.featShowAll && entry.actualType == BuildFeatType.GRANTED) {
                continue
            }
            val state = if (entry.errorState) Valid.ERRORS else if (entry.feat == null) Valid.EMPTY else Valid.COMPLETE
            if (entry.actualType == BuildFeatType.ALTERNATE) {
                // VB adds to the entry before; with none yet, it's entry 0, which the output never reads.
                if (featOutput.isNotEmpty()) {
                    featOutput[featOutput.size - 1].alternates.add(entry.displayAlternate)
                }
            } else {
                var replaces = ""
                if (entry.actualType == BuildFeatType.EXCHANGE && entry.exchangeIndex >= 0) {
                    replaces = list[entry.exchangeIndex].display
                }
                featOutput.add(FeatOutput(entry.level, entry.sourceOutput, entry.display, entry.tab, replaces))
            }
            when (state) {
                Valid.EMPTY -> complete = false
                Valid.ERRORS -> {
                    empty = false
                    errors = true
                    complete = false
                }
                Valid.COMPLETE -> empty = false
                else -> {}
            }
        }
        if (empty) {
            return Valid.EMPTY
        }
        if (errors) {
            return Valid.ERRORS
        }
        if (complete) {
            return Valid.COMPLETE
        }
        return Valid.INCOMPLETE
    }

    // ---------------------------------------------------------------- spells

    /** OutputSpells (:1151): each casting class's spells, a numbered line per spell level. */
    private fun outputSpells() {
        if (!display && !exportSection(OutputView.SPELLS)) {
            return
        }
        val result = validSpells()
        val valid = result.first
        if (valid == Valid.SKIP) {
            return
        }
        if (valid == Valid.EMPTY && view != OutputView.SPELLS) {
            return
        }
        val split = classSplit()
        val castingClasses = result.second
        outputTitle("Spells")
        blankLine()
        if (reddit) {
            outputSpellsReddit(split)
            return
        } else if (castingClasses == 1 && !display) {
            blankLine()
        }
        for (e in split) {
            val spells = build.spellsFor(e.characterClass) ?: continue
            val lastLevel = spells.maxSpellLevel
            if (lastLevel != 0) {
                if (castingClasses > 1) {
                    outputText(e.characterClass.name)
                }
                listBegin(true)
                for (level in 1..lastLevel) {
                    listStartLine()
                    outputText(spellLine(spells.level(level).slots), newLine = level < lastLevel)
                }
                listEnd()
                blankLine()
            }
        }
        if (display) blankLine(2) else blankLine()
    }

    /** One spell level's slots: "Spell (3), <Any>, …"; an empty slot is blank on screen. */
    private fun spellLine(slots: List<ddolite.builder.build.BuildSpellSlot>): String {
        val text = StringBuilder()
        for (i in slots.indices) {
            val slot = slots[i]
            if (slot.spell.isEmpty() && !display) {
                text.append("<Any>")
            } else {
                text.append(slot.spell)
                if (slot.spell.isNotEmpty()) {
                    text.append(" (").append(slot.level).append(")")
                }
            }
            if (i < slots.size - 1) {
                text.append(", ")
            }
        }
        return text.toString()
    }

    /** OutputSpellsReddit (:1209). */
    private fun outputSpellsReddit(split: List<SplitEntry>) {
        outputText("|||")
        outputText("|:--|:--|:--")
        var blank = false
        for (e in split) {
            val spells = build.spellsFor(e.characterClass) ?: continue
            val lastLevel = spells.maxSpellLevel
            if (lastLevel != 0) {
                if (blank) outputText("|||") else blank = true
                for (level in 1..lastLevel) {
                    outputText("|" + e.characterClass.name + " " + level + "|" + spellLine(spells.level(level).slots) + "|")
                }
            }
        }
        blankLine()
    }

    /**
     * ValidSpells (:1246): InitBuildSpells first, then skip, empty, incomplete or complete, and how
     * many classes have reached their first spell level.
     */
    private fun validSpells(): Pair<Valid, Int> {
        rules.initBuildSpells()
        if (build.canCastSpell[1] == 0) {
            return Pair(Valid.SKIP, 0)
        }
        if (display && view != OutputView.ALL && view != OutputView.SPELLS) {
            return Pair(Valid.SKIP, 0)
        }
        var complete = true
        var empty = true
        val split = classSplit()
        for (e in split) {
            val spells = build.spellsFor(e.characterClass) ?: continue
            for (level in 1..spells.maxSpellLevel) {
                for (slot in spells.level(level).slots) {
                    if (slot.type == SpellSlotType.STANDARD || slot.type == SpellSlotType.FREE) {
                        if (slot.spell.isEmpty()) complete = false else empty = false
                    }
                }
            }
        }
        var casting = 0
        for (e in split) {
            val first = e.characterClass.canCastSpell.getOrElse(1) { 0 }
            if (first != 0 && e.levels >= first) {
                casting++
            }
        }
        val valid = if (empty) Valid.EMPTY else if (complete) Valid.COMPLETE else Valid.INCOMPLETE
        return Pair(valid, casting)
    }

    // ---------------------------------------------------------------- enhancements

    /** OutputEnhancements (:1305): the trees, and the leveling guide (first if its tab is showing). */
    private fun outputEnhancements() {
        if (!display && !exportSection(OutputView.ENHANCEMENTS)) {
            return
        }
        val valid = validEnhancements()
        if (valid == Valid.SKIP) {
            return
        }
        if (valid == Valid.EMPTY && build.guide.enhancements.isEmpty()) {
            return
        }
        if (settings.guideFirst) {
            if (build.guide.enhancements.isNotEmpty()) {
                outputLevelingGuide(true)
            }
            if (valid != Valid.EMPTY) {
                outputEnhancementTrees(false, valid)
            }
        } else {
            if (valid != Valid.EMPTY) {
                outputEnhancementTrees(true, valid)
            }
            if (build.guide.enhancements.isNotEmpty()) {
                outputLevelingGuide(false)
            }
        }
    }

    /** OutputEnhancementTrees (:1331): each tree with abilities, most AP first, cores then tiers. */
    private fun outputEnhancementTrees(first: Boolean, givenValid: Valid) {
        val enh = EnhancementRules(rules)
        val totals = enh.apTotals()
        var valid = givenValid
        if (totals.spentBase > totals.maxBase) {
            valid = Valid.ERRORS
        }
        outputTitle("Enhancements", "(" + totals.caption() + ")", if (valid == Valid.ERRORS) "Errors" else "")
        if (reddit) {
            outputTreesReddit(build.trees, false)
            return
        }
        val order = treeOrder(build.trees, false)
        for (entry in order) {
            val buildTree = build.trees[entry.first - 1]
            if (buildTree.abilities.isEmpty()) {
                continue
            }
            blankLine()
            val guideTree = findGuideTree(entry.first)
            if (guideTree != null && guideTree.out.spent > 0) {
                colorOverride = true
                colorOpen(guideTree.out.color)
            }
            outputText(buildTree.treeName + " (" + entry.second + " AP)")
            val tree = data.findTree(buildTree.treeName)
            outputTreeAbilities(tree, buildTree)
            if (guideTree != null && guideTree.out.spent > 0) {
                colorClose(guideTree.out.color)
                colorOverride = false
            }
        }
        if (first && build.guide.enhancements.isNotEmpty()) blankLine(1) else blankLine(2)
    }

    /** The cores, then a numbered line per tier with "(none)" for empty tiers (shared by trees and destinies). */
    private fun outputTreeAbilities(tree: Tree?, buildTree: BuildTree) {
        var text = ""
        listBegin(false)
        listStartLine()
        var tiers = false
        for (a in buildTree.abilities) {
            if (a.tier == 0) {
                if (text.isNotEmpty()) {
                    text = "$text, "
                }
                text += abilityDisplay(tree, a.tier, a.ability, a.rank, a.selector)
            } else {
                tiers = true
            }
        }
        if (text.isEmpty()) {
            text = "(none)"
        }
        outputText(text)
        text = ""
        if (tiers) {
            listBegin(true)
            var tier = 1
            for (a in buildTree.abilities) {
                if (a.tier > 0) {
                    if (a.tier > tier) {
                        text = if (text.isNotEmpty()) text.substring(0, text.length - 2) else "(none)"
                        listStartLine()
                        outputText(text)
                        tier++
                        while (a.tier > tier) {
                            listStartLine()
                            outputText("(none)")
                            tier++
                        }
                        text = ""
                    }
                    text = text + abilityDisplay(tree, a.tier, a.ability, a.rank, a.selector) + ", "
                }
            }
            text = if (text.isNotEmpty()) text.substring(0, text.length - 2) else "(none)"
            listStartLine()
            outputText(text)
            listEnd()
        }
        listEnd()
    }

    /** OutputEnhancementTreesReddit (:1427) and OutputDestinyTreesReddit (:2279). */
    private fun outputTreesReddit(trees: List<BuildTree>, destiny: Boolean) {
        for (entry in treeOrder(trees, destiny)) {
            val buildTree = trees[entry.first - 1]
            if (buildTree.abilities.isEmpty()) {
                continue
            }
            blankLine()
            outputText(buildTree.treeName + " (" + entry.second + " AP)")
            blankLine()
            outputText("|||")
            outputText("|:--|:--|:--")
            val tree = if (destiny) data.findDestiny(buildTree.treeName) else data.findTree(buildTree.treeName)
            var text = ""
            var tiers = false
            for (a in buildTree.abilities) {
                if (a.tier == 0) {
                    if (text.isNotEmpty()) {
                        text = "$text, "
                    }
                    text += abilityDisplay(tree, a.tier, a.ability, a.rank, a.selector)
                } else {
                    tiers = true
                }
            }
            if (text.isEmpty()) {
                text = "(none)"
            }
            outputText("|Cores|$text|")
            text = ""
            if (tiers) {
                var tier = 1
                for (a in buildTree.abilities) {
                    if (a.tier > 0) {
                        if (a.tier > tier) {
                            text = if (text.isNotEmpty()) text.substring(0, text.length - 2) else "(none)"
                            outputText("|Tier $tier|$text|")
                            tier++
                            while (a.tier > tier) {
                                outputText("|Tier $tier|(none)|")
                                tier++
                            }
                            text = ""
                        }
                        text = text + abilityDisplay(tree, a.tier, a.ability, a.rank, a.selector) + ", "
                    }
                }
                text = if (text.isNotEmpty()) text.substring(0, text.length - 2) else "(none)"
                outputText("|Tier $tier|$text|")
            }
        }
        blankLine()
    }

    /** The guide tree with this build tree (1-based), if the build has a guide. FindGuideTree (:1565). */
    private fun findGuideTree(buildTree: Int): GuideTree? {
        if (build.guide.enhancements.isEmpty()) {
            return null
        }
        for (g in rules.guideTrees) {
            if (g.buildTree == buildTree) {
                return g
            }
        }
        return null
    }

    /**
     * Build trees (or destinies) by AP spent, most first; equal ones keep their order. TreeOrder
     * (:1712) and DestinyOrder (:1731). Pairs of 1-based position and AP.
     */
    private fun treeOrder(trees: List<BuildTree>, destiny: Boolean): List<Pair<Int, Int>> {
        val enh = EnhancementRules(rules)
        val order = ArrayList<Pair<Int, Int>>()
        for (i in 1..trees.size) {
            order.add(Pair(i, if (destiny) enh.quickSpentInDestiny(i) else enh.quickSpentInTree(i)))
        }
        for (i in 1 until order.size) {
            val swap = order[i]
            var j = i
            while (j >= 1) {
                if (swap.second > order[j - 1].second) {
                    order[j] = order[j - 1]
                } else {
                    break
                }
                j--
            }
            order[j] = swap
        }
        return order
    }

    /**
     * ValidEnhancements (:1755): errors if any tree has one, empty if none has abilities; then
     * InitGuideOutput.
     */
    private fun validEnhancements(): Valid {
        if (display && view != OutputView.ALL && view != OutputView.ENHANCEMENTS) {
            return Valid.SKIP
        }
        var empty = true
        var error = false
        for (bt in build.trees) {
            when (validTree(data.findTree(bt.treeName), bt)) {
                Valid.ERRORS -> {
                    error = true
                    empty = false
                }
                Valid.COMPLETE -> empty = false
                else -> {}
            }
        }
        val result = if (empty) Valid.EMPTY else if (error) Valid.ERRORS else Valid.COMPLETE
        initGuideOutput()
        return result
    }

    /**
     * ValidTree (:1789) with CheckSpentInTree (:1826): an error in any ability, or a tree whose
     * tiers lack the AP the next one needs.
     *
     * VB quirk, kept: `GetSpentInTree` reports success only for a destiny or a tree that has its
     * first core, so any other tree with abilities counts as an error here.
     */
    private fun validTree(tree: Tree?, buildTree: BuildTree): Valid {
        val enh = EnhancementRules(rules)
        var error = false
        if (tree != null) {
            val spend = enh.spentInTree(tree, buildTree)
            for (a in buildTree.abilities) {
                if (a.ability == BLANK_ABILITY || enh.abilityError(tree, buildTree, a, spend) != null) {
                    error = true
                    break
                }
            }
        }
        val result: Valid
        if (buildTree.abilities.isEmpty()) {
            result = Valid.EMPTY
        } else {
            result = checkSpentInTree(tree, buildTree)
        }
        return if (error) Valid.ERRORS else result
    }

    private fun checkSpentInTree(tree: Tree?, buildTree: BuildTree): Valid {
        val enh = EnhancementRules(rules)
        var hasCore = buildTree.treeType == TreeStyle.DESTINY
        for (a in buildTree.abilities) {
            if (a.tier == 0 && a.ability == 0) {
                hasCore = true
            }
        }
        val spend = enh.spentInTree(tree, buildTree)
        var error = false
        if (!hasCore) {
            error = true
        } else if (tree != null) {
            val maxTier = buildTree.abilities[buildTree.abilities.size - 1].tier
            var i = maxTier
            while (i >= 2) {
                if (spend.byTier[i] > 0 && spend.byTier[i - 1] < enh.spentReq(tree.style, i, 1)) {
                    error = true
                    break
                }
                i--
            }
        }
        if (error) {
            return Valid.ERRORS
        }
        if (spend.total == 0) {
            return Valid.EMPTY
        }
        return Valid.COMPLETE
    }

    /** "Abbr: Selector III", the selector alone, or "Error" for a blank. GetAbilityDisplay (:1856). */
    private fun abilityDisplay(tree: Tree?, tier: Int, ability: Int, ranks: Int, selector: Int): String {
        if (ability == BLANK_ABILITY || tree == null) {
            return "Error"
        }
        val data = tree.tiers[tier][ability]
        var text: String
        if (selector != 0 && selector <= data.selectors.size) {
            text = if (data.selectorOnly) data.selectors[selector - 1].name else data.abbreviation + ": " + data.selectors[selector - 1].name
        } else {
            text = data.abbreviation
        }
        if (data.ranks > 1 && ranks != 0) {
            text = text + " " + "III".substring(0, minOf(ranks, 3))
        }
        return text
    }

    // ---------------------------------------------------------------- leveling guide

    private class GuideOutputStep(val guideTree: Int, val tree: String, val color: OutputColor?, val tier: Int, val enhancement: String, var display: String)

    private class GuideOutputTree(val guideTree: Int, val color: OutputColor?, val treeName: String) {
        val abilities = ArrayList<ddolite.builder.build.BuildAbility>()
        var tiers = ArrayList<String>()
    }

    private class GuideOutputLevel {
        val blocks = ArrayList<Pair<String, OutputColor?>>()
        val steps = ArrayList<GuideOutputStep>()
        var reset = ""
        val trees = ArrayList<GuideOutputTree>()
    }

    /** VB's Guide.Tree(i).Color, .Spent and .Initial, which only the output sets. */
    private class GuideTreeOutput {
        var color: OutputColor? = null
        var spent = 0
        var initial = ""
    }

    private val guideTreeOutput = HashMap<GuideTree, GuideTreeOutput>()

    private val GuideTree.out: GuideTreeOutput
        get() {
            var o = guideTreeOutput[this]
            if (o == null) {
                o = GuideTreeOutput()
                guideTreeOutput[this] = o
            }
            return o
        }

    /** mtypGuideOutput: index level - 1. */
    private var guideOutput: List<GuideOutputLevel> = emptyList()

    /**
     * Gathers the guide by level: the steps of each level grouped into runs of one colour, and at
     * a reset (or an iconic's start) the trees as they are then. InitGuideOutput (:1880) with
     * ResetAbility (:1979) and ResetBuildTree (:2045).
     */
    private fun initGuideOutput() {
        if (build.guide.enhancements.isEmpty()) {
            return
        }
        initGuideOutputTrees()
        val steps = rules.guideEnhancements
        if (steps.isEmpty()) {
            guideOutput = emptyList()
            return
        }
        val lastLevel = steps[steps.size - 1].level
        val levels = ArrayList<GuideOutputLevel>()
        for (i in 0 until maxOf(lastLevel, 0)) {
            levels.add(GuideOutputLevel())
        }
        guideOutput = levels
        var resetLevel = 0
        if (build.race?.type == RaceType.ICONIC) {
            if (build.maxLevels > 14) {
                resetLevel = 14
                levelAt(14)?.reset = "Iconic spends 56 AP to start"
            } else {
                resetLevel = build.maxLevels
                levelAt(build.maxLevels)?.reset = "Iconic spends all AP to start"
            }
        }
        for (i in steps.indices) {
            val e = steps[i]
            val out = levelAt(e.level) ?: continue
            when (e.style) {
                GuideStepStyle.ENHANCEMENT -> {
                    if (e.level == resetLevel) {
                        resetAbility(i)
                    } else {
                        var isNew = true
                        if (out.steps.isNotEmpty()) {
                            val last = out.steps[out.steps.size - 1]
                            if (last.guideTree == e.guideTree && last.tier == e.tier && last.enhancement == e.display) {
                                isNew = false
                            }
                        }
                        val g = rules.guideTrees[e.guideTree - 1]
                        if (isNew) {
                            out.steps.add(GuideOutputStep(e.guideTree, g.out.initial, g.out.color, e.tier, e.display, g.out.initial + e.tier + " " + e.display + e.rankText))
                        } else {
                            val last = out.steps[out.steps.size - 1]
                            last.display = last.display + "," + e.rankText
                        }
                    }
                }
                GuideStepStyle.BANK_AP -> out.steps.add(GuideOutputStep(0, "", null, 0, "", "(Bank " + e.bank + " AP)"))
                GuideStepStyle.RESET_TREE, GuideStepStyle.RESET_ALL_TREES -> {
                    resetLevel = e.level
                    out.reset = e.display
                }
                GuideStepStyle.UNKNOWN -> {}
            }
        }
        for (level in levels) {
            if (level.reset.isEmpty()) {
                var color: OutputColor? = null
                for (i in level.steps.indices) {
                    val step = level.steps[i]
                    if (i == 0 || step.color != color) {
                        color = step.color
                        level.blocks.add(Pair("", color))
                    }
                    val last = level.blocks.size - 1
                    level.blocks[last] = Pair(level.blocks[last].first + step.display + "; ", level.blocks[last].second)
                }
                if (level.blocks.isNotEmpty()) {
                    val last = level.blocks.size - 1
                    val text = level.blocks[last].first
                    level.blocks[last] = Pair(text.substring(0, text.length - 2), level.blocks[last].second)
                }
            } else {
                for (t in level.trees) {
                    resetBuildTree(t)
                }
            }
        }
    }

    private fun levelAt(level: Int): GuideOutputLevel? = guideOutput.getOrNull(level - 1)

    /** ResetAbility (:1979): adds a step to its level's tree list (a higher rank raises one there). */
    private fun resetAbility(index: Int) {
        val e = rules.guideEnhancements[index]
        val g = rules.guideTrees[e.guideTree - 1]
        val out = levelAt(e.level) ?: return
        var tree: GuideOutputTree? = null
        for (t in out.trees) {
            if (t.guideTree == e.guideTree) {
                tree = t
                break
            }
        }
        if (tree == null) {
            tree = GuideOutputTree(e.guideTree, g.out.color, g.treeName)
            out.trees.add(tree)
        }
        var insert = 0
        var found = false
        while (insert < tree.abilities.size) {
            val a = tree.abilities[insert]
            if (a.tier == e.tier) {
                if (a.ability == e.ability) {
                    found = true
                    break
                } else if (a.ability > e.ability) {
                    break
                }
            } else if (a.tier > e.tier) {
                break
            }
            insert++
        }
        if (found) {
            if (tree.abilities[insert].rank < e.rank) {
                tree.abilities[insert] = tree.abilities[insert].copy(rank = e.rank)
            }
        } else {
            tree.abilities.add(insert, ddolite.builder.build.BuildAbility(e.tier, e.ability, e.rank, e.selector))
        }
    }

    /** ResetBuildTree (:2045): the tree's abilities as text, ": cores" then each tier or "(none)". */
    private fun resetBuildTree(t: GuideOutputTree) {
        if (t.abilities.isEmpty() || t.guideTree == 0) {
            return
        }
        val tree = rules.guideTrees[t.guideTree - 1].tree
        val tiers = t.abilities[t.abilities.size - 1].tier
        val texts = ArrayList<String>()
        for (i in 0..tiers) {
            texts.add("")
        }
        for (a in t.abilities) {
            val comma = if (texts[a.tier].isNotEmpty()) ", " else ""
            texts[a.tier] = texts[a.tier] + comma + abilityDisplay(tree, a.tier, a.ability, a.rank, a.selector)
        }
        if (texts[0].isNotEmpty()) {
            texts[0] = ": " + texts[0]
        }
        for (i in 1..tiers) {
            if (texts[i].isEmpty()) {
                texts[i] = "(none)"
            }
        }
        t.tiers = texts
    }

    /**
     * Each guide tree's colour and initial for the output: trees with AP, most first, take their
     * own colour or the next free one (racial trees light grey); initials made unique. InitGuideOutputTrees
     * (:2076) with FindColor (:2136).
     *
     * VB quirk, kept: a tree with no `Color:` has colour 0, which is cveBackground.
     */
    private fun initGuideOutputTrees() {
        val trees = rules.guideTrees
        val ap = IntArray(trees.size + 1)
        for (g in trees) {
            g.out.color = null
            g.out.spent = 0
            g.out.initial = g.tree?.initials?.getOrNull(0) ?: ""
            if (g.tree?.style == TreeStyle.RACE) {
                g.out.color = null
            } else {
                g.out.color = treeColor(g.tree?.color)
            }
        }
        for (e in rules.guideEnhancements) {
            if (e.guideTree != 0 && e.style == GuideStepStyle.ENHANCEMENT) {
                ap[e.guideTree] += e.cost
                trees[e.guideTree - 1].out.spent += e.cost
            }
        }
        val order = ArrayList<Int>()
        for (i in 1..trees.size) {
            order.add(i)
        }
        for (i in 1 until order.size) {
            val swap = order[i]
            var j = i
            while (j >= 1) {
                if (ap[swap] > ap[order[j - 1]]) {
                    order[j] = order[j - 1]
                } else {
                    break
                }
                j--
            }
            order[j] = swap
        }
        val taken = HashSet<OutputColor>()
        for (id in order) {
            if (ap[id] == 0) {
                break
            }
            val g = trees[id - 1]
            if (g.tree?.style == TreeStyle.RACE) {
                g.out.color = OutputColor.LIGHT_GRAY
            } else {
                g.out.color = findColor(g.out.color, taken)
            }
        }
        for (i in order.indices) {
            for (j in i + 1 until order.size) {
                val a = trees[order[i] - 1]
                val b = trees[order[j] - 1]
                if (a.out.initial == b.out.initial) {
                    a.out.initial = a.tree?.initials?.getOrNull(1) ?: ""
                    b.out.initial = b.tree?.initials?.getOrNull(1) ?: ""
                    break
                }
            }
        }
    }

    private fun treeColor(color: TreeColor?): OutputColor = when (color) {
        TreeColor.RED -> OutputColor.RED
        TreeColor.GREEN -> OutputColor.GREEN
        TreeColor.BLUE -> OutputColor.BLUE
        TreeColor.YELLOW -> OutputColor.YELLOW
        TreeColor.PURPLE -> OutputColor.PURPLE
        TreeColor.ORANGE -> OutputColor.ORANGE
        null -> OutputColor.BACKGROUND
    }

    /** FindColor (:2136): the tree's own colour if free, else the first free of yellow, green, blue, red, purple, orange. */
    private fun findColor(own: OutputColor?, taken: HashSet<OutputColor>): OutputColor? {
        if (own == null) {
            return null
        }
        val choices = listOf(own, OutputColor.YELLOW, OutputColor.GREEN, OutputColor.BLUE, OutputColor.RED, OutputColor.PURPLE, OutputColor.ORANGE)
        for (c in choices) {
            if (!taken.contains(c)) {
                taken.add(c)
                return c
            }
        }
        return null
    }

    /** OutputLevelingGuide (:1578): a numbered line per level (iconics: from level 14). */
    private fun outputLevelingGuide(first: Boolean) {
        var errors = false
        for (e in rules.guideEnhancements) {
            if (e.errorState) {
                errors = true
                break
            }
        }
        outputTitle("Leveling Guide", error = if (errors) "(Errors)" else "", underline = false)
        if (reddit) {
            outputLevelingGuideReddit()
            return
        }
        val iconic = build.race?.type == RaceType.ICONIC
        if (!iconic) {
            listBegin(true)
        }
        for (level in firstGuideLevel()..lastGuideLevel()) {
            if (level < 1) {
                continue
            }
            if (iconic) {
                if (level > 14) {
                    outputText("$level. ", newLine = false)
                }
            } else {
                listStartLine()
            }
            val out = levelAt(level) ?: continue
            if (out.reset.isNotEmpty()) {
                if (iconic) {
                    if (build.maxLevels > 14) {
                        if (level != 14) {
                            outputText(out.reset, newLine = display)
                        }
                    } else {
                        if (level != build.maxLevels) {
                            outputText(out.reset, newLine = display)
                        }
                    }
                } else {
                    outputText(out.reset, newLine = display || (!display && (!format.codes || !format.lists.enabled)))
                }
                colorOverride = true
                for (t in out.trees) {
                    colorOpen(t.color)
                    listBegin(false)
                    listStartLine()
                    outputText(t.treeName + t.tiers.getOrElse(0) { "" })
                    if (t.tiers.size > 1) {
                        listBegin(true)
                        for (tier in 1 until t.tiers.size) {
                            listStartLine()
                            outputText(t.tiers[tier])
                        }
                        listEnd()
                    }
                    listEnd()
                    colorClose(t.color)
                }
                colorOverride = false
            } else if (out.steps.isEmpty()) {
                outputText("(Bank AP)")
            } else {
                for (i in out.blocks.indices) {
                    outputText(out.blocks[i].first, newLine = i == out.blocks.size - 1, color = out.blocks[i].second)
                }
            }
        }
        if (!iconic) {
            listEnd()
        }
        if (!first || build.trees.isEmpty()) blankLine(2) else blankLine(1)
    }

    /** OutputLevelingGuideReddit (:1652). */
    private fun outputLevelingGuideReddit() {
        val iconic = build.race?.type == RaceType.ICONIC
        blankLine()
        outputText("|||")
        outputText("|--:|:--|:--")
        for (level in firstGuideLevel()..lastGuideLevel()) {
            val out = levelAt(level) ?: continue
            if (out.reset.isNotEmpty()) {
                if (iconic) {
                    if (build.maxLevels > 14) {
                        if (level != 14) {
                            outputText("|$level|" + out.reset + "|")
                        }
                    } else {
                        if (level != build.maxLevels) {
                            outputText("|$level|" + out.reset + "|")
                        }
                    }
                } else {
                    outputText("|$level|" + out.reset + "|")
                }
                for (t in out.trees) {
                    val cores = t.tiers.getOrElse(0) { "" }
                    outputText("||**" + t.treeName + ":**" + (if (cores.length > 1) cores.substring(1) else "") + "|")
                    for (tier in 1 until t.tiers.size) {
                        outputText("||_Tier " + tier + ":_ " + t.tiers[tier] + "|")
                    }
                }
            } else if (out.steps.isEmpty()) {
                outputText("|$level|**(Bank AP)**|")
            } else {
                val text = StringBuilder()
                for (b in out.blocks) {
                    text.append(b.first)
                }
                outputText("|$level|$text|")
            }
        }
        blankLine()
    }

    /** FirstGuideLevel (:1698). */
    private fun firstGuideLevel(): Int {
        if (rules.guideEnhancements.isEmpty()) {
            return 0
        }
        if (build.race?.type == RaceType.ICONIC) {
            return if (build.maxLevels > 14) 14 else build.maxLevels
        }
        return 1
    }

    /** LastGuideLevel (:1707). */
    private fun lastGuideLevel(): Int {
        val steps = rules.guideEnhancements
        if (steps.isEmpty()) {
            return 0
        }
        return steps[steps.size - 1].level
    }

    // ---------------------------------------------------------------- destinies

    /** OutputDestinies (:2163). (The Destinies window's tab doesn't change anything: both orders are the same.) */
    private fun outputDestinies() {
        if (!display && !exportSection(OutputView.DESTINY)) {
            return
        }
        val valid = validDestinies()
        if (valid == Valid.SKIP || valid == Valid.EMPTY) {
            return
        }
        outputDestinyTrees(valid)
    }

    /** OutputDestinyTrees (:2187). */
    private fun outputDestinyTrees(givenValid: Valid) {
        val totals = EnhancementRules(rules).destinyTotals()
        var valid = givenValid
        if (totals.spentBase > totals.maxBase) {
            valid = Valid.ERRORS
        }
        // GetSpentDestinyText (:1536)
        val text = "(Spent: " + (totals.spentBase + totals.spentPdp) + " (" + totals.spentBase + " +" + totals.spentPdp + "pdp) / Max: " +
            (totals.maxBase + totals.maxPdp) + " (" + totals.maxBase + " +" + totals.maxPdp + "pdp) AP)"
        outputTitle("Destinies", text, if (valid == Valid.ERRORS) "Errors" else "")
        if (reddit) {
            outputTreesReddit(build.destinies, true)
            return
        }
        for (entry in treeOrder(build.destinies, true)) {
            val buildTree = build.destinies[entry.first - 1]
            if (buildTree.abilities.isEmpty()) {
                continue
            }
            blankLine()
            outputText(buildTree.treeName + " (" + entry.second + " AP)")
            outputTreeAbilities(data.findDestiny(buildTree.treeName), buildTree)
        }
        blankLine(1)
    }

    /**
     * ValidDestinies (:2346).
     *
     * VB quirk, kept: it accepts oeDestiny, but the Destinies window passes oeDestiny2, so the
     * destinies never show on screen while that window is active (only in the full output).
     */
    private fun validDestinies(): Valid {
        if (display && view != OutputView.ALL && view != OutputView.DESTINY) {
            return Valid.SKIP
        }
        var empty = true
        var error = false
        for (bt in build.destinies) {
            when (validTree(data.findDestiny(bt.treeName), bt)) {
                Valid.ERRORS -> {
                    error = true
                    empty = false
                }
                Valid.COMPLETE -> empty = false
                else -> {}
            }
        }
        return if (empty) Valid.EMPTY else if (error) Valid.ERRORS else Valid.COMPLETE
    }

    // ---------------------------------------------------------------- writing

    private enum class Valid { SKIP, EMPTY, ERRORS, INCOMPLETE, COMPLETE }

    /** SpecialCodeEnum (:21): Courier for this text, or from here, or up to here. */
    private enum class Special { NONE, COURIER, COURIER_BEGIN, COURIER_END }

    private enum class Align { LEFT, RIGHT, CENTER }

    /** The export line being written exists. */
    private fun ensureLine() {
        while (join.size <= current) {
            join.add("")
        }
    }

    /** OutputText (:2399). [color] null is VB's -1 (no colour of its own). */
    private fun outputText(
        text: String,
        newLine: Boolean = true,
        color: OutputColor? = null,
        special: Special = Special.NONE,
        bold: Boolean = false,
        underline: Boolean = false,
    ) {
        if (display) {
            // SetStyle (:2469)
            if (color != null && format.textColors) {
                foreColor = color
            }
            if (special == Special.COURIER || special == Special.COURIER_BEGIN) {
                courier = true
            }
            lineRuns.add(DisplayRun(text, foreColor, courier, bold, underline))
            if (newLine) {
                endLine()
            }
            // ResetStyle (:2483)
            if (color != null && format.textColors) {
                foreColor = OutputColor.TEXT
            }
            if (special == Special.COURIER || special == Special.COURIER_END) {
                courier = false
            }
        } else {
            var out = text
            if (format.codes) {
                out = insertCodes(out, bold, underline, color, special)
            }
            ensureLine()
            join[current] = join[current] + out
            if (newLine) {
                join[current] = rtrim(join[current])
                current++
            }
        }
    }

    /** Ends the screen line being written (Print with a line break). */
    private fun endLine() {
        if (!display) {
            return
        }
        lines.add(DisplayLine(lineRuns, lineLevel, linePrefix))
        lineRuns = ArrayList()
        lineLevel = 0
        linePrefix = null
    }

    /** OutputTitle (:2421): underlined title, the extra text, and an error in the error colour. */
    private fun outputTitle(text: String, extra: String = "", error: String = "", underline: Boolean = true) {
        if (reddit) {
            var line = "**$text**"
            if (extra.isNotEmpty()) {
                line = "$line $extra"
            }
            if (error.isNotEmpty()) {
                line = "$line _" + error + "_"
            }
            outputText(line)
        } else {
            outputText(text, newLine = false, underline = underline)
            if (extra.isNotEmpty()) {
                outputText(" $extra", newLine = false)
            }
            if (error.isNotEmpty()) {
                outputText(" $error", newLine = false, color = OutputColor.TEXT_ERROR)
            }
            blankLine()
        }
    }

    /** ColorOpen (:2437). */
    private fun colorOpen(color: OutputColor?) {
        if (!format.textColors) {
            return
        }
        if (display) {
            foreColor = color ?: OutputColor.TEXT
        } else if (color != null && format.color.enabled) {
            ensureLine()
            join[current] = join[current] + format.color.open.replace("$", colorHex(color))
        }
    }

    /** ColorClose (:2449). */
    private fun colorClose(color: OutputColor?) {
        if (!format.textColors) {
            return
        }
        if (display) {
            foreColor = OutputColor.TEXT
        } else if (color != null && format.color.enabled) {
            ensureLine()
            join[current] = join[current] + format.color.close
        }
    }

    /**
     * InsertCodes (:2500): tags go right against the text, inside its leading and trailing spaces;
     * Courier tags go outside them.
     */
    private fun insertCodes(text: String, bold: Boolean, underline: Boolean, color: OutputColor?, special: Special): String {
        if (!format.codes) {
            return text
        }
        var body = text.trimStart(' ')
        val leading = text.length - body.length
        var trailing = body.length
        body = rtrim(body)
        trailing -= body.length
        var prefix = ""
        var suffix = ""
        if (body.isNotEmpty()) {
            if (color != null && format.textColors && format.color.enabled) {
                prefix += format.color.open.replace("$", colorHex(color))
            }
            if (bold && format.bold.enabled) {
                prefix += format.bold.open
            }
            if (underline && format.underline.enabled) {
                prefix += format.underline.open
            }
            if (underline && format.underline.enabled) {
                suffix += format.underline.close
            }
            if (bold && format.bold.enabled) {
                suffix += format.bold.close
            }
            if (color != null && format.textColors && format.color.enabled) {
                suffix += format.color.close
            }
        }
        prefix = spaces(leading) + prefix
        suffix += spaces(trailing)
        if (format.fixed.enabled && (special == Special.COURIER || special == Special.COURIER_BEGIN)) {
            prefix = format.fixed.open + prefix
        }
        if (format.fixed.enabled && (special == Special.COURIER || special == Special.COURIER_END)) {
            suffix += format.fixed.close
        }
        return prefix + body + suffix
    }

    /**
     * AlignText (:2543): pads or cuts to [width].
     *
     * VB detail: centring an odd gap of 1 would start at position 0 and stop with an error; the
     * output never centres.
     */
    private fun alignText(text: String, width: Int, align: Align): String {
        val padding = width - text.length
        if (text.length > width) {
            return if (align == Align.RIGHT) text.substring(text.length - width) else text.substring(0, width)
        }
        if (text.isEmpty()) {
            return spaces(width)
        }
        return when (align) {
            Align.RIGHT -> spaces(padding) + text
            Align.LEFT -> text + spaces(padding)
            Align.CENTER -> {
                val start = maxOf(padding / 2 - 1, 0)
                spaces(start) + text + spaces(width - start - text.length)
            }
        }
    }

    /** BlankLine (:2563): [count] line breaks. */
    private fun blankLine(count: Int = 1) {
        if (display) {
            for (i in 1..count) {
                endLine()
            }
        } else {
            current += count
        }
    }

    /** ListBegin (:2576): pushes a list; the export opens it (even when lists are switched off, as VB). */
    private fun listBegin(numbered: Boolean) {
        listNumbered.add(numbered)
        listCount.add(0)
        if (!display && format.codes) {
            if (current > 1) {
                ensureLine()
                if (join[current].isEmpty()) {
                    current--
                }
            }
            ensureLine()
            join[current] = join[current] + if (numbered) format.lists.numberedOpen else format.lists.bulletOpen
        }
    }

    /** ListStartLine (:2597): the next item's number or bullet. */
    private fun listStartLine() {
        val level = listNumbered.size
        val top = level - 1
        listCount[top] = listCount[top] + 1
        val prefix = if (listNumbered[top]) listCount[top].toString() + ". " else "$BULLET "
        val spaces = level * 6 - prefix.length
        if (display) {
            if (!colorOverride) {
                foreColor = OutputColor.TEXT
            }
            lineLevel = level
            linePrefix = DisplayRun(prefix, foreColor, courier, false, false)
        } else if (format.codes && format.lists.enabled) {
            ensureLine()
            join[current] = join[current] + "[*]"
        } else {
            ensureLine()
            join[current] = spaces(maxOf(spaces, 0)) + prefix
        }
    }

    /** ListEnd (:2623). */
    private fun listEnd() {
        if (!display && format.codes && format.lists.enabled) {
            val close = if (listNumbered[listNumbered.size - 1]) format.lists.numberedClose else format.lists.bulletClose
            if (current > 1) {
                ensureLine()
                if (join[current].isEmpty()) {
                    current--
                }
            }
            ensureLine()
            join[current] = join[current] + close
        }
        listNumbered.removeAt(listNumbered.size - 1)
        listCount.removeAt(listCount.size - 1)
    }

    /** TrimBlanks (:237) with LastBlank (:244). */
    private fun trimBlanks() {
        while (lastBlank()) {
            current--
            if (current == 0) {
                break
            }
        }
    }

    private fun lastBlank(): Boolean {
        if (current == -1) {
            return false
        }
        ensureLine()
        while (join[current].endsWith("\r\n")) {
            join[current] = rtrim(join[current].substring(0, join[current].length - 2))
        }
        return rtrim(join[current]).isEmpty()
    }

    /**
     * AddDots (:2727): every other space of a run of spaces becomes a dot (odd positions, so the
     * dots line up), wrapped in the background colour so they don't show; also a single leading
     * space, and a space right after the Courier tag.
     */
    private fun addDots(line: String): String {
        var text = line
        var start = 1
        while (true) {
            start = Vbx.inStr(start, text, "  ")
            if (start == 0) {
                break
            }
            var end = start + 2
            while (end <= text.length) {
                if (text[end - 1] != ' ') {
                    break
                }
                end++
            }
            val chars = text.toCharArray()
            for (i in start until end) {
                if (i % 2 == 1) {
                    chars[i - 1] = '.'
                }
            }
            text = String(chars)
            if (format.codes && format.color.enabled) {
                text = text.substring(0, start - 1) + hideColor + text.substring(start - 1, end - 1) + format.color.close + text.substring(end - 1)
            }
            start = end
        }
        if (text.startsWith(" ")) {
            text = "." + text.substring(1)
            if (format.codes && format.color.enabled) {
                text = hideColor + text.substring(0, 1) + format.color.close + text.substring(1)
            }
        }
        if (format.codes && format.fixed.enabled && format.fixed.open.isNotEmpty()) {
            if (text.startsWith(format.fixed.open + " ")) {
                text = format.fixed.open + hideColor + "." + format.color.close + text.substring(format.fixed.open.length + 1)
            }
        }
        return text
    }

    private fun spaces(count: Int): String = " ".repeat(maxOf(count, 0))

    private fun rtrim(s: String): String = s.trimEnd(' ')

    companion object {
        /** BulletPoint (:5): Windows-1252 0x95. */
        const val BULLET = "•"

        /** MAX_CLASS_NAME (basCONST.bas): the widest class name, for the level order's columns. */
        const val MAX_CLASS_NAME = "Arcane Trickster"
    }
}

/** VB string helpers the output needs. */
private object Vbx {
    /** Trim$: spaces only. */
    fun trim(s: String): String = s.trim(' ')

    /** InStr(start, s, find), 1-based; 0 if not found. */
    fun inStr(start: Int, s: String, find: String): Int {
        if (start > s.length) {
            return 0
        }
        val at = s.indexOf(find, start - 1)
        return if (at < 0) 0 else at + 1
    }
}
