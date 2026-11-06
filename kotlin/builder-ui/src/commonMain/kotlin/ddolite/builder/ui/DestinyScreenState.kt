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
import ddolite.builder.build.Build
import ddolite.builder.build.BuildAbility
import ddolite.builder.build.BuildTree
import ddolite.builder.build.DropState
import ddolite.builder.build.EnhancementRules
import ddolite.builder.model.SelectorStyle
import ddolite.builder.model.Tree
import ddolite.builder.model.TreeStyle

/** The most destinies a build can have (frmDestiny2's MAX_DESTINIES). */
private const val MAX_DESTINIES = 3

/**
 * The state behind the Destinies window: VB's frmDestiny2 (Source/Forms/Build/frmDestiny2.frm),
 * a near-copy of frmEnhancements with two tabs, Destiny Trees and Destinies. The rules are in
 * builder-core's [EnhancementRules]; this edits `build.Destiny` directly and only marks the build
 * dirty (VB's SetDirty).
 *
 * Drags reuse [EnhancementDrag] and its drop keys. Rows are numbered from 1; 0 means none.
 */
class DestinyScreenState(private val session: BuilderSession) : TreeAbilitiesScreen, TreeListScreen {
    /** The tab showing (mlngTab): 0 Destiny Trees, 1 Destinies. */
    var tab by mutableIntStateOf(0)
        private set

    var revision by mutableIntStateOf(0)
        private set

    override val drag = DragDropState<EnhancementDrag>()

    private val rules: EnhancementRules?
        get() {
            val r = session.rules ?: return null
            return EnhancementRules(r)
        }

    private fun build(): Build? = session.rules?.build

    private fun changed() {
        revision++
    }

    private fun destiniesChanged() {
        session.markDirty()
        changed()
    }

    // ================================================================ form

    /** Form_Load (:574). */
    fun opened() {
        tab = 0
        treeListIndex = -1
        treeDropStates = emptyList()
        treeHover = 0
        loadData()
        showTab()
        changed()
    }

    /** Cascade (:611). */
    fun cascade() {
        loadData()
        if (tab == 1) {
            buildDestiny = rules?.findBuildDestiny(comboTree) ?: 0
        }
        showTab()
        changed()
    }

    /** usrHeader_Click (:650) → ChangeTab (:668): the Destinies tab needs a destiny in the build. */
    fun chooseTab(newTab: Int) {
        if (tab == newTab || comboTrees().isEmpty()) {
            return
        }
        tab = newTab
        showTab()
        session.saveBackup()
        changed()
    }

    /** ShowTab (:677). */
    private fun showTab() {
        if (tab == 0) {
            showDestinies(drop = false, add = false)
        } else {
            if (buildDestiny == 0) {
                val names = comboTrees()
                if (names.isNotEmpty()) {
                    comboTree = names[0]
                }
                treeClick()
            } else {
                showAbilities()
                showAvailable()
            }
        }
    }

    /**
     * LoadData (:707): the permanent destiny points are raised to the minimum; the tier 5 destiny is
     * dropped below level 29 (VB quirk kept: its label shows only above level 29, so at exactly 29
     * it's kept but not shown).
     */
    private fun loadData() {
        val edits = edits() ?: return
        sourceRow = 0
        edits.loadDataLimits()
        populateCombo()
    }

    // ================================================================ Destiny Trees tab

    override var treeDropStates: List<DropState> by mutableStateOf(emptyList())
        private set

    override var treeHover by mutableIntStateOf(0)

    /** The destiny highlighted in the available list (lstTree), -1 = none. */
    override var treeListIndex by mutableIntStateOf(-1)
        private set

    override var treeMenuRow by mutableIntStateOf(0)
        private set

    /** The rows of usrTree: one per destiny, plus an empty one while a destiny is dragged in. */
    override fun treeRows(): List<BuildTree?> {
        val build = build() ?: return emptyList()
        val rows = ArrayList<BuildTree?>()
        for (t in build.destinies) {
            rows.add(t)
        }
        while (rows.size < treeDropStates.size) {
            rows.add(null)
        }
        return rows
    }

