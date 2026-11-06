package ddolite.builder.ui

import ddolite.builder.model.AbilityRef
import ddolite.builder.build.AbilityChoice
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ddolite.builder.build.TreeEdits
import ddolite.builder.build.AbilityDetails
import ddolite.builder.build.BLANK_ABILITY
import ddolite.builder.build.BuildAbility
import ddolite.builder.build.BuildTree
import ddolite.builder.build.DropState
import ddolite.builder.build.EnhancementRules
import ddolite.builder.model.RaceType
import ddolite.builder.model.SelectorStyle
import ddolite.builder.model.Tree
import ddolite.builder.model.TreeStyle

/** What's being dragged on the Enhancements window. */
sealed class EnhancementDrag {
    /** A tree from the available list (lstTree). */
    data class ListTree(val treeName: String) : EnhancementDrag()

    /** A build tree, by its row (usrTree). */
    data class TreeRow(val row: Int) : EnhancementDrag()

    /** An ability (and maybe a selector) from the lists (lstAbility / lstSub). */
    data class ListAbility(val tier: Int, val ability: Int) : EnhancementDrag()

    /** An ability slot, by its row (usrList). */
    data class AbilityRow(val row: Int) : EnhancementDrag()
}

/** A build tree row as a drop target (usrTree). */
data class TreeRowKey(val row: Int)

/** An ability slot row as a drop target (usrList). */
data class AbilityRowKey(val row: Int)

/** The available tree list (lstTree). */
object TreeListKey

/** The ability list (lstAbility), which takes slots dragged off (lstAbility_OLEDragDrop clears them). */
object AbilityListKey

/** A tree in the available list: its name and source column (a class or race name; "" for global trees). */
class ListedTree(val name: String, val source: String)

/** One row of the ability slots: tier, caption, AP, ranks and max ranks, error; all blank for an empty row. */
class AbilitySlotRow(val tier: String, val caption: String, val cost: String, val ranks: Int, val maxRanks: Int, val error: Boolean)

/**
 * The state behind the Enhancements window's Trees and Enhancements tabs: VB's frmEnhancements
 * module variables and handlers (Source/Forms/Build/frmEnhancements.frm). The rules are in
 * builder-core's [EnhancementRules]; this edits `build.Tree` directly, as VB does.
 *
 * Rows are numbered from 1 as VB's userList; 0 means none. The Leveling Guide tab's state is [guide].
 */
class EnhancementsScreenState(private val session: BuilderSession) : TreeAbilitiesScreen {
    /** The tab showing (mlngTab): 0 Trees, 1 Enhancements, 2 Leveling Guide. */
    var tab by mutableIntStateOf(0)
        private set

    /** The Leveling Guide tab. */
    val guide = GuideScreenState(session)

    /** Bumped after each change, so the window redraws. */
    var revision by mutableIntStateOf(0)
        private set

    override val drag = DragDropState<EnhancementDrag>()

    // The button of the last press, so only a left-button drag starts a drag.
    internal var lastButton = PointerButton.PRIMARY

    private val rules: EnhancementRules?
        get() {
            val r = session.rules ?: return null
            return EnhancementRules(r)
        }

    private fun changed() {
        revision++
    }

    /**
     * After a change to the trees or abilities: VB calls InitLevelingGuide and SetDirty, not
     * CascadeChanges, so other windows aren't refreshed.
     */
    private fun treesChanged() {
        val r = session.rules ?: return
        r.initLevelingGuide()
        session.enhancementsEdited()
        changed()
    }

    // ================================================================ form

    /** Form_Load (:997): the Trees tab, then LoadData. */
    fun opened() {
        tab = 0
        treeListIndex = -1
        treeDropStates = emptyList()
        loadData()
        changed()
    }

    /** The build changed elsewhere: Cascade (:1040). */
    fun cascade() {
        loadData()
        if (tab == 1) {
            buildTree = session.rules?.findBuildTree(comboTree) ?: 0
        }
        showTab()
        changed()
    }

    /** usrHeader_Click (:1082) → ChangeTab (:1103). */
    fun chooseTab(newTab: Int) {
        if (tab == newTab) {
            return
        }
        tab = newTab
        showTab()
        session.saveBackup()
        changed()
    }

