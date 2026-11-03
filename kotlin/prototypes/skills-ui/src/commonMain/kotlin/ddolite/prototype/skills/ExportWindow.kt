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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import ddolite.builder.output.OutputView
import ddolite.common.theme.ColorGroup
import ddolite.common.theme.ColorValue
import kotlinx.coroutines.delay

/** The section combo's items (cboSection, frmExport.frx), with the output section each picks. */
private val EXPORT_SECTIONS = listOf(
    Pair("All Sections", OutputView.ALL),
    Pair("Overview", OutputView.OVERVIEW),
    Pair("Stats", OutputView.STATS),
    Pair("Skills", OutputView.SKILLS),
    Pair("Feats", OutputView.FEATS),
    Pair("Spells", OutputView.SPELLS),
    Pair("Enhancements", OutputView.ENHANCEMENTS),
    Pair("Destiny", OutputView.DESTINY),
)

/**
 * The Export window (Source/Forms/Build/frmExport.frm): links along the top (Copy to Clipboard,
 * Save to File, Format: with its combo, the Section combo, Help) and the text below in Courier,
 * editable. [onCopy] puts text on the clipboard and [onSave] saves it (the platform's part).
 */
@Composable
fun ExportWindowContent(state: PrototypeState, onCopy: (String) -> Unit, onSave: (String) -> Unit) {
    val screen = state.export
    val theme = state.skills.theme
    @Suppress("UNUSED_VARIABLE")
    val revision = screen.revision
    var formatOpen by remember { mutableStateOf(false) }
    var sectionOpen by remember { mutableStateOf(false) }
    val nav = TextStyle(fontSize = FONT_SIZE, color = theme.color(ColorGroup.NAVIGATION, ColorValue.TEXT))
    val link = TextStyle(fontSize = FONT_SIZE, color = theme.color(ColorGroup.NAVIGATION, ColorValue.TEXT_LINK))
    val control = TextStyle(fontSize = FONT_SIZE, color = theme.color(ColorGroup.CONTROLS, ColorValue.TEXT))
    // Form_Resize: the text starts under the bar, half a bar lower when the bar and box are the same colour.
    val sameColour = theme.get(ColorGroup.NAVIGATION, ColorValue.BACKGROUND) == theme.get(ColorGroup.CONTROLS, ColorValue.BACKGROUND)
    val textTop = if (sameColour) 39 else 26

    if (screen.copiedShowing) {
        LaunchedEffect(Unit) {
            delay(1000)
            screen.hideCopied()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(theme.color(ColorGroup.CONTROLS, ColorValue.BACKGROUND))) {
        // shpNav and the links on it.
        Box(modifier = Modifier.fillMaxWidth().size(815.dp, 26.dp).background(theme.color(ColorGroup.NAVIGATION, ColorValue.BACKGROUND)))
        BasicText("Copy to Clipboard", style = link, modifier = Modifier.offset(18.dp, 6.dp).clickable {
            onCopy(screen.text)
            screen.copied()
        })
        BasicText("Save to File", style = link, modifier = Modifier.offset(170.dp, 6.dp).clickable {
            if (screen.text.isEmpty()) {
                state.prompts.notice("Nothing to export.")
            } else {
                onSave(screen.text)
            }
        })
        // "Format:" opens the format editor (lnkNav_MouseDown, :339).
        BasicText("Format:", style = nav, modifier = Modifier.offset(285.dp, 6.dp).clickable { state.format.open() })
        ComboBox(theme, control, screen.currentFormatName(), 344, 2, 141, 21) { formatOpen = !formatOpen }
        BasicText("Section:", style = nav, modifier = Modifier.offset(522.dp, 6.dp))
        var sectionName = ""
        for (s in EXPORT_SECTIONS) {
            if (s.second == screen.section) {
                sectionName = s.first
            }
        }
        ComboBox(theme, control, sectionName, 576, 2, 129, 21) { sectionOpen = !sectionOpen }
        BasicText("Help", style = link, modifier = Modifier.offset(768.dp, 6.dp).clickable { state.help.show("Export") })

        // txtOutput: Courier New 9pt, both scroll bars, editable.
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

        if (screen.copiedShowing) {
            // picTooltip: " Copied! " under the link.
            BasicText(
                " Copied! ",
                style = TextStyle(fontSize = FONT_SIZE, color = theme.color(ColorGroup.CONTROLS, ColorValue.TEXT)),
                modifier = Modifier.offset(50.dp, 22.dp).background(theme.color(ColorGroup.CONTROLS, ColorValue.BACKGROUND))
                    .border(1.dp, theme.color(ColorGroup.CONTROLS, ColorValue.BORDER_EXTERIOR)),
            )
        }
        if (formatOpen) {
            DropList(theme, control, screen.formatItems(), 344, 23, 141) { index ->
                formatOpen = false
                screen.chooseFormat(screen.formatItems()[index])
            }
        }
        if (sectionOpen) {
            val names = ArrayList<String>()
            for (s in EXPORT_SECTIONS) {
                names.add(s.first)
            }
            DropList(theme, control, names, 576, 23, 129) { index ->
                sectionOpen = false
                screen.chooseSection(EXPORT_SECTIONS[index].second)
            }
        }
    }
}