    /**
     * usrTree's columns for a destiny (ShowDestinies, :887). VB bug kept: the max tier goes in the
     * "Destiny" column (`SetText i, 1, GetMaxTier(i)`) and the Tier column stays empty.
     */
    override fun treeColumns(row: Int): TreeColumns {
        val r = rules ?: return TreeColumns("", "", "")
        if (treeRows().getOrNull(row - 1) == null) {
            return TreeColumns("", "", "")
        }
        return TreeColumns("5", "", r.quickSpentInDestiny(row).toString())
    }

    /** ShowDestinies (:887). */
    private fun showDestinies(drop: Boolean, add: Boolean) {
        val build = build() ?: return
        var count = build.destinies.size
        if (add && build.destinies.size < MAX_DESTINIES) {
            count++
        }
        val states = ArrayList<DropState>()
        for (i in 0 until count) {
            states.add(if (drop) DropState.CAN_DROP else DropState.DEFAULT)
        }
        treeDropStates = states
        changed()
    }

    /** The destiny AP line (ShowSpentAll, :924). */
    override fun apCaption(): Caption {
        val r = rules ?: return Caption("", false)
        val totals = r.destinyTotals()
        return Caption(totals.caption(), totals.overspent)
    }

    /** The Destiny Trees tab's first column. It shows each destiny's max tier (treeColumns), so it's sized to that. */
    override val nameColumn = "Destiny"

    override fun nameColumnTexts(): List<String> = emptyList()

    override val maxTrees = MAX_DESTINIES

    /** The destinies to add, for the shared tab (no class column). */
    override fun listedTrees(): List<ListedTree> {
        val result = ArrayList<ListedTree>()
        for (name in availableDestinies()) {
            result.add(ListedTree(name, ""))
        }
        return result
    }

    /** ShowAvailableDestinies (:962) and AddDestiny (:975): destinies not chosen or locked out. */
    fun availableDestinies(): List<String> {
        val r = session.rules ?: return emptyList()
        val result = ArrayList<String>()
        for (tree in r.data.destinies) {
            var skip = false
            for (bt in r.build.destinies) {
                if (bt.treeName == tree.name) {
                    skip = true
                }
            }
            val lockout = tree.lockout
            if (lockout != null && lockout.isNotEmpty()) {
                for (bt in r.build.destinies) {
                    if (bt.treeName == lockout) {
                        skip = true
                    }
                }
            }
            if (!skip && !result.contains(tree.name)) {
                result.add(tree.name)
            }
        }
        return result
    }

    fun selectedTreeRow(): Int = singleSelected(treeDropStates)

    private fun selectTreeRow(row: Int) {
        treeDropStates = selectedStates(treeDropStates.size, row)
    }

    /** usrTree_SlotClick (:806). */
    override fun clickTree(row: Int, button: PointerButton) {
        treeListIndex = -1
        if (button == PointerButton.PRIMARY) {
            selectTreeRow(if (selectedTreeRow() == row) 0 else row)
        } else if (button == PointerButton.SECONDARY && row <= (build()?.destinies?.size ?: 0)) {
            selectTreeRow(row)
            treeHover = row
            treeMenuRow = row
        }
        changed()
    }

    /** The menu (mnuTrees): "Delete Abbr", "Delete All Trees", "-", "Reset Abbr", "Reset All Trees". */
    fun treeMenuItems(): List<String> {
        val r = session.rules ?: return emptyList()
        val bt = r.build.destinies.getOrNull(treeMenuRow - 1) ?: return emptyList()
        val abbreviation = r.data.findDestiny(bt.treeName)?.abbreviation ?: ""
        return listOf("Delete $abbreviation", "Delete All Trees", "-", "Reset $abbreviation", "Reset All Trees")
    }

    fun closeTreeMenu() {
        treeMenuRow = 0
    }

    /**
     * mnuTrees_Click (:826). VB quirks kept: deleting or resetting from the menu leaves the tier 5
     * destiny set (double-clicking clears it).
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
        showDestinies(drop = false, add = false)
        destiniesChanged()
    }

    /** usrTree_SlotDblClick (:866): removes the destiny, clearing it as the tier 5 destiny. */
    override fun doubleClickTree(row: Int) {
        treeListIndex = -1
        val edits = edits() ?: return
        if (!edits.removeTree(row)) {
            return
        }
        showDestinies(drop = false, add = false)
        populateCombo()
        destiniesChanged()
    }

