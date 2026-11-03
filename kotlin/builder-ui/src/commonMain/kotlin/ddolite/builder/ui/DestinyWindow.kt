package ddolite.builder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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

/** frmDestiny2's client width and the top of its footer, in pixels at 100%. */
private const val DESTINY_WIDTH = 815
private const val DESTINY_FOOTER_TOP = 492

/**
 * The Destinies window (Source/Forms/Build/frmDestiny2.frm): the header (Destiny Trees,
 * Destinies; Help), the tab, and the footer ("< Enhancements"). Laid out from the form's
 * positions (tools/form-layout's frmDestiny2.md); the client area is 815 x 518 at 100%.
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

    Box(
        modifier = Modifier.fillMaxSize()
            .background(theme.color(ColorGroup.WORKSPACE, ColorValue.BACKGROUND))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        pointer = event.changes[0].position
                    }
                }
            },
    ) {
        if (state.rules != null) {
            if (screen.tab == 0) {
                DestinyTreesTab(screen, theme) { menuAt = pointer }
            } else {
                DestiniesTab(screen, theme) { menuAt = pointer }
            }
        }
        Column(modifier = Modifier.fillMaxWidth()) {
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
        }
        Box(modifier = Modifier.offset(0.dp, DESTINY_FOOTER_TOP.dp).size(DESTINY_WIDTH.dp, 26.dp)) {
            // usrFooter_Click (:659).
            HeaderBar(theme, listOf("< Enhancements"), null, emptyList(), isEnabled = { true }, onClick = { link -> onNavigate(link) })
        }
        // The drag's item next to the pointer.
        val dragging = screen.drag.dragging
        if (dragging != null) {
            val text = screen.dragText(dragging)
            if (text.isNotEmpty()) {
                val at = screen.drag.position
                Box(
                    modifier = Modifier.offset((at.x / density + 12).dp, (at.y / density + 8).dp)
                        .background(theme.color(ColorGroup.DROP_SLOTS, ColorValue.BACKGROUND))
                        .border(1.dp, theme.color(ColorGroup.DROP_SLOTS, ColorValue.BORDER_EXTERIOR)),
                ) {
                    BasicText(text, style = TextStyle(fontSize = FONT_SIZE, color = theme.color(ColorGroup.DROP_SLOTS, ColorValue.TEXT)), modifier = Modifier.offset(4.dp, 1.dp))
                }
            }
        }
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
    }
}

/** The Destiny Trees tab (picTab(0)): the build's destinies, the destinies it could add, the AP line and the permanent destiny points. */
@Composable
private fun DestinyTreesTab(screen: DestinyScreenState, theme: Theme, onMenu: () -> Unit) {
    val label = TextStyle(fontSize = FONT_SIZE, color = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT))
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
                0 -> columns.first
                2 -> columns.second
                3 -> columns.third
                else -> ""
            }
        },
        slot = { row -> SlotContent(rows.getOrNull(row - 1)?.treeName ?: "", false) },
        dropState = { row -> screen.treeDropStates.getOrNull(row - 1) ?: DropState.DEFAULT },
        hoverRow = screen.treeHover,
        left = 80, top = 56, width = 361, height = 201, maxRows = 21,
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
    Label("Tree", label, 464, 64)
    val listed = screen.availableDestinies()
    ListBox(
        theme, listed, { i -> i == screen.listIndex }, 464, 80, 265, 177, screen.drag,
        onPress = { i, _ -> screen.pressListTree(i) },
        onDoubleClick = { _ -> screen.doubleClickListTree() },
        dragItem = { i -> EnhancementDrag.ListTree(listed[i]) },
        onStartDrag = { item, button -> button == PointerButton.PRIMARY && screen.startTreeDrag(item) },
        onDrop = { item, target -> screen.treeDropped(item, target) },
        dropKey = TreeListKey,
    )
    val ap = screen.apCaption()
    var apColor = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT)
    if (ap.second) {
        apColor = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT_ERROR)
    }
    BasicText(ap.first, style = TextStyle(fontSize = FONT_SIZE, color = apColor), modifier = Modifier.offset(296.dp, 264.dp))

    // fraDestinyAP: lblPermDestinyPoints over shpPermDestinyPoints (which starts 16 pixels lower).
    Frame(theme, 427, 308, 333, 149)
    BasicText(
        "Perm Destiny Points",
        style = label,
        modifier = Modifier.offset(435.dp, 292.dp).background(theme.color(ColorGroup.WORKSPACE, ColorValue.BACKGROUND)),
    )
    Spinner(
        value = screen.pdp(),
        min = screen.pdpMin(),
        max = screen.pdpMax(),
        theme = theme,
        onValueChange = { screen.setPdp(it) },
        modifier = Modifier.offset(463.dp, 316.dp).size(65.dp, 20.dp),
    )
    Label("- Perm Destiny Points can be spent at level 20", label, 451, 340)
    Label("- PDPs are earned at 1 per 3 Fate Points", label, 451, 360)
    Label("- Fate Point are earned 1 per 3 EPL ", label, 451, 380)
    Label("- Fate/Destiny Tomes can add more points", label, 451, 400)
    Label("- You get 36 FP's for unlocking all Destiny Trees", label, 451, 420)
}

