package ddolite.builder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ddolite.builder.output.ListTags
import ddolite.builder.output.OutputFormat
import ddolite.builder.output.Tags
import ddolite.common.theme.ColorGroup
import ddolite.common.theme.ColorValue
import ddolite.common.theme.Theme

/**
 * The format editor's state (Source/Forms/Build/frmFormat.frm, "Output Format"): the output format
 * being edited, and whether it differs from the one in use. Ported from the form with clsConfig's
 * ReadOutputFormat (:2499), WriteOutputFormat (:2533) and SetOutputFormat (:2490).
 *
 * The platform supplies [onLoad] (an open dialog in the Settings folder; the chosen file's name
 * without ".bbcodes", or null), [onSaveDialog] (a save dialog that asks before overwriting; the
 * file, or null), [onWrite] (writes the text to that file) and [onFormatsChanged] (the list of
 * .bbcodes files is read again).
 */
class FormatScreenState(private val session: BuilderSession) {
    var isOpen by mutableStateOf(false)
        private set

    /** The controls' values. */
    var format by mutableStateOf(OutputFormat.DEFAULT)
        private set

    /** mblnDirty: "Apply Changes" is a link only when set. */
    var dirty by mutableStateOf(false)
        private set

    var onLoad: () -> String? = { null }
    var onSaveDialog: () -> String? = { null }
    var onWrite: (String, String) -> Unit = { _, _ -> }
    var onFormatsChanged: () -> Unit = {}

    /** OpenForm "frmFormat": Form_Load reads the format in use (LoadData, ReadOutputFormat). */
    fun open() {
        if (!isOpen) {
            format = session.outputFormat
            dirty = false
            isOpen = true
        }
        onShow()
    }

    /** Brings the open window to the front (the platform's part). */
    var onShow: () -> Unit = {}

    /** Form_Unload: with changes, "Apply changes?" (Ask); no leaves them unapplied. */
    fun close() {
        if (dirty && session.ask("Apply changes?")) {
            apply()
        }
        isOpen = false
    }

    /** UserChange: any control changed. */
    fun change(changed: OutputFormat) {
        format = changed
        dirty = true
    }

    /** EnableControls (:574): the tags with BBCodes ticked and Reddit not. */
    val tagsEnabled: Boolean get() = format.codes && !format.reddit

    /** EnableControls: Use Dots and BBCodes unless Reddit. */
    val styleEnabled: Boolean get() = !format.reddit

    /**
     * ApplyChanges (:516): the edited format is the one in use (WriteOutputFormat); the Export window
     * shows it, or the main window does, under the name it matches (IdentifyOutputFormat).
     */
    fun apply() {
        useFormat(format)
        dirty = false
    }

    private fun useFormat(f: OutputFormat) {
        session.outputFormat = f
        session.settings.output = f
        val name = session.formats.identify(f)
        session.settings.bbCodes = name
        session.formatName = name
        if (session.export.isOpen) {
            session.export.regenerate()
        }
    }

    /**
     * Load (FormatLoad, :484): a .bbcodes file chosen in Settings, loaded by its name (cfg.SetOutputFormat,
     * which reads Settings\<name>.bbcodes); "Unrecognized file format." if there's none.
     *
     * VB detail kept: the loaded format is in use straight away (SetOutputFormat), and Apply Changes
     * is lit though there's nothing left to apply.
     */
    fun load() {
        val name = onLoad() ?: return
        val loaded = session.formats.load(name)
        if (loaded == null) {
            session.prompts.notice("Unrecognized file format.")
            return
        }
        session.outputFormat = loaded
        session.settings.output = loaded
        session.settings.bbCodes = name
        session.formatName = name
        format = loaded
        dirty = true
    }

