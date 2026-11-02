package ddolite.tools.formlayout

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage

/**
 * One picture of the form. Containers that sit on top of each other (like the tab pages
 * picTab(0), picTab(1)… in frmEnhancements) can't be shown in one picture, so each gets its own view.
 */
class WireframeView(val title: String, val skipped: Set<PlacedControl>)

/** Border colours for containers, one per nesting depth (repeats after the last). */
private val DEPTH_COLOURS = arrayOf(
    Color(0x20, 0x20, 0x20), // the form
    Color(0xD0, 0x30, 0x30), // red
    Color(0x20, 0x70, 0xD0), // blue
    Color(0x20, 0xA0, 0x40), // green
    Color(0xB0, 0x50, 0xC0), // purple
    Color(0xE0, 0x90, 0x10), // orange
)

/** Space around the form for its name tag and its row/column numbers. */
private const val FORM_MARGIN = 40

private fun depthColour(depth: Int): Color = DEPTH_COLOURS[depth % DEPTH_COLOURS.size]

/**
 * The views needed for a form: the main one, plus one for each container that overlaps an
 * earlier sibling container by more than half its area (shown in place of that sibling).
 */
fun wireframeViews(root: PlacedControl): List<WireframeView> {
    val alternates = ArrayList<PlacedControl>()
    val covers = HashMap<PlacedControl, ArrayList<PlacedControl>>()
    findAlternates(root, alternates, covers)
    val views = ArrayList<WireframeView>()
    views.add(WireframeView("main", HashSet(alternates)))
    for (alternate in alternates) {
        val skipped = HashSet<PlacedControl>(alternates)
        skipped.remove(alternate)
        val hidden = covers[alternate]
        if (hidden != null) {
            skipped.addAll(hidden)
        }
        views.add(WireframeView(alternate.control.displayName, skipped))
    }
    return views
}

private fun findAlternates(
    placed: PlacedControl,
    alternates: MutableList<PlacedControl>,
    covers: MutableMap<PlacedControl, ArrayList<PlacedControl>>,
) {
    val containers = ArrayList<PlacedControl>()
    for (child in placed.children) {
        if (child.children.isEmpty()) {
            continue
        }
        val overlapped = ArrayList<PlacedControl>()
        for (earlier in containers) {
            if (!alternates.contains(earlier) && mostlyOverlaps(child.bounds, earlier.bounds)) {
                overlapped.add(earlier)
            }
        }
        if (overlapped.isNotEmpty()) {
            alternates.add(child)
            covers[child] = overlapped
        }
        containers.add(child)
        findAlternates(child, alternates, covers)
    }
}

private fun mostlyOverlaps(a: PixelRect, b: PixelRect): Boolean {
    val w = minOf(a.right, b.right) - maxOf(a.left, b.left)
    val h = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
    if (w <= 0 || h <= 0) {
        return false
    }
    val smaller = minOf(a.width * a.height, b.width * b.height)
    return smaller > 0 && w * h * 2 > smaller
}

/**
 * Draws a view at 100% scale (1 pixel per VB pixel), to lay beside a screenshot of the real form.
 *
 * - Containers: a solid border, coloured by nesting depth, with their name in a tag at the top left.
 * - Each container's rows and columns: dashed lines in the container's colour, numbered r0, r1… and c0, c1…
 *   along its top and left edges.
 * - Other controls: a thin grey box with the name and caption.
 * - Controls that start hidden (Visible = False): dotted border.
 */
fun drawWireframe(root: PlacedControl, view: WireframeView): BufferedImage {
    val margin = FORM_MARGIN
    val image = BufferedImage(root.bounds.width + margin * 2, root.bounds.height + margin * 2, BufferedImage.TYPE_INT_RGB)
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.color = Color.WHITE
    g.fillRect(0, 0, image.width, image.height)
    g.translate(margin, margin)
    g.font = Font(Font.SANS_SERIF, Font.PLAIN, 10)
    drawPlaced(g, root, view)
    g.dispose()
    return image
}