    /** ShowTab (:1111). */
    private fun showTab() {
        if (tab == 0) {
            showTrees(drop = false, add = false)
        } else if (tab == 1) {
            if (buildTree == 0) {
                val names = comboTrees()
                if (names.isNotEmpty()) {
                    comboTree = names[0]
                }
                treeClick()
            } else {
                showAbilities()
                showAvailable()
            }
        } else if (tab == 2) {
            guide.show()
        }
    }

    /**
     * LoadData (:1148): the footer links, the racial and universal AP spinners, and the tier 5
     * tree (dropped below level 12). The trees are shown (ShowTrees, :1184) and the combo of trees
     * is refilled (PopulateCombo).
     */
    private fun loadData() {
        val edits = edits() ?: return
        sourceRow = 0
        edits.loadDataLimits()
        showTrees(drop = false, add = false)
        populateCombo()
        guide.loadData()
    }

    /** Footer: "< Spells" for casters, else "< Feats"; "Destiny >" from level 20. */
    fun footerLeft(): String {
        val build = session.rules?.build ?: return "< Feats"
        if (build.canCastSpell[1] != 0) {
            return "< Spells"
        }
        return "< Feats"
    }

    fun footerRight(): String? {
        val build = session.rules?.build ?: return null
        if (build.maxLevels > 19) {
            return "Destiny >"
        }
        return null
    }

    // ================================================================ Trees tab

    /** The build trees' rows' drop states (usrTree), index row - 1; may have one more row than trees while adding. */
    var treeDropStates: List<DropState> by mutableStateOf(emptyList())
        private set

    /** The row under the mouse (usrTree.Active). */
    var treeHover by mutableIntStateOf(0)

    /** The tree highlighted in the available list (lstTree.ListIndex), -1 = none. */
    var treeListIndex by mutableIntStateOf(-1)
        private set

    /** The build tree whose menu is showing; 0 = none. */
    var treeMenuRow by mutableIntStateOf(0)
        private set

    /** lblRacialAPreq: shown only after the spinner changes (VB doesn't set it in LoadData). */
    var racialApNote: String? by mutableStateOf(null)
        private set

    /** The rows of usrTree: one per build tree, plus an empty one while a tree is dragged in (ShowTrees, :1354). */
    fun treeRows(): List<BuildTree?> {
        val build = session.rules?.build ?: return emptyList()
        val rows = ArrayList<BuildTree?>()
        for (t in build.trees) {
            rows.add(t)
        }
        while (rows.size < treeDropStates.size) {
            rows.add(null)
        }
        return rows
    }

    /** usrTree's Class, Tier and AP columns for a build tree (ShowTrees). */
    fun treeColumns(row: Int): TreeColumns {
        val r = rules ?: return TreeColumns("", "", "")
        val bt = treeRows().getOrNull(row - 1) ?: return TreeColumns("", "", "")
        if (bt.treeType == TreeStyle.RACE) {
            return TreeColumns("", "4", r.quickSpentInTree(row).toString())
        }
        return TreeColumns(bt.source?.name ?: "", r.maxTier(bt).toString(), r.quickSpentInTree(row).toString())
    }

    /**
     * ShowTrees (:1354): one row per build tree (plus an empty row when [add] and there's room);
     * with [drop], every row but the race tree's can take a tree.
     */
    private fun showTrees(drop: Boolean, add: Boolean) {
        val build = session.rules?.build ?: return
        var count = build.trees.size
        if (add && build.trees.size < 7) {
            count++
        }
        val states = ArrayList<DropState>()
        for (i in 0 until count) {
            val isRace = i < build.trees.size && build.trees[i].treeType == TreeStyle.RACE
            states.add(if (drop && !isRace) DropState.CAN_DROP else DropState.DEFAULT)
        }
        treeDropStates = states
        changed()
    }

    /** The AP line (ShowSpentAll, :1390). */
    override fun apCaption(): Caption {
        val r = rules ?: return Caption("", false)
        val totals = r.apTotals()
        return Caption(totals.caption(), totals.overspent)
    }

