package ddolite.builder.ui

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import ddolite.builder.rules.RankStatus
import ddolite.builder.rules.formatRanks
import ddolite.common.theme.ColorGroup
import ddolite.common.theme.ColorValue
import kotlinx.coroutines.Job

private const val SKILL_ROWS = 21
private const val LEVEL_COLUMNS = 20

/** The grid's font: picGrid.FontSize = 10 (InitGrid), 10 pt at 96 dpi. */
internal val GRID_FONT_SIZE = 13.33.sp

/**
 * Sizes of the grid, in pixels: the skill name column ([nameWidth], VB's mlngLeft), each level
 * column ([cellWidth], mlngWidth) and each row ([rowHeight], mlngHeight). The header is one row of
 * level numbers, with the classes above it if the build has more than one: a row of initials, or
 * of icons [iconSize] pixels square (mlngIconWidth), [iconOffset] in from the column's left. Then
 * 21 skill rows and two footer rows (points spent, points available); the column after level 20
 * holds each skill's total.
 */
data class GridLayout(
    val nameWidth: Float,
    val cellWidth: Float,
    val rowHeight: Float,
    val classRow: Boolean,
    val iconSize: Float,
    val iconOffset: Float,
) {
    /** Top of the first skill row (VB's mlngTop). */
    val top: Float = gridTop()

    private fun gridTop(): Float {
        if (!classRow) {
            return rowHeight
        }
        if (iconSize > 0) {
            return iconSize + rowHeight
        }
        return rowHeight * 2
    }

    val width: Float = nameWidth + cellWidth * (LEVEL_COLUMNS + 1)
    val height: Float = top + rowHeight * (SKILL_ROWS + 2)

    fun cellLeft(col: Int): Float = nameWidth + cellWidth * (col - 1)
    fun rowTop(row: Int): Float = top + rowHeight * (row - 1)

    /** The row under [y]: 0 for the header, 1-21 for skills, -1 outside. Ported from `frmSkills.ActiveCell` (:1719). */
    fun rowAt(y: Float): Int {
        if (y < 0) {
            return -1
        }
        if (y < top) {
            return 0
        }
        val row = ((y - top) / rowHeight).toInt() + 1
        if (row > SKILL_ROWS) {
            return -1
        }
        return row
    }

    /** The column under [x]: 0 for the skill names, 1-20 for levels, -1 outside. */
    fun colAt(x: Float): Int {
        if (x < 0) {
            return -1
        }
        if (x < nameWidth) {
            return 0
        }
        val col = ((x - nameWidth) / cellWidth).toInt() + 1
        if (col > LEVEL_COLUMNS) {
            return -1
        }
        return col
    }
}

/** The grid's place in the window and its sizes: picGrid.Move in InitGrid. */
data class WindowGrid(val left: Float, val top: Float, val layout: GridLayout)

/**
 * Sizes and places the grid to fill the space between the header and footer bars. Ported from
 * frmSkills.InitGrid (Source/Forms/Build/frmSkills.frm:1304):
 * - the skill names are as wide as "Use Magic Device  " in bold, each level column as "10½";
 * - icons are four fifths of a column wide, in whole pixels, and square;
 * - the rows share the height: 25 rows less the icons, 26 rows with initials, or 25 rows for one
 *   class (the grid is 24 rows or 25 with initials, centred in that space);
 * - the grid is centred across the window, then moved right by a quarter of the width of "½"
 *   (VB's lngFudge, so it looks centred without the totals column);
 * - if it's too wide for the window, the skill names get narrower (VB, 6/22/24).
 */
