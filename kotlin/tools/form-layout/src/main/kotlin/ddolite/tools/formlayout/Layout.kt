package ddolite.tools.formlayout

import kotlin.math.roundToInt

/** Twips per pixel at 96 DPI (100% scaling), as VB's Screen.TwipsPerPixelX reports it. */
const val TWIPS_PER_PIXEL = 15

/** A rectangle in pixels, relative to the form's client area. */
class PixelRect(val left: Int, val top: Int, val width: Int, val height: Int) {
    val right: Int
        get() = left + width
    val bottom: Int
        get() = top + height
}

/** A control with its absolute position worked out, and its place in its container's grid. */
class PlacedControl(
    val control: FrmControl,
    val parent: PlacedControl?,
    /** How many containers it sits inside (0 for the form, 1 for the form's own controls). */
    val depth: Int,
    /** Where it is on the form, in pixels. */
    val bounds: PixelRect,
) {
    val children = ArrayList<PlacedControl>()

    /** Row and column in the parent's [ContainerGrid], 0-based; spans are at least 1. */
    var row = 0
    var col = 0
    var rowSpan = 1
    var colSpan = 1

    /** The grid of this control's own children; null if it has no visible children. */
    var grid: ContainerGrid? = null
}

/**
 * Rows and columns inferred from where a container's children start.
 * Each entry is a pixel position relative to the form, so they line up with [PlacedControl.bounds].
 */
class ContainerGrid(val rows: List<Int>, val cols: List<Int>) {
    /** Children whose edges are close to a row/column line but not exactly on it (e.g. 5520 vs 5530 twips). */
    val nearMisses = ArrayList<String>()
}

/**
 * Edges closer than this (in pixels) count as the same row or column. VB forms were laid out
 * by hand in the IDE, so controls meant to line up are often a pixel or two apart.
 */
const val ALIGN_TOLERANCE = 3

/**
 * Works out absolute positions for every visible control on the form, then groups each
 * container's children into rows and columns by their left and top edges.
 *
 * New code (plan step F0): it turns the IDE's free-form twip positions into a grid that a
 * Compose layout can follow. Approximations: a PictureBox's border is taken as 1 pixel
 * (flat) or 2 pixels (3D); Frame children are measured from the frame's outer corner, as VB does.
 * Positions that code sets at run time aren't applied; [findRuntimeMoves] lists them.
 */
fun placeControls(form: FrmControl): PlacedControl {
    val width = pixels(firstInt(form, "ScaleWidth", "ClientWidth"))
    val height = pixels(firstInt(form, "ScaleHeight", "ClientHeight"))
    val root = PlacedControl(form, null, 0, PixelRect(0, 0, width, height))
    placeChildren(root)
    return root
}

private fun placeChildren(placed: PlacedControl) {
    val inset = clientInset(placed.control)
    for (child in placed.control.children) {
        if (child.isNonVisual) {
            continue
        }
        val bounds = childBounds(child, placed.bounds.left + inset, placed.bounds.top + inset)
        val childPlaced = PlacedControl(child, placed, placed.depth + 1, bounds)
        placed.children.add(childPlaced)
        placeChildren(childPlaced)
    }
    if (placed.children.isNotEmpty()) {
        placed.grid = buildGrid(placed.children)
    }
}

/** A control's rectangle on the form, given where its container's client area starts. */
private fun childBounds(control: FrmControl, originX: Int, originY: Int): PixelRect {
    if (control.isLine) {
        val x1 = pixels(control.intProperty("X1"))
        val y1 = pixels(control.intProperty("Y1"))
        val x2 = pixels(control.intProperty("X2"))
        val y2 = pixels(control.intProperty("Y2"))
        val left = minOf(x1, x2)
        val top = minOf(y1, y2)
        return PixelRect(originX + left, originY + top, maxOf(x1, x2) - left + 1, maxOf(y1, y2) - top + 1)
    }
    val left = pixels(control.intProperty("Left"))
    val top = pixels(control.intProperty("Top"))
    return PixelRect(originX + left, originY + top, pixels(control.intProperty("Width")), pixels(control.intProperty("Height")))
}

/**
 * How far a container's client area is inset from its outer edge, in pixels.
 * PictureBox defaults to a 3D fixed-single border (VB leaves default properties out of the file).
 */
private fun clientInset(control: FrmControl): Int {
    if (control.type != "VB.PictureBox") {
        return 0
    }
    val borderStyle = control.intProperty("BorderStyle")
    if (borderStyle == 0) {
        return 0
    }
    if (control.intProperty("Appearance") == 0) {
        return 1
    }
    return 2
}

/**
 * Groups the children's left edges into columns and top edges into rows, then gives each child
 * its row, column and spans. A child spans every column line that falls inside its width.
 */