private fun drawPlaced(g: Graphics2D, placed: PlacedControl, view: WireframeView) {
    val b = placed.bounds
    val isContainer = placed.children.isNotEmpty() || placed.control.isForm ||
        placed.control.type == "VB.Frame" || placed.control.type == "VB.PictureBox"
    if (!isContainer) {
        drawLeaf(g, placed)
        return
    }
    val colour = depthColour(placed.depth)
    val grid = placed.grid
    if (grid != null) {
        drawGridLines(g, placed, grid, colour)
    }
    for (child in placed.children) {
        if (!view.skipped.contains(child)) {
            drawPlaced(g, child, view)
        }
    }
    // The border and tag go on top, so children don't hide the hierarchy.
    g.color = colour
    g.stroke = borderStroke(placed.control.startsVisible, 2f)
    g.drawRect(b.left, b.top, b.width - 1, b.height - 1)
    // The form's tag goes above it, so it doesn't hide the header.
    var tagY = b.top
    if (placed.control.isForm) {
        tagY = b.top - FORM_MARGIN + 2
    }
    drawTag(g, b.left, tagY, placed.control.displayName + "  " + shortType(placed.control.type), colour)
}

private fun drawGridLines(g: Graphics2D, placed: PlacedControl, grid: ContainerGrid, colour: Color) {
    val b = placed.bounds
    val faint = Color(colour.red, colour.green, colour.blue, 110)
    g.stroke = BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, floatArrayOf(4f, 3f), 0f)
    for (i in grid.cols.indices) {
        val x = grid.cols[i]
        g.color = faint
        g.drawLine(x, b.top, x, b.bottom - 1)
        g.color = colour
        g.drawString("c$i", x + 2, b.top - 3)
    }
    for (i in grid.rows.indices) {
        val y = grid.rows[i]
        g.color = faint
        g.drawLine(b.left, y, b.right - 1, y)
        g.color = colour
        val label = "r$i"
        g.drawString(label, b.left - g.fontMetrics.stringWidth(label) - 3, y + 9)
    }
}

private fun drawLeaf(g: Graphics2D, placed: PlacedControl) {
    val b = placed.bounds
    val type = placed.control.type
    if (type == "VB.Shape" || type == "VB.Line") {
        // Outline only: shapes are usually drawn around other controls (frame lines, highlights).
        g.color = Color(0xA0, 0xA0, 0xA0)
        g.stroke = BasicStroke(1f)
        g.drawRect(b.left, b.top, maxOf(b.width - 1, 0), maxOf(b.height - 1, 0))
        return
    }
    g.color = leafFill(type)
    g.fillRect(b.left, b.top, b.width, b.height)
    g.color = Color(0x70, 0x70, 0x70)
    g.stroke = borderStroke(placed.control.startsVisible, 1f)
    g.drawRect(b.left, b.top, maxOf(b.width - 1, 0), maxOf(b.height - 1, 0))
    var text = placed.control.displayName
    val caption = placed.control.caption
    if (caption != null && caption.isNotEmpty() && !caption.contains(".frx")) {
        text = "$text \"$caption\""
    }
    val clip = g.clip
    g.clipRect(b.left + 1, b.top + 1, maxOf(b.width - 2, 0), maxOf(b.height - 2, 0))
    g.color = Color.BLACK
    g.drawString(text, b.left + 2, b.top + minOf(11, maxOf(b.height - 2, 8)))
    g.clip = clip
}

/** A name tag with a solid background, drawn just inside a container's top-left corner. */
private fun drawTag(g: Graphics2D, x: Int, y: Int, text: String, colour: Color) {
    val metrics = g.fontMetrics
    val w = metrics.stringWidth(text) + 6
    val h = metrics.height
    g.color = colour
    g.fillRect(x, y, w, h)
    g.color = Color.WHITE
    g.drawString(text, x + 3, y + metrics.ascent)
}

private fun borderStroke(visible: Boolean, width: Float): BasicStroke {
    if (visible) {
        return BasicStroke(width)
    }
    return BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, floatArrayOf(2f, 2f), 0f)
}

/** Light fills so different kinds of control can be told apart at a glance. */
private fun leafFill(type: String): Color {
    if (type == "VB.Label") {
        return Color(0xF4, 0xF4, 0xF4)
    }
    if (type == "VB.ListBox" || type == "VB.ComboBox" || type == "VB.TextBox") {
        return Color(0xE4, 0xF0, 0xFF)
    }
    if (type.startsWith("CharacterBuilderLite.")) {
        return Color(0xFF, 0xF2, 0xD8) // the app's own user controls
    }
    return Color(0xEC, 0xFF, 0xEC)
}

/** "VB.PictureBox" → "PictureBox", "CharacterBuilderLite.userList" → "userList". */
internal fun shortType(type: String): String {
    val dot = type.lastIndexOf('.')
    if (dot < 0) {
        return type
    }
    return type.substring(dot + 1)
}
