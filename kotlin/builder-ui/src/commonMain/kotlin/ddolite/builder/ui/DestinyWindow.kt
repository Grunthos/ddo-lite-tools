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
import ddolite.builder.build.DropState
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
        modifier = Modifier.trackPointer { pointer = it },
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
            val dragging = screen.drag.dragging
            if (dragging != null) {
                DragGhost(theme, screen.dragText(dragging), screen.drag.position)
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
        },
    ) { area ->
        if (state.rules != null) {
            if (screen.tab == 0) {
                DestinyTreesTab(screen, theme, area) { menuAt = pointer }
            } else {
                TreeAbilitiesTab(screen, theme, area) { menuAt = pointer }
            }
        }
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
    val label = labelStyle(theme)
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
        left = 0, top = 0, width = DESTINIES_WIDTH, height = DESTINIES_HEIGHT,
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
        theme, listed, { i -> i == screen.treeListIndex }, ADD_LEFT, ADD_TOP, area.width - ADD_LEFT, DESTINIES_HEIGHT - ADD_TOP, screen.drag,
        onPress = { i, _ -> screen.pressListTree(i) },
        onDoubleClick = { _ -> screen.doubleClickListTree() },
        dragItem = { i -> EnhancementDrag.ListTree(listed[i]) },
        onStartDrag = { item, button -> button == PointerButton.PRIMARY && screen.startTreeDrag(item) },
        onDrop = { item, target -> screen.treeDropped(item, target) },
        dropKey = TreeListKey,
    )
    val ap = screen.apCaption()
    BasicText(ap.text, style = captionStyle(theme, ap.error), modifier = Modifier.offset(216.dp, DESTINY_AP_TOP.dp))

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

