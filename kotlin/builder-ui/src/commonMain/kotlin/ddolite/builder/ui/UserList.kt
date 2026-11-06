package ddolite.builder.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ddolite.builder.build.DetailLine
import ddolite.builder.build.DropState
import ddolite.common.theme.ColorGroup
import ddolite.common.theme.ColorValue
import ddolite.common.theme.Theme
import kotlin.time.TimeSource

/** One column of a [UserList]: header, the text it's sized to (VB's DefineColumn), alignment; [slot] is the slot column. */
class ListColumn(val header: String, val sizeText: String, val align: TextAlign, val slot: Boolean = false)

/** What one row of a [UserList] shows in its slot. [maxRanks] above 1 shows "I", "II", "III" and the rank arrows. */
class SlotContent(val caption: String, val error: Boolean, val ranks: Int = 0, val maxRanks: Int = 0)

/** Tells a double-click from two presses: the same item pressed again within half a second. */
class DoubleClickTimer {
    private var lastTime: TimeSource.Monotonic.ValueTimeMark? = null
    private var lastItem = -1

    fun isDoubleClick(item: Int): Boolean {
        val now = TimeSource.Monotonic.markNow()
        val last = lastTime
        val result = item == lastItem && last != null && (now - last).inWholeMilliseconds < 500
        if (result) {
            lastItem = -1
        } else {
            lastItem = item
            lastTime = now
        }
        return result
    }
}

/**
 * VB's userList control (Source/Controls/Build/userList.ctl) with its userSlot rows
 * (Source/Controls/userSlot.ctl): column headers above a bordered list, columns sized as
 * DefineColumn/Refresh do (fixed columns from the left up to the slot, from the right after it),
 * a scroll bar when the rows don't fit. Each slot shows its drop state, error and hover border; a
 * press calls [onPress] (VB raises SlotClick on mouse down), a quick second press [onDoubleClick],
 * a left drag [dragItem] (refused if [onStartDrag] says no), and the rank arrows [onRankChange].
 * Rows are numbered from 1.
 *
 * [headerHeight] is the space above the box for the column headers, whose text sits at its bottom: VB's
 * 30 pixels, or one label line ([LINE]) to put the headers on a window's first line (WindowFrame).
 */