    /**
     * The trees the build could add: its classes' trees (most levels first), its race's trees,
     * then global trees; not ones already chosen or locked out by a chosen one. Ported from
     * ShowAvailableTrees (:1430) and AddTree (:1462).
     */
    fun availableTrees(): List<ListedTree> {
        val r = session.rules ?: return emptyList()
        val data = r.data
        val result = ArrayList<ListedTree>()
        for (split in r.classSplit()) {
            for (name in split.characterClass.trees) {
                val tree = data.findTree(name)
                if (tree != null) {
                    addTree(result, tree, split.characterClass.name)
                }
            }
        }
        val race = r.build.race
        if (race != null) {
            for (name in race.trees) {
                val tree = data.findTree(name)
                if (tree != null) {
                    addTree(result, tree, race.name)
                }
            }
        }
        for (tree in data.trees) {
            if (tree.style == TreeStyle.GLOBAL) {
                addTree(result, tree, "")
            }
        }
        return result
    }

    private fun addTree(list: ArrayList<ListedTree>, tree: Tree, source: String) {
        val build = session.rules?.build ?: return
        for (bt in build.trees) {
            if (bt.treeName == tree.name) {
                return
            }
        }
        for (listed in list) {
            if (listed.name == tree.name) {
                return
            }
        }
        val lockout = tree.lockout
        if (lockout != null && lockout.isNotEmpty()) {
            for (bt in build.trees) {
                if (bt.treeName == lockout) {
                    return
                }
            }
        }
        list.add(ListedTree(tree.name, source))
    }

    /** usrTree.Selected as VB reads it back (see [FeatsScreenState.selectedRow]). */
    fun selectedTreeRow(): Int {
        var selected = 0
        for (i in treeDropStates.indices) {
            when (treeDropStates[i]) {
                DropState.DEFAULT -> {}
                DropState.CAN_DROP -> {
                    if (selected != 0) {
                        return 0
                    }
                    selected = i + 1
                }
                else -> return 0
            }
        }
        return selected
    }

    private fun selectTreeRow(row: Int) {
        val states = ArrayList<DropState>()
        for (i in 1..treeDropStates.size) {
            states.add(if (i == row) DropState.CAN_DROP else DropState.DEFAULT)
        }
        treeDropStates = states
    }

    /** usrTree_SlotClick (:1267): left toggles the selection, right opens the tree menu. */
    fun clickTree(row: Int, button: PointerButton) {
        treeListIndex = -1
        if (button == PointerButton.PRIMARY) {
            if (selectedTreeRow() == row) {
                selectTreeRow(0)
            } else {
                selectTreeRow(row)
            }
        } else if (button == PointerButton.SECONDARY && row <= (session.rules?.build?.trees?.size ?: 0)) {
            selectTreeRow(row)
            treeHover = row
            treeMenuRow = row
        }
        changed()
    }

    /** The tree menu (mnuTrees): "Delete Abbr" (disabled for the race tree), "Delete All Trees", "-", "Reset Abbr", "Reset All Trees". */
    fun treeMenuItems(): List<MenuChoice> {
        val r = session.rules ?: return emptyList()
        val bt = r.build.trees.getOrNull(treeMenuRow - 1) ?: return emptyList()
        val abbreviation = r.data.findTree(bt.treeName)?.abbreviation ?: ""
        return listOf(
            MenuChoice("Delete $abbreviation", bt.treeType != TreeStyle.RACE),
            MenuChoice("Delete All Trees", true),
            MenuChoice("-", true),
            MenuChoice("Reset $abbreviation", true),
            MenuChoice("Reset All Trees", true),
        )
    }

    fun closeTreeMenu() {
        treeMenuRow = 0
    }

    /**
     * mnuTrees_Click (:1287). VB quirks kept: deleting a tree from the menu doesn't clear the tier 5
     * tree (double-clicking does); "Delete All Trees" starts the race tree afresh, losing its abilities;
     * resetting a tree doesn't clear the tier 5 tree.
     */
    fun chooseTreeMenu(index: Int) {
        treeMenuRow = 0
        val edits = edits() ?: return
        when (index) {
            0 -> {
                if (!edits.deleteTree(selectedTreeRow())) {
                    return
                }
                populateCombo()
            }
            1 -> {
                if (!session.ask("Delete all trees?")) {
                    return
                }
                edits.deleteAllTrees()
                populateCombo()
            }
            3 -> {
                val row = selectedTreeRow()
                if (row == 0) {
                    return
                }
                edits.resetTree(row)
            }
            4 -> {
                if (!session.ask("Reset all trees?")) {
                    return
                }
                edits.resetAllTrees()
            }
            else -> return
        }
        showTrees(drop = false, add = false)
        treesChanged()
    }

