package ddolite.builder.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ddolite.builder.output.BuildOutput
import ddolite.builder.output.OutputFormat
import ddolite.builder.output.OutputView
import ddolite.common.theme.ColorGroup

/**
 * The state behind the Export window (File › Export › to Forums: frmExport): the text, the format
 * and section chosen, and the "Copied!" note. The text is regenerated when the window opens, the
 * format or section changes, or the build changes (frmExport.Cascade, from SetDirty); edits made
 * in the box are kept until then, as in VB.
 */
class ExportScreenState(private val session: BuilderSession) {
    /** The window is open. */
    var isOpen by mutableStateOf(false)
        private set

    /** txtOutput. */
    var text by mutableStateOf("")

    /** cboSection: `cfg.OutputSection`, All when the window opens. */
    var section by mutableStateOf(OutputView.ALL)
        private set

    /** picTooltip ("Copied!") is showing. */
    var copiedShowing by mutableStateOf(false)
        private set

    /** The build revision the text was made for. */
    private var madeFor = -1

    /** Bumped after each change. */
    var revision by mutableIntStateOf(0)
        private set

    /** OpenForm "frmExport": Form_Load (:271) sets the section to All, then tmrLoad fills the text. */
    fun open() {
        if (!isOpen) {
            section = OutputView.ALL
            isOpen = true
        }
        regenerate()
    }

    fun close() {
        isOpen = false
    }

    /** GenerateOutput(oeExport) with the theme's Output colours for the colour tags. */
    fun regenerate() {
        val rules = session.rules
        if (rules == null) {
            text = ""
        } else {
            val settings = outputSettings(session)
            val exportSettings = ddolite.builder.output.OutputSettings(
                settings.featOrder, settings.featShowAll, settings.skillOrder, section, settings.guideFirst,
            )
            val theme = session.skills.theme
            text = BuildOutput(rules, session.outputFormat, exportSettings).export { color ->
                colorToHex(theme.get(ColorGroup.OUTPUT, outputColorValue(color)))
            }
        }
        madeFor = session.revision
        revision++
    }

    /** frmExport.Cascade: SetDirty regenerates the text while the window is open. */
    fun refreshIfChanged() {
        if (isOpen && madeFor != session.revision) {
            regenerate()
        }
    }

    /** cboSection_Click (:368). */
    fun chooseSection(view: OutputView) {
        section = view
        regenerate()
    }

    /**
     * cboFormat_Click (:361): `cfg.SetOutputFormat` (clsConfig.cls:2490) loads the named format;
     * the blank item, or a name with no settings, changes nothing.
     */
    fun chooseFormat(name: String) {
        if (name.isEmpty()) {
            return
        }
        val format = session.formats.load(name) ?: return
        session.outputFormat = format
        session.formatName = name
        session.settings.output = format // cfg.SetOutputFormat
        session.settings.bbCodes = name
        regenerate()
    }

    /** Copy to Clipboard: shows "Copied!" for a second (tmrTooltip). */
    fun copied() {
        copiedShowing = true
    }

    fun hideCopied() {
        copiedShowing = false
    }

    /** The formats for the combo, with a blank item first (UpdateCombo, :343). */
    fun formatItems(): List<String> {
        val items = ArrayList<String>()
        items.add("")
        items.addAll(session.formats.names())
        return items
    }

    /** The format the combo shows: `cfg.IdentifyOutputFormat`, or blank for one of your own. */
    fun currentFormatName(): String = session.formats.identify(session.outputFormat)
}

/** VB's xp.ColorToHex: "RRGGBB" in capitals from an ARGB colour. */
fun colorToHex(argb: Int): String {
    val red = (argb shr 16) and 0xFF
    val green = (argb shr 8) and 0xFF
    val blue = argb and 0xFF
    return hex2(red) + hex2(green) + hex2(blue)
}

private fun hex2(value: Int): String {
    val s = value.toString(16).uppercase()
    return if (s.length < 2) "0$s" else s
}

/** A .bbcodes file: its name (without ".bbcodes") and its text. */
class FormatFile(val name: String, val text: String)

/**
 * The output formats there are: the three built in and the .bbcodes files in Settings. Ported
 * from clsConfig's GetFormatNames (:2583), InitFormat with LoadFormat (:847, :889) and
 * IdentifyOutputFormat (:2565).
 *
 * @param files each .bbcodes file's name (without ".bbcodes") and text, in folder order.
 */
class OutputFormats(private val files: List<FormatFile>) {
    /**
     * The names in the combo. VB quirk, kept: the list starts five long, so with fewer than two
     * extra files the combo has blank items at the end.
     */
    fun names(): List<String> {
        val names = ArrayList<String>()
        names.add("DDO Forums")
        names.add("Code Tags")
        names.add("Plain Text")
        for (file in files) {
            val lower = file.name.lowercase()
            if (lower != "ddo forums" && lower != "code tags" && lower != "plain text") {
                names.add(file.name)
            }
        }
        while (names.size < 5) {
            names.add("")
        }
        return names
    }

    /** InitFormat: a name's settings, or null if VB couldn't load it (no file, and not one it knows). */
    fun load(name: String): OutputFormat? {
        var text: String? = null
        for (file in files) {
            if (file.name.equals(name, ignoreCase = true)) {
                text = file.text
            }
        }
        if (text == null) {
            val lower = name.lowercase()
            if (lower != "default" && lower != "code tags" && lower != "plain text" && lower != "reddit") {
                return null
            }
        }
        return OutputFormat.load(name, text)
    }

    /**
     * IdentifyOutputFormat: the first name whose settings match [format], or "". As VB's
     * CompareFormats (:2610), the Reddit setting isn't compared.
     */
    fun identify(format: OutputFormat): String {
        for (name in names()) {
            if (name.isEmpty()) {
                continue
            }
            val other = load(name) ?: continue
            if (other.copy(reddit = format.reddit) == format) {
                return name
            }
        }
        return ""
    }
}