@Composable
fun <T> UserList(
    theme: Theme,
    columns: List<ListColumn>,
    rowCount: Int,
    cellText: (Int, Int) -> String,
    slot: (Int) -> SlotContent,
    dropState: (Int) -> DropState,
    hoverRow: Int,
    left: Int,
    top: Int,
    width: Int,
    height: Int,
    drag: DragDropState<T>,
    rowKey: (Int) -> Any,
    dragItem: (Int) -> T?,
    onStartDrag: (T, PointerButton) -> Boolean,
    onDrop: (T, Any?) -> Unit,
    onPress: (Int, PointerButton) -> Unit,
    onDoubleClick: (Int) -> Unit,
    onHover: (Int) -> Unit,
    onRankChange: (Int, Int) -> Unit = { _, _ -> },
    fitRows: Int = 0,
    headerHeight: Int = 30,
) {
    val textMeasurer = rememberTextMeasurer()
    val normal = TextStyle(platformStyle = TEXT_RENDERING, fontSize = FONT_SIZE)
    val bold = TextStyle(platformStyle = TEXT_RENDERING, fontSize = FONT_SIZE, fontWeight = FontWeight.Bold)
    // The measurer works in pixels; the layout below is in dp.
    val scale = LocalDensity.current.density
    val textHeight = (textMeasurer.measure("X", normal).size.height / scale).toInt()
    var slotHeight = textHeight * 4 / 3
    var marginY = slotHeight / 4
    val marginX = (textMeasurer.measure("  ", normal).size.width / scale).toInt()
    val headerSpace = headerHeight
    // New: [fitRows] rows always fit without scrolling (VB's Verdana rows are shorter than this font's,
    // so VB fits them in the same height). The slots and gaps shrink, keeping their 4:1 proportion.
    if (fitRows > 0) {
        val available = height - headerSpace - 2
        if ((slotHeight + marginY) * fitRows + marginY * 2 > available) {
            marginY = available / (fitRows * 5 + 2)
            slotHeight = marginY * 4
        }
    }
    // Room for the scroll bar when the rows don't fit (VB's DEFAULT_MAX_ROWS, here from the height).
    var clientWidth = width - 2
    if ((slotHeight + marginY) * rowCount + marginY * 2 > height - headerSpace - 2) {
        clientWidth -= SCROLL_BAR
    }
    var slotColumn = 0
    for (i in columns.indices) {
        if (columns[i].slot) {
            slotColumn = i
        }
    }
    val lefts = IntArray(columns.size)
    val widths = IntArray(columns.size)
    var x = marginX
    for (i in 0 until slotColumn) {
        lefts[i] = x
        widths[i] = (textMeasurer.measure(columns[i].sizeText, bold).size.width / scale).toInt()
        x = lefts[i] + widths[i] + marginX
    }
    lefts[slotColumn] = x
    var right = clientWidth - marginX
    for (i in columns.size - 1 downTo slotColumn + 1) {
        widths[i] = (textMeasurer.measure(columns[i].sizeText, bold).size.width / scale).toInt()
        lefts[i] = right - widths[i]
        right = lefts[i] - marginX
    }
    widths[slotColumn] = right - lefts[slotColumn]

    // DrawColumnHeaders: headers may be wider than their columns.
    val headerStyle = labelStyle(theme)
    for (i in columns.indices) {
        val headerWidth = (textMeasurer.measure(columns[i].header, normal).size.width / scale).toInt()
        var hx = lefts[i]
        if (columns[i].align == TextAlign.Center) {
            hx = lefts[i] + (widths[i] - headerWidth) / 2
        } else if (columns[i].align == TextAlign.End) {
            hx = lefts[i] + widths[i] - headerWidth
        }
        BasicText(columns[i].header, style = headerStyle, softWrap = false, modifier = Modifier.offset((left + 1 + hx).dp, (top + headerSpace - textHeight - 2).dp))
    }
    val scroll = rememberScrollState()
    Box(
        modifier = Modifier.offset(left.dp, (top + headerSpace).dp).size(width.dp, (height - headerSpace).dp)
            .background(theme.color(ColorGroup.CONTROLS, ColorValue.BACKGROUND))
            .border(1.dp, theme.color(ColorGroup.WORKSPACE, ColorValue.BORDER_EXTERIOR)),
    ) {
        Box(modifier = Modifier.offset(1.dp, 1.dp).size((width - 2).dp, (height - headerSpace - 2).dp).verticalScroll(scroll)) {
            Box(modifier = Modifier.size(clientWidth.dp, ((slotHeight + marginY) * rowCount + marginY * 2).dp)) {
                for (r in 0 until rowCount) {
                    val row = r + 1
                    val rowTop = (slotHeight + marginY) * r + marginY
                    var style = controlStyle(theme)
                    if (hoverRow == row) {
                        style = style.copy(fontWeight = FontWeight.Bold)
                    }
                    for (i in columns.indices) {
                        if (i == slotColumn) {
                            continue
                        }
                        BasicText(
                            cellText(row, i),
                            style = style.copy(textAlign = columns[i].align),
                            maxLines = 1,
                            modifier = Modifier.offset(lefts[i].dp, (rowTop + (slotHeight - textHeight) / 2).dp).width(widths[i].dp),
                        )
                    }
                    SlotBox(
                        theme, slot(row), dropState(row), hoverRow == row, lefts[slotColumn], rowTop, widths[slotColumn], slotHeight,
                        drag, rowKey(row), dragItem(row), onStartDrag, onDrop,
                        onPress = { button -> onPress(row, button) },
                        onDoubleClick = { onDoubleClick(row) },
                        onHover = { inside -> onHover(if (inside) row else 0) },
                        onRankChange = { ranks -> onRankChange(row, ranks) },
                    )
                }
            }
        }
        ScrollBar(theme, scroll, width - 1 - SCROLL_BAR, 1, height - headerSpace - 2)
    }
}

