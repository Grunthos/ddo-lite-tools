package ddolite.builder.ui

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ddolite.builder.build.AbilityDetails
import ddolite.builder.build.DropState
import ddolite.builder.model.AbilityRef
import ddolite.common.theme.Theme

/**
 * What the Enhancements and Destinies windows' ability tabs share. VB's frmDestiny2 is a copy of
 * frmEnhancements, and each window's state ([EnhancementsScreenState], [DestinyScreenState]) keeps
 * its own rules, ported from its own form; this is just what [TreeAbilitiesTab] draws and calls.
 */
interface TreeAbilitiesScreen {
    val drag: DragDropState<EnhancementDrag>

    /** The tree's slots (usrList), their drop states, the one under the mouse, and the one whose menu is open. */
    fun slotRows(): List<AbilitySlotRow>
    val slotStates: List<DropState>
    var slotHover: Int
    val slotMenuRow: Int

    /** The tree drop-down (cboTree), and the tier 5 tree it shows beside it. */
    val comboTree: String
    fun comboTrees(): List<String>
    fun chooseComboTree(name: String)
    fun tier5(): String

    /** The abilities list (lstAbility) with "Show All", and the selectors list (lstSub). */
    val abilities: List<AbilityRef>
    val abilityIndex: Int
    val showAll: Boolean
    val selectors: List<SelectorItem>
    val selectorIndex: Int
    fun abilityText(item: AbilityRef): String
    fun changeShowAll(value: Boolean)

    /** The details box. */
    val details: AbilityDetails?

    /** The totals line. */
    fun apCaption(): Caption
    fun spentCaption(): String

    fun clickSlot(row: Int, button: PointerButton)
    fun doubleClickSlot(row: Int)
    fun changeRank(row: Int, ranks: Int)
    fun clickAbility(index: Int): Boolean
    fun clickSelector(index: Int)
    fun doubleClickList(fromSelectors: Boolean)
    fun startAbilityDrag(item: EnhancementDrag): Boolean
    fun abilityDropped(item: EnhancementDrag, target: Any?)
}

/*
 * The ability tab's layout, from the content corner: the tree's slots on the left, down to the totals
 * line at the bottom; to the right the tree drop-down under its caption, the abilities and selectors
 * lists under theirs, and the details under them, ending level with the slots.
 */
private const val SLOTS_WIDTH = 329
private const val ABILITIES_LEFT = SLOTS_WIDTH + GAP
private const val ABILITIES_WIDTH = 233
private const val SELECTORS_LEFT = ABILITIES_LEFT + ABILITIES_WIDTH + GAP
private const val TREE_LABEL_TOP = 0
private const val TREE_COMBO_TOP = TREE_LABEL_TOP + LINE
private const val LISTS_LABEL_TOP = TREE_COMBO_TOP + COMBO_HEIGHT + 11
private const val LISTS_TOP = LISTS_LABEL_TOP + LINE
private const val LISTS_HEIGHT = 201
private const val ABILITY_CAPTION_TOP = LISTS_TOP + LISTS_HEIGHT + 8
private const val ABILITY_DETAILS_TOP = ABILITY_CAPTION_TOP + LINE

/**
 * The Enhancements tab (frmEnhancements picTab(1)) and the Destinies tab (frmDestiny2 picTab(1)):
 * the chosen tree's ability slots, the tree, ability and selector lists, the details and the totals.
 * Kotlin layout: the slots fill the window's height (VB's two forms are different heights, so their
 * lists were), and both windows put the totals in the same places.
 */
