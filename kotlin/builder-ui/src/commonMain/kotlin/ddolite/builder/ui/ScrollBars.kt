package ddolite.builder.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import ddolite.common.theme.ColorGroup
import ddolite.common.theme.ColorValue
import ddolite.common.theme.Theme

/** A Windows scroll bar's width (and a horizontal one's height), at 100%. */
const val SCROLL_BAR = 17

/** How far an arrow click moves: about a list line. */
private const val ARROW_STEP = 15

/**
 * A scroll bar for [state], VB-style: arrows at the ends (a line), the track either side of the thumb
 * (a page), and the thumb dragged. Placed at [left], [top] with [length] along it. While everything fits
 * it isn't drawn, or with [always] it's drawn greyed with no thumb, so the space beside it doesn't change.
 * New code: VB's controls had them built in.
 */
@Composable
fun ScrollBar(theme: Theme, state: ScrollState, left: Int, top: Int, length: Int, vertical: Boolean = true, always: Boolean = false) {
    if (!canScroll(state)) {
        if (always) {
            DisabledScrollBar(theme, left, top, length, vertical)
        }
        return
    }
    val density = LocalDensity.current.density
    val back = theme.color(ColorGroup.CONTROLS, ColorValue.BACKGROUND)
    val border = theme.color(ColorGroup.CONTROLS, ColorValue.BORDER_EXTERIOR)
    val arrowStyle = TextStyle(fontSize = FONT_SIZE, color = theme.color(ColorGroup.CONTROLS, ColorValue.TEXT))
    val viewport = state.viewportSize.toFloat()
    val content = viewport + state.maxValue
    val track = length - SCROLL_BAR * 2
    var thumb = (track * viewport / content).toInt()
    if (thumb < 12) {
        thumb = 12
    }
    if (thumb > track) {
        thumb = track
    }
    val thumbStart = SCROLL_BAR + ((track - thumb) * state.value.toFloat() / state.maxValue).toInt()
    // Pixels of content per pixel of thumb travel.
    val ratio = if (track > thumb) state.maxValue / ((track - thumb) * density) else 0f

    fun at(along: Int, across: Int): Modifier = if (vertical) Modifier.offset(across.dp, along.dp) else Modifier.offset(along.dp, across.dp)
    fun sized(along: Int, across: Int): Modifier = if (vertical) Modifier.size(across.dp, along.dp) else Modifier.size(along.dp, across.dp)

    Box(modifier = Modifier.offset(left.dp, top.dp).then(sized(length, SCROLL_BAR)).background(back).border(1.dp, border)) {
        // The arrows.
        Box(
            modifier = sized(SCROLL_BAR, SCROLL_BAR).pointerInput(state) { detectTapGestures { state.dispatchRawDelta(-ARROW_STEP * density) } },
        ) {
            BasicText(if (vertical) "▴" else "◂", style = arrowStyle, modifier = Modifier.offset(5.dp, 0.dp))
        }
        Box(
            modifier = at(length - SCROLL_BAR, 0).then(sized(SCROLL_BAR, SCROLL_BAR))
                .pointerInput(state) { detectTapGestures { state.dispatchRawDelta(ARROW_STEP * density) } },
        ) {
            BasicText(if (vertical) "▾" else "▸", style = arrowStyle, modifier = Modifier.offset(5.dp, 0.dp))
        }
        // The track before and after the thumb: a page.
        Box(
            modifier = at(SCROLL_BAR, 0).then(sized(thumbStart - SCROLL_BAR, SCROLL_BAR))
                .pointerInput(state) { detectTapGestures { state.dispatchRawDelta(-viewport) } },
        )
        Box(
            modifier = at(thumbStart + thumb, 0).then(sized(length - SCROLL_BAR - thumbStart - thumb, SCROLL_BAR))
                .pointerInput(state) { detectTapGestures { state.dispatchRawDelta(viewport) } },
        )
        // The thumb.
        Box(
            modifier = at(thumbStart, 2).then(sized(thumb, SCROLL_BAR - 4)).background(border)
                .pointerInput(state, ratio) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        state.dispatchRawDelta((if (vertical) drag.y else drag.x) * ratio)
                    }
                },
        )
    }
}

/** True when [state] has more than fits (its size is known after the first layout). */
fun canScroll(state: ScrollState): Boolean = state.maxValue in 1 until Int.MAX_VALUE

/** A scroll bar with nothing to scroll: the track and greyed arrows. */
@Composable
private fun DisabledScrollBar(theme: Theme, left: Int, top: Int, length: Int, vertical: Boolean) {
    val back = theme.color(ColorGroup.CONTROLS, ColorValue.BACKGROUND)
    val border = theme.color(ColorGroup.CONTROLS, ColorValue.BORDER_EXTERIOR)
    val dim = TextStyle(fontSize = FONT_SIZE, color = theme.color(ColorGroup.CONTROLS, ColorValue.TEXT_DIM))
    val size = if (vertical) Modifier.size(SCROLL_BAR.dp, length.dp) else Modifier.size(length.dp, SCROLL_BAR.dp)
    Box(modifier = Modifier.offset(left.dp, top.dp).then(size).background(back).border(1.dp, border)) {
        BasicText(if (vertical) "▴" else "◂", style = dim, modifier = Modifier.offset(5.dp, 0.dp))
        val end = (length - SCROLL_BAR).dp
        BasicText(if (vertical) "▾" else "▸", style = dim, modifier = if (vertical) Modifier.offset(5.dp, end) else Modifier.offset(end + 5.dp, 0.dp))
    }
}
