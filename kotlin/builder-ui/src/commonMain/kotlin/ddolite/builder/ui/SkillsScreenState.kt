package ddolite.builder.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ddolite.builder.build.Build
import ddolite.builder.data.BuilderData
import ddolite.builder.model.CharacterClass
import ddolite.builder.model.Skill
import ddolite.builder.rules.HEROIC_LEVELS
import ddolite.builder.rules.SkillGrid
import ddolite.builder.rules.SkillSpend
import ddolite.common.theme.Theme

/** A made-up level plan for the prototype: a name and the class at each of the 20 heroic levels. */
class BuildPreset(val name: String, val classNames: List<String>)

/** Which mouse button started an action. Touch counts as [PRIMARY]. */
enum class PointerButton { PRIMARY, SECONDARY, TERTIARY }

/**
 * The state behind the prototype skills screen: a ViewModel in the sense of plan §8.4 (screen
 * state only; every rule is in builder-core's [SkillGrid]).
 *
 * The mouse handling is a port of `frmSkills.picGrid_MouseDown` (Source/Forms/Build/frmSkills.frm:1654),
 * `MouseDown` (:1778, used by the repeat timer) and `MaxRanks` (:1889). Grid positions use VB's
 * numbering: row 0 = the header rows, rows 1-21 = skills; column 0 = the skill names,
 * columns 1-20 = levels; -1 = outside.
 *
 * The build is the open .build, with the Intelligence modifier at each level from its stats (as
 * VB's InitBuildSkills); the Android prototype still uses a made-up [BuildPreset] with a Human
 * and one Intelligence modifier for every level.
 */