@Composable
internal fun TreeAbilitiesTab(screen: TreeAbilitiesScreen, theme: Theme, area: ContentArea, onMenu: () -> Unit) {
    val label = labelStyle(theme)
    val control = controlStyle(theme)
    var comboOpen by remember { mutableStateOf(false) }
    val rows = screen.slotRows()
    val rightWidth = area.width - ABILITIES_LEFT
    // The slots end where every window's boxes end, with the totals under them.
    val slotsHeight = area.boxesBottom
    val totalsTop = slotsHeight + TIGHT
    UserList(
        theme,
        listOf(
            ListColumn("Tier", "Tier", TextAlign.Center),
            ListColumn("Ability", "", TextAlign.Center, slot = true),
            ListColumn("AP", " 20", TextAlign.Center),
        ),
        rows.size,
        cellText = { row, col ->
            val r = rows.getOrNull(row - 1)
            when (col) {
                0 -> r?.tier ?: ""
                2 -> r?.cost ?: ""
                else -> ""
            }
        },
        slot = { row ->
            val r = rows.getOrNull(row - 1)
            if (r == null) SlotContent("", false) else SlotContent(r.caption, r.error, r.ranks, r.maxRanks)
        },
        dropState = { row -> screen.slotStates.getOrNull(row - 1) ?: DropState.DEFAULT },
        hoverRow = screen.slotHover,
        left = 0, top = 0, width = SLOTS_WIDTH, height = slotsHeight,
        headerHeight = LINE,
        drag = screen.drag,
        rowKey = { row -> AbilityRowKey(row) },
        dragItem = { row -> EnhancementDrag.AbilityRow(row) },
        onStartDrag = { item, button -> button == PointerButton.PRIMARY && screen.startAbilityDrag(item) },
        onDrop = { item, target -> screen.abilityDropped(item, target) },
        onPress = { row, button ->
            screen.clickSlot(row, button)
            if (screen.slotMenuRow != 0) {
                onMenu()
            }
        },
        onDoubleClick = { row -> screen.doubleClickSlot(row) },
        onHover = { row -> screen.slotHover = row },
        onRankChange = { row, ranks -> screen.changeRank(row, ranks) },
    )
    Label("Tree", label, ABILITIES_LEFT, TREE_LABEL_TOP)
    ComboBox(theme, control, screen.comboTree, ABILITIES_LEFT, TREE_COMBO_TOP, ABILITIES_WIDTH, COMBO_HEIGHT) { comboOpen = !comboOpen }
    val tier5 = screen.tier5()
    if (tier5.isNotEmpty()) {
        Label("Tier 5 Tree", label, SELECTORS_LEFT, TREE_LABEL_TOP)
        Label(tier5, label, SELECTORS_LEFT, TREE_COMBO_TOP + 3) // level with the drop-down's text
    }
    Label("Abilities", label, ABILITIES_LEFT, LISTS_LABEL_TOP)
    CheckBox(theme, "Show All", screen.showAll, true, ABILITIES_LEFT + 112, LISTS_LABEL_TOP - 2, ABILITIES_WIDTH - 112) { screen.changeShowAll(!screen.showAll) }
    Label("Selectors", label, SELECTORS_LEFT, LISTS_LABEL_TOP)
    val abilityTexts = ArrayList<String>()
    for (item in screen.abilities) {
        abilityTexts.add(screen.abilityText(item))
    }
    ListBox(
        theme, abilityTexts, { i -> i == screen.abilityIndex }, ABILITIES_LEFT, LISTS_TOP, ABILITIES_WIDTH, LISTS_HEIGHT, screen.drag,
        onPress = { i, _ -> screen.clickAbility(i) },
        onDoubleClick = { _ -> screen.doubleClickList(fromSelectors = false) },
        dragItem = { i -> EnhancementDrag.ListAbility(screen.abilities[i].tier, screen.abilities[i].ability) },
        onStartDrag = { item, button -> button == PointerButton.PRIMARY && screen.startAbilityDrag(item) },
        onDrop = { item, target -> screen.abilityDropped(item, target) },
        dropKey = AbilityListKey,
    )
    val selectorTexts = ArrayList<String>()
    for (s in screen.selectors) {
        selectorTexts.add(s.name)
    }
    ListBox(
        theme, selectorTexts, { i -> i == screen.selectorIndex }, SELECTORS_LEFT, LISTS_TOP, area.width - SELECTORS_LEFT, LISTS_HEIGHT, screen.drag,
        onPress = { i, _ -> screen.clickSelector(i) },
        onDoubleClick = { _ -> screen.doubleClickList(fromSelectors = true) },
        dragItem = { _ ->
            val item = screen.abilities.getOrNull(screen.abilityIndex)
            if (item == null) null else EnhancementDrag.ListAbility(item.tier, item.ability)
        },
        onStartDrag = { item, button -> button == PointerButton.PRIMARY && screen.startAbilityDrag(item) },
        onDrop = { item, target -> screen.abilityDropped(item, target) },
    )
    val details = screen.details
    BasicText(
        details?.caption ?: "Details",
        style = label.copy(fontWeight = FontWeight.Bold),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.offset(ABILITIES_LEFT.dp, ABILITY_CAPTION_TOP.dp).width(rightWidth.dp),
    )
    // The details box ends level with the slots.
    DetailsBox(theme, details?.lines ?: emptyList(), ABILITIES_LEFT, ABILITY_DETAILS_TOP, rightWidth, slotsHeight - ABILITY_DETAILS_TOP)
    val ap = screen.apCaption()
    BasicText(ap.text, style = captionStyle(theme, ap.error), modifier = Modifier.offset(8.dp, totalsTop.dp))
    Label(screen.spentCaption(), label, 280, totalsTop)
    if (details != null) {
        Label(details.ranks, label, ABILITIES_LEFT + 96, totalsTop)
        Label(details.cost, label, ABILITIES_LEFT + 168, totalsTop)
        val prog = details.progression
        if (prog != null) {
            Label(prog, label, ABILITIES_LEFT + 304, totalsTop)
        }
    }
    if (comboOpen) {
        val names = screen.comboTrees()
        DropList(theme, control, names, ABILITIES_LEFT, TREE_COMBO_TOP + COMBO_HEIGHT, ABILITIES_WIDTH) { index ->
            comboOpen = false
            screen.chooseComboTree(names[index])
        }
    }
}

