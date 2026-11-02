package ddolite.prototype.skills

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ddolite.builder.build.BuildPoints
import ddolite.builder.build.CascadeChange
import ddolite.builder.build.OverviewChoices
import ddolite.builder.build.OverviewRules
import ddolite.builder.build.RaceRow
import ddolite.builder.model.Alignment
import ddolite.builder.model.CharacterClass
import ddolite.builder.model.Race

/** What's being dragged on the Overview screen. Data classes, so equal drags compare equal (see DragAndDrop.kt). */
sealed class OverviewDrag {
    /** A class icon from the class list (VB's picClass). */
    data class ClassIcon(val characterClass: CharacterClass) : OverviewDrag()

    /** One of the three build class slots above the grid (VB's picBuildClass). */
    data class BuildSlot(val slot: Int) : OverviewDrag()
}

/** Drop target keys on the Overview screen. */
data class BuildSlotKey(val slot: Int)

/** A level in the grid's class column, as a drop target. */
data class LevelKey(val level: Int)

/** The class icon list, which takes build class slots dragged off (VB's picIcons/picClass_DragDrop). */
object ClassIconsKey

/**
 * The state behind the Overview window: VB's frmOverview module variables and event handlers
 * (Source/Forms/Build/frmOverview.frm). The rules are in builder-core's [OverviewRules]; this
 * keeps the screen's own flags, asks the questions VB asks, and reports each change to the
 * session.
 *
 * Only the icon layout is ported (the default: `cfg.UseIcons And cfg.IconOverview`); VB's
 * combo-box layout for the build classes, used when icons are turned off in Options, isn't yet.
 */
class OverviewScreenState(private val session: PrototypeState) {
    /** VB's mblnInclude32: Champion points were included when the window opened (Form_Load, :1234). */
    private var include32 = false

    /** VB's mblnIconicClass: choosing an iconic race also sets the main class (:1221). */
    private var iconicClass = false

    /** The Notes tab is showing (VB's mblnNotes). */
    var notesTab by mutableStateOf(false)

    /** The race list is dropped down (VB's usrRace.Visible). */
    var raceListOpen by mutableStateOf(false)

    /** The grid cell under the mouse (VB's mlngRow / mlngCol); 0 = none. */
    var activeRow by mutableIntStateOf(0)
        private set
    var activeCol by mutableIntStateOf(0)
        private set

    /** The level whose class is being dragged in the class column (VB's mlngRow during OLEDrag); 0 = none. */
    var levelDragFrom by mutableIntStateOf(0)
        private set

    /** The level under the pointer during that drag; 0 = none. */
    var levelDragOver by mutableIntStateOf(0)
        private set

    val drag = DragDropState<OverviewDrag>()

    /** The alignment list and class filter, from the last [refreshChoices]. */
    var choices: OverviewChoices by mutableStateOf(OverviewChoices(listOf(null), emptySet()))
        private set

    private val rules: OverviewRules?
        get() {
            val r = session.rules
            if (r == null) {
                return null
            }
            return OverviewRules(r)
        }

    /** A build was opened or created: VB's Form_Load / Cascade (:1229, :1282). */
    fun opened() {
        val r = rules
        if (r == null) {
            return
        }
        include32 = r.build.includePoints[BuildPoints.CHAMPION.ordinal]
        iconicClass = r.build.buildClasses[0] == null
        raceListOpen = false
        raceRows = r.raceRows()
        refreshChoices()
    }

    /** UpdateChoices (:1733): refilter after any change (may choose the only possible alignment). */
    fun refreshChoices() {
        val r = rules
        if (r == null) {
            return
        }
        choices = r.filterChoices(disallowed = true)
    }

    /** VB's CheckGuide (:1293): true if the change may go ahead. */
    private fun guideAllowsChange(r: OverviewRules): Boolean {
        if (!r.hasLevelingGuide()) {
            return true
        }
        return session.prompts.confirm("This will erase the Leveling Guide. Are you sure?")
    }

    // ---------------------------------------------------------------- tabs

    /** usrHeader_Click (:1395). */
    fun showTab(notes: Boolean) {
        notesTab = notes
        if (notes) {
            raceListOpen = false
        }
    }

    // ---------------------------------------------------------------- fields

    /** txtName_Change (:1644). */
    fun setName(name: String) {
        val r = rules ?: return
        r.build.buildName = name
        session.buildChanged(null)
    }

    /** txtNotes_Change (:1675). */
    fun setNotes(notes: String) {
        val r = rules ?: return
        r.build.notes = notes
        session.buildChanged(null)
    }

    /** The race list's rows (userRaceCombo.InitRows), made when a build is opened. */
    var raceRows: List<RaceRow> = emptyList()
        private set

    /** The highlighted row of the race list (userRaceCombo's mlngActive, here 0-based); -1 = none. */
    var raceListActive by mutableIntStateOf(-1)

    /** txtRace_Click / RaceToggle (:1517, :1540). */
    fun toggleRaceList() {
        if (raceListOpen) {
            raceListOpen = false
        } else {
            showRaceList()
        }
    }

    /** RaceShow (:1521) → userRaceCombo.DropDown/DrawList (:88, :140): the current race is highlighted. */
    fun showRaceList() {
        if (notesTab) {
            return
        }
        val race = rules?.build?.race
        raceListActive = -1
        for (i in raceRows.indices) {
            if (race != null && raceRows[i].race == race) {
                raceListActive = i
            }
        }
        raceListOpen = true
    }

    /** Keys pressed in the race box. */
    enum class RaceKey { UP, DOWN, PAGE_UP, PAGE_DOWN, HOME, END, ENTER, F4, ESCAPE }