    /** lstTree_MouseDown (:1012). */
    override fun pressListTree(index: Int) {
        selectTreeRow(0)
        treeListIndex = index
        changed()
    }

    /** lstTree_DblClick (:1002): adds the destiny after the others (at most 3). */
    override fun doubleClickListTree() {
        val build = build() ?: return
        if (treeListIndex == -1 || build.destinies.size >= MAX_DESTINIES) {
            return
        }
        addBuildDestiny(build.destinies.size + 1)
        showDestinies(drop = false, add = false)
        populateCombo()
        treeHover = build.destinies.size
        destiniesChanged()
    }

    /** AddBuildDestiny (:1080): the highlighted destiny into row [row], replacing or adding. */
    private fun addBuildDestiny(row: Int) {
        val edits = edits() ?: return
        val name = availableDestinies().getOrNull(treeListIndex) ?: return
        edits.putTree(row, edits.newDestiny(name))
    }

    /** The build's destiny edits (core [TreeEdits]), or null with no build open. */
    private fun edits(): TreeEdits? {
        val r = session.rules ?: return null
        return TreeEdits(r, destinies = true)
    }

    /** A destiny drag is starting: from the list (lstTree_MouseMove, :1018) or a row (usrTree_RequestDrag, :1068). */
    override fun startTreeDrag(item: EnhancementDrag): Boolean {
        val build = build() ?: return false
        if (item is EnhancementDrag.ListTree) {
            if (treeListIndex == -1) {
                return false
            }
            showDestinies(drop = true, add = true)
            return true
        }
        if (item is EnhancementDrag.TreeRow) {
            if (item.row > build.destinies.size) {
                return false
            }
            val states = ArrayList<DropState>()
            for (i in 1..build.destinies.size) {
                states.add(if (i == item.row) DropState.DEFAULT else DropState.CAN_DROP)
            }
            treeDropStates = states
            changed()
            return true
        }
        return false
    }

    /** usrTree_OLEDragDrop (:1048), then ShowDestinies. */
    override fun treeDropped(item: EnhancementDrag, target: Any?) {
        val build = build()
        var changedTrees = false
        if (build != null && target is TreeRowKey) {
            val state = treeDropStates.getOrNull(target.row - 1)
            if (state != null && state != DropState.DEFAULT) {
                if (item is EnhancementDrag.ListTree) {
                    addBuildDestiny(target.row)
                    changedTrees = true
                } else if (item is EnhancementDrag.TreeRow && item.row != target.row) {
                    edits()?.swapTrees(item.row, target.row)
                    changedTrees = true
                }
            }
        }
        showDestinies(drop = false, add = false)
        if (changedTrees) {
            treeListIndex = -1
            populateCombo()
            destiniesChanged()
        }
    }

    /** The permanent destiny points spinner (usrspnPDP, its limits from Tomes.txt). */
    fun pdp(): Int = build()?.permDestinyPoints ?: 0

    fun pdpMin(): Int = session.rules?.data?.tomes?.permDestinyPointMin ?: 0

    fun pdpMax(): Int = session.rules?.data?.tomes?.permDestinyPointMax ?: 0

    /** usrspnPDP_Change (:790). */
    fun setPdp(value: Int) {
        val edits = edits() ?: return
        edits.setPermDestinyPoints(value)
        destiniesChanged()
    }

    // ================================================================ Destinies tab

    override var comboTree by mutableStateOf("")
        private set

    // mlngBuildDestiny: the chosen destiny's position in build.Destiny (1-based, 0 = none).
    private var buildDestiny = 0

    override var slotStates: List<DropState> by mutableStateOf(emptyList())
        private set

    override var slotHover by mutableIntStateOf(0)

    override var abilities: List<AbilityRef> by mutableStateOf(emptyList())
        private set
    override var abilityIndex by mutableIntStateOf(-1)
        private set

    override var selectors: List<SelectorItem> by mutableStateOf(emptyList())
        private set
    override var selectorIndex by mutableIntStateOf(-1)
        private set

    override var showAll by mutableStateOf(false)
        private set

