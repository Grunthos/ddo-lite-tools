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
import ddolite.common.theme.ColorGroup
import ddolite.common.theme.ColorValue
import ddolite.common.theme.Theme

/** frmEnhancements' client area, in pixels at 100%. */
private const val ENH_WIDTH = 815
private const val ENH_HEIGHT = 555

/**
 * The Enhancements window (Source/Forms/Build/frmEnhancements.frm): the header (Trees,
 * Enhancements, Leveling Guide; Help), the tab, and the footer ("< Feats" or "< Spells",
 * "Destiny >" from level 20), in a [WindowFrame]; the client area is 815 x 555 at 100%. The
 * Leveling Guide tab is [GuideTab]; its Export link (the guide as csv) comes with the build output.
 *
 * Kotlin layout: each tab's content starts at the frame's content corner and ends at its right
 * margin (VB's three tabs each start somewhere different); within a tab, controls are placed from
 * one another, from the positions in tools/form-layout's frmEnhancements.md.
 *
 * [onNavigate] gets the footer's links.
 */
@Composable
fun EnhancementsWindowContent(state: BuilderSession, onExportGuide: () -> Unit = {}, onNavigate: (String) -> Unit) {
    val screen = state.enhancements
    val theme = state.skills.theme
    @Suppress("UNUSED_VARIABLE")
    val revision = state.revision + screen.revision + screen.guide.revision // read so this redraws after changes
    val density = LocalDensity.current.density
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var menuAt by remember { mutableStateOf<Offset?>(null) }

    WindowFrame(
        theme, ENH_WIDTH, ENH_HEIGHT,
        modifier = Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    pointer = event.changes[0].position
                }
            }
        },
        header = {
            // usrHeader_Click (:1082); "Export" appears on the Leveling Guide tab (ShowTab, :1111).
            val right = if (screen.tab == 2) listOf("Export", "Help") else listOf("Help")
            HeaderBar(
                theme,
                listOf("Trees", "Enhancements", "Leveling Guide"),
                screen.tab,
                right,
                isEnabled = { true },
                onClick = { link ->
                    when (link) {
                        "Help" -> state.help.show(if (screen.tab == 2) "Leveling_Guide" else "Enhancements")
                        "Trees" -> screen.chooseTab(0)
                        "Enhancements" -> screen.chooseTab(1)
                        "Leveling Guide" -> screen.chooseTab(2)
                    }
                },
            )
        },
        footer = {
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
                isEnabled = { true },
                onClick = { link -> onNavigate(link) },
            )
        },
        overlay = {
            EnhancementDragGhost(screen, theme)
            EnhancementMenus(screen, menuAt, density, onClose = { menuAt = null })
        },
    ) { area ->
        if (state.rules != null) {
            when (screen.tab) {
                0 -> TreesTab(screen, theme, area) { menuAt = pointer }
                1 -> AbilitiesTab(screen, theme, area) { menuAt = pointer }
                else -> GuideTab(screen.guide, theme, area) { menuAt = pointer }
            }
        }
    }
}

/** The pop-up menus at [at] (window pixels): mnuMain(0) for trees, mnuMain(1) for abilities, mnuMain(2) for the guide. */
@Composable
private fun EnhancementMenus(screen: EnhancementsScreenState, at: Offset?, density: Float, onClose: () -> Unit) {
    if (at != null && screen.treeMenuRow != 0) {
        val items = screen.treeMenuItems()
        val captions = ArrayList<String>()
        for (item in items) {
            captions.add(item.caption)
        }
        PopupMenu(captions, -1, at, density, onChoose = { index ->
            onClose()
            if (items[index].enabled) {
                screen.chooseTreeMenu(index)
            } else {
                screen.closeTreeMenu()
            }
        }, onDismiss = {
            onClose()
            screen.closeTreeMenu()
        }, clientWidth = ENH_WIDTH)
    } else if (at != null && screen.tab == 2 && screen.guide.menuRow != 0) {
        // The guide's row menu (PopupGuideMenu, :3299).
        val items = screen.guide.menuItems()
        val captions = ArrayList<String>()
        for (item in items) {
            captions.add(item.caption)
        }
        PopupMenu(captions, -1, at, density, onChoose = { index ->
            onClose()
            screen.guide.chooseMenu(index)
        }, onDismiss = {
            onClose()
            screen.guide.closeMenu()
        }, clientWidth = ENH_WIDTH, enabled = { index -> items[index].enabled })
    } else if (at != null && screen.slotMenuRow != 0) {
        PopupMenu(listOf("Clear this ability", "Reset tree"), -1, at, density, onChoose = { index ->
            onClose()
            screen.chooseSlotMenu(index)
        }, onDismiss = {
            onClose()
            screen.closeSlotMenu()
        }, clientWidth = ENH_WIDTH)
    }
}