/** One userSlot: background by drop state, error text, hover border, and the rank arrows. */
@Composable
private fun <T> SlotBox(
    theme: Theme,
    content: SlotContent,
    state: DropState,
    hover: Boolean,
    left: Int,
    top: Int,
    width: Int,
    height: Int,
    drag: DragDropState<T>,
    key: Any,
    dragItem: T?,
    onStartDrag: (T, PointerButton) -> Boolean,
    onDrop: (T, Any?) -> Unit,
    onPress: (PointerButton) -> Unit,
    onDoubleClick: () -> Unit,
    onHover: (Boolean) -> Unit,
    onRankChange: (Int) -> Unit,
) {
    val background = when (state) {
        DropState.DEFAULT -> theme.color(ColorGroup.DROP_SLOTS, ColorValue.BACKGROUND)
        DropState.CAN_DROP -> theme.color(ColorGroup.DROP_SLOTS, ColorValue.BACK_HIGHLIGHT)
        DropState.CAN_DROP_ERROR -> theme.color(ColorGroup.DROP_SLOTS, ColorValue.BACK_ERROR)
        DropState.SUBORDINATE -> theme.color(ColorGroup.DROP_SLOTS, ColorValue.BACK_RELATED)
    }
    var border = theme.color(ColorGroup.DROP_SLOTS, ColorValue.BORDER_EXTERIOR)
    if (hover) {
        border = theme.color(ColorGroup.DROP_SLOTS, ColorValue.BORDER_HIGHLIGHT)
    }
    var textColor = theme.color(ColorGroup.DROP_SLOTS, ColorValue.TEXT)
    if (content.error) {
        textColor = theme.color(ColorGroup.DROP_SLOTS, ColorValue.TEXT_ERROR)
    }
    val clicks = remember { DoubleClickTimer() }
    val buttons = remember { arrayOf(PointerButton.PRIMARY) }
    val arrows = content.maxRanks > 1 && content.caption.isNotEmpty()
    val arrowWidth = ((height - 5) / 2) * 2
    var modifier = Modifier.offset(left.dp, top.dp).size(width.dp, height.dp)
        .dropTarget(drag, key)
        .background(background)
        .border(1.dp, border)
        // Keyed on what the handler reads, so a slot filled or re-ranked since it was first drawn gets its arrows.
        .pointerInput(key, arrows, content.ranks, content.maxRanks, width, height) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type == PointerEventType.Enter || event.type == PointerEventType.Move) {
                        if (drag.dragging == null) {
                            onHover(true)
                        }
                    } else if (event.type == PointerEventType.Exit) {
                        if (drag.dragging == null) {
                            onHover(false)
                        }
                    } else if (event.type == PointerEventType.Press) {
                        val button = pressedButton(event)
                        buttons[0] = button
                        val x = event.changes[0].position.x / density
                        if (arrows && x >= width - arrowWidth - 1 && button == PointerButton.PRIMARY) {
                            // userSlot.picArrows_MouseDown: the top half raises the rank, the bottom half lowers it.
                            val y = event.changes[0].position.y / density
                            if (y < height / 2f) {
                                if (content.ranks < content.maxRanks) {
                                    onRankChange(content.ranks + 1)
                                }
                            } else if (content.ranks > 1) {
                                onRankChange(content.ranks - 1)
                            }
                            event.changes[0].consume()
                        } else if (button == PointerButton.PRIMARY && clicks.isDoubleClick(0)) {
                            onDoubleClick()
                        } else {
                            onPress(button)
                        }
                    }
                }
            }
        }
    if (dragItem != null) {
        modifier = modifier.dragSource(drag, dragItem, onStart = { item -> onStartDrag(item, buttons[0]) }) { item, target -> onDrop(item, target) }
    }
    Box(modifier = modifier) {
        // userSlot.DisplayText: the caption, then " I", " II" or " III" when it has ranks.
        var text = content.caption
        var textWidth = width - 8
        if (arrows) {
            var shown = content.ranks
            if (shown > 3) {
                shown = 3
            }
            if (shown < 0) {
                shown = 0
            }
            text = text + " " + "III".substring(0, shown)
            textWidth -= arrowWidth
        }
        BasicText(
            text,
            style = TextStyle(platformStyle = TEXT_RENDERING, fontSize = FONT_SIZE, color = textColor),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.offset(4.dp, ((height - 15) / 2).dp).width(textWidth.dp),
        )
        if (arrows) {
            val arrowColor = theme.color(ColorGroup.DROP_SLOTS, ColorValue.TEXT)
            Canvas(modifier = Modifier.offset((width - arrowWidth - 1).dp, 1.dp).size(arrowWidth.dp, (height - 2).dp)) {
                // DrawSkinnyTriangle: an up arrow in the top half, a down arrow in the bottom half.
                val w = size.width
                val h = size.height
                val up = Path()
                up.moveTo(w / 2, 2f)
                up.lineTo(w - 2, h / 2 - 2)
                up.lineTo(2f, h / 2 - 2)
                up.close()
                drawPath(up, arrowColor)
                val down = Path()
                down.moveTo(2f, h / 2 + 2)
                down.lineTo(w - 2, h / 2 + 2)
                down.lineTo(w / 2, h - 2)
                down.close()
                drawPath(down, arrowColor)
            }
        }
    }
}