    /**
     * A key in the race box. Ported from txtRace_KeyDown (frmOverview.frm:1496) and
     * userRaceCombo.KeyDown (userRaceCombo.ctl:98). Down and Enter open the list when it's closed.
     * Quirk not copied: Enter with no row highlighted makes VB fail (row 0 of a 1-based array);
     * here it does nothing.
     */
    fun raceKey(key: RaceKey) {
        if ((key == RaceKey.DOWN || key == RaceKey.ENTER) && !raceListOpen) {
            showRaceList()
            return
        }
        if (key == RaceKey.F4) {
            toggleRaceList()
            return
        }
        if (key == RaceKey.ESCAPE) {
            raceListOpen = false
            return
        }
        val last = raceRows.size - 1
        var active = raceListActive
        when (key) {
            RaceKey.UP -> {
                active = raceListActive - 1
                while (active >= 1 && raceRows[active].race == null) {
                    active--
                }
                if (active < 1) {
                    active = 1
                }
            }
            RaceKey.DOWN -> {
                active = raceListActive + 1
                while (active <= last && raceRows[active].race == null) {
                    active++
                }
                if (active > last) {
                    active = last
                }
            }
            RaceKey.PAGE_UP, RaceKey.HOME -> active = 1
            RaceKey.PAGE_DOWN, RaceKey.END -> active = last
            RaceKey.ENTER -> {
                if (raceListActive >= 0) {
                    chooseRace(raceRows[raceListActive].race)
                }
                return
            }
            else -> return
        }
        raceListActive = active
    }

    /** usrRace_Click (:1544): a race chosen in the list (null = the empty first row). */
    fun chooseRace(race: Race?) {
        val r = rules ?: return
        raceListOpen = false
        if (!guideAllowsChange(r)) {
            return
        }
        val hadIconic = iconicClass
        r.setRace(race, include32, iconicClass)
        var change = CascadeChange.RACE
        if (hadIconic) {
            change = CascadeChange.CLASS
        }
        session.buildChanged(change)
    }

    /** cboAlignment_Click (:1651). */
    fun chooseAlignment(alignment: Alignment?) {
        val r = rules ?: return
        r.setAlignment(alignment)
        session.buildChanged(CascadeChange.ALIGNMENT)
    }

    /** usrSpinner_Change (:1663). */
    fun setMaxLevels(levels: Int) {
        val r = rules ?: return
        r.setMaxLevels(levels)
        session.buildChanged(CascadeChange.MAX_LEVEL)
    }

    /** lnkSubRace_MouseUp (:1604). */
    fun showSubRaceNotice() {
        val r = rules ?: return
        val message = r.subRaceMessage() ?: return
        session.prompts.notice(message)
    }

    // ---------------------------------------------------------------- class icons and slots

    /**
     * A drag on the screen ended over [target]. Ported from picBuildClass_DragDrop (:2013),
     * picClass_DragDrop (:2038) and the drops on picGrid, the form and picIcons (:2058-:2066),
     * which all clear a build class slot dragged away.
     */
    fun dropped(item: OverviewDrag, target: Any?) {
        val r = rules ?: return
        if (item is OverviewDrag.ClassIcon) {
            if (target is BuildSlotKey && r.isSlotEnabled(target.slot)) {
                if (!guideAllowsChange(r)) {
                    return
                }
                r.dropClass(target.slot, item.characterClass)
                iconicClass = false
                session.buildChanged(CascadeChange.CLASS)
            }
        } else if (item is OverviewDrag.BuildSlot) {
            if (target is BuildSlotKey) {
                if (r.isSlotEnabled(target.slot) && r.swapBuildClasses(item.slot, target.slot)) {
                    session.buildChanged(null)
                }
                iconicClass = false
            } else if (target != null) {
                // Anywhere else on the window: the slot (and the slots after it) are cleared.
                if (!guideAllowsChange(r)) {
                    return
                }
                r.clearBuildClass(item.slot)
                session.buildChanged(CascadeChange.CLASS)
            }
        }
    }

    // ---------------------------------------------------------------- level grid

    /** The pointer moved over grid cell [row]/[col] (ActiveCell, :2324); 0 = outside. */
    fun hover(row: Int, col: Int) {
        activeRow = row
        activeCol = col
        if (levelDragFrom != 0) {
            if (col == 1 && row > 0) {
                levelDragOver = row
            } else {
                levelDragOver = 0
            }
        }
    }

    /**
     * A press on grid cell [row]/[col]. Ported from picGrid_MouseDown (:2245): in the class
     * column it starts dragging that level's class (DragClass, :2254); in a build class column it
     * gives the level that class (ChangeLevelSplit, :2271).
     */
    fun press(row: Int, col: Int) {
        val r = rules ?: return
        hover(row, col)
        raceListOpen = false
        if (row == 0) {
            return
        }
        if (col == 1) {
            if (r.canDragLevel(row)) {
                levelDragFrom = row
                levelDragOver = 0
            }
        } else if (col in 2..4) {
            if (!r.canChangeLevelSplit(row, col - 2)) {
                return
            }
            if (!guideAllowsChange(r)) {
                return
            }
            r.changeLevelSplit(row, col - 2)
            session.buildChanged(CascadeChange.CLASS)
        }
    }

    /** The button came up: ends a level drag. picGrid_OLEDragDrop / OLECompleteDrag (:2303, :2318). */
    fun release(row: Int, col: Int) {
        val r = rules
        val from = levelDragFrom
        levelDragFrom = 0
        levelDragOver = 0
        if (r == null || from == 0 || row == 0 || col != 1) {
            return
        }
        if (!r.canSwapLevels(from, row)) {
            return
        }
        if (!guideAllowsChange(r)) {
            return
        }
        r.swapLevels(from, row)
        session.buildChanged(CascadeChange.CLASS)
    }
}