/**
 * What the Enhancements and Destinies windows' Trees tabs share (frmEnhancements and frmDestiny2
 * picTab(0)): the build's trees on the left, the ones it could add on the right. Each window's state
 * keeps its own rules; this is what [TreeListTab] draws and calls.
 */
interface TreeListScreen {
    val drag: DragDropState<EnhancementDrag>

    /** The first column's caption ("Class", "Destiny"), and the texts it can show, so it's sized to the widest. */
    val nameColumn: String
    fun nameColumnTexts(): List<String>

    /** The most trees a build can have (7 enhancement trees, 3 destinies): the list always fits them. */
    val maxTrees: Int

    /** The build's trees (usrTree), a row's columns, the drop states, the row under the mouse, the row whose menu is open. */
    fun treeRows(): List<ddolite.builder.build.BuildTree?>
    fun treeColumns(row: Int): TreeColumns
    val treeDropStates: List<DropState>
    var treeHover: Int
    val treeMenuRow: Int

    /** The trees it could add (lstTree), with their class where they have one, and the highlighted one. */
    fun listedTrees(): List<ListedTree>
    val treeListIndex: Int

    /** The AP line. */
    fun apCaption(): Caption

    fun clickTree(row: Int, button: PointerButton)
    fun doubleClickTree(row: Int)
    fun pressListTree(index: Int)
    fun doubleClickListTree()
    fun startTreeDrag(item: EnhancementDrag): Boolean
    fun treeDropped(item: EnhancementDrag, target: Any?)
}

/*
 * The Trees tab's layout, from the content corner: the build's trees on the left and the trees to add
 * on the right, captions on the first line and boxes a line below; the AP line under them; and each
 * window's own frames under that, one under each list (the windows place them from these).
 */
internal const val TREE_LIST_WIDTH = 361
internal const val TREE_LIST_HEIGHT = LINE + 171
internal const val ADD_TREES_LEFT = TREE_LIST_WIDTH + 23
private const val ADD_TREES_TOP = LINE
private const val TREES_AP_TOP = TREE_LIST_HEIGHT + 7
internal const val TREE_FRAMES_TOP = TREES_AP_TOP + 28
internal const val TREE_FRAME_HEIGHT = 157

/**
 * The Trees tab (frmEnhancements picTab(0)) and the Destiny Trees tab (frmDestiny2 picTab(0)), with
 * [below] the window's own frames (the racial and universal AP, or the permanent destiny points).
 * Kotlin only: the first column is as wide as the widest text it can show, and the add list's Class
 * column starts after its longest tree name (VB sizes both from fixed sample names).
 */