    /**
     * Save As (FormatSave, :499): the dialog, Apply Changes, the file (SaveFormat), then the format
     * list again. VB detail kept: only the Export window's combo (UpdateCombo) names the format after
     * the new file; without it the name stays as Apply Changes found it, before the file existed.
     */
    fun saveAs() {
        val file = onSaveDialog() ?: return
        apply()
        onWrite(file, format.fileText())
        onFormatsChanged()
        if (session.export.isOpen) {
            val name = session.formats.identify(session.outputFormat)
            session.settings.bbCodes = name
            session.formatName = name
        }
    }
}

/**
 * The format editor (frmFormat, "Output Format"): Load, Save As, Apply Changes and Help along the top;
 * Text Colors, Use Dots, Reddit and BBCodes; and in the BBCodes frame each kind of tag with its
 * open and close text. In a [WindowFrame] (no footer), the links in its header bar; the client area
 * is 368 x 436 at 100%. Controls are placed from the content corner, the tags from the BBCodes
 * frame's corner (VB's positions are in frmFormat.frm).
 */
/** frmFormat's client area, pixels at 100%. */
private const val FORMAT_WIDTH = 368
private const val FORMAT_HEIGHT = 436

@Composable
fun FormatWindowContent(state: BuilderSession) {
    val screen = state.format
    val theme = state.skills.theme
    val f = screen.format
    val tags = screen.tagsEnabled
    val labelColor = theme.color(ColorGroup.WORKSPACE, if (tags) ColorValue.TEXT else ColorValue.TEXT_DIM)
    val label = TextStyle(platformStyle = TEXT_RENDERING, fontSize = FONT_SIZE, color = labelColor)

    WindowFrame(
        theme, FORMAT_WIDTH, FORMAT_HEIGHT,
        header = {
            // shpNav and the links; Apply Changes only with something to apply.
            HeaderBar(
                theme,
                listOf("Load", "Save As", "Apply Changes"),
                null,
                listOf("Help"),
                isEnabled = { item -> item != "Apply Changes" || screen.dirty },
                onClick = { item ->
                    when (item) {
                        "Load" -> screen.load()
                        "Save As" -> screen.saveAs()
                        "Apply Changes" -> screen.apply()
                        "Help" -> state.help.show("Output Format")
                    }
                },
            )
        },
        footer = null,
    ) { area ->
        CheckBox(theme, "Text Colors", f.textColors, true, 8, 12, 121) { screen.change(f.copy(textColors = !f.textColors)) }
        CheckBox(theme, "Use Dots", f.dots, screen.styleEnabled, 8, 36, 121) { screen.change(f.copy(dots = !f.dots)) }
        CheckBox(theme, "Reddit", f.reddit, true, 224, 12, 105) { screen.change(f.copy(reddit = !f.reddit)) }

        // fraFormat: Shape1 is its border, from 8 down, out to the right margin; the BBCodes check box
        // sits on it as its caption. The tags are placed from its corner.
        val x = 0
        val y = 60
        Box(
            modifier = Modifier.offset(x.dp, (y + 8).dp).size(area.width.dp, 317.dp)
                .border(1.dp, theme.color(ColorGroup.WORKSPACE, ColorValue.BORDER_EXTERIOR)),
        )
        Box(modifier = Modifier.offset((x + 8).dp, y.dp).background(theme.color(ColorGroup.WORKSPACE, ColorValue.BACKGROUND))) {
            CheckBox(theme, "BBCodes", f.codes, screen.styleEnabled, 0, 0, 81) { screen.change(f.copy(codes = !f.codes)) }
        }
        CenteredLabel("Open", label, x + 124, y + 24, 97)
        CenteredLabel("Close", label, x + 228, y + 24, 73)
        TagRow(theme, "Bold", f.bold, tags, x, y + 40) { screen.change(f.copy(bold = it)) }
        TagRow(theme, "Underline", f.underline, tags, x, y + 64) { screen.change(f.copy(underline = it)) }
        TagRow(theme, "Fixed", f.fixed, tags, x, y + 88) { screen.change(f.copy(fixed = it)) }
        CheckBox(theme, "Lists", f.lists.enabled, tags, x + 20, y + 112, 97) { screen.change(f.copy(lists = f.lists.copy(enabled = !f.lists.enabled))) }
        RightLabel("Bullet Points:", label, x + 20, y + 138, 97)
        TagText(theme, f.lists.bulletOpen, tags, x + 124, y + 136, 97) { screen.change(f.copy(lists = f.lists.copy(bulletOpen = it))) }
        TagText(theme, f.lists.bulletClose, tags, x + 228, y + 136, 73) { screen.change(f.copy(lists = f.lists.copy(bulletClose = it))) }
        RightLabel("Numbered:", label, x + 20, y + 162, 97)
        TagText(theme, f.lists.numberedOpen, tags, x + 124, y + 160, 97) { screen.change(f.copy(lists = listsWith(f.lists, numberedOpen = it))) }
        TagText(theme, f.lists.numberedClose, tags, x + 228, y + 160, 73) { screen.change(f.copy(lists = listsWith(f.lists, numberedClose = it))) }
        TagRow(theme, "Color", f.color, tags, x, y + 192) { screen.change(f.copy(color = it)) }
        WrappedLabel("Use \$ as the placeholder for the numeric color value in the Color Open tag.", label, x + 44, y + 220, 257)
        TagRow(theme, "Wrapper", f.wrapper, tags, x, y + 264) { screen.change(f.copy(wrapper = it)) }
        WrappedLabel("Wrap the entire build in tags?", label, x + 44, y + 292, 257)
    }
}

