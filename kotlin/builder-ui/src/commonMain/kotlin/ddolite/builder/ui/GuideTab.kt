package ddolite.builder.ui

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ddolite.builder.build.GuideFilter
import ddolite.builder.build.GuideStepStyle
import ddolite.common.theme.ColorGroup
import ddolite.common.theme.ColorValue
import ddolite.common.theme.Theme

/*
 * The tab's layout (pixels at 100%), each control placed from the one before it with the shared
 * spacing in Layout.kt: two columns on the left (the trees, the abilities) over the details, and
 * the grid on the right.
 */

/** picTab(2)'s top in the window. */
private const val TAB_TOP = 32

/** The tree list (or the selectors) and the ability list beside it; the details span both. */
private const val TREE_WIDTH = 125
private const val ABILITY_LEFT = MARGIN + TREE_WIDTH + GAP
private const val ABILITY_WIDTH = 229
private const val PANEL_RIGHT = ABILITY_LEFT + ABILITY_WIDTH
private const val PANEL_WIDTH = PANEL_RIGHT - MARGIN

/** The filter drop-down, then the lists under it. */
private const val COMBO_TOP = TAB_TOP + 12
private const val LIST_TOP = COMBO_TOP + COMBO_HEIGHT + TIGHT
private const val LIST_HEIGHT = 213
private const val LIST_BOTTOM = LIST_TOP + LIST_HEIGHT

/** "Back to Trees" ends level with the lists, a little below the selector list. */
private const val BACK_TOP = LIST_BOTTOM - BUTTON_HEIGHT
private const val BACK_WIDTH = 109
private const val SELECTOR_HEIGHT = BACK_TOP - 7 - LIST_TOP

/** The details: caption, the Cost / Spent in Tree / Min Level line, then the box, down to the grid's bottom. */
private const val CAPTION_TOP = LIST_BOTTOM + 13
private const val INFO_TOP = CAPTION_TOP + LINE
private const val DETAILS_TOP = INFO_TOP + LINE

/** picContainer: the grid, one gap right of the left panel, under its header line. */
private const val GRID_LEFT = PANEL_RIGHT + GAP
private const val GRID_TOP = TAB_TOP + 24
private const val GRID_WIDTH = 398

/** mlngHeight: picContainer's 441 pixels shared by 20 rows. */
private const val ROW_HEIGHT = 22
private const val GRID_HEIGHT = ROW_HEIGHT * GUIDE_ROWS + 1
private const val GRID_BOTTOM = GRID_TOP + GRID_HEIGHT

/** The totals line, under the grid. */
private const val TOTALS_TOP = GRID_BOTTOM + TIGHT

/**
 * The Leveling Guide tab (picTab(2) of frmEnhancements): the tree list (or the chosen ability's
 * selectors), the ability filter and list, the details, and the guide grid (Level, Ability, Tier,
 * Tree, AP, Prog) with the totals under it. Laid out from the form's positions; the grid's
 * columns are sized as InitGrid (:3597) does, to their headers and the widest tree name.
 *
 * Kotlin layout changes: the lists are 24 pixels shorter; the details caption is bold, with the Cost,
 * Spent in Tree and Min Level labels between it and the box (VB has them under the box), lined up
 * with the box's edges; and the totals (whose level caption also gives the build's classes) are
 * right-aligned under the grid.
 *
 * Not copied yet: dragging past the grid's top or bottom to scroll while selecting (tmrScroll).
 */
