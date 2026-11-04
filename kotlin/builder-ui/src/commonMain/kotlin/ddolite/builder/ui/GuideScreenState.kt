package ddolite.builder.ui

import ddolite.builder.build.GuideListItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ddolite.builder.build.DetailLine
import ddolite.builder.build.GuideChoice
import ddolite.builder.build.GuideEnhancement
import ddolite.builder.build.GuideFilter
import ddolite.builder.build.GuideRules
import ddolite.builder.build.GuideStepStyle
import ddolite.builder.build.GuideTotals

/** The guide grid's rows on screen at once (DrawGrid, frmEnhancements.frm:3660). */
const val GUIDE_ROWS = 20

/**
 * The state behind the Enhancements window's Leveling Guide tab (picTab(2) of frmEnhancements):
 * the tree list or a selector list, the ability filter and list, the details, and the guide grid
 * with its row selection and menu. The rules are in builder-core's [GuideRules]; this keeps what
 * the form keeps (mlngGuideTree, mlngGuideLevel, mlngGuideAP, the selection, mblnMoveRows …).
 *
 * VB keeps each row's Selected flag in `Guide.Enhancement`, which is rebuilt whenever the guide
 * changes, so any change clears the selection; [selected] does the same.
 */
class GuideScreenState(private val session: BuilderSession) {
    /** usrchkGuideTree ("Show All" trees); unticked in the form. */
    var showAllTrees by mutableStateOf(false)
        private set

    /** lstGuideTree: display and guide tree (0 = "(Commands)"), and the highlighted row. */
    var trees: List<GuideListItem> by mutableStateOf(emptyList())
        private set
    var treeIndex by mutableIntStateOf(-1)
        private set

    /** mlngGuideTree: the tree whose abilities are listed; 0 = the commands. */
    var guideTree = 0
        private set

    /** cboGuideAbility's choice. */
    var filter by mutableStateOf(GuideFilter.SELECTED_AVAILABLE)
        private set

    /** lstGuideAbility and its highlighted row. */
    var choices: List<GuideChoice> by mutableStateOf(emptyList())
        private set
    var choiceIndex by mutableIntStateOf(-1)
        private set

    /** Shown in the empty ability list when "Available" finds nothing (Kotlin only; VB leaves it blank). */
    var nothingAvailable: String? by mutableStateOf(null)
        private set

    /** picSelector (shown in place of the tree list) with lstGuideSub: name and selector number. */
    var selectorsShowing by mutableStateOf(false)
        private set
    var selectors: List<GuideListItem> by mutableStateOf(emptyList())
        private set
    var selectorIndex by mutableIntStateOf(-1)
        private set

    /** GuideTotals: the level and AP labels under the grid. */
    var totals: GuideTotals? by mutableStateOf(null)
        private set

    /** lblGuideDetails and usrdetGuide. */
    var detailsCaption by mutableStateOf("Details")
        private set
    var details: List<DetailLine> by mutableStateOf(emptyList())
        private set

    /** lblGuideCost, lblGuideSpentInTree and lblGuideLevels; null = hidden. */
    var costLabel: String? by mutableStateOf(null)
        private set
    var spentLabel: String? by mutableStateOf(null)
        private set
    var levelsLabel: String? by mutableStateOf(null)
        private set
    var levelsError by mutableStateOf(false)
        private set

    /** Guide.Enhancement(i).Selected, index row - 1. */
    var selected: List<Boolean> by mutableStateOf(emptyList())
        private set
    private var selectedFor: List<GuideEnhancement>? = null

    /** mblnMoveRows: rows chosen with "Move Rows", waiting for "Insert Rows Here". */
    var moveRows by mutableStateOf(false)
        private set
    private var lastRowSelected = 0

    /** The row with a highlighted border (the cell under the mouse, or the one inserted); 0 = none. */
    var borderRow by mutableIntStateOf(0)
        private set

    /** The first row shown (scrollVertical.Value). */
    var scroll by mutableIntStateOf(0)
        private set

