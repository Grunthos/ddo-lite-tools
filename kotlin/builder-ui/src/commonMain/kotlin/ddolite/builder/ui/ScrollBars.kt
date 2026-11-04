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
import androidx.compose.runtime.rememberUpdatedState
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
    val arrowStyle = TextStyle(platformStyle = TEXT_RENDERING, fontSize = FONT_SIZE, color = theme.color(ColorGroup.CONTROLS, ColorValue.TEXT))
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

/**
 * A vertical scroll bar for a list drawn a row at a time (VB's VScrollBar beside a hand-drawn grid):
 * [first] is the first row shown (0-based) of [count], with [visible] rows fitting. The arrows move a
 * row, the track either side of the thumb [page] rows, and the thumb is dragged; each asks
 * [onScroll] to move by a number of rows (the caller keeps it in range). With everything fitting
 * it's drawn greyed with no thumb.
 *
 * Unlike [ScrollBar], it takes the row numbers as plain values, so it's redrawn whenever they
 * change (the guide grid's own bar wasn't, and stayed greyed as rows were added).
 */
@Composable
fun RowScrollBar(
    theme: Theme, first: Int, count: Int, visible: Int, left: Int, top: Int, length: Int,
    page: Int = visible, onScroll: (Int) -> Unit,
) {
    if (count <= visible) {
        DisabledScrollBar(theme, left, top, length, vertical = true)
        return
    }
    val scrollBy = rememberUpdatedState(onScroll)
    val density = LocalDensity.current.density
    val back = theme.color(ColorGroup.CONTROLS, ColorValue.BACKGROUND)
    val border = theme.color(ColorGroup.CONTROLS, ColorValue.BORDER_EXTERIOR)
    val arrowStyle = TextStyle(platformStyle = TEXT_RENDERING, fontSize = FONT_SIZE, color = theme.color(ColorGroup.CONTROLS, ColorValue.TEXT))
    val max = count - visible
    val track = length - SCROLL_BAR * 2
    var thumb = track * visible / count
    if (thumb < 12) {
        thumb = 12
    }
    var start = first
    if (start > max) {
        start = max
    }
    val thumbStart = SCROLL_BAR + (track - thumb) * start / max
    // Pixels of thumb travel per row.
    val pixelsPerRow = (track - thumb).toFloat() / max
    val thumbAt = rememberUpdatedState(thumbStart)

    // The thumb is dragged through the bar, which stays put while the thumb moves: the drag counts
    // when it starts on the thumb, and is added up and turned into whole rows as it goes.
    val drag = Modifier.pointerInput(pixelsPerRow, thumb) {
        var dragging = false
        var dragged = 0f
        detectDragGestures(
            onDragStart = { at ->
                val y = at.y / density
                dragging = y >= thumbAt.value && y < thumbAt.value + thumb
                dragged = 0f
            },
        ) { change, amount ->
            if (dragging) {
                change.consume()
                dragged += amount.y / density
                val rows = (dragged / pixelsPerRow).toInt()
                if (rows != 0) {
                    dragged -= rows * pixelsPerRow
                    scrollBy.value(rows)
                }
            }
        }
    }

    Box(modifier = Modifier.offset(left.dp, top.dp).size(SCROLL_BAR.dp, length.dp).background(back).border(1.dp, border).then(drag)) {
        Box(modifier = Modifier.size(SCROLL_BAR.dp, SCROLL_BAR.dp).pointerInput(Unit) { detectTapGestures { scrollBy.value(-1) } }) {
            BasicText("▴", style = arrowStyle, modifier = Modifier.offset(5.dp, 0.dp))
        }
        Box(
            modifier = Modifier.offset(0.dp, (length - SCROLL_BAR).dp).size(SCROLL_BAR.dp, SCROLL_BAR.dp)
                .pointerInput(Unit) { detectTapGestures { scrollBy.value(1) } },
        ) {
            BasicText("▾", style = arrowStyle, modifier = Modifier.offset(5.dp, 0.dp))
        }
        Box(
            modifier = Modifier.offset(0.dp, SCROLL_BAR.dp).size(SCROLL_BAR.dp, (thumbStart - SCROLL_BAR).dp)
                .pointerInput(page) { detectTapGestures { scrollBy.value(-page) } },
        )
        Box(
            modifier = Modifier.offset(0.dp, (thumbStart + thumb).dp).size(SCROLL_BAR.dp, (length - SCROLL_BAR - thumbStart - thumb).dp)
                .pointerInput(page) { detectTapGestures { scrollBy.value(page) } },
        )
        // The thumb (dragged through the bar, above).
        Box(modifier = Modifier.offset(2.dp, thumbStart.dp).size((SCROLL_BAR - 4).dp, thumb.dp).background(border))
    }
}

/** True when [state] has more than fits (its size is known after the first layout). */
fun canScroll(state: ScrollState): Boolean = state.maxValue in 1 until Int.MAX_VALUE

/** A scroll bar with nothing to scroll: the track and greyed arrows. */
@Composable
private fun DisabledScrollBar(theme: Theme, left: Int, top: Int, length: Int, vertical: Boolean) {
    val back = theme.color(ColorGroup.CONTROLS, ColorValue.BACKGROUND)
    val border = theme.color(ColorGroup.CONTROLS, ColorValue.BORDER_EXTERIOR)
    val dim = TextStyle(platformStyle = TEXT_RENDERING, fontSize = FONT_SIZE, color = theme.color(ColorGroup.CONTROLS, ColorValue.TEXT_DIM))
    val size = if (vertical) Modifier.size(SCROLL_BAR.dp, length.dp) else Modifier.size(length.dp, SCROLL_BAR.dp)
    Box(modifier = Modifier.offset(left.dp, top.dp).then(size).background(back).border(1.dp, border)) {
        BasicText(if (vertical) "▴" else "◂", style = dim, modifier = Modifier.offset(5.dp, 0.dp))
        val end = (length - SCROLL_BAR).dp
        BasicText(if (vertical) "▾" else "▸", style = dim, modifier = if (vertical) Modifier.offset(5.dp, end) else Modifier.offset(end + 5.dp, 0.dp))
    }
}