private fun listsWith(l: ListTags, numberedOpen: String = l.numberedOpen, numberedClose: String = l.numberedClose): ListTags =
    l.copy(numberedOpen = numberedOpen, numberedClose = numberedClose)

/** A check box with its open and close tags (e.g. usrchkBold, txtBoldOpen, txtBoldClose). */
@Composable
private fun TagRow(theme: Theme, caption: String, t: Tags, enabled: Boolean, x: Int, top: Int, onChange: (Tags) -> Unit) {
    CheckBox(theme, caption, t.enabled, enabled, x + 20, top, 97) { onChange(t.copy(enabled = !t.enabled)) }
    TagText(theme, t.open, enabled, x + 124, top, 97) { onChange(t.copy(open = it)) }
    TagText(theme, t.close, enabled, x + 228, top, 73) { onChange(t.copy(close = it)) }
}

/** A tag text box; locked and dim when disabled (EnableControl, :590). */
@Composable
private fun TagText(theme: Theme, value: String, enabled: Boolean, left: Int, top: Int, width: Int, onChange: (String) -> Unit) {
    val color = theme.color(ColorGroup.CONTROLS, if (enabled) ColorValue.TEXT else ColorValue.TEXT_DIM)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        readOnly = !enabled,
        singleLine = true,
        textStyle = TextStyle(platformStyle = TEXT_RENDERING, fontSize = FONT_SIZE, color = color),
        cursorBrush = SolidColor(color),
        modifier = Modifier.offset(left.dp, top.dp).size(width.dp, 22.dp)
            .background(theme.color(ColorGroup.CONTROLS, ColorValue.BACKGROUND))
            .border(1.dp, theme.color(ColorGroup.CONTROLS, ColorValue.BORDER_EXTERIOR))
            .padding(horizontal = 3.dp, vertical = 3.dp),
    )
}

@Composable
private fun CenteredLabel(text: String, style: TextStyle, left: Int, top: Int, width: Int) {
    BasicText(text, style = style.copy(textAlign = TextAlign.Center), modifier = Modifier.offset(left.dp, top.dp).width(width.dp))
}

@Composable
private fun RightLabel(text: String, style: TextStyle, left: Int, top: Int, width: Int) {
    BasicText(text, style = style.copy(textAlign = TextAlign.End), modifier = Modifier.offset(left.dp, top.dp).width(width.dp))
}

@Composable
private fun WrappedLabel(text: String, style: TextStyle, left: Int, top: Int, width: Int) {
    BasicText(text, style = style, modifier = Modifier.offset(left.dp, top.dp).width(width.dp))
}
