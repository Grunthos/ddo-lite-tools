package ddolite.builder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ddolite.builder.build.DropState
import ddolite.common.theme.ColorGroup
import ddolite.common.theme.ColorValue
import ddolite.common.theme.Theme

/** frmDestiny2's client area, in pixels at 100%. */
private const val DESTINY_WIDTH = 815
private const val DESTINY_HEIGHT = 518

/**
 * The Destinies window (Source/Forms/Build/frmDestiny2.frm): the header (Destiny Trees,
 * Destinies; Help), the tab, and the footer ("< Enhancements"), in a [WindowFrame]; the client area
 * is 815 x 518 at 100%. Kotlin layout: as the Enhancements window's, from the content corner
 * (VB's positions are in tools/form-layout's frmDestiny2.md).
 *
 * [onNavigate] gets the footer's link.
 */
@Composable
fun DestinyWindowContent(state: BuilderSession, onNavigate: (String) -> Unit) {
    val screen = state.destiny
    val theme = state.skills.theme
    @Suppress("UNUSED_VARIABLE")
    val revision = state.revision + screen.revision // read so this redraws after changes
    val density = LocalDensity.current.density
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var menuAt by remember { mutableStateOf<Offset?>(null) }

    WindowFrame(
        theme, DESTINY_WIDTH, DESTINY_HEIGHT,
        modifier = Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    pointer = event.changes[0].position
                }
            }
        },
        header = {
            // usrHeader_Click (:650).
            HeaderBar(
                theme,
                listOf("Destiny Trees", "Destinies"),
                screen.tab,
                listOf("Help"),
                isEnabled = { true },
                onClick = { link ->
                    if (link == "Help") {
                        state.help.show("Destinies") // VB asks for a topic Help.txt doesn't have (VB-BUGS 22h)
                    } else if (link == "Destiny Trees") {
                        screen.chooseTab(0)
                    } else if (link == "Destinies") {
                        screen.chooseTab(1)
                    }
                },
            )
        },
        footer = {
            // usrFooter_Click (:659).
            HeaderBar(theme, listOf("< Enhancements"), null, emptyList(), isEnabled = { true }, onClick = { link -> onNavigate(link) })
        },
        overlay = {
            DestinyDragGhost(screen, theme, density)
            val at = menuAt
            if (at != null && screen.treeMenuRow != 0) {
                PopupMenu(screen.treeMenuItems(), -1, at, density, onChoose = { index ->
                    menuAt = null
                    screen.chooseTreeMenu(index)
                }, onDismiss = {
                    menuAt = null
                    screen.closeTreeMenu()
                }, clientWidth = DESTINY_WIDTH)
            } else if (at != null && screen.slotMenuRow != 0) {
                PopupMenu(listOf("Clear this ability", "Reset tree"), -1, at, density, onChoose = { index ->
                    menuAt = null
                    screen.chooseSlotMenu(index)
                }, onDismiss = {
                    menuAt = null
                    screen.closeSlotMenu()
                }, clientWidth = DESTINY_WIDTH)
            }
        },
    ) { area ->
        if (state.rules != null) {
            if (screen.tab == 0) {
                DestinyTreesTab(screen, theme, area) { menuAt = pointer }
            } else {
                DestiniesTab(screen, theme, area) { menuAt = pointer }
            }
        }
    }
}

/** The dragged item next to the pointer (window coordinates). */
@Composable
private fun DestinyDragGhost(screen: DestinyScreenState, theme: Theme, density: Float) {
    val dragging = screen.drag.dragging ?: return
    val text = screen.dragText(dragging)
    if (text.isEmpty()) {
        return
    }
    val at = screen.drag.position
    Box(
        modifier = Modifier.offset((at.x / density + 12).dp, (at.y / density + 8).dp)
            .background(theme.color(ColorGroup.DROP_SLOTS, ColorValue.BACKGROUND))
            .border(1.dp, theme.color(ColorGroup.DROP_SLOTS, ColorValue.BORDER_EXTERIOR)),
    ) {
        BasicText(text, style = TextStyle(platformStyle = TEXT_RENDERING, fontSize = FONT_SIZE, color = theme.color(ColorGroup.DROP_SLOTS, ColorValue.TEXT)), modifier = Modifier.offset(4.dp, 1.dp))
    }
}

/*
 * The Destiny Trees tab's layout, from the content corner: the build's destinies on the left, the
 * destinies it could add on the right (captions on the first line, boxes a line below), the AP line
 * under them, and the permanent destiny points under the right-hand list.
 */
private const val DESTINIES_WIDTH = 361
private const val DESTINIES_HEIGHT = LINE + 171
private const val ADD_LEFT = DESTINIES_WIDTH + 23
private const val ADD_TOP = LINE
private const val DESTINY_AP_TOP = DESTINIES_HEIGHT + 7
private const val PDP_TOP = DESTINY_AP_TOP + 28
private const val PDP_HEIGHT = 157

