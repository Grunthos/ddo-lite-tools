package ddolite.prototype.skills

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
import ddolite.common.theme.ColorGroup
import ddolite.common.theme.ColorValue
import ddolite.common.theme.Theme

/** frmEnhancements' client width and the top of its footer, in pixels at 100%. */
private const val ENH_WIDTH = 815
private const val ENH_FOOTER_TOP = 528

/**
 * The Enhancements window (Source/Forms/Build/frmEnhancements.frm): the header (Trees,
 * Enhancements, Leveling Guide; Help), the tab, and the footer ("< Feats" or "< Spells",
 * "Destiny >" from level 20). Laid out from the form's positions (tools/form-layout's
 * frmEnhancements.md); the client area is 815 x 555 at 100%. The Leveling Guide tab isn't ported yet.
 *
 * [onNavigate] gets the footer's links.
 */
@Composable
fun EnhancementsWindowContent(state: PrototypeState, onNavigate: (String) -> Unit) {
    val screen = state.enhancements
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
            when (screen.tab) {
                0 -> TreesTab(screen, theme) { menuAt = pointer }
                1 -> AbilitiesTab(screen, theme) { menuAt = pointer }
                else -> BasicText(
                    "The Leveling Guide tab isn't ported yet.",
                    style = TextStyle(fontSize = FONT_SIZE, color = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT_DIM)),
                    modifier = Modifier.offset(24.dp, 48.dp),
                )
            }
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            // usrHeader_Click (:1082); "Export" appears on the Leveling Guide tab (ShowTab, :1111).
            val right = if (screen.tab == 2) listOf("Export", "Help") else listOf("Help")
            HeaderBar(
                theme,
                listOf("Trees", "Enhancements", "Leveling Guide"),
                screen.tab,
                right,
                isEnabled = { link -> link != "Help" && link != "Export" },
                onClick = { link ->
                    when (link) {
                        "Trees" -> screen.chooseTab(0)
                        "Enhancements" -> screen.chooseTab(1)
                        "Leveling Guide" -> screen.chooseTab(2)
                    }
                },
            )
        }
        Box(modifier = Modifier.offset(0.dp, ENH_FOOTER_TOP.dp).size(ENH_WIDTH.dp, 26.dp)) {
            // usrFooter_Click (:1092).
            val right = ArrayList<String>()
            val next = screen.footerRight()
            if (next != null) {
                right.add(next)
            }
            HeaderBar(
                theme,
                listOf(screen.footerLeft()),
                null,
                right,
                isEnabled = { link -> link != "Destiny >" },
                onClick = { link -> onNavigate(link) },
            )
        }
        EnhancementDragGhost(screen, theme)

        // Pop-up menus (mnuMain(0) for trees, mnuMain(1) for abilities).
        val at = menuAt
        if (at != null && screen.treeMenuRow != 0) {
            val items = screen.treeMenuItems()
            val captions = ArrayList<String>()
            for (item in items) {
                captions.add(item.first)
            }
            PopupMenu(captions, -1, at, density, onChoose = { index ->
                menuAt = null
                if (items[index].second) {
                    screen.chooseTreeMenu(index)
                } else {
                    screen.closeTreeMenu()
                }
            }, onDismiss = {
                menuAt = null
                screen.closeTreeMenu()
            }, clientWidth = ENH_WIDTH)
        } else if (at != null && screen.slotMenuRow != 0) {
            PopupMenu(listOf("Clear this ability", "Reset tree"), -1, at, density, onChoose = { index ->
                menuAt = null
                screen.chooseSlotMenu(index)
            }, onDismiss = {
                menuAt = null
                screen.closeSlotMenu()
            }, clientWidth = ENH_WIDTH)
        }
    }
}