    /** usrTree_SlotDblClick (:1333): removes the tree (not the race's), clearing it as the tier 5 tree. */
    fun doubleClickTree(row: Int) {
        treeListIndex = -1
        val edits = edits() ?: return
        if (!edits.removeTree(row)) {
            return
        }
        showTrees(drop = false, add = false)
        populateCombo()
        treesChanged()
    }

    /** lstTree_MouseDown (:1501): selects the tree and clears the slot selection. */
    fun pressListTree(index: Int) {
        selectTreeRow(0)
        treeListIndex = index
        changed()
    }

    /** lstTree_DblClick (:1491): adds the tree after the others (at most 7). */
    fun doubleClickListTree() {
        val build = session.rules?.build ?: return
        if (treeListIndex == -1 || build.trees.size > 6) {
            return
        }
        addBuildTree(build.trees.size + 1)
        showTrees(drop = false, add = false)
        populateCombo()
        treeHover = build.trees.size
        treesChanged()
    }

    /** The build's tree edits (core [TreeEdits]), or null with no build open. */
    private fun edits(): TreeEdits? {
        val r = session.rules ?: return null
        return TreeEdits(r, destinies = false)
    }

    /** Puts the highlighted list tree in row [row], replacing what's there or adding a tree (AddBuildTree, :1564). */
    private fun addBuildTree(row: Int) {
        val edits = edits() ?: return
        val listed = availableTrees().getOrNull(treeListIndex) ?: return
        edits.putTree(row, edits.newEnhancementTree(listed.name, listed.source))
    }

    /** A tree drag is starting: from the list, ShowTrees(True, True) (lstTree_MouseMove, :1507); from a row, usrTree_RequestDrag (:1553). */
    fun startTreeDrag(item: EnhancementDrag): Boolean {
        val build = session.rules?.build ?: return false
        if (item is EnhancementDrag.ListTree) {
            if (treeListIndex == -1) {
                return false
            }
            showTrees(drop = true, add = true)
            return true
        }
        if (item is EnhancementDrag.TreeRow) {
            if (item.row > build.trees.size) {
                return false
            }
            // Every other row can take it, the race tree's too.
            val states = ArrayList<DropState>()
            for (i in 1..build.trees.size) {
                states.add(if (i == item.row) DropState.DEFAULT else DropState.CAN_DROP)
            }
            treeDropStates = states
            changed()
            return true
        }
        return false
    }

    /** A tree drag ended: usrTree_OLEDragDrop (:1537), then the complete-drag handlers' ShowTrees. */
    fun treeDropped(item: EnhancementDrag, target: Any?) {
        val build = session.rules?.build
        var changedTrees = false
        if (build != null && target is TreeRowKey) {
            val state = treeDropStates.getOrNull(target.row - 1)
            if (state != null && state != DropState.DEFAULT) {
                if (item is EnhancementDrag.ListTree) {
                    addBuildTree(target.row)
                    changedTrees = true
                } else if (item is EnhancementDrag.TreeRow && item.row != target.row) {
                    edits()?.swapTrees(item.row, target.row)
                    changedTrees = true
                }
            }
        }
        showTrees(drop = false, add = false)
        if (changedTrees) {
            treeListIndex = -1
            populateCombo()
            treesChanged()
        }
    }

    /** The racial AP spinner's maximum: non-iconic main races' past lives plus the tomes (LoadData). */
    fun racialApMax(): Int {
        val r = session.rules ?: return 0
        var lives = 0
        for (race in r.data.races) {
            if (race.type != RaceType.ICONIC && race.subRace == null) {
                lives++
            }
        }
        return lives + r.data.tomes.racialApMax
    }

    /** "Racial Past Lives offer up to +N Racial AP" (lblRacialAPhelp(1)). */
    fun racialLives(): Int = racialApMax() - (session.rules?.data?.tomes?.racialApMax ?: 0)

    /** usrspnRacialAP_Change (:1236). */
    fun setRacialAp(value: Int) {
        val r = session.rules ?: return
        TreeEdits(r, destinies = false).setRacialAp(value)
        var lives = 0
        if (value > r.data.tomes.racialApMax) {
            lives = value - r.data.tomes.racialApMax
        }
        racialApNote = when {
            value == 0 -> null
            lives == 0 -> "Can be reached with tomes"
            else -> "Requires " + (lives * 3) + " racial past lives"
        }
        treesChanged()
    }

