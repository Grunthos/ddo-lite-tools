package ddolite.prototype.skills

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
 * (a page), and the thumb dragged. Placed at [left], [top] with [length] along it; nothing is drawn
 * while everything fits (VB's ListBox shows its bar only then). New code: VB's controls had them built in.
 */
@Composable
fun ScrollBar(theme: Theme, state: ScrollState, left: Int, top: Int, length: Int, vertical: Boolean = true) {
    if (state.maxValue <= 0 || state.maxValue == Int.MAX_VALUE) {
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
