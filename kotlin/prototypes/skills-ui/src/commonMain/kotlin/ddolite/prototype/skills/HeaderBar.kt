package ddolite.prototype.skills

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ddolite.common.theme.ColorGroup
import ddolite.common.theme.ColorValue
import ddolite.common.theme.Theme

/**
 * The strip of text links across the top (header) or bottom (footer) of every Edit-menu window.
 * Ported from the userHeader control (Source/Controls/userHeader.ctl), drawn in the theme's
 * Navigation colours as clsConfig.RefreshColors does for userHeader (`ApplyColors ctl, cgeNavigation`).
 *
 * - Header: [leftLinks] are the window's tabs (e.g. frmEnhancements "Trees;Enhancements;Leveling Guide");
 *   the current one, [currentTab], is underlined (userHeader with UseTabs = True). [rightLinks] are
 *   actions such as "Help".
 * - Footer: [currentTab] = null (UseTabs = False); the links go to the previous and next windows in the
 *   Edit-menu order, e.g. frmSkills "< Stats" and "Feats >".
 *
 * Links for which [isEnabled] returns false are drawn dim and do nothing (prototype only: the
 * windows they lead to don't exist yet).
 */
@Composable
fun HeaderBar(
    theme: Theme,
    leftLinks: List<String>,
    currentTab: Int?,
    rightLinks: List<String>,
    isEnabled: (String) -> Boolean,
    onClick: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.color(ColorGroup.NAVIGATION, ColorValue.BACKGROUND))
            .border(1.dp, theme.color(ColorGroup.NAVIGATION, ColorValue.BORDER_EXTERIOR))
            .padding(horizontal = 12.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            for (i in leftLinks.indices) {
                HeaderLink(theme, leftLinks[i], currentTab == i, isEnabled(leftLinks[i]), onClick)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            for (link in rightLinks) {
                HeaderLink(theme, link, false, isEnabled(link), onClick)
            }
        }
    }
}

/** One link: link colour, underlined when it's the current tab, dim when disabled. */
@Composable
private fun HeaderLink(theme: Theme, text: String, current: Boolean, enabled: Boolean, onClick: (String) -> Unit) {
    var color = theme.color(ColorGroup.NAVIGATION, ColorValue.TEXT_LINK)
    if (current) {
        color = theme.color(ColorGroup.NAVIGATION, ColorValue.TEXT)
    }
    if (!enabled) {
        color = theme.color(ColorGroup.NAVIGATION, ColorValue.TEXT_DIM)
    }
    var decoration = TextDecoration.None
    if (current) {
        decoration = TextDecoration.Underline
    }
    var modifier: Modifier = Modifier
    if (enabled) {
        modifier = modifier.clickable { onClick(text) }
    }
    Box(modifier = modifier) {
        BasicText(text, style = TextStyle(fontSize = 13.sp, color = color, textDecoration = decoration))
    }
}