    /** The open build, for the AP spinners. */
    fun build(): ddolite.builder.build.Build? = session.rules?.build

    /** The universal AP spinner's maximum: the tomes (LoadData). */
    fun universalApMax(): Int = session.rules?.data?.tomes?.universalApMax ?: 0

    /** What a drag shows next to the pointer. */
    fun dragText(item: EnhancementDrag): String {
        return when (item) {
            is EnhancementDrag.ListTree -> item.treeName
            is EnhancementDrag.TreeRow -> treeRows().getOrNull(item.row - 1)?.treeName ?: ""
            is EnhancementDrag.ListAbility -> currentTree()?.tiers?.getOrNull(item.tier)?.getOrNull(item.ability)?.abbreviation ?: ""
            is EnhancementDrag.AbilityRow -> slotRows().getOrNull(item.row - 1)?.caption ?: ""
        }
    }

    /** usrspnUniversalAP_Change (:1255). */
    fun setUniversalAp(value: Int) {
        val edits = edits() ?: return
        edits.setUniversalAp(value)
        treesChanged()
    }

    // ================================================================ Enhancements tab

    /** The tree chosen in cboTree (its text). */
    override var comboTree by mutableStateOf("")
        private set

    // mlngBuildTree: the chosen tree's position in build.Tree (1-based, 0 = none).
    private var buildTree = 0

    /** The ability slots' drop states (usrList), index row - 1. */
    override var slotStates: List<DropState> by mutableStateOf(emptyList())
        private set

    /** The slot under the mouse (usrList.Active). */
    override var slotHover by mutableIntStateOf(0)

    /** The ability list (lstAbility) as (tier, ability), and its highlighted row (-1 = none). */
    override var abilities: List<AbilityRef> by mutableStateOf(emptyList())
        private set
    override var abilityIndex by mutableIntStateOf(-1)
        private set

    /** The selector list (lstSub), and its highlighted row (-1 = none). */
    override var selectors: List<SelectorItem> by mutableStateOf(emptyList())
        private set
    override var selectorIndex by mutableIntStateOf(-1)
        private set

    /** "Show All" (usrchkShowAll). */
    override var showAll by mutableStateOf(false)
        private set

    /** The details box; null shows "Details" with nothing else. */
    override var details: AbilityDetails? by mutableStateOf(null)
        private set

    /** The slot whose menu is showing (mnuMain(1)); 0 = none. */
    override var slotMenuRow by mutableIntStateOf(0)
        private set

    // mlngSourceIndex: the slot a drag started from; mblnDragComplete: the drag put an ability in a slot.
    private var sourceRow = 0
    private var dragComplete = false

    /** The data tree being shown (mlngTree). */
    fun currentTree(): Tree? = session.rules?.data?.findTree(comboTree)

    private fun currentBuildTree(): BuildTree? = session.rules?.build?.trees?.getOrNull(buildTree - 1)

    /** cboTree's items: the build's trees (PopulateCombo). */
    override fun comboTrees(): List<String> {
        val build = session.rules?.build ?: return emptyList()
        val names = ArrayList<String>()
        for (bt in build.trees) {
            names.add(bt.treeName)
        }
        return names
    }

    /**
     * PopulateCombo (:1212): keeps the chosen tree if the build still has it, else chooses the
     * tier 5 tree or the race's tree.
     */
    private fun populateCombo() {
        val r = session.rules ?: return
        if (!comboTrees().contains(comboTree)) {
            if (r.build.tier5.isNotEmpty()) {
                comboTree = r.build.tier5
            } else {
                comboTree = r.build.race?.name ?: ""
            }
            if (comboTrees().contains(comboTree)) {
                treeClick()
            } else {
                comboTree = ""
            }
        }
    }

    /** cboTree_Click (:2182). */
    override fun chooseComboTree(name: String) {
        comboTree = name
        treeClick()
        session.saveBackup()
        changed()
    }

    /** TreeClick (:2188). */
    private fun treeClick() {
        noSelection()
        val r = session.rules ?: return
        buildTree = r.findBuildTree(comboTree)
        showAbilities()
        showAvailable()
    }