    /** The row whose menu is showing; 0 = none. */
    var menuRow by mutableIntStateOf(0)
        private set

    /** Bumped after each change, so the tab redraws. */
    var revision by mutableIntStateOf(0)
        private set

    private fun changed() {
        revision++
    }

    private fun rules(): GuideRules? {
        val r = session.rules ?: return null
        return GuideRules(r)
    }

    /** The guide's rows (VB's Guide.Enhancement). */
    fun rows(): List<GuideEnhancement> = session.rules?.guideEnhancements ?: emptyList()

    /** The guide trees' display names, for the grid's Tree column; index guide tree - 1. */
    fun treeDisplay(guide: Int): String {
        val trees = session.rules?.guideTrees ?: return ""
        if (guide < 1 || guide > trees.size) {
            return ""
        }
        return trees[guide - 1].display
    }

    /** The widest name in the Tree column (InitGrid sizes the column to it). */
    fun widestTree(measure: (String) -> Int): String {
        var widest = ""
        for (g in session.rules?.guideTrees ?: emptyList()) {
            if (measure(g.display) > measure(widest)) {
                widest = g.display
            }
        }
        return widest
    }

    /** Keeps [selected] in step with the rows: a rebuilt guide has nothing selected. */
    private fun syncSelection() {
        val rows = rows()
        if (rows !== selectedFor || selected.size != rows.size) {
            selectedFor = rows
            selected = List(rows.size) { false }
        }
    }

    fun isSelected(row: Int): Boolean {
        syncSelection()
        return row >= 1 && row <= selected.size && selected[row - 1]
    }

    private fun setSelected(row: Int, value: Boolean) {
        syncSelection()
        val list = ArrayList(selected)
        list[row - 1] = value
        selected = list
    }

    // ---------------------------------------------------------------- the tab

    /** LoadData (:1208) sets the filter back to its first item, each time the form loads or cascades. */
    fun loadData() {
        filter = GuideFilter.SELECTED_AVAILABLE
    }

    /**
     * ShowTab for the Leveling Guide (:1134): InitGuide (:2494), then ClearSelection. Also after Undo
     * and Redo. The tree showing stays selected, its list rebuilt (Kotlin only, VB-BUGS 22j).
     */
    fun show() {
        val wasShowing = treeIndex != -1
        initGuide()
        reselectTree(wasShowing)
        clearSelection()
        changed()
    }

    /** InitGuide: GuideTotals, LoadGuideTrees, InitGrid (which scrolls to the top). */
    private fun initGuide() {
        totals = rules()?.totals()
        loadGuideTrees()
        drawGrid(scrollToEnd = false)
    }

    /** DrawGrid (:3660): forgets the last row clicked and "Move Rows"; scrolls to the top or the end. */
    private fun drawGrid(scrollToEnd: Boolean) {
        lastRowSelected = 0
        moveRows = false
        val count = rows().size
        if (count > GUIDE_ROWS) {
            scroll = if (scrollToEnd) count - GUIDE_ROWS else 0
        } else {
            scroll = 0
        }
    }

    /** Scrolls the grid by [rows] (the scroll bar or the mouse wheel). */
    fun scrollBy(rows: Int) {
        val max = maxOf(0, rows().size - GUIDE_ROWS)
        scroll = (scroll + rows).coerceIn(0, max)
        changed()
    }

    /**
     * LoadGuideTrees (:2570). The ability list and mlngGuideTree are left as they were: clearing the
     * tree list doesn't raise its Click.
     */
    private fun loadGuideTrees() {
        trees = rules()?.treeList(showAllTrees) ?: emptyList()
        treeIndex = -1
        selectorsShowing = false
    }

    /** usrchkGuideTree_UserChange (:2566). */
    fun toggleShowAllTrees() {
        showAllTrees = !showAllTrees
        loadGuideTrees()
        changed()
    }

    /** lstGuideTree_Click (:2585). */
    fun clickTree(index: Int) {
        choices = emptyList()
        choiceIndex = -1
        treeIndex = index
        guideTree = trees.getOrNull(index)?.number ?: 0
        showGuideAvailable()
        if (index != -1) { // VB leaves before SaveBackup with nothing selected
            session.saveBackup()
        }
        changed()
    }

