package ddolite.builder.ui

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
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
                TreeListTab(screen, theme, area, onMenu = { menuAt = pointer }) { PermDestinyPoints(screen, theme, area) }
            } else {
                TreeAbilitiesTab(screen, theme, area) { menuAt = pointer }
            }
        }
    }
}

/** The permanent destiny points frame under the Destiny Trees tab's right-hand list (fraDestinyAP). */
@Composable
private fun PermDestinyPoints(screen: DestinyScreenState, theme: Theme, area: ContentArea) {
    val label = labelStyle(theme)

    // fraDestinyAP, under the destinies to add (a captioned frame, as the Enhancements window's).
    GroupFrame(theme, "Perm Destiny Points", ADD_TREES_LEFT, TREE_FRAMES_TOP, area.width - ADD_TREES_LEFT, TREE_FRAME_HEIGHT)
    Spinner(
        value = screen.pdp(),
        min = screen.pdpMin(),
        max = screen.pdpMax(),
        theme = theme,
        onValueChange = { screen.setPdp(it) },
        modifier = Modifier.offset((ADD_TREES_LEFT + 36).dp, (TREE_FRAMES_TOP + 24).dp).size(65.dp, 20.dp),
    )
    Label("- Perm Destiny Points can be spent at level 20", label, ADD_TREES_LEFT + 24, TREE_FRAMES_TOP + 48)
    Label("- PDPs are earned at 1 per 3 Fate Points", label, ADD_TREES_LEFT + 24, TREE_FRAMES_TOP + 68)
    Label("- Fate Point are earned 1 per 3 EPL ", label, ADD_TREES_LEFT + 24, TREE_FRAMES_TOP + 88)
    Label("- Fate/Destiny Tomes can add more points", label, ADD_TREES_LEFT + 24, TREE_FRAMES_TOP + 108)
    Label("- You get 36 FP's for unlocking all Destiny Trees", label, ADD_TREES_LEFT + 24, TREE_FRAMES_TOP + 128)
}