    override var details: AbilityDetails? by mutableStateOf(null)
        private set

    override var slotMenuRow by mutableIntStateOf(0)
        private set

    private var sourceRow = 0
    private var dragComplete = false

    fun currentTree(): Tree? = session.rules?.data?.findDestiny(comboTree)

    private fun currentBuildTree(): BuildTree? = build()?.destinies?.getOrNull(buildDestiny - 1)

    override fun comboTrees(): List<String> {
        val build = build() ?: return emptyList()
        val names = ArrayList<String>()
        for (bt in build.destinies) {
            names.add(bt.treeName)
        }
        return names
    }

    /** PopulateCombo (:767): keeps the chosen destiny, else the tier 5 destiny. */
    private fun populateCombo() {
        val build = build() ?: return
        if (!comboTrees().contains(comboTree)) {
            comboTree = ""
            if (build.destinyTier5.isNotEmpty() && comboTrees().contains(build.destinyTier5)) {
                comboTree = build.destinyTier5
            }
            treeClick()
        }
    }

    override fun chooseComboTree(name: String) {
        comboTree = name
        treeClick()
        session.saveBackup()
        changed()
    }

    /** TreeClick (:1665). */
    private fun treeClick() {
        noSelection()
        buildDestiny = rules?.findBuildDestiny(comboTree) ?: 0
        showAbilities()
        showAvailable()
    }

