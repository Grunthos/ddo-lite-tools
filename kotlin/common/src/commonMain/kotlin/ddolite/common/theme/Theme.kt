package ddolite.common.theme

import ddolite.dataformat.Vb

// Colour themes: the .screen and .output files in Settings/. Ported from the colour handling
// in Source/Classes/clsConfig.cls, which all three VB6 apps share.
//
// No UI dependency here, so desktop and mobile can both use it; the UI turns the ARGB numbers
// into its own colour type.

/**
 * Which part of the screen a colour is for. Ported from `ColorGroupEnum` (clsConfig.cls:107).
 * [prefix] is the start of the setting name in theme files ("WrkText", "CtlBackground", …).
 */
enum class ColorGroup(val prefix: String) {
    /** Window backgrounds, labels and headings. */
    WORKSPACE("Wrk"),

    /** Controls: lists, grids, spinners. */
    CONTROLS("Ctl"),

    /** Header and footer navigation bars. */
    NAVIGATION("Nav"),

    /** Drag-and-drop slots. */
    DROP_SLOTS("Drp"),

    /** The build output on the main window (only in .output files). */
    OUTPUT("Out"),
}

/**
 * A named colour within a group. Ported from `ColorValueEnum` (clsConfig.cls:115).
 * [settingName] is how it's written in theme files, after the group prefix.
 */
enum class ColorValue(val settingName: String) {
    BACKGROUND("Background"),
    BACK_HIGHLIGHT("BackHighlight"),
    BACK_ERROR("BackError"),

    /** VB calls this cveBackRelated, but theme files call it "BackSub". */
    BACK_RELATED("BackSub"),
    TEXT("Text"),
    TEXT_ERROR("TextError"),
    TEXT_DIM("TextDim"),
    TEXT_LINK("TextLink"),
    BORDER_INTERIOR("BorderInt"),
    BORDER_EXTERIOR("BorderExt"),
    BORDER_HIGHLIGHT("BorderHighlight"),

    /** VB calls this cveLightGray; theme files call it "Gray". */
    LIGHT_GRAY("Gray"),
    RED("Red"),
    YELLOW("Yellow"),
    BLUE("Blue"),
    PURPLE("Purple"),
    GREEN("Green"),
    ORANGE("Orange"),
}

/** Which groups a theme file may set. Ported from `ColorFileEnum` (clsConfig.cls:101). */
enum class ThemeFileKind {
    /** A .screen file: every group except OUTPUT. */
    SCREEN,

    /** A .output file: only OUTPUT. */
    OUTPUT,

    /** Anything else: all groups. */
    ALL,
}

/**
 * A full set of colours: one ARGB value (0xAARRGGBB) per group and colour.
 * Replaces clsConfig's `mlngColor(group, color)` array and `GetColor` (clsConfig.cls:2421).
 */
class Theme(val name: String) {
    private val colors = Array(ColorGroup.entries.size) { IntArray(ColorValue.entries.size) }

    init {
        // Mid grey until a theme file sets a colour. (VB starts from defaults built into
        // clsConfig.SetDefaults; every shipped theme sets every colour, so this rarely shows.)
        for (group in ColorGroup.entries) {
            for (value in ColorValue.entries) {
                colors[group.ordinal][value.ordinal] = argb(128, 128, 128)
            }
        }
    }

    /** `cfg.GetColor(group, color)`, as ARGB. */
    fun get(group: ColorGroup, value: ColorValue): Int = colors[group.ordinal][value.ordinal]

    fun set(group: ColorGroup, value: ColorValue, argb: Int) {
        colors[group.ordinal][value.ordinal] = argb
    }
}

/** The result of reading a theme file. */
class ThemeLoadResult(
    val theme: Theme,
    /** False if the file set no colours at all; VB then says "<file> is not a valid color file". */
    val valid: Boolean,
    /** Lines with a Windows system colour name ("Button Face" …), which aren't supported yet. */
    val unsupported: List<String>,
)

/**
 * Reads a theme file's text into [into] (by default a new theme). Ported from
 * `clsConfig.LoadColors` (clsConfig.cls:948); the version check and old-file clean-up at its
 * start are left out, as they only matter when upgrading old VB6 installs.
 *
 * Each line is `<Group><Color>=<value>`, e.g. `CtlText=235,235,235`. Unknown lines are ignored, as in VB.
 */