/*
 * The Trees tab's layout, from the content corner: the build's trees on the left, the trees it could
 * add on the right, both with their captions on the first line and their boxes a line below; under
 * them the AP line and the racial and universal AP frames, each under its column.
 */
private const val BUILD_TREES_WIDTH = 361
private const val BUILD_TREES_HEIGHT = LINE + 171
private const val ADD_TREES_LEFT = BUILD_TREES_WIDTH + 23
private const val ADD_TREES_LABEL_TOP = 0
private const val ADD_TREES_TOP = ADD_TREES_LABEL_TOP + LINE

/** Where the add list's Class column starts, and its caption over it. */
private const val ADD_TREES_CLASS_COLUMN = 176
private const val TREES_AP_TOP = BUILD_TREES_HEIGHT + 7
private const val AP_FRAMES_TOP = TREES_AP_TOP + 28
private const val AP_FRAME_HEIGHT = 157

/** The Trees tab (picTab(0)): the build's trees, the trees it could add, the AP line, and the racial and universal AP. */
@Composable
private fun TreesTab(screen: EnhancementsScreenState, theme: Theme, area: ContentArea, onMenu: () -> Unit) {
    val label = TextStyle(platformStyle = TEXT_RENDERING, fontSize = FONT_SIZE, color = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT))
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
                0 -> columns.className
                2 -> columns.tier
                3 -> columns.ap
                else -> ""
            }
        },
        slot = { row -> SlotContent(rows.getOrNull(row - 1)?.treeName ?: "", false) },
        dropState = { row -> screen.treeDropStates.getOrNull(row - 1) ?: ddolite.builder.build.DropState.DEFAULT },
        hoverRow = screen.treeHover,
        left = 0, top = 0, width = BUILD_TREES_WIDTH, height = BUILD_TREES_HEIGHT, maxRows = 21,
        fitRows = 7, // a build has at most seven trees
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
    Label("Tree", label, ADD_TREES_LEFT, ADD_TREES_LABEL_TOP)
    Label("Class", label, ADD_TREES_LEFT + ADD_TREES_CLASS_COLUMN, ADD_TREES_LABEL_TOP)
    val listed = screen.availableTrees()
    val texts = ArrayList<String>()
    for (tree in listed) {
        texts.add(tree.name + "\t" + tree.source)
    }
    ListBox(
        theme, texts, { i -> i == screen.treeListIndex }, ADD_TREES_LEFT, ADD_TREES_TOP, area.width - ADD_TREES_LEFT,
        BUILD_TREES_HEIGHT - ADD_TREES_TOP, screen.drag,
        onPress = { i, _ -> screen.pressListTree(i) },
        onDoubleClick = { _ -> screen.doubleClickListTree() },
        dragItem = { i -> EnhancementDrag.ListTree(listed[i].name) },
        onStartDrag = { item, button -> button == PointerButton.PRIMARY && screen.startTreeDrag(item) },
        onDrop = { item, target -> screen.treeDropped(item, target) },
        dropKey = TreeListKey,
        itemColumn = ADD_TREES_CLASS_COLUMN,
    )
    val ap = screen.apCaption()
    var apColor = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT)
    if (ap.error) {
        apColor = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT_ERROR)
    }
    BasicText(ap.text, style = TextStyle(platformStyle = TEXT_RENDERING, fontSize = FONT_SIZE, color = apColor), modifier = Modifier.offset(216.dp, TREES_AP_TOP.dp))

    // fraRacialAP, under the build's trees.
    GroupFrame(theme, "Racial Past Life AP", 0, AP_FRAMES_TOP, BUILD_TREES_WIDTH, AP_FRAME_HEIGHT)
    val build = screenBuild(screen)
    Spinner(
        value = build?.racialAP ?: 0,
        min = 0,
        max = screen.racialApMax(),
        theme = theme,
        onValueChange = { screen.setRacialAp(it) },
        modifier = Modifier.offset(36.dp, (AP_FRAMES_TOP + 32).dp).size(65.dp, 20.dp),
    )
    val note = screen.racialApNote
    if (note != null) {
        Label(note, label, 112, AP_FRAMES_TOP + 34)
    }
    Label("- Racial AP can be spent at level 1", label, 24, AP_FRAMES_TOP + 68)
    Label("- Racial Past Lives offer up to +" + screen.racialLives() + " Racial AP", label, 24, AP_FRAMES_TOP + 88)
    Label("- Racial AP Tomes can add another +" + (screen.racialApMax() - screen.racialLives()) + " Racial AP", label, 24, AP_FRAMES_TOP + 108)

    // fraUniversalAP, under the trees to add. VB never fills in lblUniversalAPhelp(1)'s "?".
    GroupFrame(theme, "Universal AP", ADD_TREES_LEFT, AP_FRAMES_TOP, area.width - ADD_TREES_LEFT, AP_FRAME_HEIGHT)
    Spinner(
        value = build?.universalAP ?: 0,
        min = 0,
        max = universalMax(screen),
        theme = theme,
        onValueChange = { screen.setUniversalAp(it) },
        modifier = Modifier.offset((ADD_TREES_LEFT + 36).dp, (AP_FRAMES_TOP + 32).dp).size(65.dp, 20.dp),
    )
    Label("- Universal AP can be spent at level 1", label, ADD_TREES_LEFT + 24, AP_FRAMES_TOP + 68)
    Label("- Universal AP Tomes can add +?", label, ADD_TREES_LEFT + 24, AP_FRAMES_TOP + 88)
}