    /** The slot rows (ShowAbilities, :1622): one blank row when the tree has no abilities. */
    override fun slotRows(): List<AbilitySlotRow> {
        val r = rules ?: return emptyList()
        val tree = currentTree()
        val bt = currentBuildTree()
        if (tree == null || bt == null || bt.abilities.isEmpty()) {
            return listOf(AbilitySlotRow("", "", "", 0, 0, false))
        }
        val spend = r.spentInTree(tree, bt)
        val rows = ArrayList<AbilitySlotRow>()
        for (a in bt.abilities) {
            if (a.ability == BLANK_ABILITY) {
                rows.add(AbilitySlotRow("", "", "", 0, 0, false))
            } else {
                val info = r.slotInfo(tree, a)
                val error = r.abilityError(tree, bt, a, spend) != null
                rows.add(AbilitySlotRow(a.tier.toString(), info.caption, info.cost.toString(), a.rank, info.maxRanks, error))
            }
        }
        return rows
    }

    /** "Spent in Tree: N" (lblSpent). */
    override fun spentCaption(): String {
        val r = rules ?: return ""
        return "Spent in Tree: " + r.spentInTree(currentTree(), currentBuildTree()).total
    }

    /** "Tier 5 Tree" label (ShowTier5, :1873). */
    override fun tier5(): String = session.rules?.build?.tier5 ?: ""

    private fun showAbilities() {
        val count = slotRows().size
        val states = ArrayList<DropState>()
        for (i in 0 until count) {
            states.add(DropState.DEFAULT)
        }
        slotStates = states
        changed()
    }

    /** ShowAvailable (:2241). */
    private fun showAvailable() {
        val r = rules ?: return
        selectors = emptyList()
        selectorIndex = -1
        abilityIndex = -1
        val tree = currentTree()
        if (tree == null) {
            abilities = emptyList()
            return
        }
        abilities = r.availableAbilities(tree, currentBuildTree(), showAll)
        changed()
    }

    /** lstAbility's text for a row: "tier: Abbreviation". */
    override fun abilityText(item: AbilityRef): String {
        val tree = currentTree() ?: return ""
        return item.tier.toString() + ": " + tree.tiers[item.tier][item.ability].abbreviation
    }

    /** usrchkShowAll_UserChange (:2178). */
    override fun changeShowAll(value: Boolean) {
        showAll = value
        showAvailable()
    }

    /** NoSelection (:2464). */
    private fun noSelection() {
        if (buildTree == 0) {
            return
        }
        selectorIndex = -1
        abilityIndex = -1
        val states = ArrayList<DropState>()
        for (i in slotStates.indices) {
            states.add(DropState.DEFAULT)
        }
        slotStates = states
        details = null
    }

    private fun selectSlot(row: Int) {
        val states = ArrayList<DropState>()
        for (i in 1..slotStates.size) {
            states.add(if (i == row) DropState.CAN_DROP else DropState.DEFAULT)
        }
        slotStates = states
    }

    private fun selectedSlot(): Int {
        var selected = 0
        for (i in slotStates.indices) {
            when (slotStates[i]) {
                DropState.DEFAULT -> {}
                DropState.CAN_DROP -> {
                    if (selected != 0) {
                        return 0
                    }
                    selected = i + 1
                }
                else -> return 0
            }
        }
        return selected
    }

    private fun showDetails(tier: Int, ability: Int, selector: Int, slotRow: Int) {
        val r = rules ?: return
        val tree = currentTree() ?: return
        val bt = currentBuildTree()
        var slot: BuildAbility? = null
        if (slotRow != 0 && bt != null) {
            slot = bt.abilities.getOrNull(slotRow - 1)
        }
        details = r.details(tree, bt, tier, ability, selector, slot)
    }

