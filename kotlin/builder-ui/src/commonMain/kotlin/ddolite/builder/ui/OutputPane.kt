package ddolite.builder.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ddolite.builder.output.BuildOutput
import ddolite.builder.output.DisplayLine
import ddolite.builder.output.DisplayRun
import ddolite.builder.output.OutputColor
import ddolite.builder.output.OutputFormat
import ddolite.builder.output.OutputSettings
import ddolite.common.theme.ColorGroup
import ddolite.common.theme.ColorValue
import ddolite.common.theme.Theme

/** A theme's Output colour for an [OutputColor] (VB's `cfg.GetColor(cgeOutput, …)`). */
fun outputColorValue(color: OutputColor): ColorValue = when (color) {
    OutputColor.BACKGROUND -> ColorValue.BACKGROUND
    OutputColor.TEXT -> ColorValue.TEXT
    OutputColor.TEXT_ERROR -> ColorValue.TEXT_ERROR
    OutputColor.TEXT_DIM -> ColorValue.TEXT_DIM
    OutputColor.LIGHT_GRAY -> ColorValue.LIGHT_GRAY
    OutputColor.RED -> ColorValue.RED
    OutputColor.YELLOW -> ColorValue.YELLOW
    OutputColor.BLUE -> ColorValue.BLUE
    OutputColor.PURPLE -> ColorValue.PURPLE
    OutputColor.GREEN -> ColorValue.GREEN
    OutputColor.ORANGE -> ColorValue.ORANGE
}

/** The output options from the settings (Options window), and whether the guide tab is showing. */
fun outputSettings(state: BuilderSession): OutputSettings {
    val s = state.settings
    return OutputSettings(
        featOrder = s.featOrderOutput,
        featShowAll = s.featShowAll,
        skillOrder = s.skillOrderOutput,
        guideFirst = state.enhancementsOpen && state.enhancements.tab == 2,
    )
}

/**
 * The main window's output (frmMain's picBuild): the build as basOutput draws it for the window
 * that's active, in the theme's Output colours; nothing when no build is open (VB clears it).
 * Lists are indented as VB's, with each number or bullet ending at the list's indent.
 */
@Composable
fun OutputPane(state: BuilderSession) {
    val theme = state.skills.theme
    @Suppress("UNUSED_VARIABLE")
    val revision = state.revision + state.skills.revision + state.enhancements.revision
    val rules = state.rules
    val vertical = rememberScrollState()
    val horizontal = rememberScrollState()
    var modifier: Modifier = Modifier
    // CanDrag (basOutput.bas:2696): with a scroll bar showing, the output can be dragged with the mouse,
    // under a hand cursor (picBuild_MouseDown/Move/Up, frmMain.frm:862).
    val showVertical = vertical.maxValue in 1 until Int.MAX_VALUE
    val showHorizontal = horizontal.maxValue in 1 until Int.MAX_VALUE
    if (showVertical || showHorizontal) {
        modifier = modifier.pointerHoverIcon(PointerIcon.Hand).pointerInput(Unit) {
            detectDragGestures { change, drag ->
                change.consume()
                horizontal.dispatchRawDelta(-drag.x)
                vertical.dispatchRawDelta(-drag.y)
            }
        }
    }
    // scrollVertical and scrollHorizontal (frmMain), shown when the output doesn't fit.
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(theme.color(ColorGroup.OUTPUT, ColorValue.BACKGROUND))) {
        val width = maxWidth.value.toInt()
        val height = maxHeight.value.toInt()
        val contentWidth = if (showVertical) width - SCROLL_BAR else width
        val contentHeight = if (showHorizontal) height - SCROLL_BAR else height
        Box(modifier = modifier.size(contentWidth.dp, contentHeight.dp).verticalScroll(vertical).horizontalScroll(horizontal)) {
            if (rules != null) {
                val lines = BuildOutput(rules, state.outputFormat, outputSettings(state)).display(state.outputView)
                OutputLines(lines, theme, state.settings.outputMargin) { link -> state.onOutputLink(link) }
            }
        }
        ScrollBar(theme, vertical, width - SCROLL_BAR, 0, contentHeight)
        ScrollBar(theme, horizontal, 0, height - SCROLL_BAR, contentWidth, vertical = false)
    }
}

private val OUTPUT_FONT_SIZE = 12.sp

@Composable
private fun OutputLines(lines: List<DisplayLine>, theme: Theme, margin: Int, onLink: (String) -> Unit) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current.density
    val plain = TextStyle(platformStyle = TEXT_RENDERING, fontSize = OUTPUT_FONT_SIZE)
    val indent = (measurer.measure("XXXXX", plain).size.width / density).toInt()
    val lineHeight = (measurer.measure("X", plain).size.height / density).toInt()
    // ResizeOutput: the output starts cfg.OutputMargin spaces in; Kotlin only: inside a [TEXT_INSET] all round.
    val marginWidth = if (margin > 0) (measurer.measure(" ".repeat(margin), plain).size.width / density).toInt() else 0
    Column(modifier = Modifier.padding(start = (TEXT_INSET + marginWidth).dp, top = TEXT_INSET.dp, end = TEXT_INSET.dp, bottom = TEXT_INSET.dp)) {
        for (line in lines) {
            Row(modifier = Modifier.height(lineHeight.dp)) {
                val prefix = line.prefix
                if (prefix != null) {
                    val width = (measurer.measure(prefix.text, runStyle(prefix, theme)).size.width / density).toInt()
                    val x = maxOf(line.listLevel * indent - width, 0)
                    Box(modifier = Modifier.width(x.dp))
                    BasicText(prefix.text, style = runStyle(prefix, theme), maxLines = 1, softWrap = false)
                }
                for (run in line.runs) {
                    if (run.link.isEmpty()) {
                        BasicText(run.text, style = runStyle(run, theme), maxLines = 1, softWrap = false)
                    } else {
                        // A heading's "(Errors)": underlined, and a click opens that section's window.
                        BasicText(
                            run.text, style = runStyle(run, theme).copy(textDecoration = TextDecoration.Underline), maxLines = 1, softWrap = false,
                            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand).clickable { onLink(run.link) },
                        )
                    }
                }
            }
        }
    }
}

private fun runStyle(run: DisplayRun, theme: Theme): TextStyle {
    var style = TextStyle(platformStyle = TEXT_RENDERING,
        fontSize = OUTPUT_FONT_SIZE,
        color = theme.color(ColorGroup.OUTPUT, outputColorValue(run.color)),
    )
    if (run.courier) {
        style = style.copy(fontFamily = FontFamily.Monospace)
    }
    if (run.bold) {
        style = style.copy(fontWeight = FontWeight.Bold)
    }
    if (run.underline) {
        style = style.copy(textDecoration = TextDecoration.Underline)
    }
    return style
}