    /** cboGuideAbility_Click (:2600). */
    fun chooseFilter(newFilter: GuideFilter) {
        filter = newFilter
        showGuideAvailable()
        session.saveBackup()
        changed()
    }

    /** ShowGuideAvailable (:2606). */
    private fun showGuideAvailable() {
        choices = emptyList()
        choiceIndex = -1
        nothingAvailable = null
        val g = rules() ?: return
        val t = totals ?: g.totals()
        if (guideTree == 0) {
            if (treeIndex == -1) {
                return
            }
            choices = g.commands(t.ap)
            return
        }
        choices = g.availableAbilities(guideTree, filter, t.level)
        if (choices.isEmpty() && filter == GuideFilter.ALL_AVAILABLE) {
            nothingAvailable = g.nothingAvailableMessage(guideTree, t.level)
        }
    }

    // ---------------------------------------------------------------- abilities and selectors

    /**
     * A click on lstGuideAbility: lstGuideAbility_Click (:2887). Clearing a highlighted selector
     * raises lstGuideSub_Click, which does the same again.
     */
    fun clickChoice(index: Int) {
        choiceIndex = index
        if (selectorsShowing) {
            selectorIndex = -1
        }
        clearSelection()
        guideDetails(0)
        changed()
    }

    /** A click on lstGuideSub: lstGuideSub_Click (:2894). */
    fun clickSelector(index: Int) {
        selectorIndex = index
        clearSelection()
        guideDetails(0)
        changed()
    }

    /** ClearSelection (:2900). */
    private fun clearSelection() {
        syncSelection()
        selected = List(selected.size) { false }
        lastRowSelected = 0
        moveRows = false
    }

    /** chkBackToTrees_Click (:3015). */
    fun backToTrees() {
        selectorsShowing = false
        selectors = emptyList()
        selectorIndex = -1
        changed()
    }

    /** HideGuideSelectors (:3022). */
    private fun hideGuideSelectors() {
        selectorsShowing = false
        selectorIndex = -1
        selectors = emptyList()
    }

    /** ShowGuideSelectors (:2921). */
    private fun showGuideSelectors(tier: Int, ability: Int) {
        val g = rules() ?: return
        val offered = g.selectors(guideTree, tier, ability)
        val list = ArrayList<GuideListItem>()
        for (i in 1 until offered.size) {
            if (offered[i]) {
                list.add(GuideListItem(g.selectorName(guideTree, tier, ability, i), i))
            }
        }
        selectors = list
        selectorIndex = -1
        selectorsShowing = true
    }

    /** GuideDetails (:3460) for the list's choice. */
    private fun guideDetails(row: Int) {
        val choice = choices.getOrNull(choiceIndex)
        if (choice == null) {
            clearGuideDetails(clearLabel = true)
            hideGuideSelectors()
            return
        }
        val g = rules() ?: return
        if (guideTree == 0) {
            // The cost, spent and level labels keep whatever they showed (VB doesn't touch them).
            val (caption, lines) = g.commandDetails(choice.command, totals?.ap ?: 0)
            detailsCaption = caption
            details = lines
            return
        }
        var selector = choice.selector
        if (g.needsSelector(guideTree, choice.tier, choice.ability) && selector == 0) {
            if (selectorIndex == -1) {
                showGuideSelectors(choice.tier, choice.ability)
            } else {
                selector = selectors[selectorIndex].number
            }
        } else {
            hideGuideSelectors()
        }
        showGuideDetails(guideTree, choice.tier, choice.ability, selector, choice.rank, row)
    }

    /** ShowGuideDetails (:3507). */
    private fun showGuideDetails(guide: Int, tier: Int, ability: Int, selector: Int, rank: Int, row: Int) {
        clearGuideDetails(clearLabel = false)
        val d = rules()?.details(guide, tier, ability, selector, rank, row) ?: return
        detailsCaption = d.caption
        details = d.lines
        costLabel = d.cost
        spentLabel = d.spentInTree
        levelsLabel = d.levels
        levelsError = d.levelsError
    }