    /** usrList_SlotClick (:1902). */
    override fun clickSlot(row: Int, button: PointerButton) {
        val bt = currentBuildTree() ?: return
        selectorIndex = -1
        abilityIndex = -1
        val rows = slotRows()
        if (rows.isEmpty() || rows[0].caption.isEmpty()) {
            changed()
            return
        }
        if (selectedSlot() == row) {
            noSelection()
            if (button == PointerButton.SECONDARY) {
                clearSlot(row)
            }
        } else if (button == PointerButton.PRIMARY) {
            selectSlot(row)
            val a = bt.abilities[row - 1]
            showDetails(a.tier, a.ability, a.selector, row)
        } else if (button == PointerButton.SECONDARY) {
            selectSlot(row)
            slotHover = row
            val a = bt.abilities[row - 1]
            showDetails(a.tier, a.ability, a.selector, row)
            slotMenuRow = row
        }
        changed()
    }

    fun closeSlotMenu() {
        slotMenuRow = 0
    }

    /** mnuEnhancements_Click (:1939): "Clear this ability" or "Reset tree". */
    fun chooseSlotMenu(index: Int) {
        slotMenuRow = 0
        val r = session.rules ?: return
        if (index == 0) {
            clearSlot(selectedSlot())
            return
        }
        val bt = currentBuildTree() ?: return
        if (!session.ask("Reset $comboTree?")) {
            return
        }
        TreeEdits(r, destinies = false).resetAbilities(bt)
        showAbilities()
        showAvailable()
        noSelection()
        treesChanged()
    }

    /** usrList_SlotDblClick (:1958). */
    override fun doubleClickSlot(row: Int) {
        clearSlot(row)
    }

    /**
     * ClearSlot (:1962). VB quirk kept: emptying any tree clears the tier 5 tree, even when it's
     * another tree (whose tier 5 abilities are then removed).
     */
    private fun clearSlot(row: Int) {
        val edits = edits() ?: return
        val bt = currentBuildTree() ?: return
        if (!edits.clearSlot(bt, row)) {
            return
        }
        showAbilities()
        showAvailable()
        noSelection()
        treesChanged()
    }

    /** A rank arrow on slot [row] (usrList_RankChange, :2001). */
    override fun changeRank(row: Int, ranks: Int) {
        val edits = edits() ?: return
        val bt = currentBuildTree() ?: return
        val a = edits.setRanks(bt, row, ranks) ?: return
        showAbilities()
        showAvailable()
        showDetails(a.tier, a.ability, a.selector, row)
        treesChanged()
    }

    /**
     * An ability pressed in the list: lstAbility_MouseDown (:2029) and ListAbilityClick (:2060).
     * Shows its details and selectors; returns true if it has no selectors (so it can be dragged).
     */
    override fun clickAbility(index: Int): Boolean {
        val r = rules ?: return false
        val tree = currentTree() ?: return false
        val item = abilities.getOrNull(index) ?: return false
        selectSlot(0)
        abilityIndex = index
        selectors = emptyList()
        selectorIndex = -1
        showDetails(item.tier, item.ability, 0, 0)
        val ability = tree.tiers[item.tier][item.ability]
        if (ability.selectorStyle == SelectorStyle.NONE) {
            changed()
            return true
        }
        // ShowSelectors (:2276): the valid ones, in data order (lstSub isn't sorted).
        val valid = r.selectors(tree, item.tier, item.ability)
        val list = ArrayList<SelectorItem>()
        for (i in 1..ability.selectors.size) {
            if (valid[i]) {
                list.add(SelectorItem(ability.selectors[i - 1].name, i))
            }
        }
        selectors = list
        changed()
        return false
    }

    /** lstSub_MouseDown / lstSub_Click (:2128, :2105). */
    override fun clickSelector(index: Int) {
        val item = abilities.getOrNull(abilityIndex) ?: return
        val selector = selectors.getOrNull(index) ?: return
        selectorIndex = index
        selectSlot(0)
        showDetails(item.tier, item.ability, selector.number, 0)
        changed()
    }

    /** GetUserChoices (:1763): (tier, ability, selector) chosen in the lists, or null if a needed selector isn't. */
    private fun userChoice(): AbilityChoice? {
        val tree = currentTree() ?: return null
        val item = abilities.getOrNull(abilityIndex) ?: return null
        var selector = 0
        if (tree.tiers[item.tier][item.ability].selectorStyle != SelectorStyle.NONE) {
            val s = selectors.getOrNull(selectorIndex) ?: return null
            selector = s.number
        }
        return AbilityChoice(item.tier, item.ability, selector)
    }