    /** ShowAbilities (:1112). */
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
                rows.add(AbilitySlotRow(a.tier.toString(), info.caption, info.cost.toString(), a.rank, info.maxRanks, r.abilityError(tree, bt, a, spend) != null))
            }
        }
        return rows
    }

    override fun spentCaption(): String {
        val r = rules ?: return ""
        return "Spent in Tree: " + r.spentInTree(currentTree(), currentBuildTree()).total
    }

    override fun tier5(): String = build()?.destinyTier5 ?: ""

    private fun showAbilities() {
        slotStates = selectedStates(slotRows().size, 0)
        changed()
    }

    /** ShowAvailable (:1722): every tier (GetMaxTier is always 5). */
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
        abilities = r.availableAbilities(tree, currentBuildTree(), showAll, tierLimit = 5)
        changed()
    }

    override fun abilityText(item: AbilityRef): String {
        val tree = currentTree() ?: return ""
        return item.tier.toString() + ": " + tree.tiers[item.tier][item.ability].abbreviation
    }

    override fun changeShowAll(value: Boolean) {
        showAll = value
        showAvailable()
    }

    /** NoSelection (:1936). */
    private fun noSelection() {
        if (buildDestiny == 0) {
            return
        }
        selectorIndex = -1
        abilityIndex = -1
        slotStates = selectedStates(slotStates.size, 0)
        details = null
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

    /** usrList_SlotClick (:1375). */
    override fun clickSlot(row: Int, button: PointerButton) {
        val bt = currentBuildTree() ?: return
        selectorIndex = -1
        abilityIndex = -1
        val rows = slotRows()
        if (rows.isEmpty() || rows[0].caption.isEmpty()) {
            changed()
            return
        }
        if (singleSelected(slotStates) == row) {
            noSelection()
            if (button == PointerButton.SECONDARY) {
                clearSlot(row)
            }
        } else if (button == PointerButton.PRIMARY) {
            slotStates = selectedStates(slotStates.size, row)
            val a = bt.abilities[row - 1]
            showDetails(a.tier, a.ability, a.selector, row)
        } else if (button == PointerButton.SECONDARY) {
            slotStates = selectedStates(slotStates.size, row)
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

    /** mnuDestinies_Click (:1413). */
    fun chooseSlotMenu(index: Int) {
        slotMenuRow = 0
        val r = rules ?: return
        if (index == 0) {
            clearSlot(singleSelected(slotStates))
            return
        }
        val bt = currentBuildTree() ?: return
        if (!session.ask("Reset $comboTree?")) {
            return
        }
        edits()?.resetAbilities(bt)
        showAbilities()
        showAvailable()
        noSelection()
        destiniesChanged()
    }

    override fun doubleClickSlot(row: Int) {
        clearSlot(row)
    }

    /** ClearSlot (:1438). VB quirk kept: emptying any destiny clears the tier 5 destiny. */
    private fun clearSlot(row: Int) {
        val edits = edits() ?: return
        val bt = currentBuildTree() ?: return
        if (!edits.clearSlot(bt, row)) {
            return
        }
        showAbilities()
        showAvailable()
        noSelection()
        destiniesChanged()
    }

    /** usrList_RankChange (:1477). */
    override fun changeRank(row: Int, ranks: Int) {
        val edits = edits() ?: return
        val bt = currentBuildTree() ?: return
        val a = edits.setRanks(bt, row, ranks) ?: return
        showAbilities()
        showAvailable()
        showDetails(a.tier, a.ability, a.selector, row)
        destiniesChanged()
    }

    /** lstAbility_MouseDown (:1505) and ListAbilityClick (:1536). */
    override fun clickAbility(index: Int): Boolean {
        val r = rules ?: return false
        val tree = currentTree() ?: return false
        val item = abilities.getOrNull(index) ?: return false
        slotStates = selectedStates(slotStates.size, 0)
        abilityIndex = index
        selectors = emptyList()
        selectorIndex = -1
        showDetails(item.tier, item.ability, 0, 0)
        val ability = tree.tiers[item.tier][item.ability]
        if (ability.selectorStyle == SelectorStyle.NONE) {
            changed()
            return true
        }
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

    /** lstSub_MouseDown (:1605). */
    override fun clickSelector(index: Int) {
        val item = abilities.getOrNull(abilityIndex) ?: return
        val selector = selectors.getOrNull(index) ?: return
        selectorIndex = index
        slotStates = selectedStates(slotStates.size, 0)
        showDetails(item.tier, item.ability, selector.number, 0)
        changed()
    }

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

    /** ConfirmDestinyTier5Change (:1355). */
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

    /** lstAbility_DblClick / lstSub_DblClick (:1495, :1595). */
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
            destiniesChanged()
        }
    }

    /** AddAbility (:1267). */
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

    /** StartDrag (:1254) with ShowDropSlots (:1160), or usrList_RequestDrag (:1457). */
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
        if (buildDestiny != 0 && !addAbility(blank = true)) {
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
     * usrList_OLEDragDrop (:1463) / DropAbility (:1305), lstAbility_OLEDragDrop (:1567), then the
     * complete-drag handlers. VB quirk kept: a tier 5 ability dropped (not double-clicked) doesn't
     * make its destiny the tier 5 destiny.
     */
    override fun abilityDropped(item: EnhancementDrag, target: Any?) {
        val r = rules
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
                    edits()?.dropAbility(bt, target.row, choice.tier, choice.ability, tree.tiers[choice.tier][choice.ability].ranks, choice.selector)
                    showAbilities()
                    slotHover = target.row
                    showAvailable()
                    destiniesChanged()
                }
            }
        }
        if (!dragComplete && bt != null && r != null) {
            edits()?.removeBlanks(bt)
            showAbilities()
            slotHover = 0
        }
        dragComplete = false
    }

    /** What a drag shows next to the pointer. */
    fun dragText(item: EnhancementDrag): String {
        return when (item) {
            is EnhancementDrag.ListTree -> item.treeName
            is EnhancementDrag.TreeRow -> treeRows().getOrNull(item.row - 1)?.treeName ?: ""
            is EnhancementDrag.ListAbility -> currentTree()?.tiers?.getOrNull(item.tier)?.getOrNull(item.ability)?.abbreviation ?: ""
            is EnhancementDrag.AbilityRow -> slotRows().getOrNull(item.row - 1)?.caption ?: ""
        }
    }
}

/** usrList.Selected as VB reads it back: the only can-drop row while the rest are default; 0 otherwise. */
internal fun singleSelected(states: List<DropState>): Int {
    var selected = 0
    for (i in states.indices) {
        when (states[i]) {
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

/** usrList.Selected = [row]: that row can-drop, the rest default. */
internal fun selectedStates(count: Int, row: Int): List<DropState> {
    val states = ArrayList<DropState>()
    for (i in 1..count) {
        states.add(if (i == row) DropState.CAN_DROP else DropState.DEFAULT)
    }
    return states
}