/** The Trees tab (picTab(0)): the build's trees, the trees it could add, the AP line, and the racial and universal AP. */
@Composable
private fun TreesTab(screen: EnhancementsScreenState, theme: Theme, onMenu: () -> Unit) {
    val label = TextStyle(fontSize = FONT_SIZE, color = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT))
    val rows = screen.treeRows()
    UserList(
        theme,
        listOf(
            ListColumn("Class", "Arcane Trickster", TextAlign.Start),
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
        dropState = { row -> screen.treeDropStates.getOrNull(row - 1) ?: ddolite.builder.build.DropState.DEFAULT },
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
    Label("Class", label, 640, 64)
    val listed = screen.availableTrees()
    val texts = ArrayList<String>()
    for (tree in listed) {
        texts.add(tree.name + "\t" + tree.source)
    }
    ListBox(
        theme, texts, { i -> i == screen.treeListIndex }, 464, 80, 305, 177, screen.drag,
        onPress = { i, _ -> screen.pressListTree(i) },
        onDoubleClick = { _ -> screen.doubleClickListTree() },
        dragItem = { i -> EnhancementDrag.ListTree(listed[i].name) },
        onStartDrag = { item, button -> button == PointerButton.PRIMARY && screen.startTreeDrag(item) },
        onDrop = { item, target -> screen.treeDropped(item, target) },
        dropKey = TreeListKey,
        itemColumn = 176,
    )
    val ap = screen.apCaption()
    var apColor = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT)
    if (ap.second) {
        apColor = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT_ERROR)
    }
    BasicText(ap.first, style = TextStyle(fontSize = FONT_SIZE, color = apColor), modifier = Modifier.offset(296.dp, 264.dp))

    // fraRacialAP
    GroupFrame(theme, "Racial Past Life AP", 80, 292, 333, 157)
    val build = screenBuild(screen)
    Spinner(
        value = build?.racialAP ?: 0,
        min = 0,
        max = screen.racialApMax(),
        theme = theme,
        onValueChange = { screen.setRacialAp(it) },
        modifier = Modifier.offset(116.dp, 324.dp).size(65.dp, 20.dp),
    )
    val note = screen.racialApNote
    if (note != null) {
        Label(note, label, 192, 326)
    }
    Label("- Racial AP can be spent at level 1", label, 104, 360)
    Label("- Racial Past Lives offer up to +" + screen.racialLives() + " Racial AP", label, 104, 380)
    Label("- Racial AP Tomes can add another +" + (screen.racialApMax() - screen.racialLives()) + " Racial AP", label, 104, 400)

    // fraUniversalAP. VB never fills in lblUniversalAPhelp(1)'s "?".
    GroupFrame(theme, "Universal AP", 427, 292, 333, 157)
    Spinner(
        value = build?.universalAP ?: 0,
        min = 0,
        max = universalMax(screen),
        theme = theme,
        onValueChange = { screen.setUniversalAp(it) },
        modifier = Modifier.offset(463.dp, 324.dp).size(65.dp, 20.dp),
    )
    Label("- Universal AP can be spent at level 1", label, 451, 360)
    Label("- Universal AP Tomes can add +?", label, 451, 380)
}

/** The Enhancements tab (picTab(1)): the chosen tree's abilities, the tree, ability and selector lists, details and totals. */
@Composable
private fun AbilitiesTab(screen: EnhancementsScreenState, theme: Theme, onMenu: () -> Unit) {
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
        dropState = { row -> screen.slotStates.getOrNull(row - 1) ?: ddolite.builder.build.DropState.DEFAULT },
        hoverRow = screen.slotHover,
        left = 24, top = 32, width = 329, height = 473, maxRows = 18,
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
    DetailsBox(theme, details?.lines ?: emptyList(), 368, 329, 425, 176)
    val ap = screen.apCaption()
    var apColor = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT)
    if (ap.second) {
        apColor = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT_ERROR)
    }
    BasicText(ap.first, style = TextStyle(fontSize = FONT_SIZE, color = apColor), modifier = Modifier.offset(32.dp, 508.dp))
    Label(screen.spentCaption(), label, 304, 508)
    if (details != null) {
        Label(details.ranks, label, 464, 508)
        Label(details.cost, label, 536, 508)
        val prog = details.progression
        if (prog != null) {
            Label(prog, label, 672, 508)
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

private fun screenBuild(screen: EnhancementsScreenState) = screen.build()

private fun universalMax(screen: EnhancementsScreenState): Int = screen.universalApMax()

/** The tree or ability being dragged, next to the pointer. */
@Composable
private fun EnhancementDragGhost(screen: EnhancementsScreenState, theme: Theme) {
    val item = screen.drag.dragging ?: return
    val text = screen.dragText(item)
    if (text.isEmpty()) {
        return
    }
    val density = LocalDensity.current.density
    val at = screen.drag.position
    Box(
        modifier = Modifier.offset((at.x / density + 12).dp, (at.y / density + 8).dp)
            .background(theme.color(ColorGroup.DROP_SLOTS, ColorValue.BACKGROUND))
            .border(1.dp, theme.color(ColorGroup.DROP_SLOTS, ColorValue.BORDER_EXTERIOR)),
    ) {
        BasicText(text, style = TextStyle(fontSize = FONT_SIZE, color = theme.color(ColorGroup.DROP_SLOTS, ColorValue.TEXT)), modifier = Modifier.offset(4.dp, 1.dp))
    }
}
