package ddolite.builder.ui

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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
        modifier = Modifier.trackPointer { pointer = it },
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
            val dragging = screen.drag.dragging
            if (dragging != null) {
                DragGhost(theme, screen.dragText(dragging), screen.drag.position)
            }
            EnhancementMenus(screen, menuAt, density, onClose = { menuAt = null })
        },
    ) { area ->
        if (state.rules != null) {
            when (screen.tab) {
                0 -> TreesTab(screen, theme, area) { menuAt = pointer }
                1 -> TreeAbilitiesTab(screen, theme, area) { menuAt = pointer }
                else -> GuideTab(screen.guide, theme, area) { menuAt = pointer }
            }
        }
    }
}

/** The pop-up menus at [at] (window pixels): mnuMain(0) for trees, mnuMain(1) for abilities, mnuMain(2) for the guide. */
@Composable
private fun EnhancementMenus(screen: EnhancementsScreenState, at: Offset?, density: Float, onClose: () -> Unit) {
    if (at != null && screen.treeMenuRow != 0) {
        // Disabled items are grey and can't be chosen (as VB's).
        PopupMenu(screen.treeMenuItems(), at, density, onChoose = { index ->
            onClose()
            screen.chooseTreeMenu(index)
        }, onDismiss = {
            onClose()
            screen.closeTreeMenu()
        }, clientWidth = ENH_WIDTH)
    } else if (at != null && screen.tab == 2 && screen.guide.menuRow != 0) {
        // The guide's row menu (PopupGuideMenu, :3299).
        PopupMenu(screen.guide.menuItems(), at, density, onChoose = { index ->
            onClose()
            screen.guide.chooseMenu(index)
        }, onDismiss = {
            onClose()
            screen.guide.closeMenu()
        }, clientWidth = ENH_WIDTH)
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
    val label = labelStyle(theme)
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
        left = 0, top = 0, width = BUILD_TREES_WIDTH, height = BUILD_TREES_HEIGHT,
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
    BasicText(ap.text, style = captionStyle(theme, ap.error), modifier = Modifier.offset(216.dp, TREES_AP_TOP.dp))

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

private fun screenBuild(screen: EnhancementsScreenState) = screen.build()

private fun universalMax(screen: EnhancementsScreenState): Int = screen.universalApMax()