@Composable
internal fun GuideTab(screen: GuideScreenState, theme: Theme, onMenu: () -> Unit) {
    val label = TextStyle(platformStyle = TEXT_RENDERING, fontSize = FONT_SIZE, color = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT))
    val control = TextStyle(platformStyle = TEXT_RENDERING, fontSize = FONT_SIZE, color = theme.color(ColorGroup.CONTROLS, ColorValue.TEXT))
    var comboOpen by remember { mutableStateOf(false) }
    val drag = remember { DragDropState<Int>() }

    // picGuideTree, or picSelector in its place.
    if (screen.selectorsShowing) {
        val names = ArrayList<String>()
        for (s in screen.selectors) {
            names.add(s.text)
        }
        ListBox(
            theme, names, isSelected = { it == screen.selectorIndex }, left = MARGIN, top = LIST_TOP, width = TREE_WIDTH, height = SELECTOR_HEIGHT,
            drag = drag, onPress = { i, _ -> screen.clickSelector(i) }, onDoubleClick = { screen.chooseAbility() },
        )
        // Centred under the selector list.
        GuideButton(theme, "Back to Trees", MARGIN + (TREE_WIDTH - BACK_WIDTH) / 2, BACK_TOP, BACK_WIDTH, BUTTON_HEIGHT) { screen.backToTrees() }
    } else {
        // Its caption level with the drop-down's text.
        CheckBox(theme, "Show All", screen.showAllTrees, true, MARGIN, COMBO_TOP + 4, TREE_WIDTH) { screen.toggleShowAllTrees() }
        val names = ArrayList<String>()
        for (t in screen.trees) {
            names.add(t.text)
        }
        ListBox(
            theme, names, isSelected = { it == screen.treeIndex }, left = MARGIN, top = LIST_TOP, width = TREE_WIDTH, height = LIST_HEIGHT,
            drag = drag, onPress = { i, _ -> screen.clickTree(i) },
        )
    }

    // cboGuideAbility and lstGuideAbility.
    val choiceTexts = ArrayList<String>()
    for (c in screen.choices) {
        choiceTexts.add(c.text)
    }
    ListBox(
        theme, choiceTexts, isSelected = { it == screen.choiceIndex }, left = ABILITY_LEFT, top = LIST_TOP, width = ABILITY_WIDTH, height = LIST_HEIGHT,
        drag = drag, onPress = { i, _ -> screen.clickChoice(i) }, onDoubleClick = { screen.chooseAbility() },
    )
    val nothing = screen.nothingAvailable
    if (nothing != null) {
        // Kotlin only: why "Available" lists nothing, inside the empty list (6 pixels in), clear of its scroll bar.
        val dim = control.copy(color = theme.color(ColorGroup.CONTROLS, ColorValue.TEXT_DIM))
        BasicText(nothing, style = dim, modifier = Modifier.offset((ABILITY_LEFT + 6).dp, (LIST_TOP + 6).dp).width((ABILITY_WIDTH - 12 - SCROLL_BAR).dp))
    }

    // Details.
    Label(screen.detailsCaption, label.copy(fontWeight = FontWeight.Bold), MARGIN, CAPTION_TOP)
    DetailsBox(theme, screen.details, MARGIN, DETAILS_TOP, PANEL_WIDTH, GRID_BOTTOM - DETAILS_TOP)
    val cost = screen.costLabel
    if (cost != null) {
        Label(cost, label, MARGIN, INFO_TOP)
    }
    val spent = screen.spentLabel
    if (spent != null) {
        BasicText(spent, style = label.copy(textAlign = TextAlign.Center), modifier = Modifier.offset(MARGIN.dp, INFO_TOP.dp).width(PANEL_WIDTH.dp))
    }
    val levels = screen.levelsLabel
    if (levels != null) {
        var style = label.copy(textAlign = TextAlign.End)
        if (screen.levelsError) {
            style = style.copy(color = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT_ERROR))
        }
        BasicText(levels, style = style, modifier = Modifier.offset(MARGIN.dp, INFO_TOP.dp).width(PANEL_WIDTH.dp))
    }

    GuideGrid(screen, theme, onMenu)

    // The combo goes last so its list drops over the ability list.
    ComboBox(theme, control, screen.filter.caption, ABILITY_LEFT, COMBO_TOP, ABILITY_WIDTH, COMBO_HEIGHT) { comboOpen = !comboOpen }
    if (comboOpen) {
        val captions = ArrayList<String>()
        for (f in GuideFilter.entries) {
            captions.add(f.caption)
        }
        DropList(theme, control, captions, ABILITY_LEFT, COMBO_TOP + COMBO_HEIGHT, ABILITY_WIDTH) { index ->
            comboOpen = false
            screen.chooseFilter(GuideFilter.entries[index])
        }
    }
}

/** A graphical CheckBox used as a button (chkBackToTrees). */
@Composable
private fun GuideButton(theme: Theme, caption: String, left: Int, top: Int, width: Int, height: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier.offset(left.dp, top.dp).size(width.dp, height.dp)
            .background(theme.color(ColorGroup.CONTROLS, ColorValue.BACKGROUND))
            .border(1.dp, theme.color(ColorGroup.CONTROLS, ColorValue.BORDER_EXTERIOR))
            .clickable(onClick = onClick),
    ) {
        BasicText(
            caption,
            style = TextStyle(platformStyle = TEXT_RENDERING, fontSize = FONT_SIZE, color = theme.color(ColorGroup.CONTROLS, ColorValue.TEXT), textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth().offset(0.dp, 4.dp),
        )
    }
}

