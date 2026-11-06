package ddolite.builder.ui

import androidx.compose.ui.text.TextStyle
import ddolite.common.theme.ColorGroup
import ddolite.common.theme.ColorValue
import ddolite.common.theme.Theme

/*
 * The text styles the windows share, in the theme's colours. Kotlin only: VB sets each control's
 * colours from the theme as the form loads (cfg.RefreshColors).
 */

/** A label on the window's background: the workspace text colour. */
internal fun labelStyle(theme: Theme): TextStyle =
    TextStyle(platformStyle = TEXT_RENDERING, fontSize = FONT_SIZE, color = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT))

/** Text inside a control (a list, text box or drop-down): the controls' text colour. */
internal fun controlStyle(theme: Theme): TextStyle =
    TextStyle(platformStyle = TEXT_RENDERING, fontSize = FONT_SIZE, color = theme.color(ColorGroup.CONTROLS, ColorValue.TEXT))

/** A label in the workspace's error colour when [error] (the AP and spent lines), else as [labelStyle]. */
internal fun captionStyle(theme: Theme, error: Boolean): TextStyle {
    if (error) {
        return labelStyle(theme).copy(color = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT_ERROR))
    }
    return labelStyle(theme)
}
