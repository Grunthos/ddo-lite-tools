package ddolite.builder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ddolite.common.theme.ColorGroup
import ddolite.common.theme.ColorValue

/** App.ProductName (CharacterBuilderLite.vbp, VersionProductName). */
const val APP_NAME = "Character Builder Lite"

/** The VB version this one is ported from (CharacterBuilderLite.vbp: MajorVer 4, MinorVer 15, RevisionVer 0). */
const val APP_VERSION = "v4.15"

/**
 * The About window (Source/Forms/frmAbout.frm): the program's name, version and authors, centred.
 * The client area is 262 x 139 at 100%. Form_Load sets the colours (cfg.RefreshColors) and the version
 * ("v" & Major & "." & Minor, plus "." & Revision when it isn't 0).
 */
@Composable
fun AboutWindowContent(state: BuilderSession) {
    val theme = state.skills.theme
    val style = TextStyle(fontSize = FONT_SIZE, color = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT), textAlign = TextAlign.Center)
    Box(modifier = Modifier.fillMaxSize().background(theme.color(ColorGroup.WORKSPACE, ColorValue.BACKGROUND))) {
        BasicText(APP_NAME, style = style, modifier = Modifier.offset(0.dp, 24.dp).width(265.dp))
        BasicText(APP_VERSION, style = style, modifier = Modifier.offset(0.dp, 48.dp).width(265.dp))
        BasicText("Written by Ellis Dee", style = style, modifier = Modifier.offset(0.dp, 96.dp).width(265.dp))
        BasicText("Updates by Chris Lewis", style = style, modifier = Modifier.offset(0.dp, 113.dp).width(265.dp))
    }
}