fun loadTheme(text: String, name: String, kind: ThemeFileKind, into: Theme = Theme(name)): ThemeLoadResult {
    var valid = false
    val unsupported = ArrayList<String>()
    for (line in text.split("\n")) {
        val equals = line.indexOf('=')
        if (line.isEmpty() || equals < 0) {
            continue
        }
        // clsConfig.ParseLine (clsConfig.cls:1105): "field=value", both trimmed.
        val field = Vb.trim(line.substring(0, equals)).lowercase()
        val value = Vb.trim(line.substring(equals + 1)).trimEnd('\r')

        val group = findGroup(field)
        val color = findColorValue(field)
        if (group == null || color == null) {
            continue
        }
        valid = true // VB counts the file as valid once any colour setting is recognised

        var apply = true
        if (kind == ThemeFileKind.SCREEN) {
            apply = group != ColorGroup.OUTPUT
        } else if (kind == ThemeFileKind.OUTPUT) {
            apply = group == ColorGroup.OUTPUT
        }
        if (!apply) {
            continue
        }
        val argb = colorToArgb(value)
        if (argb != null) {
            into.set(group, color, argb)
        } else if (SYSTEM_COLOR_NAMES.contains(value)) {
            unsupported.add(line)
        }
    }
    return ThemeLoadResult(into, valid, unsupported)
}

/** The group from the first three letters of a setting name. Part of `clsConfig.ParseColor` (clsConfig.cls:1014). */
private fun findGroup(field: String): ColorGroup? {
    if (field.length < 4) {
        return null
    }
    val prefix = field.substring(0, 3)
    for (group in ColorGroup.entries) {
        if (prefix == group.prefix.lowercase()) {
            return group
        }
    }
    return null
}

/** The colour from the rest of a setting name. Part of `clsConfig.ParseColor`. */
private fun findColorValue(field: String): ColorValue? {
    if (field.length < 4) {
        return null
    }
    val rest = field.substring(3)
    for (value in ColorValue.entries) {
        if (rest == value.settingName.lowercase()) {
            return value
        }
    }
    return null
}

/**
 * A colour setting's value as ARGB, or null if it isn't one. Ported from
 * `clsConfig.ColorToValue` (clsConfig.cls:1052):
 * - "R,G,B" with each part read by `Val`;
 * - a plain number, which VB treats as a Windows colour value (0x00BBGGRR);
 * - a Windows system colour name: not supported yet (null).
 */
fun colorToArgb(value: String): Int? {
    if (value.contains(',')) {
        val parts = value.split(",")
        if (parts.size != 3) {
            return null
        }
        return argb(
            clampByte(Vb.cLng(Vb.valOf(parts[0]))),
            clampByte(Vb.cLng(Vb.valOf(parts[1]))),
            clampByte(Vb.cLng(Vb.valOf(parts[2]))),
        )
    }
    if (Vb.isNumeric(value)) {
        val bgr = Vb.cLng(Vb.valOf(value))
        if (bgr < 0) {
            return null // a VB system colour constant (vbButtonFace etc.); not supported yet
        }
        return argb(bgr and 0xFF, (bgr shr 8) and 0xFF, (bgr shr 16) and 0xFF)
    }
    return null
}

/** Opaque ARGB from red, green and blue (0-255). */
fun argb(red: Int, green: Int, blue: Int): Int = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue

/** VB's `RGB()` uses 255 for any part over 255; parts below 0 become 0 here (VB would raise an error). */
private fun clampByte(v: Int): Int {
    if (v < 0) {
        return 0
    }
    if (v > 255) {
        return 255
    }
    return v
}

/** The system colour names `ColorToValue` accepts (clsConfig.cls:1073-1088). */
private val SYSTEM_COLOR_NAMES = listOf(
    "Button Text", "Button Face", "Button Shadow", "Window Text", "Window Background",
    "Window Frame", "Application Workspace", "Gray Text", "Inactive Caption Text", "Menu Text",
    "Highlight Text", "Highlight", "Active Border", "Inactive Border", "Info Text", "Info Background",
)