/**
 * A ListBox in the controls colours, with Windows' selection colours. An item is chosen as the
 * button goes down; [onPress] also gets whether Ctrl was held. [itemColumn] puts text after a tab
 * at that x (a ListBox's tab stops). [dragItem] lets an item be dragged; [dropKey] makes the list a
 * drop target. A list that isn't [enabled] shows its items dim and ignores the mouse.
 */
@Composable
fun <T> ListBox(
    theme: Theme,
    items: List<String>,
    isSelected: (Int) -> Boolean,
    left: Int,
    top: Int,
    width: Int,
    height: Int,
    drag: DragDropState<T>,
    onPress: (Int, Boolean) -> Unit,
    onDoubleClick: (Int) -> Unit = {},
    dragItem: ((Int) -> T?)? = null,
    onStartDrag: (T, PointerButton) -> Boolean = { _, _ -> true },
    onDrop: (T, Any?) -> Unit = { _, _ -> },
    dropKey: Any? = null,
    itemColumn: Int = 0,
    enabled: Boolean = true,
) {
    var modifier = Modifier.offset(left.dp, top.dp).size(width.dp, height.dp)
    if (dropKey != null) {
        modifier = modifier.dropTarget(drag, dropKey)
    }
    val clicks = remember { DoubleClickTimer() }
    val buttons = remember { arrayOf(PointerButton.PRIMARY) }
    val scroll = rememberScrollState()
    val inner = width - 2 - SCROLL_BAR // beside the scroll bar, always there (greyed when not needed)
    Box(
        modifier = modifier
            .background(theme.color(ColorGroup.CONTROLS, ColorValue.BACKGROUND))
            .border(1.dp, theme.color(ColorGroup.CONTROLS, ColorValue.BORDER_EXTERIOR)),
    ) {
        Column(modifier = Modifier.offset(1.dp, 1.dp).size(inner.dp, (height - 2).dp).verticalScroll(scroll)) {
            for (i in items.indices) {
                var back = Color.Transparent
                var color = theme.color(ColorGroup.CONTROLS, ColorValue.TEXT)
                if (!enabled) {
                    color = theme.color(ColorGroup.CONTROLS, ColorValue.TEXT_DIM)
                }
                if (isSelected(i)) {
                    back = HIGHLIGHT
                    color = HIGHLIGHT_TEXT
                }
                var itemModifier = Modifier.size(inner.dp, 15.dp).background(back)
                    .pointerInput(i, items.size, enabled) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type == PointerEventType.Press && enabled) {
                                    buttons[0] = pressedButton(event)
                                    if (buttons[0] == PointerButton.PRIMARY && clicks.isDoubleClick(i)) {
                                        onDoubleClick(i)
                                    } else if (buttons[0] == PointerButton.PRIMARY) {
                                        onPress(i, event.keyboardModifiers.isCtrlPressed)
                                    }
                                }
                            }
                        }
                    }
                if (dragItem != null) {
                    val item = dragItem(i)
                    if (item != null) {
                        itemModifier = itemModifier.dragSource(drag, item, onStart = { dragged -> onStartDrag(dragged, buttons[0]) }) { dragged, target -> onDrop(dragged, target) }
                    }
                }
                Box(modifier = itemModifier) {
                    val text = items[i]
                    val tab = text.indexOf('\t')
                    val style = TextStyle(platformStyle = TEXT_RENDERING, fontSize = FONT_SIZE, color = color)
                    if (tab >= 0 && itemColumn > 0) {
                        BasicText(text.substring(0, tab), style = style, maxLines = 1, modifier = Modifier.offset(2.dp, 0.dp).width((itemColumn - 4).dp))
                        BasicText(text.substring(tab + 1), style = style, maxLines = 1, modifier = Modifier.offset(itemColumn.dp, 0.dp))
                    } else {
                        BasicText(text, style = style, maxLines = 1, modifier = Modifier.offset(2.dp, 0.dp))
                    }
                }
            }
        }
        ScrollBar(theme, scroll, width - 1 - SCROLL_BAR, 1, height - 2, always = true)
    }
}