/*
 * The Enhancements tab's layout, from the content corner: the tree's ability slots on the left, with
 * their headers on the first line; to the right the tree drop-down under its caption, the abilities
 * and selectors lists, and the details under them; the totals line under everything.
 */
private const val SLOTS_WIDTH = 329
private const val SLOTS_HEIGHT = LINE + 443
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
private const val ABILITY_TOTALS_TOP = SLOTS_HEIGHT + TIGHT

/** The Enhancements tab (picTab(1)): the chosen tree's abilities, the tree, ability and selector lists, details and totals. */
@Composable
private fun AbilitiesTab(screen: EnhancementsScreenState, theme: Theme, area: ContentArea, onMenu: () -> Unit) {
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
        dropState = { row -> screen.slotStates.getOrNull(row - 1) ?: ddolite.builder.build.DropState.DEFAULT },
        hoverRow = screen.slotHover,
        left = 0, top = 0, width = SLOTS_WIDTH, height = SLOTS_HEIGHT, maxRows = 18,
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
    DetailsBox(theme, details?.lines ?: emptyList(), ABILITIES_LEFT, ABILITY_DETAILS_TOP, rightWidth, SLOTS_HEIGHT - ABILITY_DETAILS_TOP)
    val ap = screen.apCaption()
    var apColor = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT)
    if (ap.error) {
        apColor = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT_ERROR)
    }
    BasicText(ap.text, style = TextStyle(platformStyle = TEXT_RENDERING, fontSize = FONT_SIZE, color = apColor), modifier = Modifier.offset(8.dp, ABILITY_TOTALS_TOP.dp))
    Label(screen.spentCaption(), label, 280, ABILITY_TOTALS_TOP)
    if (details != null) {
        Label(details.ranks, label, ABILITIES_LEFT + 96, ABILITY_TOTALS_TOP)
        Label(details.cost, label, ABILITIES_LEFT + 168, ABILITY_TOTALS_TOP)
        val prog = details.progression
        if (prog != null) {
            Label(prog, label, ABILITIES_LEFT + 304, ABILITY_TOTALS_TOP)
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
        BasicText(text, style = TextStyle(platformStyle = TEXT_RENDERING, fontSize = FONT_SIZE, color = theme.color(ColorGroup.DROP_SLOTS, ColorValue.TEXT)), modifier = Modifier.offset(4.dp, 1.dp))
    }
}