fun windowGridLayout(
    textMeasurer: TextMeasurer,
    classRow: Boolean,
    icons: Boolean,
    clientWidth: Float,
    headerHeight: Float,
    footerTop: Float,
): WindowGrid {
    val normal = TextStyle(platformStyle = TEXT_RENDERING, fontSize = GRID_FONT_SIZE)
    val bold = TextStyle(platformStyle = TEXT_RENDERING, fontSize = GRID_FONT_SIZE, fontWeight = FontWeight.Bold)
    var nameWidth = textMeasurer.measure("Use Magic Device  ", bold).size.width.toFloat()
    val cellWidth = textMeasurer.measure("10½", normal).size.width.toFloat()
    val fudge = (textMeasurer.measure("½", normal).size.width / 4).toFloat()
    val iconSize = (cellWidth * 4 / 5).toInt().toFloat()
    val iconOffset = ((cellWidth - iconSize) / 2).toInt().toFloat()
    var width = nameWidth + cellWidth * 21
    if (width + fudge > clientWidth) {
        nameWidth = clientWidth - (cellWidth * 21 + fudge)
        width = nameWidth + cellWidth * 21
    }
    val space = footerTop - headerHeight
    var rowHeight: Float
    if (classRow && icons) {
        rowHeight = ((space - iconSize) / 25).toInt().toFloat()
    } else if (classRow) {
        rowHeight = (space / 26).toInt().toFloat()
    } else {
        rowHeight = (space / 25).toInt().toFloat()
    }
    var iconRow = 0f
    if (classRow && icons) {
        iconRow = iconSize
    }
    val layout = GridLayout(nameWidth, cellWidth, rowHeight, classRow, iconRow, iconOffset)
    val left = ((clientWidth - width) / 2).toInt() + fudge
    val top = headerHeight + ((space - layout.height) / 2).toInt()
    return WindowGrid(left, top, layout)
}

/**
 * Fixed sizes for the single-window prototype on Android, which scrolls the grid instead of
 * fitting it to a window (no icons; initials when there's more than one class).
 */
fun compactGridLayout(textMeasurer: TextMeasurer, classRow: Boolean): GridLayout {
    val normal = TextStyle(platformStyle = TEXT_RENDERING, fontSize = GRID_FONT_SIZE)
    val bold = TextStyle(platformStyle = TEXT_RENDERING, fontSize = GRID_FONT_SIZE, fontWeight = FontWeight.Bold)
    val nameWidth = textMeasurer.measure("Use Magic Device  ", bold).size.width.toFloat()
    val cellWidth = textMeasurer.measure("10½", normal).size.width * 1.35f
    val rowHeight = textMeasurer.measure("Xg", normal).size.height * 1.45f
    return GridLayout(nameWidth, cellWidth, rowHeight, classRow, 0f, 0f)
}

/**
 * The skills grid: draws the build's skill ranks per level and handles the mouse. The `picGrid`
 * picture box on frmSkills; drawing follows `DrawGrid` (:1400) and the Draw* routines after it,
 * interaction goes to [SkillsScreenState]. [classIcons] (by [classResourceId]) are drawn above the
 * levels when [layout] has room for them. [onMenu] gets the pointer position when a right-click on a
 * skill name asks for its menu (picGrid_MouseDown's PopupMenu).
 */
