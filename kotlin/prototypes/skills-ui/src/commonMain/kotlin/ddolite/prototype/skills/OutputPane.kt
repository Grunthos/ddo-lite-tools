package ddolite.prototype.skills

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
fun outputSettings(state: PrototypeState): OutputSettings {
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
fun OutputPane(state: PrototypeState) {
    val theme = state.skills.theme
    @Suppress("UNUSED_VARIABLE")
    val revision = state.revision + state.skills.revision + state.enhancements.revision
    val rules = state.rules
    Box(
        modifier = Modifier.fillMaxSize()
            .background(theme.color(ColorGroup.OUTPUT, ColorValue.BACKGROUND))
            .verticalScroll(rememberScrollState())
            .horizontalScroll(rememberScrollState()),
    ) {
        if (rules != null) {
            val lines = BuildOutput(rules, state.outputFormat, outputSettings(state)).display(state.outputView)
            OutputLines(lines, theme, state.settings.outputMargin)
        }
    }
}

private val OUTPUT_FONT_SIZE = 12.sp

@Composable
private fun OutputLines(lines: List<DisplayLine>, theme: Theme, margin: Int) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current.density
    val plain = TextStyle(fontSize = OUTPUT_FONT_SIZE)
    val indent = (measurer.measure("XXXXX", plain).size.width / density).toInt()
    val lineHeight = (measurer.measure("X", plain).size.height / density).toInt()
    // ResizeOutput: the output starts cfg.OutputMargin spaces in.
    val marginWidth = if (margin > 0) (measurer.measure(" ".repeat(margin), plain).size.width / density).toInt() else 0
    Column(modifier = Modifier.padding(start = marginWidth.dp, top = 0.dp)) {
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
                    BasicText(run.text, style = runStyle(run, theme), maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

private fun runStyle(run: DisplayRun, theme: Theme): TextStyle {
    var style = TextStyle(
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