@Composable
internal fun TreeListTab(screen: TreeListScreen, theme: Theme, area: ContentArea, onMenu: () -> Unit, below: @Composable () -> Unit) {
    val label = labelStyle(theme)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current.density
    val bold = label.copy(fontWeight = FontWeight.Bold)
    val rows = screen.treeRows()

    // The first column's widest text: its caption, what it can show, and what it shows now.
    var widest = screen.nameColumn
    var widestWidth = measurer.measure(widest, bold).size.width
    val candidates = ArrayList(screen.nameColumnTexts())
    for (row in 1..rows.size) {
        candidates.add(screen.treeColumns(row).className)
    }
    for (text in candidates) {
        val w = measurer.measure(text, bold).size.width
        if (w > widestWidth) {
            widest = text
            widestWidth = w
        }
    }
    UserList(
        theme,
        listOf(
            ListColumn(screen.nameColumn, widest, TextAlign.Start),
            ListColumn("Tree", "", TextAlign.Center, slot = true),
            ListColumn("Tier", "Tier", TextAlign.Center),
            ListColumn("AP", "AP", TextAlign.Center),
        ),
        rows.size,
        cellText = { row, col ->
            val columns = screen.treeColumns(row)
            when (col) {
                0 -> columns.className
                2 -> columns.tier
                3 -> columns.ap
                else -> ""
            }
        },
        slot = { row -> SlotContent(rows.getOrNull(row - 1)?.treeName ?: "", false) },
        dropState = { row -> screen.treeDropStates.getOrNull(row - 1) ?: DropState.DEFAULT },
        hoverRow = screen.treeHover,
        left = 0, top = 0, width = TREE_LIST_WIDTH, height = TREE_LIST_HEIGHT,
        fitRows = screen.maxTrees,
        headerHeight = LINE,
        drag = screen.drag,
        rowKey = { row -> TreeRowKey(row) },
        dragItem = { row -> EnhancementDrag.TreeRow(row) },
        onStartDrag = { item, button -> button == PointerButton.PRIMARY && screen.startTreeDrag(item) },
        onDrop = { item, target -> screen.treeDropped(item, target) },
        onPress = { row, button ->
            screen.clickTree(row, button)
            if (screen.treeMenuRow != 0) {
                onMenu()
            }
        },
        onDoubleClick = { row -> screen.doubleClickTree(row) },
        onHover = { row -> screen.treeHover = row },
    )

    // The trees to add: names, and a Class column after the longest name when any has a class.
    val listed = screen.listedTrees()
    val texts = ArrayList<String>()
    var classes = false
    var longestName = 0
    val listStyle = controlStyle(theme)
    for (tree in listed) {
        if (tree.source.isEmpty()) {
            texts.add(tree.name)
        } else {
            texts.add(tree.name + "\t" + tree.source)
            classes = true
        }
        val w = (measurer.measure(tree.name, listStyle).size.width / density).toInt()
        if (w > longestName) {
            longestName = w
        }
    }
    var classColumn = 0
    Label("Tree", label, ADD_TREES_LEFT, 0)
    if (classes) {
        classColumn = longestName + ITEM_GAP
        Label("Class", label, ADD_TREES_LEFT + classColumn, 0)
    }
    ListBox(
        theme, texts, { i -> i == screen.treeListIndex }, ADD_TREES_LEFT, ADD_TREES_TOP, area.width - ADD_TREES_LEFT,
        TREE_LIST_HEIGHT - ADD_TREES_TOP, screen.drag,
        onPress = { i, _ -> screen.pressListTree(i) },
        onDoubleClick = { _ -> screen.doubleClickListTree() },
        dragItem = { i -> EnhancementDrag.ListTree(listed[i].name) },
        onStartDrag = { item, button -> button == PointerButton.PRIMARY && screen.startTreeDrag(item) },
        onDrop = { item, target -> screen.treeDropped(item, target) },
        dropKey = TreeListKey,
        itemColumn = classColumn,
    )
    val ap = screen.apCaption()
    BasicText(ap.text, style = captionStyle(theme, ap.error), modifier = Modifier.offset(216.dp, TREES_AP_TOP.dp))
    below()
}