    /**
     * ConfirmTier5Change (:1882): taking a tier 5 ability from a second tree asks to move the tier 5
     * tree there. Returns false if the user says no.
     */
    private fun confirmTier5(): Boolean {
        val edits = edits() ?: return true
        val choice = userChoice() ?: return true
        val bt = currentBuildTree() ?: return true
        val question = edits.tier5Question(bt, choice.tier) ?: return true
        if (!session.ask(question)) {
            return false
        }
        edits.confirmTier5(bt)
        return true
    }

    /** lstAbility_DblClick / lstSub_DblClick (:2019, :2118). */
    override fun doubleClickList(fromSelectors: Boolean) {
        if (abilityIndex == -1) {
            return
        }
        if (!fromSelectors && selectors.isNotEmpty()) {
            return
        }
        if (fromSelectors && selectorIndex == -1) {
            return
        }
        if (!confirmTier5()) {
            return
        }
        if (addAbility(blank = false)) {
            showAvailable()
            treesChanged()
        }
    }

    /**
     * AddAbility (:1792): inserts the chosen ability (at its max ranks) in tier order, or a blank
     * to drop onto when [blank]. A tier 5 ability makes this the tier 5 tree.
     */
    private fun addAbility(blank: Boolean): Boolean {
        val edits = edits() ?: return false
        val tree = currentTree() ?: return false
        val bt = currentBuildTree() ?: return false
        val choice = userChoice() ?: return false
        val ranks = tree.tiers[choice.tier][choice.ability].ranks
        val insert = edits.insertAbility(bt, choice.tier, choice.ability, ranks, choice.selector, blank)
        showAbilities()
        slotHover = insert + 1
        return true
    }

    /**
     * A drag is starting. From the lists: StartDrag (:1779) adds a blank slot where the ability
     * would go and lights it up (ShowDropSlots, :1675). From a slot: usrList_RequestDrag (:1981).
     */
    override fun startAbilityDrag(item: EnhancementDrag): Boolean {
        if (item is EnhancementDrag.AbilityRow) {
            val bt = currentBuildTree() ?: return false
            if (item.row > bt.abilities.size) {
                return false
            }
            sourceRow = item.row
            return true
        }
        val r = rules ?: return false
        val choice = userChoice() ?: return false
        if (buildTree != 0 && !addAbility(blank = true)) {
            return false
        }
        val tree = currentTree() ?: return false
        val bt = currentBuildTree() ?: return false
        val spend = r.spentInTree(tree, bt)
        val states = ArrayList<DropState>()
        for (a in bt.abilities) {
            if (a.ability != BLANK_ABILITY) {
                states.add(DropState.DEFAULT)
            } else {
                val check = BuildAbility(choice.tier, choice.ability, 1, choice.selector)
                states.add(if (r.abilityError(tree, bt, check, spend) != null) DropState.CAN_DROP_ERROR else DropState.CAN_DROP)
            }
        }
        slotStates = states
        changed()
        return true
    }

    /**
     * A drag ended on [target]. usrList_OLEDragDrop (:1987) puts the ability in the blank slot;
     * lstAbility_OLEDragDrop (:2090) clears a slot dragged onto the list; otherwise the blank is
     * removed again (lstAbility_OLECompleteDrag, :2080).
     */
    override fun abilityDropped(item: EnhancementDrag, target: Any?) {
        val r = session.rules
        val bt = currentBuildTree()
        if (item is EnhancementDrag.AbilityRow) {
            val from = sourceRow
            sourceRow = 0
            if (target == AbilityListKey && from != 0) {
                clearSlot(from)
            }
            return
        }
        if (r != null && bt != null && target is AbilityRowKey) {
            val state = slotStates.getOrNull(target.row - 1)
            if (state != null && state != DropState.DEFAULT && confirmTier5()) {
                val choice = userChoice()
                val tree = currentTree()
                if (choice != null && tree != null) {
                    dragComplete = true
                    TreeEdits(r, destinies = false).dropAbility(bt, target.row, choice.tier, choice.ability, tree.tiers[choice.tier][choice.ability].ranks, choice.selector)
                    showAbilities()
                    slotHover = target.row
                    showAvailable()
                    treesChanged()
                }
            }
        }
        if (!dragComplete && bt != null) {
            TreeEdits(r ?: return, destinies = false).removeBlanks(bt)
            showAbilities()
            slotHover = 0
        }
        dragComplete = false
    }
}
