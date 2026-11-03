package ddolite.prototype.skills

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ddolite.builder.build.BuildPoints
import ddolite.builder.output.FeatOrderOutput
import ddolite.builder.output.SkillOrderOutput
import ddolite.builder.settings.WindowSize
import ddolite.common.theme.ColorGroup
import ddolite.common.theme.ColorValue
import ddolite.common.theme.Theme

/**
 * The Options window (Source/Forms/Build/frmOptions.frm): General, Build, Appearance and Output
 * groups. Each change goes straight into the settings, as VB's handlers do, and the output is
 * redrawn where VB calls GenerateOutput or ResizeOutput. Laid out from the form's positions; the
 * client area is 474 x 428 at 100%.
 *
 * Not yet: the Colors button (VB's Colors program). Stored but not used yet: the skill order and
 * feat order of the screens, and child windows.
 */
@Composable
fun OptionsWindowContent(state: PrototypeState) {
    val theme = state.skills.theme
    @Suppress("UNUSED_VARIABLE")
    val revision = state.settingsRevision
    val s = state.settings
    var open by remember { mutableStateOf("") }
    val label = TextStyle(fontSize = FONT_SIZE, color = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT))
    val control = TextStyle(fontSize = FONT_SIZE, color = theme.color(ColorGroup.CONTROLS, ColorValue.TEXT))
    val windowItems = listOf("Maximized", "Fill Desktop", "Last Position")
    val pointsItems = listOf("Adventurer", "Champion", "Hero", "Legend")
    val skillScreenItems = listOf("Alphabetical", "Native First")
    val featScreenItems = listOf("Alphabetical", "Group Related")
    val skillOutputItems = listOf("Alphabetical", "Ranks Spent")
    val featOutputItems = listOf("Character Level", "Feat Channel")

    fun changed() {
        state.settingsChanged()
    }

    Box(modifier = Modifier.fillMaxSize().background(theme.color(ColorGroup.WORKSPACE, ColorValue.BACKGROUND))) {
        // General (fraGeneral at 20, 40)
        GroupFrame(theme, "General", 20, 40, 249, 181)
        Label("Main Window", label, 40, 67)
        ComboBox(theme, control, windowItems[s.windowSize.ordinal], 128, 64, 113, 21) { open = if (open == "window") "" else "window" }
        Label("Recent Files Menu", label, 40, 98)
        Spinner(
            value = s.mruCount, min = 1, max = 9, theme = theme, stepLarge = 3,
            onValueChange = { v ->
                s.changeMruCount(v)
                changed()
            },
            modifier = Modifier.offset(160.dp, 96.dp).size(65.dp, 20.dp),
        )
        CheckBox(theme, "Show Error Log on startup", s.showErrors, true, 40, 128, 213) {
            s.showErrors = !s.showErrors
            changed()
        }
        CheckBox(theme, "Child Windows", s.childWindows, true, 40, 156, 213) {
            s.childWindows = !s.childWindows
            changed()
        }
        CheckBox(theme, "Confirmation Prompts", s.confirm, true, 40, 184, 213) {
            s.confirm = !s.confirm
            changed()
        }

        // Build (fraBuild at 284, 40)
        GroupFrame(theme, "Build", 284, 40, 169, 181)
        Label("Default Build Points", label, 304, 68)
        ComboBox(theme, control, pointsItems[s.buildPoints.ordinal], 304, 84, 129, 21) { open = if (open == "points") "" else "points" }
        Label("Skill Order", label, 304, 116)
        ComboBox(theme, control, skillScreenItems.getOrElse(s.skillOrderScreen) { "" }, 304, 132, 129, 21) { open = if (open == "skillScreen") "" else "skillScreen" }
        Label("Feat Order", label, 304, 164)
        ComboBox(theme, control, featScreenItems.getOrElse(s.featOrder) { "" }, 304, 180, 129, 21) { open = if (open == "featScreen") "" else "featScreen" }

        // Appearance (fraAppearance at 20, 232)
        GroupFrame(theme, "Appearance", 20, 232, 245, 173)
        CheckBox(theme, "Use Class Icons", s.useIcons, true, 40, 264, 173) {
            s.useIcons = !s.useIcons
            changed()
        }
        CheckBox(theme, "Overview Screen", s.iconOverview, s.useIcons, 60, 288, 153) {
            s.iconOverview = !s.iconOverview
            changed()
        }
        CheckBox(theme, "Skills Screen", s.iconSkills, s.useIcons, 60, 312, 153) {
            s.iconSkills = !s.iconSkills
            changed()
        }
        Label("Indent Output", label, 40, 342)
        Spinner(
            value = s.outputMargin, min = 0, max = 10, theme = theme, stepLarge = 3,
            onValueChange = { v ->
                s.outputMargin = v
                changed()
                state.markOutputChanged() // ResizeOutput
            },
            modifier = Modifier.offset(129.dp, 340.dp).size(65.dp, 20.dp),
        )
        Label("Spaces", label, 200, 342)
        OptionsButton(theme, "Colors", false, 164, 372)

        // Output (fraOutput at 284, 232)
        GroupFrame(theme, "Output", 284, 232, 169, 197)
        Label("Skill Order", label, 304, 260)
        ComboBox(theme, control, skillOutputItems[s.skillOrderOutput.ordinal], 304, 276, 129, 21) { open = if (open == "skillOutput") "" else "skillOutput" }
        Label("Feat Order", label, 304, 308)
        ComboBox(theme, control, featOutputItems[s.featOrderOutput.ordinal], 304, 324, 129, 21) { open = if (open == "featOutput") "" else "featOutput" }
        // VB quirk, kept: Show All Feats doesn't redraw the output (usrchkFeatsShowAll_UserChange, :663).
        CheckBox(theme, "Show All Feats", s.featShowAll, true, 300, 360, 121) {
            s.featShowAll = !s.featShowAll
            changed()
        }
        // chkFormat_Click (:626): OpenForm "frmFormat".
        OptionsButton(theme, "BBCodes", true, 352, 384) { state.format.open() }

        Column(modifier = Modifier.fillMaxWidth()) {
            HeaderBar(theme, emptyList(), null, listOf("Help"), isEnabled = { true }, onClick = { state.help.show("Options") })
        }

        // The open combo's list, over everything.
        when (open) {
            "window" -> DropList(theme, control, windowItems, 128, 85, 113) { i ->
                open = ""
                s.windowSize = WindowSize.entries[i]
                changed()
            }
            "points" -> DropList(theme, control, pointsItems, 304, 105, 129) { i ->
                open = ""
                s.buildPoints = BuildPoints.entries[i]
                changed()
            }
            "skillScreen" -> DropList(theme, control, skillScreenItems, 304, 153, 129) { i ->
                open = ""
                s.skillOrderScreen = i
                changed()
            }
            "featScreen" -> DropList(theme, control, featScreenItems, 304, 201, 129) { i ->
                open = ""
                s.featOrder = i
                changed()
            }
            "skillOutput" -> DropList(theme, control, skillOutputItems, 304, 297, 129) { i ->
                open = ""
                s.skillOrderOutput = SkillOrderOutput.entries[i]
                changed()
                state.markOutputChanged() // GenerateOutput oeAll
            }
            "featOutput" -> DropList(theme, control, featOutputItems, 304, 345, 129) { i ->
                open = ""
                s.featOrderOutput = FeatOrderOutput.entries[i]
                changed()
                state.markOutputChanged() // GenerateOutput oeAll
            }
        }
    }
}

/** A graphical CheckBox used as a button (chkColors, chkFormat), 93 x 25; grey when not available. */
@Composable
private fun OptionsButton(theme: Theme, caption: String, enabled: Boolean, left: Int, top: Int, onClick: () -> Unit = {}) {
    val color = theme.color(ColorGroup.CONTROLS, if (enabled) ColorValue.TEXT else ColorValue.TEXT_DIM)
    var modifier = Modifier.offset(left.dp, top.dp).size(93.dp, 25.dp)
        .background(theme.color(ColorGroup.CONTROLS, ColorValue.BACKGROUND))
        .border(1.dp, theme.color(ColorGroup.CONTROLS, ColorValue.BORDER_EXTERIOR))
    if (enabled) {
        modifier = modifier.clickable(onClick = onClick)
    }
    Box(modifier = modifier) {
        BasicText(caption, style = TextStyle(fontSize = FONT_SIZE, color = color, textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth().offset(0.dp, 4.dp))
    }
}
