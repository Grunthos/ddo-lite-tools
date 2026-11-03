package ddolite.builder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import ddolite.common.help.HelpText
import ddolite.common.help.HelpTopic
import ddolite.common.help.HelpWord
import ddolite.common.theme.ColorGroup
import ddolite.common.theme.ColorValue

/**
 * The Help window's state: the help file, the topic shown and the "< Back" stack. Ported from
 * basHelp.ShowHelp (basHelp.bas:121) and frmHelp's link handling (lnkLink_MouseDown, picBack_MouseDown).
 *
 * The platform supplies what links do outside the window: [onUrl] opens a web page, [onRun] a file
 * (VB's "\" prefix means the program's folder, which the caller resolves), [onForm] a window by its
 * VB name ("frmOptions").
 */
class HelpScreenState(private val session: BuilderSession) {
    /** Data/Builder/Help.txt; empty until the app loads it. */
    var help = HelpText(emptyList())

    var topic: HelpTopic? by mutableStateOf(null)
        private set

    var isOpen by mutableStateOf(false)
        private set

    /** mstrStack: the topics "< Back" returns to. */
    private val stack = ArrayList<String>()
    var stackSize by mutableIntStateOf(0)
        private set

    var onUrl: (String) -> Unit = {}
    var onRun: (String) -> Unit = {}
    var onForm: (String) -> Unit = {}

    /** ShowHelp: the topic, in the Help window (opened, or redrawn if open). */
    fun show(code: String) {
        val found = help.find(code)
        if (found == null) {
            session.prompts.notice("Help Code '$code' not found.")
            return
        }
        topic = found
        isOpen = true
    }

    fun close() {
        isOpen = false
    }

    /** A click on a link: url, run, hlp (remembering this topic for Back) or frm. */
    fun clickLink(code: String) {
        val equals = code.indexOf('=')
        if (equals < 0) {
            return
        }
        val command = code.substring(0, equals).lowercase()
        val value = code.substring(equals + 1)
        when (command) {
            "url" -> onUrl(value)
            "run" -> onRun(value)
            "hlp" -> {
                val current = topic
                if (current == null) {
                    session.prompts.notice("There was a problem and Help needs to close")
                    close()
                    return
                }
                stack.add(current.code)
                stackSize = stack.size
                show(value)
            }
            "frm" -> onForm(value)
        }
    }

    /** "< Back": the topic before the last link followed. */
    fun back() {
        if (stack.isEmpty()) {
            return
        }
        val code = stack.removeAt(stack.size - 1)
        stackSize = stack.size
        show(code)
    }
}

/** A word placed on the page. */
private class PlacedWord(val word: HelpWord, val x: Int, val y: Int)

/**
 * The Help window (Source/Forms/frmHelp.frm): the topic's words laid out as DrawText does: each
 * line a paragraph, words wrapped at the width, a wrapped line of an indented paragraph lining up
 * after its " - "; links in bold in the link colour; "< Back" at the top right after a help link.
 * Escape closes it (Form_KeyDown; the caller's window). Not yet: VB's arrow-key and page scrolling.
 */
@Composable
fun HelpWindowContent(state: BuilderSession) {
    val screen = state.help
    val theme = state.skills.theme
    val topic = screen.topic ?: return
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current.density
    val text = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT)
    val link = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT_LINK)
    val error = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT_ERROR)

    fun style(w: HelpWord): TextStyle {
        var s = TextStyle(fontSize = FONT_SIZE, color = if (w.error) error else text)
        if (w.code.isNotEmpty()) {
            return TextStyle(fontSize = FONT_SIZE, color = link, fontWeight = FontWeight.Bold)
        }
        if (w.bold) {
            s = s.copy(fontWeight = FontWeight.Bold)
        }
        if (w.italics) {
            s = s.copy(fontStyle = FontStyle.Italic)
        }
        if (w.underline) {
            s = s.copy(textDecoration = TextDecoration.Underline)
        }
        return s
    }

    fun width(s: String, st: TextStyle): Int = (measurer.measure(s, st).size.width / density).toInt()

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(theme.color(ColorGroup.WORKSPACE, ColorValue.BACKGROUND))) {
        // picContainer: a scroll bar's width in from each side.
        val scrollWidth = 17
        val pageWidth = maxWidth.value.toInt() - scrollWidth * 2
        val plain = TextStyle(fontSize = FONT_SIZE)
        val lineHeight = (measurer.measure("X", plain).size.height / density).toInt()
        val space = width(" ", plain)
        val margins = intArrayOf(0, width(" - ", plain), width("   - ", plain))
        // DrawText: place every word.
        val placed = ArrayList<PlacedWord>()
        var x = 0
        var y = 0
        for (p in topic.paragraphs) {
            for (w in p.words) {
                val wordWidth = width(w.text, style(w))
                if (x + wordWidth + space > pageWidth && x > 0) {
                    x = margins[p.indent]
                    y += lineHeight
                }
                placed.add(PlacedWord(w, x, y))
                x += wordWidth + space
            }
            x = 0
            y += lineHeight
        }
        Box(modifier = Modifier.offset(scrollWidth.dp, 0.dp).size(pageWidth.dp, maxHeight).verticalScroll(rememberScrollState())) {
            Box(modifier = Modifier.size(pageWidth.dp, maxOf(y, 1).dp)) {
                for (pw in placed) {
                    var modifier = Modifier.offset(pw.x.dp, pw.y.dp)
                    val code = pw.word.code
                    if (code.isNotEmpty()) {
                        modifier = modifier.clickable { screen.clickLink(code) }
                    }
                    BasicText(pw.word.text, style = style(pw.word), modifier = modifier, maxLines = 1, softWrap = false)
                }
            }
        }
        if (screen.stackSize > 0) {
            // picBack: " < Back  " at the top right.
            val backStyle = TextStyle(fontSize = FONT_SIZE, color = link, fontWeight = FontWeight.Bold)
            val backWidth = width(" < Back  ", backStyle)
            BasicText(
                " < Back  ",
                style = backStyle,
                modifier = Modifier.offset((maxWidth.value.toInt() - scrollWidth - backWidth).dp, 0.dp)
                    .background(theme.color(ColorGroup.WORKSPACE, ColorValue.BACKGROUND))
                    .clickable { screen.back() },
            )
        }
    }
}