class SkillsScreenState(
    private val data: BuilderData,
    val themes: List<Theme>,
    val presets: List<BuildPreset>,
) {
    /** The skill on each grid row (index 0 = row 1), as [orderRows] sets them. */
    var rowSkills: List<Skill> by mutableStateOf(Skill.entries)
        private set

    /**
     * InitRows (frmSkills.frm:1256): Alphabetical, or Native First: the main class's native skills,
     * then the other classes' native skills, then the rest, each in skill order. [split] is the
     * class split, most levels first; with none the rows stay as they were.
     */
    fun orderRows(split: List<ddolite.builder.model.CharacterClass>, nativeFirst: Boolean) {
        if (split.isEmpty()) {
            return
        }
        if (!nativeFirst) {
            rowSkills = Skill.entries
            return
        }
        val rows = ArrayList<Skill>()
        for (skill in Skill.entries) {
            if (split[0].nativeSkills.contains(skill)) {
                rows.add(skill)
            }
        }
        for (skill in Skill.entries) {
            for (i in 1 until split.size) {
                if (split[i].nativeSkills.contains(skill) && !rows.contains(skill)) {
                    rows.add(skill)
                }
            }
        }
        for (skill in Skill.entries) {
            if (!rows.contains(skill)) {
                rows.add(skill)
            }
        }
        rowSkills = rows
    }

    var presetIndex by mutableIntStateOf(0)
        private set
    var themeIndex by mutableIntStateOf(0)
        private set
    var intModifier by mutableIntStateOf(2)
        private set

    /** The build opened from a file, or null while a made-up [BuildPreset] is shown. (Declared before [grid], which reads it.) */
    var openedBuild: Build? by mutableStateOf(null)
        private set

    // Heroic levels of the opened build (basBuild.GetHeroicLevels).
    private var openedLevels = 0

    // The opened build's Intelligence modifier at each heroic level (from its stats).
    private var openedModifiers: List<Int> = emptyList()

    /** The skill points spent. Kept when the preset changes, as VB keeps build.Skills when classes change. */
    val spend = SkillSpend()

    /** Bumped after every change to [spend], so the grid redraws. */
    var revision by mutableIntStateOf(0)
        private set

    var grid: SkillGrid by mutableStateOf(makeGrid())
        private set

    /** The cell under the mouse (VB's mlngRow/mlngCol), -1 when outside. */
    var activeRow by mutableIntStateOf(-1)
        private set
    var activeCol by mutableIntStateOf(-1)
        private set

    // VB's mlngMaxRanksRow and mlngMaxRanks: clicking a skill name repeatedly cycles the fill mode.
    private var maxRanksRow = 0
    private var maxRanksMode = 0

    // The button held down, for the repeat timer (VB's mintButton).
    private var heldButton = PointerButton.PRIMARY

    val theme: Theme get() = themes[themeIndex]

    /** The build's race: the opened build's, else the prototype's Human. */
    val raceName: String
        get() {
            val race = openedBuild?.race
            if (race != null) {
                return race.name
            }
            return RACE_NAME
        }

    /** Choosing a made-up level plan leaves the opened build (its skill points stay in [spend]). */
    fun selectPreset(index: Int) {
        presetIndex = index
        openedBuild = null
        noLevels = false
        grid = makeGrid()
    }

    /**
     * Shows an opened build: its classes by level, its race and its skill points.
     * VB's frmSkills reads `build.Skills` directly; here the screen keeps its own [SkillSpend]
     * and [storeInto] copies it back before saving.
     */
    fun openBuild(build: Build, heroicLevels: Int, intModifiers: List<Int>) {
        openedBuild = build
        openedLevels = heroicLevels
        openedModifiers = intModifiers
        for (skill in Skill.entries) {
            for (level in 1..HEROIC_LEVELS) {
                spend.set(skill, level, build.skills[skill.ordinal][level - 1])
            }
        }
        // The grid needs a class at level 1; until the build has one, the Skills window can't open.
        noLevels = build.levelClass(1) == null
        if (!noLevels) {
            grid = makeGrid()
        }
        revision = revision + 1
    }

    /** Copies the skill points into [build] (`build.Skills`), for saving. */
    fun storeInto(build: Build) {
        for (skill in Skill.entries) {
            for (level in 1..HEROIC_LEVELS) {
                build.skills[skill.ordinal][level - 1] = spend.get(skill, level)
            }
        }
    }

    /** "name (race)" for the opened build or the chosen preset. */
    fun buildTitle(): String {
        val build = openedBuild
        if (build != null) {
            return openedBuildLabel(build) + " (" + raceName + ")"
        }
        return presets[presetIndex].name + " (" + raceName + ")"
    }

    /** True if the grid has levels to show (an opened build may have no class at level 1). */
    fun hasLevels(): Boolean = !noLevels

    // True while the opened build has no class at level 1, so [grid] is out of date.
    private var noLevels = false

    fun selectTheme(index: Int) {
        themeIndex = index
    }

    fun changeIntModifier(value: Int) {
        intModifier = value
        if (!noLevels) {
            grid = makeGrid()
        }
    }

    /** The class shown above a level, if the build has more than one class (VB's `mblnClassRow`). */
    fun classInitial(level: Int): String {
        val c = grid.classAt(level)
        // VB picks whichever of the four initials makes every class in the build unique
        // (basBuild.GetClassSplit); the prototype just uses the second, e.g. "Rg", "Wz".
        if (c.initials.size > 1) {
            return c.initials[1]
        }
        return c.initials[0]
    }

    fun isMulticlass(): Boolean {
        if (grid.levels == 0) {
            return false
        }
        val first = grid.classAt(1)
        for (level in 2..grid.levels) {
            if (grid.classAt(level) != first) {
                return true
            }
        }
        return false
    }

    /** The mouse moved over [row]/[col]. From `frmSkills.picGrid_MouseMove` → `ActiveCell`. */
    fun hover(row: Int, col: Int) {
        activeRow = row
        activeCol = col
    }

    /**
     * A button went down on [row]/[col]. Ported from `frmSkills.picGrid_MouseDown`
     * (frmSkills.frm:1654). Returns true if holding the button should repeat the action
     * (VB starts tmrStart for any cell in a level column).
     */
    fun press(row: Int, col: Int, button: PointerButton): Boolean {
        hover(row, col)
        if (row < 0 || row > rowSkills.size || col < 0 || col > grid.levels) {
            return false
        }
        if (row == 0) {
            // Header: right-click on a level clears that level.
            if (col != 0 && button == PointerButton.SECONDARY) {
                grid.clearLevel(spend, col)
                changed()
            }
            return false
        }
        heldButton = button
        val skill = rowSkills[row - 1]
        if (button == PointerButton.PRIMARY) {
            if (col == 0) {
                // Clicking the same skill name again moves on to the next fill mode.
                if (maxRanksRow == row) {
                    applyMaxRanks(skill, -1)
                } else {
                    maxRanksRow = row
                    applyMaxRanks(skill, 1)
                }
            } else {
                grid.increment(spend, skill, col)
                maxRanksRow = 0
            }
        } else if (button == PointerButton.TERTIARY) {
            if (col > 0) {
                grid.clearCell(spend, skill, col)
            }
            maxRanksRow = 0
        } else {
            if (col == 0) {
                // VB pops up mnuMain(0) here; the window shows the menu for [menuRow] and calls [chooseMenu].
                menuRow = row
                maxRanksRow = 0
                return false
            }
            grid.decrement(spend, skill, col)
            maxRanksRow = 0
        }
        changed()
        return col != 0
    }

    /** The repeat timer fired while a button is held. Ported from `frmSkills.MouseDown` (frmSkills.frm:1778). */
    fun repeat() {
        if (activeRow < 1 || activeRow > rowSkills.size || activeCol < 1 || activeCol > grid.levels) {
            return
        }
        val skill = rowSkills[activeRow - 1]
        if (heldButton == PointerButton.PRIMARY) {
            grid.increment(spend, skill, activeCol)
        } else if (heldButton == PointerButton.SECONDARY) {
            grid.decrement(spend, skill, activeCol)
        }
        changed()
    }

    /**
     * Fill mode for a skill: 1 = half ranks, 2 = full ranks, 3 = even levels, 4 = clear; -1 = the
     * next mode after the last one. Ported from `frmSkills.MaxRanks` (frmSkills.frm:1889).
     */
    private fun applyMaxRanks(skill: Skill, mode: Int) {
        if (mode == -1) {
            maxRanksMode = maxRanksMode + 1
        } else {
            maxRanksMode = mode
        }
        if (maxRanksMode > 4) {
            maxRanksMode = 1
        }
        if (maxRanksMode == 1) {
            grid.maxHalfRanks(spend, skill)
        } else if (maxRanksMode == 2) {
            grid.maxFullRanks(spend, skill)
        } else if (maxRanksMode == 3) {
            grid.maxEvenLevels(spend, skill)
        } else {
            grid.clearSkill(spend, skill)
        }
    }

    /** The skill-name menu's items (mnuSkill(0..4)); "-" is a separator. */
    val menuItems = listOf("Max Ranks (Half Ranks)", "Max Ranks (Full Ranks)", "Max Ranks (Even Levels)", "-", "Clear Ranks")

    /** The row whose skill-name menu is showing; 0 = none. */
    var menuRow by mutableIntStateOf(0)

    /**
     * An item chosen from the skill-name menu. Ported from mnuSkill_Click (frmSkills.frm:1698):
     * "Clear Ranks" clears the skill; the others fill it as clicking the name does, and set the
     * mode that the next click on the same name moves on from.
     */
    fun chooseMenu(index: Int) {
        val row = menuRow
        menuRow = 0
        if (row < 1 || row > rowSkills.size) {
            return
        }
        val skill = rowSkills[row - 1]
        if (index == 4) {
            grid.clearSkill(spend, skill)
        } else {
            applyMaxRanks(skill, index + 1)
        }
        changed()
    }

    /** How strongly class skills stand out (cfg.Contrast, default Medium: clsConfig.cls:456). */
    var contrast by mutableStateOf(Contrast.MEDIUM)

    /** The Skill Tomes tab is showing (picTomes visible; usrHeader_Click, frmSkills.frm:1186). */
    var tomesTab by mutableStateOf(false)

    /** "Clear Skills" after the user agreed: Erase build.Skills (usrFooter_Click, :1203). */
    fun clearSkills() {
        for (skill in Skill.entries) {
            for (level in 1..HEROIC_LEVELS) {
                spend.set(skill, level, 0)
            }
        }
        changed()
    }

    /**
     * A skill tome's spinner on the Skill Tomes tab, or 0 if no build is open. VB's LoadData
     * (:1376) shows a tome above the current maximum as the maximum, without changing the build.
     */
    fun tome(skill: Skill): Int {
        val build = openedBuild ?: return 0
        val value = build.skillTomes[skill.ordinal]
        if (value > data.tomes.skill.max) {
            return data.tomes.skill.max
        }
        return value
    }

    /** The highest skill tome (tomes.Skill.Max). */
    val tomeMax: Int get() = data.tomes.skill.max

    /** usrspnSkill_Change (:1390). */
    fun setTome(skill: Skill, value: Int) {
        val build = openedBuild ?: return
        ddolite.builder.build.SkillTomes.set(build, skill, value)
        changed()
    }

    /** "Clear Tomes" after the user agreed: Erase build.SkillTome (tmrFocus_Timer, :1229). */
    fun clearTomes() {
        val build = openedBuild ?: return
        ddolite.builder.build.SkillTomes.clear(build)
        changed()
    }

    private fun changed() {
        revision = revision + 1
        onChanged()
    }

    /** Called after each change the user makes, so the app can copy the points into the build. */
    var onChanged: () -> Unit = {}

    private fun makeGrid(): SkillGrid {
        val classes = ArrayList<CharacterClass>()
        val build = openedBuild
        if (build != null) {
            // The grid needs a class at every level, so it stops at the first level without one
            // (VB's Skills screen can't be opened until every level has a class).
            for (level in 1..openedLevels) {
                val c = build.levelClass(level)
                if (c == null) {
                    break
                }
                classes.add(c)
            }
        } else {
            for (name in presets[presetIndex].classNames) {
                val c = data.findClass(name)
                if (c == null) {
                    throw IllegalStateException("Class $name not found in Classes.txt")
                }
                classes.add(c)
            }
        }
        var raceSkillPoints = 0
        val race = data.findRace(raceName)
        if (race != null) {
            raceSkillPoints = race.skillPoints
        }
        val modifiers = ArrayList<Int>()
        for (i in classes.indices) {
            if (build != null) {
                modifiers.add(openedModifiers[i])
            } else {
                modifiers.add(intModifier)
            }
        }
        return SkillGrid(classes, raceSkillPoints, modifiers)
    }
}

/** The level plans offered by the prototype. */
fun prototypePresets(): List<BuildPreset> {
    return listOf(
        BuildPreset("Rogue 20", repeated("Rogue", 20)),
        BuildPreset("Fighter 12 / Rogue 6 / Wizard 2", repeated("Fighter", 3) + repeated("Rogue", 6) + repeated("Wizard", 2) + repeated("Fighter", 9)),
        BuildPreset("Rogue 2 / Wizard 18", repeated("Rogue", 2) + repeated("Wizard", 18)),
        BuildPreset("Bard 20", repeated("Bard", 20)),
    )
}

private fun repeated(name: String, times: Int): List<String> {
    val result = ArrayList<String>()
    for (i in 0 until times) {
        result.add(name)
    }
    return result
}