/** The Destinies tab (picTab(1)): the chosen destiny's abilities, lists, details and totals. */
@Composable
private fun DestiniesTab(screen: DestinyScreenState, theme: Theme, onMenu: () -> Unit) {
    val label = TextStyle(fontSize = FONT_SIZE, color = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT))
    val control = TextStyle(fontSize = FONT_SIZE, color = theme.color(ColorGroup.CONTROLS, ColorValue.TEXT))
    var comboOpen by remember { mutableStateOf(false) }
    val rows = screen.slotRows()
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
        left = 24, top = 32, width = 329, height = 433, maxRows = 16,
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
    Label("Tree", label, 368, 41)
    ComboBox(theme, control, screen.comboTree, 368, 56, 233, 21) { comboOpen = !comboOpen }
    val tier5 = screen.tier5()
    if (tier5.isNotEmpty()) {
        Label("Tier 5 Tree", label, 616, 41)
        Label(tier5, label, 616, 59)
    }
    Label("Abilities", label, 368, 88)
    CheckBox(theme, "Show All", screen.showAll, true, 480, 86, 121) { screen.changeShowAll(!screen.showAll) }
    Label("Selectors", label, 616, 88)
    val abilityTexts = ArrayList<String>()
    for (item in screen.abilities) {
        abilityTexts.add(screen.abilityText(item))
    }
    ListBox(
        theme, abilityTexts, { i -> i == screen.abilityIndex }, 368, 104, 233, 201, screen.drag,
        onPress = { i, _ -> screen.clickAbility(i) },
        onDoubleClick = { _ -> screen.doubleClickList(fromSelectors = false) },
        dragItem = { i -> EnhancementDrag.ListAbility(screen.abilities[i].first, screen.abilities[i].second) },
        onStartDrag = { item, button -> button == PointerButton.PRIMARY && screen.startAbilityDrag(item) },
        onDrop = { item, target -> screen.abilityDropped(item, target) },
        dropKey = AbilityListKey,
    )
    val selectorTexts = ArrayList<String>()
    for (s in screen.selectors) {
        selectorTexts.add(s.name)
    }
    ListBox(
        theme, selectorTexts, { i -> i == screen.selectorIndex }, 616, 104, 177, 201, screen.drag,
        onPress = { i, _ -> screen.clickSelector(i) },
        onDoubleClick = { _ -> screen.doubleClickList(fromSelectors = true) },
        dragItem = { _ ->
            val item = screen.abilities.getOrNull(screen.abilityIndex)
            if (item == null) null else EnhancementDrag.ListAbility(item.first, item.second)
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
        modifier = Modifier.offset(368.dp, 313.dp).width(425.dp),
    )
    DetailsBox(theme, details?.lines ?: emptyList(), 368, 329, 425, 136)
    val ap = screen.apCaption()
    var apColor = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT)
    if (ap.second) {
        apColor = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT_ERROR)
    }
    BasicText(ap.first, style = TextStyle(fontSize = FONT_SIZE, color = apColor), modifier = Modifier.offset(32.dp, 468.dp))
    Label(screen.spentCaption(), label, 288, 468)
    if (details != null) {
        Label(details.ranks, label, 480, 468)
        Label(details.cost, label, 544, 468)
        val prog = details.progression
        if (prog != null) {
            Label(prog, label, 680, 468)
        }
    }
    if (comboOpen) {
        val names = screen.comboTrees()
        DropList(theme, control, names, 368, 77, 233) { index ->
            comboOpen = false
            screen.chooseComboTree(names[index])
        }
    }
}