/** VB's userDetails control: wrapped lines, errors in the error colour, "(Wiki)" as a link. */
@Composable
fun DetailsBox(theme: Theme, lines: List<DetailLine>, left: Int, top: Int, width: Int, height: Int) {
    val uriHandler = LocalUriHandler.current
    val text = theme.color(ColorGroup.CONTROLS, ColorValue.TEXT)
    val scroll = rememberScrollState()
    Box(
        modifier = Modifier.offset(left.dp, top.dp).size(width.dp, height.dp)
            .background(theme.color(ColorGroup.CONTROLS, ColorValue.BACKGROUND))
            .border(1.dp, theme.color(ColorGroup.CONTROLS, ColorValue.BORDER_EXTERIOR)),
    ) {
        // The text wraps beside the scroll bar, which is always there (greyed when not needed).
        Column(
            modifier = Modifier.offset(1.dp, 1.dp).size((width - 2 - SCROLL_BAR).dp, (height - 2).dp)
                .verticalScroll(scroll).padding(start = 4.dp, top = 1.dp, end = 4.dp, bottom = 2.dp),
        ) {
            for (line in lines) {
                var color = text
                if (line.error) {
                    color = theme.color(ColorGroup.CONTROLS, ColorValue.TEXT_ERROR)
                }
                var style = TextStyle(platformStyle = TEXT_RENDERING, fontSize = FONT_SIZE, color = color)
                if (line.text.startsWith(" ")) {
                    style = style.copy(textIndent = TextIndent(restLine = 14.sp))
                }
                val link = line.link
                if (link != null && line.text.endsWith("(Wiki)")) {
                    val start = line.text.length - "(Wiki)".length
                    val annotated = buildAnnotatedString {
                        append(line.text.substring(0, start))
                        withStyle(SpanStyle(color = theme.color(ColorGroup.CONTROLS, ColorValue.TEXT_LINK))) {
                            append("(Wiki)")
                        }
                    }
                    BasicText(annotated, style = style, modifier = Modifier.clickable { uriHandler.openUri(link) })
                } else {
                    BasicText(line.text, style = style)
                }
            }
        }
        ScrollBar(theme, scroll, width - 1 - SCROLL_BAR, 1, height - 2, always = true)
    }
}