    /** ClearGuideDetails (:3585). */
    private fun clearGuideDetails(clearLabel: Boolean) {
        if (clearLabel) {
            detailsCaption = "Details"
        }
        details = emptyList()
        costLabel = null
        spentLabel = null
        levelsLabel = null
    }

    /** A double-click on lstGuideAbility or lstGuideSub: GuideChooseAbility (:3033). */
    fun chooseAbility() {
        val choice = choices.getOrNull(choiceIndex) ?: return
        val g = rules() ?: return
        if (guideTree == 0) {
            g.addCommand(choice.command)
        } else {
            var selector = choice.selector
            if (g.needsSelector(guideTree, choice.tier, choice.ability) && selector == 0) {
                if (selectorIndex == -1) {
                    return
                }
                selector = selectors[selectorIndex].number
                hideGuideSelectors()
                if (selector == 0) {
                    return
                }
            }
            g.addAbility(guideTree, choice.tier, choice.ability, selector, choice.rank)
        }
        totals = g.totals()
        drawGrid(scrollToEnd = true)
        showGuideAvailable()
        session.markDirty()
        changed()
    }

    // ---------------------------------------------------------------- the grid

    /**
     * A press on the Ability cell of 1-based [row]: picGuide_MouseDown (:3165). [right] for the
     * right button; [ctrl] and [shift] as held (Ctrl wins).
     */
    fun pressRow(row: Int, right: Boolean, ctrl: Boolean, shift: Boolean) {
        val rows = rows()
        if (row < 1 || row > rows.size) {
            return
        }
        syncSelection()
        val normal = !ctrl && !shift
        var selections = 0
        if ((!right && normal) || (right && !moveRows && !isSelected(row))) {
            moveRows = false
            if (!right || !isSelected(row)) {
                val list = ArrayList(selected)
                for (i in list.indices) {
                    if (i != row - 1 && list[i]) {
                        selections++
                        list[i] = false
                    }
                }
                selected = list
            }
        }
        if (!right) {
            if (normal) {
                setSelected(row, selections > 1 || !isSelected(row))
            } else if (ctrl) {
                setSelected(row, !isSelected(row))
            } else {
                setSelected(row, true)
                var start = row
                var end = row
                if (lastRowSelected != 0) {
                    if (row > lastRowSelected) {
                        start = lastRowSelected
                    } else {
                        end = lastRowSelected
                    }
                }
                for (i in start..end) {
                    setSelected(i, true)
                }
            }
            lastRowSelected = if (isSelected(row)) row else 0
            borderRow = row
            guideClicked(row)
        } else {
            if (!moveRows) {
                setSelected(row, true)
            }
            borderRow = row
            guideClicked(row)
            menuRow = row
        }
        changed()
    }

    /** The mouse over a row's Ability cell with the left button down: picGuide_MouseMove (:3137). */
    fun dragOverRow(row: Int) {
        val rows = rows()
        if (row < 1 || row > rows.size) {
            return
        }
        setSelected(row, true)
        borderRow = row
        lastRowSelected = row
        changed()
    }

    /** The mouse over a cell (0 = none): ActiveCell (:3262) moves the highlighted border. */
    fun hoverRow(row: Int) {
        val count = rows().size
        borderRow = if (row >= 1 && row <= count) row else 0
    }

    /** The button released over a row: picGuide_MouseUp (:3253). */
    fun releaseRow(row: Int) {
        val rows = rows()
        if (row < 1 || row > rows.size) {
            return
        }
        val e = rows[row - 1]
        if (isSelected(row) && e.style == GuideStepStyle.ENHANCEMENT) {
            showGuideDetails(e.guideTree, e.tier, e.ability, e.selector, e.rank, row)
            changed()
        }
    }