@Composable
fun SkillsGrid(
    state: SkillsScreenState,
    layout: GridLayout,
    classIcons: Map<String, ImageBitmap>,
    modifier: Modifier = Modifier,
    onMenu: (Offset) -> Unit = {},
) {
    val textMeasurer = rememberTextMeasurer()
    val scope = rememberCoroutineScope()
    val theme = state.theme
    val fontSize = GRID_FONT_SIZE
    val normal = TextStyle(platformStyle = TEXT_RENDERING, fontSize = fontSize, color = Color.Unspecified)
    val bold = TextStyle(platformStyle = TEXT_RENDERING, fontSize = fontSize, fontWeight = FontWeight.Bold, color = Color.Unspecified)
    val density = LocalDensity.current
    val currentOnMenu by rememberUpdatedState(onMenu)

    // Reading these here makes the grid redraw when they change.
    val grid = state.grid
    val activeRow = state.activeRow
    val activeCol = state.activeCol

    val widthDp = ((layout.width + 2) / density.density).dp
    val heightDp = ((layout.height + 2) / density.density).dp

    Canvas(
        modifier = modifier
            .size(widthDp, heightDp)
            .pointerInput(state, layout) {
                var repeating: Job? = null
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val position = event.changes[0].position
                        val row = layout.rowAt(position.y)
                        val col = layout.colAt(position.x)
                        if (event.type == PointerEventType.Press) {
                            // From picGrid_MouseDown: act now, then repeat while held.
                            if (state.press(row, col, pressedButton(event))) {
                                repeating = startRepeating(scope) { state.repeat() }
                            }
                            if (state.menuRow != 0) {
                                currentOnMenu(position)
                            }
                            event.changes[0].consume()
                        } else if (event.type == PointerEventType.Release) {
                            // picGrid_MouseUp: stop the timers.
                            repeating?.cancel()
                            repeating = null
                        } else if (event.type == PointerEventType.Move) {
                            // picGrid_MouseMove: only follow the mouse while no button is held.
                            if (repeating == null) {
                                state.hover(row, col)
                            }
                        } else if (event.type == PointerEventType.Exit) {
                            // Form_MouseMove → ShowActiveCell -1, -1.
                            repeating?.cancel()
                            repeating = null
                            state.hover(-1, -1)
                        }
                    }
                }
            },
    ) {
        // The skill points themselves aren't Compose state, so read the revision number here:
        // that makes Compose redraw the grid after every edit.
        if (state.revision < 0) {
            return@Canvas
        }
        val fills = cellFills(theme, state.contrast)
        val wrkBackground = theme.color(ColorGroup.WORKSPACE, ColorValue.BACKGROUND)
        val wrkText = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT)

        drawRect(wrkBackground)

        // Column headers: the class (if more than one) as an icon or initials, then the level number.
        // From DrawColumnHeader (:1510).
        for (col in 1..grid.levels) {
            val style = if (col == activeCol) bold.copy(color = wrkText) else normal.copy(color = wrkText)
            val left = layout.cellLeft(col)
            var top = 0f
            if (layout.classRow) {
                val icon = classIcons[classResourceId(grid.classAt(col))]
                if (layout.iconSize > 0 && icon != null) {
                    // PaintPicture LoadResPicture(GetClassResourceID(...)), scaled to the icon size.
                    val size = layout.iconSize.toInt()
                    drawImage(
                        icon,
                        dstOffset = IntOffset((left + 1 + layout.iconOffset).toInt(), 0),
                        dstSize = IntSize(size, size),
                    )
                    top = layout.iconSize
                } else {
                    drawCentred(textMeasurer, state.classInitial(col), style, left, left + layout.cellWidth, layout.rowHeight, top)
                    top = layout.rowHeight
                }
            }
            drawCentred(textMeasurer, col.toString(), style, left, left + layout.cellWidth, layout.rowHeight, top)
        }

        // Skill names. From DrawRowHeader (:1460); names that don't fit use the short form.
        for (row in 1..SKILL_ROWS) {
            val skill = state.rowSkills[row - 1]
            val style = if (row == activeRow) bold.copy(color = wrkText) else normal.copy(color = wrkText)
            var name = skill.displayName
            if (textMeasurer.measure(name, bold).size.width > layout.nameWidth) {
                name = skill.abbreviation
            }
            val measured = textMeasurer.measure(name, style)
            drawText(measured, topLeft = Offset(2f, layout.rowTop(row) + (layout.rowHeight - measured.size.height) / 2f))
        }

        // Cells. From DrawCell (:1572). The active cell is drawn last so its highlighted border is on top.
        for (col in 1..grid.levels) {
            for (row in 1..SKILL_ROWS) {
                if (row != activeRow || col != activeCol) {
                    drawCell(state, layout, textMeasurer, normal, fills, row, col, active = false)
                }
            }
        }
        if (activeRow >= 1 && activeCol >= 1 && activeCol <= grid.levels) {
            drawCell(state, layout, textMeasurer, normal, fills, activeRow, activeCol, active = true)
        }

        // Skill totals, in the column after level 20. From DrawRowFooter (:1482).
        for (row in 1..SKILL_ROWS) {
            val skill = state.rowSkills[row - 1]
            val ranks = grid.totalRanks(state.spend, skill)
            if (ranks > 0) {
                val color = statusColor(theme, ColorGroup.WORKSPACE, grid.totalStatus(state.spend, skill))
                val left = layout.cellLeft(LEVEL_COLUMNS + 1)
                drawCentred(textMeasurer, formatRanks(ranks), normal.copy(color = color), left, left + layout.cellWidth, layout.rowHeight, layout.rowTop(row))
            }
        }

        // Points spent / available per level, under the grid. From DrawColumnFooter (:1551).
        for (col in 1..grid.levels) {
            val left = layout.cellLeft(col)
            val spentColor = statusColor(theme, ColorGroup.WORKSPACE, grid.pointsStatus(state.spend, col))
            val spentTop = layout.rowTop(SKILL_ROWS + 1)
            drawCentred(textMeasurer, grid.pointsSpent(state.spend, col).toString(), normal.copy(color = spentColor), left, left + layout.cellWidth, layout.rowHeight, spentTop)
            drawCentred(textMeasurer, grid.maxPoints(col).toString(), normal.copy(color = wrkText), left, left + layout.cellWidth, layout.rowHeight, spentTop + layout.rowHeight)
        }
    }
}