private fun buildGrid(children: List<PlacedControl>): ContainerGrid {
    val lefts = ArrayList<Int>()
    val tops = ArrayList<Int>()
    for (child in children) {
        lefts.add(child.bounds.left)
        tops.add(child.bounds.top)
    }
    val grid = ContainerGrid(clusterEdges(tops), clusterEdges(lefts))
    for (child in children) {
        child.col = nearestLine(grid.cols, child.bounds.left)
        child.row = nearestLine(grid.rows, child.bounds.top)
        child.colSpan = linesInside(grid.cols, child.col, child.bounds.right)
        child.rowSpan = linesInside(grid.rows, child.row, child.bounds.bottom)
        val colLine = grid.cols[child.col]
        if (colLine != child.bounds.left) {
            grid.nearMisses.add(child.control.displayName + " left " + child.bounds.left + " vs column " + colLine)
        }
        val rowLine = grid.rows[child.row]
        if (rowLine != child.bounds.top) {
            grid.nearMisses.add(child.control.displayName + " top " + child.bounds.top + " vs row " + rowLine)
        }
    }
    return grid
}

/**
 * Sorts the edges and merges any within [ALIGN_TOLERANCE] of the previous one into a single line.
 * Each line is placed at its most common edge (the smallest, if tied), so most controls sit exactly on it.
 */
internal fun clusterEdges(edges: List<Int>): List<Int> {
    val sorted = ArrayList(edges)
    sorted.sort()
    val result = ArrayList<Int>()
    var group = ArrayList<Int>()
    for (edge in sorted) {
        if (group.isNotEmpty() && edge - group[group.size - 1] > ALIGN_TOLERANCE) {
            result.add(mostCommon(group))
            group = ArrayList()
        }
        group.add(edge)
    }
    if (group.isNotEmpty()) {
        result.add(mostCommon(group))
    }
    return result
}

private fun mostCommon(values: List<Int>): Int {
    var best = values[0]
    var bestCount = 0
    for (value in values) {
        var count = 0
        for (other in values) {
            if (other == value) {
                count++
            }
        }
        if (count > bestCount) {
            best = value
            bestCount = count
        }
    }
    return best
}

private fun nearestLine(lines: List<Int>, edge: Int): Int {
    var best = 0
    for (i in lines.indices) {
        if (Math.abs(lines[i] - edge) < Math.abs(lines[best] - edge)) {
            best = i
        }
    }
    return best
}

/** How many lines, starting at [first], fall before [end] (allowing for the tolerance); at least 1. */
private fun linesInside(lines: List<Int>, first: Int, end: Int): Int {
    var count = 1
    for (i in first + 1 until lines.size) {
        if (lines[i] < end - ALIGN_TOLERANCE) {
            count++
        }
    }
    return count
}

private fun firstInt(control: FrmControl, name: String, fallback: String): Int? {
    val value = control.intProperty(name)
    if (value != null) {
        return value
    }
    return control.intProperty(fallback)
}

/** Twips to whole pixels at 100% scaling; a missing value counts as 0 (VB's default). */
internal fun pixels(twips: Int?): Int {
    if (twips == null) {
        return 0
    }
    return (twips.toDouble() / TWIPS_PER_PIXEL).roundToInt()
}

/** A line in the form's code that sets a control's position or size at run time. */
class RuntimeMove(val line: Int, val target: String, val code: String)

/**
 * Finds code that moves or resizes controls (`.Move`, `.Left =`, `.Top =`, `.Width =`, `.Height =`),
 * so the report can say which design-time positions are only a starting point.
 * Inside a `With x` block, a leading `.` is taken to mean x.
 */
fun findRuntimeMoves(file: FrmFile): List<RuntimeMove> {
    val result = ArrayList<RuntimeMove>()
    val withStack = ArrayList<String>()
    val pattern = Regex("""([A-Za-z_][\w.()]*)?\.(Move\b|(Left|Top|Width|Height)\s*=)""")
    for (i in file.codeLines.indices) {
        val raw = file.codeLines[i]
        val code = stripComment(raw).trim()
        if (code.isEmpty()) {
            continue
        }
        if (code.startsWith("With ")) {
            withStack.add(code.substring(5).trim())
            continue
        }
        if (code == "End With") {
            if (withStack.isNotEmpty()) {
                withStack.removeAt(withStack.size - 1)
            }
            continue
        }
        val match = pattern.find(code)
        if (match == null) {
            continue
        }
        var target = match.groupValues[1]
        if (target.isEmpty()) {
            if (withStack.isEmpty()) {
                target = "?"
            } else {
                target = withStack[withStack.size - 1]
            }
        }
        if (target.startsWith("Me.")) {
            target = target.substring(3)
        }
        result.add(RuntimeMove(file.codeStartLine + i, target, code))
    }
    return result
}

/** Removes a trailing VB comment, ignoring apostrophes inside strings. */
private fun stripComment(line: String): String {
    var inString = false
    for (i in line.indices) {
        val c = line[i]
        if (c == '"') {
            inString = !inString
        } else if (c == '\'' && !inString) {
            return line.substring(0, i)
        }
    }
    return line
}