/** One grid column (VB's ColumnType): header, x and width within picGuide, alignment. */
private class GuideColumn(val header: String, val left: Int, val width: Int, val align: TextAlign) {
    val right: Int get() = left + width
}

/**
 * The guide grid: DrawGrid (:3660), DrawCell (:3700), FitDisplay (:3760) and DrawBorders (:3778),
 * with the mouse handlers picGuide_MouseDown / MouseMove / MouseUp (:3137-:3253). Only the
 * Ability column responds to the mouse, as in VB.
 */
@Composable
private fun GuideGrid(screen: GuideScreenState, theme: Theme, onMenu: () -> Unit) {
    val measurer = rememberTextMeasurer()
    val textStyle = TextStyle(platformStyle = TEXT_RENDERING, fontSize = FONT_SIZE)
    // The measurer works in pixels; the grid is laid out in dp.
    val scale = LocalDensity.current.density
    fun measure(text: String): Int = (measurer.measure(text, textStyle).size.width / scale).toInt()
    val space = measure(" ")
    val gridWidth = GRID_WIDTH - SCROLL_BAR

    // InitGrid: every column but Ability fits its header (or sample text); Ability takes the rest.
    val widths = IntArray(6)
    widths[0] = measure("Level") + space * 2
    widths[2] = measure("Tier") + space * 2
    widths[3] = measure(screen.widestTree { measure(it) }) + space * 2
    widths[4] = measure("Tier") + space * 2
    widths[5] = measure("Tier") + space * 2
    widths[1] = gridWidth - (widths[0] + widths[2] + widths[3] + widths[4] + widths[5]) - 1
    val headers = listOf("Level", "Ability", "Tier", "Tree", "AP", "Prog")
    val aligns = listOf(TextAlign.Center, TextAlign.Start, TextAlign.Center, TextAlign.Start, TextAlign.Center, TextAlign.Center)
    val columns = ArrayList<GuideColumn>()
    var x = 0
    for (i in 0..5) {
        columns.add(GuideColumn(headers[i], x, widths[i], aligns[i]))
        x += widths[i]
    }

    val label = TextStyle(platformStyle = TEXT_RENDERING, fontSize = FONT_SIZE, color = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT))
    for (c in columns) {
        BasicText(c.header, style = label.copy(textAlign = TextAlign.Center), modifier = Modifier.offset((GRID_LEFT + c.left).dp, (GRID_TOP - 16).dp).width(c.width.dp))
    }

    val rows = screen.rows()
    val text = theme.color(ColorGroup.CONTROLS, ColorValue.TEXT)
    val back = theme.color(ColorGroup.CONTROLS, ColorValue.BACKGROUND)
    val interior = theme.color(ColorGroup.CONTROLS, ColorValue.BORDER_INTERIOR)
    val exterior = theme.color(ColorGroup.CONTROLS, ColorValue.BORDER_EXTERIOR)
    val highlightBorder = theme.color(ColorGroup.CONTROLS, ColorValue.BORDER_HIGHLIGHT)
    val selectedBack = theme.color(ColorGroup.CONTROLS, if (screen.moveRows) ColorValue.BACK_RELATED else ColorValue.BACK_HIGHLIGHT)
    val errorText = theme.color(ColorGroup.CONTROLS, ColorValue.TEXT_ERROR)

    fun rowAt(y: Float): Int = (y / ROW_HEIGHT).toInt() + 1 + screen.scroll
    fun columnAt(xPos: Float): Int {
        for (i in columns.indices) {
            if (xPos >= columns[i].left && xPos <= columns[i].right) {
                return i + 1
            }
        }
        return 0
    }

    Box(
        modifier = Modifier.offset(GRID_LEFT.dp, GRID_TOP.dp).size(gridWidth.dp, GRID_HEIGHT.dp)
            .background(back)
            .pointerInput(rows.size, screen.scroll, columns[1].left, columns[1].right) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes[0]
                        val pos = Offset(change.position.x / density, change.position.y / density)
                        val row = rowAt(pos.y)
                        val col = columnAt(pos.x)
                        when (event.type) {
                            PointerEventType.Press -> {
                                if (col == 2) {
                                    val right = event.buttons.isSecondaryPressed
                                    screen.pressRow(row, right, event.keyboardModifiers.isCtrlPressed, event.keyboardModifiers.isShiftPressed)
                                    if (right && screen.menuRow != 0) {
                                        onMenu()
                                    }
                                }
                            }
                            PointerEventType.Move -> {
                                screen.hoverRow(if (col == 2) row else 0)
                                if (col == 2 && event.buttons.isPrimaryPressed && !screen.isSelected(row)) {
                                    screen.dragOverRow(row)
                                }
                            }
                            PointerEventType.Release -> {
                                if (col == 2) {
                                    screen.releaseRow(row)
                                }
                            }
                            PointerEventType.Exit -> screen.hoverRow(0)
                            PointerEventType.Scroll -> {
                                val dy = change.scrollDelta.y
                                if (dy > 0) {
                                    screen.scrollBy(3)
                                } else if (dy < 0) {
                                    screen.scrollBy(-3)
                                }
                            }
                        }
                    }
                }
            },
    ) {
        for (line in 0 until GUIDE_ROWS) {
            val row = screen.scroll + line + 1
            if (row > rows.size) {
                break
            }
            val e = rows[row - 1]
            val top = line * ROW_HEIGHT
            for (ci in columns.indices) {
                val c = columns[ci]
                var display = ""
                if (ci == 0) {
                    display = e.level.toString()
                } else {
                    when (e.style) {
                        GuideStepStyle.BANK_AP -> if (ci == 1) display = "Bank " + e.bank + " AP"
                        GuideStepStyle.RESET_TREE -> if (ci == 1) display = "Reset " + screen.treeDisplay(e.guideTree)
                        GuideStepStyle.RESET_ALL_TREES -> if (ci == 1) display = "Reset All Trees"
                        GuideStepStyle.ENHANCEMENT -> display = when (ci) {
                            1 -> fitDisplay(e.display, e.rankText, c.width - space * 2) { measure(it) }
                            2 -> e.tier.toString()
                            3 -> screen.treeDisplay(e.guideTree)
                            4 -> e.cost.toString()
                            else -> e.spentInTree.toString()
                        }
                        GuideStepStyle.UNKNOWN -> {}
                    }
                }
                var cellBack = back
                if (ci == 1 && screen.isSelected(row)) {
                    cellBack = selectedBack
                }
                val color = if (ci == 1 && e.errorState) errorText else text
                Box(modifier = Modifier.offset((c.left + 1).dp, (top + 1).dp).size((c.width - 1).dp, (ROW_HEIGHT - 1).dp).background(cellBack)) {
                    val align = c.align
                    val modifier = if (align == TextAlign.Start) {
                        Modifier.offset(space.dp, 3.dp)
                    } else {
                        Modifier.fillMaxWidth().offset(0.dp, 3.dp)
                    }
                    BasicText(display, style = TextStyle(platformStyle = TEXT_RENDERING, fontSize = FONT_SIZE, color = color, textAlign = align), maxLines = 1, modifier = modifier)
                }
            }
        }
        // DrawBorders: interior lines round every cell, exterior lines on the outside and between levels.
        Canvas(modifier = Modifier.size(gridWidth.dp, GRID_HEIGHT.dp)) {
            val d = density
            for (line in 0 until GUIDE_ROWS) {
                val row = screen.scroll + line + 1
                val top = line * ROW_HEIGHT * d
                val bottom = top + ROW_HEIGHT * d
                for (c in columns) {
                    drawRect(interior, Offset(c.left * d, top), Size(c.width * d, ROW_HEIGHT * d), style = Stroke(1f))
                }
                val left = 0f
                val right = columns[5].right * d
                drawLine(exterior, Offset(left, top), Offset(left, bottom))
                drawLine(exterior, Offset(right, top), Offset(right, bottom))
                val count = rows.size
                if (row == 1 || row == count + 1 || (row in 2..count && rows[row - 1].level != rows[row - 2].level)) {
                    drawLine(exterior, Offset(left, top), Offset(right, top))
                }
                if (row == count || (row in 1 until count && rows[row - 1].level != rows[row].level)) {
                    drawLine(exterior, Offset(left, bottom), Offset(right, bottom))
                }
                if (row == screen.borderRow && row <= count) {
                    val c = columns[1]
                    drawRect(highlightBorder, Offset(c.left * d, top), Size(c.width * d, ROW_HEIGHT * d), style = Stroke(1f))
                }
            }
        }
    }
    GuideScrollBar(screen, theme, GRID_LEFT + gridWidth, GRID_TOP, GRID_HEIGHT)

    // The totals: lblGuideLevel, lblGuideAP (red when negative), lblGuideSpent (red past 4 AP a heroic level).
    val totals = screen.totals
    if (totals != null) {
        val error = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT_ERROR)
        val spentWidth = measure(totals.spentCaption())
        // Kotlin only: Level, Available and Spent right-aligned to the grid's outside edge, a gap apart
        // (VB has the level at the grid's left and centres Available under it).
        val spentLeft = GRID_LEFT + GRID_WIDTH - spentWidth
        val apLeft = spentLeft - ITEM_GAP - measure(totals.apCaption())
        val levelLeft = apLeft - ITEM_GAP - measure(totals.levelCaption())
        Label(totals.levelCaption(), label, levelLeft, TOTALS_TOP)
        val apStyle = if (totals.ap < 0) label.copy(color = error) else label
        BasicText(totals.apCaption(), style = apStyle, maxLines = 1, modifier = Modifier.offset(apLeft.dp, TOTALS_TOP.dp))
        val spentStyle = if (totals.spentError) label.copy(color = error) else label
        BasicText(totals.spentCaption(), style = spentStyle, maxLines = 1, modifier = Modifier.offset(spentLeft.dp, TOTALS_TOP.dp))
    }
}