    /** GuideClicked (:3225): the lists lose their choice; a selected ability row shows its details. */
    private fun guideClicked(row: Int) {
        choiceIndex = -1
        selectorIndex = -1
        selectorsShowing = false
        val e = rows()[row - 1]
        if (isSelected(row) && e.style == GuideStepStyle.ENHANCEMENT) {
            showGuideDetails(e.guideTree, e.tier, e.ability, e.selector, e.rank, row)
        } else {
            clearGuideDetails(clearLabel = true)
        }
    }

    /** The row menu's items and whether each is enabled: PopupGuideMenu (:3299). */
    fun menuItems(): List<MenuChoice> {
        syncSelection()
        var count = 0
        for (s in selected) {
            if (s) {
                count++
            }
        }
        val plural = if (count > 1) "s" else ""
        val rowSelected = isSelected(menuRow)
        val items = ArrayList<MenuChoice>()
        if (moveRows) {
            items.add(MenuChoice("Insert Row$plural Here", !rowSelected))
            items.add(MenuChoice("-", true))
            items.add(MenuChoice("Delete Row$plural", rowSelected))
            items.add(MenuChoice("Delete to End", rowSelected && count == 1))
        } else {
            items.add(MenuChoice("Move Row$plural", true))
            items.add(MenuChoice("-", true))
            items.add(MenuChoice("Delete Row$plural", true))
            items.add(MenuChoice("Delete to End", count == 1))
        }
        items.add(MenuChoice("Delete All", true))
        return items
    }

    /** mnuGuide_Click (:3323). */
    fun chooseMenu(index: Int) {
        val g = rules()
        val row = menuRow
        menuRow = 0
        if (g == null) {
            return
        }
        val caption = menuItems().getOrNull(index)?.caption ?: ""
        syncSelection()
        if (caption.startsWith("Insert Row")) {
            val (first, count) = g.insertRows(selected, row)
            refreshGuide(first, count)
        } else if (caption.startsWith("Move Row")) {
            moveRows = true
        } else if (caption.startsWith("Delete Row")) {
            g.deleteRows(selected)
            refreshGuide(0, 0)
        } else if (caption == "Delete to End") {
            g.deleteToEnd(row)
            refreshGuide(0, 0)
        } else if (caption == "Delete All") {
            if (session.ask("Clear Leveling Guide?")) {
                g.clear()
                refreshGuide(0, 0)
            }
        }
        changed()
    }

    fun closeMenu() {
        menuRow = 0
        changed()
    }

    /**
     * After the tree list is reloaded: select the tree (or "(Commands)") that was showing again and
     * rebuild its list, so it matches the guide. Kotlin only: VB's RefreshGuide leaves the list as it
     * was, with no tree selected, still offering the abilities as they were before the change
     * (VB-BUGS 22j). If no tree was selected, or it's no longer listed, the list is emptied.
     */
    private fun reselectTree(wasShowing: Boolean) {
        if (wasShowing) {
            for (i in trees.indices) {
                if (trees[i].number == guideTree) {
                    treeIndex = i
                    showGuideAvailable()
                    return
                }
            }
        }
        guideTree = 0
        choices = emptyList()
        choiceIndex = -1
        nothingAvailable = null
    }

    /**
     * RefreshGuide (:3430): rebuild the guide, select the rows just moved (when more than one),
     * keep the scroll position, and SetDirty.
     */
    private fun refreshGuide(first: Int, count: Int) {
        val keep = scroll
        val wasShowing = treeIndex != -1
        lastRowSelected = 0
        moveRows = false
        clearGuideDetails(clearLabel = true)
        session.rules?.initLevelingGuide()
        syncSelection()
        if (count > 1) {
            val list = ArrayList(selected)
            for (i in first until first + count) {
                if (i >= 1 && i <= list.size) {
                    list[i - 1] = true
                }
            }
            selected = list
        }
        initGuide()
        reselectTree(wasShowing)
        borderRow = if (count == 1) first else 0
        val max = maxOf(0, rows().size - GUIDE_ROWS)
        scroll = keep.coerceIn(0, max)
        session.markDirty()
    }
}
