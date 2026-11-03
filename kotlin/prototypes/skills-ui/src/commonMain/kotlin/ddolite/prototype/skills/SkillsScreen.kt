package ddolite.prototype.skills

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ddolite.builder.build.Build
import ddolite.common.theme.ColorGroup
import ddolite.common.theme.ColorValue
import ddolite.common.theme.Theme

/**
 * The whole prototype screen: a bar for choosing the level plan, theme and Intelligence
 * modifier, the skills grid, and a line of help. Stands in for frmSkills, whose header/footer
 * bars (userHeader) and skill tome spinners aren't part of the prototype.
 *
 * Compose builds screens from nested function calls with lambdas (`Column { … }`); that's the
 * toolkit's own style. The logic stays in [SkillsScreenState] and builder-core.
 */
@Composable
fun SkillsScreen(state: SkillsScreenState) {
    val theme = state.theme
    val wrkBackground = theme.color(ColorGroup.WORKSPACE, ColorValue.BACKGROUND)
    val wrkText = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT)
    val label = TextStyle(fontSize = 13.sp, color = wrkText)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(wrkBackground)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Made-up level plans, only when no build is open (the Android prototype): with a build
        // open, its classes come from the Overview and its Intelligence from Stats.
        val opened = state.openedBuild
        if (opened == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText("Build:", style = label)
                for (i in state.presets.indices) {
                    ChoiceButton(theme, state.presets[i].name, selected = i == state.presetIndex) { state.selectPreset(i) }
                }
            }
        }

        // Theme and Intelligence.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText("Theme:", style = label)
            ChoiceButton(theme, "<", selected = false) {
                state.selectTheme((state.themeIndex + state.themes.size - 1) % state.themes.size)
            }
            BasicText(theme.name, style = label.copy(fontWeight = FontWeight.Bold))
            ChoiceButton(theme, ">", selected = false) {
                state.selectTheme((state.themeIndex + 1) % state.themes.size)
            }
            if (opened == null) {
                BasicText("      Intelligence modifier:", style = label)
                Spinner(
                    value = state.intModifier,
                    min = -5,
                    max = 15,
                    theme = theme,
                    onValueChange = { state.changeIntModifier(it) },
                )
            }
        }

        // The grid scrolls if the window is too small for it.
        Box(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState()),
        ) {
            val textMeasurer = rememberTextMeasurer()
            val classRow = state.isMulticlass()
            val layout = remember(textMeasurer, classRow) { compactGridLayout(textMeasurer, classRow) }
            SkillsGrid(state, layout, emptyMap())
        }

        BasicText(
            "Level cell: left-click add a point, right-click remove, middle-click clear, hold to repeat.   " +
                "Skill name: click to fill (half ranks → full ranks → even levels → clear).   " +
                "Level number: right-click to clear the level.",
            style = label.copy(color = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT_DIM), fontSize = 12.sp),
        )
    }
}

/** The opened build's name for the build row, or a placeholder if it has none. */
internal fun openedBuildLabel(build: Build): String {
    if (build.buildName.isEmpty()) {
        return "(opened build)"
    }
    return build.buildName
}

/** A small clickable label, in the navigation colours (the VB userHeader links). */
@Composable
internal fun ChoiceButton(theme: Theme, text: String, selected: Boolean, onClick: () -> Unit) {
    var background = theme.color(ColorGroup.NAVIGATION, ColorValue.BACKGROUND)
    if (selected) {
        background = theme.color(ColorGroup.NAVIGATION, ColorValue.BACK_HIGHLIGHT)
    }
    Box(
        modifier = Modifier
            .background(background)
            .border(1.dp, theme.color(ColorGroup.NAVIGATION, ColorValue.BORDER_EXTERIOR))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        BasicText(text, style = TextStyle(fontSize = 13.sp, color = theme.color(ColorGroup.NAVIGATION, ColorValue.TEXT)))
    }
}
