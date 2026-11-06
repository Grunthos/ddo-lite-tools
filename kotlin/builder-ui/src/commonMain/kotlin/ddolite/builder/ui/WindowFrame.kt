package ddolite.builder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import ddolite.common.theme.ColorGroup
import ddolite.common.theme.ColorValue
import ddolite.common.theme.Theme

/** The header and footer bars' height (HeaderBar), pixels at 100%. */
const val BAR_HEIGHT = 26

/** Where every window's content starts: [MARGIN] in from the left, a little under the header bar. */
const val CONTENT_LEFT = MARGIN
const val CONTENT_TOP = BAR_HEIGHT + 6

/** The content area of a window [windowWidth] x [windowHeight]: its size, with its top-left at (0, 0). */
class ContentArea(windowWidth: Int, windowHeight: Int, hasFooter: Boolean = true) {
    /** The width, ending [MARGIN] in from the window's right. */
    val width = windowWidth - CONTENT_LEFT - MARGIN

    /** The height, ending just above the footer, or [MARGIN] above the window's bottom without one. */
    val height = windowHeight - CONTENT_TOP - (if (hasFooter) BAR_HEIGHT + TIGHT else MARGIN)
}

/**
 * A window's frame, the same in every window: the background, [header] (a HeaderBar) at the top,
 * [footer] (a HeaderBar, or null) at the bottom, and [content] drawn in the content area, whose
 * top-left is at the same place in every window. [content] places its controls from (0, 0) and
 * is given the area's size. [overlay] is drawn last, over everything, in window coordinates (pop-up
 * menus, dragged items). Kotlin only: VB's forms each place their controls by hand.
 *
 * [modifier] goes on the whole window (e.g. to follow the pointer).
 */
@Composable
fun WindowFrame(
    theme: Theme,
    width: Int,
    height: Int,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
    footer: (@Composable () -> Unit)?,
    overlay: @Composable () -> Unit = {},
    content: @Composable (ContentArea) -> Unit,
) {
    val area = ContentArea(width, height, footer != null)
    Box(modifier = Modifier.fillMaxSize().background(theme.color(ColorGroup.WORKSPACE, ColorValue.BACKGROUND)).then(modifier)) {
        Box(modifier = Modifier.offset(CONTENT_LEFT.dp, CONTENT_TOP.dp).size(area.width.dp, area.height.dp)) {
            content(area)
        }
        header()
        if (footer != null) {
            Box(modifier = Modifier.offset(0.dp, (height - BAR_HEIGHT).dp).size(width.dp, BAR_HEIGHT.dp)) {
                footer()
            }
        }
        overlay()
    }
}

/**
 * Calls [onMove] with the pointer's position (window pixels) on every pointer event, before the
 * controls under it see the event: where a pop-up menu opens, as VB's PopupMenu shows it at the
 * pointer. Goes on a window's [WindowFrame] (its `modifier`).
 */
fun Modifier.trackPointer(onMove: (Offset) -> Unit): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            onMove(event.changes[0].position)
        }
    }
}

/**
 * What's being dragged, as [text] in a drop-slot coloured box beside the pointer at [position]
 * (window pixels). Drawn in a window's overlay. VB shows the system's drag cursor instead.
 */
@Composable
fun DragGhost(theme: Theme, text: String, position: Offset) {
    if (text.isEmpty()) {
        return
    }
    val density = LocalDensity.current.density
    Box(
        modifier = Modifier.offset((position.x / density + 12).dp, (position.y / density + 8).dp)
            .background(theme.color(ColorGroup.DROP_SLOTS, ColorValue.BACKGROUND))
            .border(1.dp, theme.color(ColorGroup.DROP_SLOTS, ColorValue.BORDER_EXTERIOR)),
    ) {
        BasicText(text, style = TextStyle(platformStyle = TEXT_RENDERING, fontSize = FONT_SIZE, color = theme.color(ColorGroup.DROP_SLOTS, ColorValue.TEXT)), modifier = Modifier.offset(4.dp, 1.dp))
    }
}
