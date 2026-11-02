package ddolite.tools.formlayout

/**
 * One control (or the form itself, or a menu item) from the design-time section of a VB6 .frm file.
 *
 * Positions are as stored in the file: twips, relative to the parent container's client area.
 * [Layout] works out absolute positions from these.
 */
class FrmControl(
    /** e.g. "VB.Label", "CharacterBuilderLite.userSpinner". */
    val type: String,
    val name: String,
    /** The line in the .frm file where its `Begin` is. */
    val line: Int,
    val parent: FrmControl?,
) {
    /** Every simple property, by name, with the value as written (quotes and comments removed). */
    val properties = LinkedHashMap<String, String>()
    val children = ArrayList<FrmControl>()

    /** Control array index, or null for a control that isn't in an array. */
    val index: Int?
        get() = intProperty("Index")

    /** "name" or "name(index)", as VB code refers to it. */
    val displayName: String
        get() {
            val i = index
            if (i == null) {
                return name
            }
            return "$name($i)"
        }

    val isForm: Boolean
        get() = type == "VB.Form"

    val isMenu: Boolean
        get() = type == "VB.Menu"

    /** VB.Line stores its ends (X1, Y1, X2, Y2) instead of Left/Top/Width/Height. */
    val isLine: Boolean
        get() = type == "VB.Line"

    /**
     * True for controls with no size on screen: menus, timers, and anything else the file gives
     * no Width or Height for (VB leaves out properties that have their default value, and
     * invisible-at-runtime controls such as Timer have no size properties at all).
     */
    val isNonVisual: Boolean
        get() {
            if (isMenu || type == "VB.Timer") {
                return true
            }
            if (isForm || isLine) {
                return false
            }
            return properties["Width"] == null && properties["Height"] == null
        }

    /** False when the file sets `Visible = 0` (the control starts hidden; code may show it). */
    val startsVisible: Boolean
        get() = intProperty("Visible") != 0

    /** Caption or Text, for identifying the control in reports. */
    val caption: String?
        get() {
            val c = properties["Caption"]
            if (c != null) {
                return c
            }
            return properties["Text"]
        }

    /** A property as a whole number, or null if it's missing or not a number. */
    fun intProperty(name: String): Int? {
        val value = properties[name]
        if (value == null) {
            return null
        }
        return value.toIntOrNull()
    }
}

/** A parsed .frm file: the form's control tree, plus the code section that follows it. */
class FrmFile(
    val form: FrmControl,
    /** The code after the design-time section, one entry per line. */
    val codeLines: List<String>,
    /** The 1-based .frm line number of codeLines[0]. */
    val codeStartLine: Int,
)

/**
 * Reads the design-time section of a VB6 .frm file: the nested `Begin <type> <name>` …
 * `End` blocks that the VB IDE writes, with one `Name = Value` property per line.
 *
 * `BeginProperty` … `EndProperty` blocks (fonts, images) are skipped: the layout doesn't need them.
 * New code: a tool for plan step F0, no VB equivalent (VB's IDE reads these files itself).
 */
fun parseFrm(text: String): FrmFile {
    val lines = text.split("\n")
    var form: FrmControl? = null
    var current: FrmControl? = null
    var propertyDepth = 0
    var i = 0
    while (i < lines.size) {
        val line = lines[i].trimEnd('\r').trim()
        val lineNumber = i + 1
        i++
        if (line.startsWith("Attribute VB_Name")) {
            break // the design-time section is over; the code follows
        }
        if (propertyDepth > 0) {
            if (line.startsWith("BeginProperty")) {
                propertyDepth++
            } else if (line.startsWith("EndProperty")) {
                propertyDepth--
            }
            continue
        }
        if (line.startsWith("BeginProperty")) {
            propertyDepth = 1
            continue
        }
        if (line.startsWith("Begin ")) {
            val parts = line.split(Regex("\\s+"))
            if (parts.size < 3) {
                continue
            }
            val control = FrmControl(parts[1], parts[2], lineNumber, current)
            if (current == null) {
                form = control
            } else {
                current.children.add(control)
            }
            current = control
            continue
        }
        if (line == "End") {
            if (current != null) {
                current = current.parent
            }
            continue
        }
        if (current != null) {
            val equals = line.indexOf('=')
            if (equals > 0) {
                val key = line.substring(0, equals).trim()
                current.properties[key] = propertyValue(line.substring(equals + 1))
            }
        }
    }
    if (form == null) {
        throw IllegalArgumentException("No 'Begin VB.Form' found")
    }
    val code = ArrayList<String>()
    val codeStart = i + 1
    while (i < lines.size) {
        code.add(lines[i].trimEnd('\r'))
        i++
    }
    return FrmFile(form, code, codeStart)
}

/**
 * The value part of a property line: a quoted string (VB doubles any quote inside it), or
 * everything up to a `'` comment, e.g. `0   'False` → "0".
 */
internal fun propertyValue(raw: String): String {
    val value = raw.trim()
    if (value.startsWith("\"")) {
        val result = StringBuilder()
        var i = 1
        while (i < value.length) {
            val c = value[i]
            if (c == '"') {
                if (i + 1 < value.length && value[i + 1] == '"') {
                    result.append('"')
                    i += 2
                    continue
                }
                break
            }
            result.append(c)
            i++
        }
        return result.toString()
    }
    val comment = value.indexOf('\'')
    if (comment >= 0) {
        return value.substring(0, comment).trim()
    }
    return value
}
