package ddolite.prototype.skills

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import ddolite.builder.imports.ForumImport
import ddolite.common.theme.ColorGroup
import ddolite.common.theme.ColorValue

/**
 * The Import window's state (Source/Forms/Build/frmImport.frm): the pasted text and the translation.
 * The rules are [ForumImport]'s. The platform supplies [checkDirty] (VB's CheckDirty: true if the
 * user cancelled at "Save changes?"), [onImported] (the build has no file now: ClearBuild's
 * mstrFile) and [onFailed] (BuildClose's windows).
 */
class ImportScreenState(private val session: PrototypeState) {
    var isOpen by mutableStateOf(false)
        private set

    /** txtImport. */
    var text by mutableStateOf("")

    var checkDirty: () -> Boolean = { false }
    var onImported: () -> Unit = {}
    var onFailed: () -> Unit = {}

    /** OpenForm "frmImport". */
    fun open() {
        isOpen = true
    }

    fun close() {
        isOpen = false
    }

    /** "Paste from Clipboard" (lnkNav_MouseDown, :250): the clipboard's text, or empty if it has none. */
    fun paste(clipboard: String?) {
        text = clipboard ?: ""
    }

    /**
     * "Translate Build": BeginTranslate (:268). The new build replaces the open one, unsaved; if the
     * text can't be read VB says so and closes the build (BuildClose).
     */
    fun translate() {
        if (text.isEmpty()) {
            session.prompts.notice("Nothing to translate.")
            return
        }
        if (checkDirty()) {
            return
        }
        val result = ForumImport(session.data, session.settings.buildPoints, outputSettings(session)).translate(text)
        onImported()
        if (result == null) {
            session.prompts.notice("Invalid or Incomplete build text. Be sure you copied the entire build into the clipboard.")
            session.closeBuild()
            onFailed()
            return
        }
        session.importBuild(result)
    }
}

/**
 * The Import window (frmImport): links along the top (Paste from Clipboard, Translate Build, Help)
 * and the text below, Courier New 9pt, editable. Laid out from the form: the client area is 815 x 518
 * at 100%. [onPaste] reads the platform's clipboard (null if it has no text).
 */
@Composable
fun ImportWindowContent(state: PrototypeState, onPaste: () -> String?) {
    val screen = state.imports
    val theme = state.skills.theme
    val link = TextStyle(fontSize = FONT_SIZE, color = theme.color(ColorGroup.NAVIGATION, ColorValue.TEXT_LINK))
    // Form_Resize: the text starts under the bar, half a bar lower when the bar and box are the same colour.
    val sameColour = theme.get(ColorGroup.NAVIGATION, ColorValue.BACKGROUND) == theme.get(ColorGroup.CONTROLS, ColorValue.BACKGROUND)
    val textTop = if (sameColour) 39 else 26

    Box(modifier = Modifier.fillMaxSize().background(theme.color(ColorGroup.CONTROLS, ColorValue.BACKGROUND))) {
        // shpNav and its links.
        Box(modifier = Modifier.fillMaxWidth().size(815.dp, 26.dp).background(theme.color(ColorGroup.NAVIGATION, ColorValue.BACKGROUND)))
        BasicText("Paste from Clipboard", style = link, modifier = Modifier.offset(18.dp, 6.dp).clickable { screen.paste(onPaste()) })
        BasicText("Translate Build", style = link, modifier = Modifier.offset(193.dp, 6.dp).clickable { screen.translate() })
        BasicText("Help", style = link, modifier = Modifier.offset(768.dp, 6.dp).clickable { state.help.show("Import") })

        // shpBorder with txtImport inside it.
        Box(
            modifier = Modifier.offset(0.dp, textTop.dp).fillMaxSize()
                .border(1.dp, theme.color(ColorGroup.CONTROLS, ColorValue.BORDER_EXTERIOR))
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState()),
        ) {
            BasicTextField(
                value = screen.text,
                onValueChange = { screen.text = it },
                textStyle = TextStyle(fontSize = FONT_SIZE, fontFamily = FontFamily.Monospace, color = theme.color(ColorGroup.CONTROLS, ColorValue.TEXT)),
                cursorBrush = SolidColor(theme.color(ColorGroup.CONTROLS, ColorValue.TEXT)),
                modifier = Modifier.offset(2.dp, 2.dp),
            )
        }
    }
}
