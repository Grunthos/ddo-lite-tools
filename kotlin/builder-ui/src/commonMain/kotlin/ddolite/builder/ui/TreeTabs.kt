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
import androidx.compose.ui.text.font.FontWeight
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
    // The slots end a line above the content's bottom, leaving room for the totals.
    val slotsHeight = area.height - LINE - TIGHT
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