/** The Destiny Trees tab (picTab(0)): the build's destinies, the destinies it could add, the AP line and the permanent destiny points. */
@Composable
private fun DestinyTreesTab(screen: DestinyScreenState, theme: Theme, area: ContentArea, onMenu: () -> Unit) {
    val label = TextStyle(platformStyle = TEXT_RENDERING, fontSize = FONT_SIZE, color = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT))
    val rows = screen.treeRows()
    UserList(
        theme,
        listOf(
            ListColumn("Destiny", "Divine Crusader", TextAlign.Start), // MAX_DESTINY_NAME
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
        left = 0, top = 0, width = DESTINIES_WIDTH, height = DESTINIES_HEIGHT, maxRows = 21,
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
        headerHeight = LINE,
    )
    Label("Tree", label, ADD_LEFT, 0)
    val listed = screen.availableDestinies()
    ListBox(
        theme, listed, { i -> i == screen.listIndex }, ADD_LEFT, ADD_TOP, area.width - ADD_LEFT, DESTINIES_HEIGHT - ADD_TOP, screen.drag,
        onPress = { i, _ -> screen.pressListTree(i) },
        onDoubleClick = { _ -> screen.doubleClickListTree() },
        dragItem = { i -> EnhancementDrag.ListTree(listed[i]) },
        onStartDrag = { item, button -> button == PointerButton.PRIMARY && screen.startTreeDrag(item) },
        onDrop = { item, target -> screen.treeDropped(item, target) },
        dropKey = TreeListKey,
    )
    val ap = screen.apCaption()
    var apColor = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT)
    if (ap.error) {
        apColor = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT_ERROR)
    }
    BasicText(ap.text, style = TextStyle(platformStyle = TEXT_RENDERING, fontSize = FONT_SIZE, color = apColor), modifier = Modifier.offset(216.dp, DESTINY_AP_TOP.dp))

    // fraDestinyAP, under the destinies to add (a captioned frame, as the Enhancements window's).
    GroupFrame(theme, "Perm Destiny Points", ADD_LEFT, PDP_TOP, area.width - ADD_LEFT, PDP_HEIGHT)
    Spinner(
        value = screen.pdp(),
        min = screen.pdpMin(),
        max = screen.pdpMax(),
        theme = theme,
        onValueChange = { screen.setPdp(it) },
        modifier = Modifier.offset((ADD_LEFT + 36).dp, (PDP_TOP + 24).dp).size(65.dp, 20.dp),
    )
    Label("- Perm Destiny Points can be spent at level 20", label, ADD_LEFT + 24, PDP_TOP + 48)
    Label("- PDPs are earned at 1 per 3 Fate Points", label, ADD_LEFT + 24, PDP_TOP + 68)
    Label("- Fate Point are earned 1 per 3 EPL ", label, ADD_LEFT + 24, PDP_TOP + 88)
    Label("- Fate/Destiny Tomes can add more points", label, ADD_LEFT + 24, PDP_TOP + 108)
    Label("- You get 36 FP's for unlocking all Destiny Trees", label, ADD_LEFT + 24, PDP_TOP + 128)
}

/*
 * The Destinies tab's layout, as the Enhancements tab's: the ability slots on the left; the tree
 * drop-down, the abilities and selectors lists and the details to the right; the totals under all.
 */
private const val SLOTS_WIDTH = 329
private const val SLOTS_HEIGHT = LINE + 403
private const val ABILITIES_LEFT = SLOTS_WIDTH + GAP
private const val ABILITIES_WIDTH = 233
private const val SELECTORS_LEFT = ABILITIES_LEFT + ABILITIES_WIDTH + GAP
private const val TREE_COMBO_TOP = LINE
private const val LISTS_LABEL_TOP = TREE_COMBO_TOP + COMBO_HEIGHT + 11
private const val LISTS_TOP = LISTS_LABEL_TOP + LINE
private const val LISTS_HEIGHT = 201
private const val ABILITY_CAPTION_TOP = LISTS_TOP + LISTS_HEIGHT + 8
private const val ABILITY_DETAILS_TOP = ABILITY_CAPTION_TOP + LINE
private const val ABILITY_TOTALS_TOP = SLOTS_HEIGHT + TIGHT

/** The Destinies tab (picTab(1)): the chosen destiny's abilities, lists, details and totals. */
@Composable
private fun DestiniesTab(screen: DestinyScreenState, theme: Theme, area: ContentArea, onMenu: () -> Unit) {
    val label = TextStyle(platformStyle = TEXT_RENDERING, fontSize = FONT_SIZE, color = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT))
    val control = TextStyle(platformStyle = TEXT_RENDERING, fontSize = FONT_SIZE, color = theme.color(ColorGroup.CONTROLS, ColorValue.TEXT))
    var comboOpen by remember { mutableStateOf(false) }
    val rows = screen.slotRows()
    val rightWidth = area.width - ABILITIES_LEFT
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
        left = 0, top = 0, width = SLOTS_WIDTH, height = SLOTS_HEIGHT, maxRows = 16,
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
        headerHeight = LINE,
    )
    Label("Tree", label, ABILITIES_LEFT, 0)
    ComboBox(theme, control, screen.comboTree, ABILITIES_LEFT, TREE_COMBO_TOP, ABILITIES_WIDTH, COMBO_HEIGHT) { comboOpen = !comboOpen }
    val tier5 = screen.tier5()
    if (tier5.isNotEmpty()) {
        Label("Tier 5 Tree", label, SELECTORS_LEFT, 0)
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
    DetailsBox(theme, details?.lines ?: emptyList(), ABILITIES_LEFT, ABILITY_DETAILS_TOP, rightWidth, SLOTS_HEIGHT - ABILITY_DETAILS_TOP)
    val ap = screen.apCaption()
    var apColor = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT)
    if (ap.error) {
        apColor = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT_ERROR)
    }
    BasicText(ap.text, style = TextStyle(platformStyle = TEXT_RENDERING, fontSize = FONT_SIZE, color = apColor), modifier = Modifier.offset(8.dp, ABILITY_TOTALS_TOP.dp))
    Label(screen.spentCaption(), label, 264, ABILITY_TOTALS_TOP)
    if (details != null) {
        Label(details.ranks, label, ABILITIES_LEFT + 112, ABILITY_TOTALS_TOP)
        Label(details.cost, label, ABILITIES_LEFT + 176, ABILITY_TOTALS_TOP)
        val prog = details.progression
        if (prog != null) {
            Label(prog, label, ABILITIES_LEFT + 312, ABILITY_TOTALS_TOP)
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