/** One cell: background, border, and the ranks gained at that level. From `frmSkills.DrawCell` (:1572). */
private fun DrawScope.drawCell(
    state: SkillsScreenState,
    layout: GridLayout,
    textMeasurer: TextMeasurer,
    normal: TextStyle,
    fills: CellFills,
    row: Int,
    col: Int,
    active: Boolean,
) {
    val theme = state.theme
    val grid = state.grid
    val skill = state.rowSkills[row - 1]
    val left = layout.cellLeft(col)
    val top = layout.rowTop(row)
    val cellSize = Size(layout.cellWidth, layout.rowHeight)

    // Background: class skills and cross-class skills differ (see cellFills).
    val fill = if (grid.native(skill, col) == 2) fills.native else fills.cross
    drawRect(fill, topLeft = Offset(left, top), size = cellSize)

    // Border: highlighted for the cell under the mouse, otherwise the interior colour,
    // with the exterior colour round the outside of the grid.
    var borderColor = theme.color(ColorGroup.CONTROLS, ColorValue.BORDER_INTERIOR)
    if (active) {
        borderColor = theme.color(ColorGroup.CONTROLS, ColorValue.BORDER_HIGHLIGHT)
    }
    drawRect(borderColor, topLeft = Offset(left + 0.5f, top + 0.5f), size = cellSize, style = Stroke(1f))
    if (!active) {
        val outside = theme.color(ColorGroup.CONTROLS, ColorValue.BORDER_EXTERIOR)
        val right = left + layout.cellWidth + 0.5f
        val bottom = top + layout.rowHeight + 0.5f
        if (row == 1) {
            drawLine(outside, Offset(left, top + 0.5f), Offset(right, top + 0.5f))
        }
        if (row == SKILL_ROWS) {
            drawLine(outside, Offset(left, bottom), Offset(right, bottom))
        }
        if (col == 1) {
            drawLine(outside, Offset(left + 0.5f, top), Offset(left + 0.5f, bottom))
        }
        if (col == grid.levels) {
            drawLine(outside, Offset(right, top), Offset(right, bottom))
        }
    }

    // Ranks gained here, coloured by the running total against this level's cap.
    val ranks = grid.ranks(state.spend, skill, col)
    if (ranks == 0) {
        return
    }
    val color = statusColor(theme, ColorGroup.CONTROLS, grid.cellStatus(state.spend, skill, col))
    drawCentred(textMeasurer, formatRanks(ranks), normal.copy(color = color), left, left + layout.cellWidth, layout.rowHeight, top)
}

/** The text colour VB uses for each [RankStatus]: dim, normal or error. */
private fun statusColor(theme: ddolite.common.theme.Theme, group: ColorGroup, status: RankStatus): Color {
    if (status == RankStatus.OVER) {
        return theme.color(group, ColorValue.TEXT_ERROR)
    }
    if (status == RankStatus.UNDER) {
        return theme.color(group, ColorValue.TEXT_DIM)
    }
    return theme.color(group, ColorValue.TEXT)
}