/** FitDisplay (:3760): cuts the name with "..." until name and rank fit [width]. */
private fun fitDisplay(name: String, rank: String, width: Int, measure: (String) -> Int): String {
    var check = name + rank
    if (measure(check) > width) {
        var length = name.length + 1
        val trimmedRank = rank.trim()
        do {
            length--
            check = name.substring(0, maxOf(length, 0)) + "..." + trimmedRank
        } while (measure(check) > width && length > 0)
    }
    return check
}

/** scrollVertical: enabled with more than 20 rows; the arrows and the track move a row (VB's LargeChange is 1). */
@Composable
private fun GuideScrollBar(screen: GuideScreenState, theme: Theme, left: Int, top: Int, height: Int) {
    val count = screen.rows().size
    val enabled = count > GUIDE_ROWS
    val back = theme.color(ColorGroup.CONTROLS, ColorValue.BACKGROUND)
    val border = theme.color(ColorGroup.CONTROLS, ColorValue.BORDER_EXTERIOR)
    val arrow = theme.color(ColorGroup.CONTROLS, if (enabled) ColorValue.TEXT else ColorValue.TEXT_DIM)
    Box(modifier = Modifier.offset(left.dp, top.dp).size(SCROLL_BAR.dp, height.dp).background(back).border(1.dp, border)) {
        BasicText("▴", style = TextStyle(platformStyle = TEXT_RENDERING, fontSize = FONT_SIZE, color = arrow), modifier = Modifier.offset(4.dp, 0.dp).clickable(enabled = enabled) { screen.scrollBy(-1) })
        if (enabled) {
            val track = height - SCROLL_BAR * 2
            val thumb = maxOf(12, track * GUIDE_ROWS / count)
            val max = count - GUIDE_ROWS
            val thumbTop = SCROLL_BAR + (track - thumb) * screen.scroll / max
            Box(modifier = Modifier.offset(2.dp, SCROLL_BAR.dp).size((SCROLL_BAR - 4).dp, (thumbTop - SCROLL_BAR).dp).clickable { screen.scrollBy(-1) })
            Box(modifier = Modifier.offset(2.dp, thumbTop.dp).size((SCROLL_BAR - 4).dp, thumb.dp).background(border))
            Box(modifier = Modifier.offset(2.dp, (thumbTop + thumb).dp).size((SCROLL_BAR - 4).dp, (height - SCROLL_BAR - thumbTop - thumb).dp).clickable { screen.scrollBy(1) })
        }
        BasicText("▾", style = TextStyle(platformStyle = TEXT_RENDERING, fontSize = FONT_SIZE, color = arrow), modifier = Modifier.offset(4.dp, (height - SCROLL_BAR).dp).clickable(enabled = enabled) { screen.scrollBy(1) })
    }
}
