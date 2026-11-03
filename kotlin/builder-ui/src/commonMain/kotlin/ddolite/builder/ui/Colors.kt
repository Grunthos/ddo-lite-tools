package ddolite.builder.ui

import androidx.compose.ui.graphics.Color
import ddolite.common.theme.ColorGroup
import ddolite.common.theme.ColorValue
import ddolite.common.theme.Theme

/** A theme colour as a Compose [Color]; the equivalent of VB's `cfg.GetColor(group, value)`. */
fun Theme.color(group: ColorGroup, value: ColorValue): Color = Color(get(group, value))

/** How strongly class-skill cells stand out. VB's `ContrastEnum` (the Contrast option in frmOptions). */
enum class Contrast { NONE, LOW, MEDIUM, HIGH }

/** The two cell backgrounds on the skills grid. */
class CellFills(val native: Color, val cross: Color)

/**
 * Background colours for class-skill and cross-class cells. Ported from `frmSkills.SetFillColors`
 * (Source/Forms/Build/frmSkills.frm:1425): both start as the controls background, then one of them
 * is brightened by an amount that depends on how dark the background is. On dark themes class
 * skills get lighter; on very light themes the cross-class cells get darker instead.
 */
fun cellFills(theme: Theme, contrast: Contrast): CellFills {
    val background = theme.get(ColorGroup.CONTROLS, ColorValue.BACKGROUND)
    val base = Color(background)
    if (contrast == Contrast.NONE) {
        return CellFills(base, base)
    }
    val red = (background shr 16) and 0xFF
    val green = (background shr 8) and 0xFF
    val blue = background and 0xFF

    // VB: Select Case 256 - ((r + g + b) \ 3)  — i.e. how dark the background is.
    val darkness = 256 - (red + green + blue) / 3
    var factor: Double
    if (darkness <= 15) {
        factor = 0.975
    } else if (darkness <= 25) {
        factor = 1.03
    } else if (darkness <= 156) {
        factor = 1.05
    } else if (darkness <= 181) {
        factor = 1.1
    } else if (darkness <= 191) {
        factor = 1.2
    } else {
        factor = 1.25
    }
    if (contrast == Contrast.MEDIUM) {
        factor = 1 + (factor - 1) * 1.5
    } else if (contrast == Contrast.HIGH) {
        factor = 1 + (factor - 1) * 2
    }

    val shaded = Color(scale(red, factor), scale(green, factor), scale(blue, factor))
    if (factor > 1) {
        return CellFills(native = shaded, cross = base)
    }
    return CellFills(native = base, cross = shaded)
}

/** One colour channel times [factor], rounded as VB assigns a Double to a Long, kept within 0-255 as `RGB()` does. */
private fun scale(channel: Int, factor: Double): Int {
    val value = kotlin.math.round(channel * factor).toInt()
    if (value > 255) {
        return 255
    }
    return value
}
