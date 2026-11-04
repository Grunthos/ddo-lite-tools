package ddolite.builder.ui

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

/** frmOptions' client area, pixels at 100%: VB's 474 x 428, 9 taller so the Output frame ends inside it. */
const val OPTIONS_WIDTH = 474
const val OPTIONS_HEIGHT = 437

/*
 * The layout, from the content corner (WindowFrame): four captioned frames, two by two (General and
 * Build, then Appearance and Output); the right-hand ones out to the right margin, and the frames
 * in a row the same height. Controls are placed from their frame's corner.
 */
private const val LEFT_FRAME_WIDTH = 249
private const val RIGHT_FRAME_LEFT = LEFT_FRAME_WIDTH + GAP
private const val ROW1_HEIGHT = 181
private const val ROW2_TOP = 192
private const val ROW2_HEIGHT = 197
private const val OPTIONS_BUTTON_WIDTH = 93

/**
 * The Options window (Source/Forms/Build/frmOptions.frm): General, Build, Appearance and Output
 * groups. Each change goes straight into the settings, as VB's handlers do, and the output is
 * redrawn where VB calls GenerateOutput or ResizeOutput. In a [WindowFrame] (no footer).
 *
 * Not yet: the Colors button (VB's Colors program). Stored but not used yet: child windows.
 */
@Composable
fun OptionsWindowContent(state: BuilderSession) {
    val theme = state.skills.theme
    @Suppress("UNUSED_VARIABLE")
    val revision = state.settingsRevision
    WindowFrame(
        theme, OPTIONS_WIDTH, OPTIONS_HEIGHT,
        header = { HeaderBar(theme, emptyList(), null, listOf("Help"), isEnabled = { true }, onClick = { state.help.show("Options") }) },
        footer = null,
    ) { area ->
        OptionsGroups(state, theme, area)
    }
}

@Composable
private fun OptionsGroups(state: BuilderSession, theme: Theme, area: ContentArea) {
    val s = state.settings
    var open by remember { mutableStateOf("") }
    val label = TextStyle(platformStyle = TEXT_RENDERING, fontSize = FONT_SIZE, color = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT))
    val control = TextStyle(platformStyle = TEXT_RENDERING, fontSize = FONT_SIZE, color = theme.color(ColorGroup.CONTROLS, ColorValue.TEXT))
    val windowItems = listOf("Maximized", "Fill Desktop", "Last Position")
    val pointsItems = listOf("Adventurer", "Champion", "Hero", "Legend")
    val skillScreenItems = listOf("Alphabetical", "Native First")
    val featScreenItems = listOf("Alphabetical", "Group Related")
    val skillOutputItems = listOf("Alphabetical", "Ranks Spent")
    val featOutputItems = listOf("Character Level", "Feat Channel")
    val rightWidth = area.width - RIGHT_FRAME_LEFT
    // The right-hand frames' combos fill them, 20 pixels in from each side.
    val rightComboLeft = RIGHT_FRAME_LEFT + 20
    val rightComboWidth = rightWidth - 40

    fun changed() {
        state.settingsChanged()
    }

    // General (fraGeneral)
    GroupFrame(theme, "General", 0, 0, LEFT_FRAME_WIDTH, ROW1_HEIGHT)
    Label("Main Window", label, 20, 27)
    ComboBox(theme, control, windowItems[s.windowSize.ordinal], 108, 24, 113, 21) { open = if (open == "window") "" else "window" }
    Label("Recent Files Menu", label, 20, 58)
    Spinner(
        value = s.mruCount, min = 1, max = 9, theme = theme, stepLarge = 3,
        onValueChange = { v ->
            s.changeMruCount(v)
            changed()
        },
        modifier = Modifier.offset(140.dp, 56.dp).size(65.dp, 20.dp),
    )
    CheckBox(theme, "Show Error Log on startup", s.showErrors, true, 20, 88, 213) {
        s.showErrors = !s.showErrors
        changed()
    }
    CheckBox(theme, "Child Windows", s.childWindows, true, 20, 116, 213) {
        s.childWindows = !s.childWindows
        changed()
    }
    CheckBox(theme, "Confirmation Prompts", s.confirm, true, 20, 144, 213) {
        s.confirm = !s.confirm
        changed()
    }

    // Build (fraBuild)
    GroupFrame(theme, "Build", RIGHT_FRAME_LEFT, 0, rightWidth, ROW1_HEIGHT)
    Label("Default Build Points", label, rightComboLeft, 28)
    ComboBox(theme, control, pointsItems[s.buildPoints.ordinal], rightComboLeft, 44, rightComboWidth, 21) { open = if (open == "points") "" else "points" }
    Label("Skill Order", label, rightComboLeft, 76)
    ComboBox(theme, control, skillScreenItems.getOrElse(s.skillOrderScreen) { "" }, rightComboLeft, 92, rightComboWidth, 21) { open = if (open == "skillScreen") "" else "skillScreen" }
    Label("Feat Order", label, rightComboLeft, 124)
    ComboBox(theme, control, featScreenItems.getOrElse(s.featOrder) { "" }, rightComboLeft, 140, rightComboWidth, 21) { open = if (open == "featScreen") "" else "featScreen" }

    // Appearance (fraAppearance)
    GroupFrame(theme, "Appearance", 0, ROW2_TOP, LEFT_FRAME_WIDTH, ROW2_HEIGHT)
    CheckBox(theme, "Use Class Icons", s.useIcons, true, 20, ROW2_TOP + 32, 173) {
        s.useIcons = !s.useIcons
        changed()
    }
    CheckBox(theme, "Overview Screen", s.iconOverview, s.useIcons, 40, ROW2_TOP + 56, 153) {
        s.iconOverview = !s.iconOverview
        changed()
    }
    CheckBox(theme, "Skills Screen", s.iconSkills, s.useIcons, 40, ROW2_TOP + 80, 153) {
        s.iconSkills = !s.iconSkills
        changed()
    }
    Label("Indent Output", label, 20, ROW2_TOP + 110)
    Spinner(
        value = s.outputMargin, min = 0, max = 10, theme = theme, stepLarge = 3,
        onValueChange = { v ->
            s.outputMargin = v
            changed()
            state.markOutputChanged() // ResizeOutput
        },
        modifier = Modifier.offset(109.dp, (ROW2_TOP + 108).dp).size(65.dp, 20.dp),
    )
    Label("Spaces", label, 180, ROW2_TOP + 110)
    // At the frame's bottom right.
    OptionsButton(theme, "Colors", false, LEFT_FRAME_WIDTH - 12 - OPTIONS_BUTTON_WIDTH, ROW2_TOP + ROW2_HEIGHT - 45)

    // Output (fraOutput)
    GroupFrame(theme, "Output", RIGHT_FRAME_LEFT, ROW2_TOP, rightWidth, ROW2_HEIGHT)
    Label("Skill Order", label, rightComboLeft, ROW2_TOP + 28)
    ComboBox(theme, control, skillOutputItems[s.skillOrderOutput.ordinal], rightComboLeft, ROW2_TOP + 44, rightComboWidth, 21) { open = if (open == "skillOutput") "" else "skillOutput" }
    Label("Feat Order", label, rightComboLeft, ROW2_TOP + 76)
    ComboBox(theme, control, featOutputItems[s.featOrderOutput.ordinal], rightComboLeft, ROW2_TOP + 92, rightComboWidth, 21) { open = if (open == "featOutput") "" else "featOutput" }
    // VB quirk, kept: Show All Feats doesn't redraw the output (usrchkFeatsShowAll_UserChange, :663).
    CheckBox(theme, "Show All Feats", s.featShowAll, true, RIGHT_FRAME_LEFT + 16, ROW2_TOP + 128, 121) {
        s.featShowAll = !s.featShowAll
        changed()
    }
    // chkFormat_Click (:626): OpenForm "frmFormat". At the frame's bottom right.
    OptionsButton(theme, "BBCodes", true, RIGHT_FRAME_LEFT + rightWidth - 12 - OPTIONS_BUTTON_WIDTH, ROW2_TOP + ROW2_HEIGHT - 45) { state.format.open() }

    // The open combo's list, over everything.
    when (open) {
        "window" -> DropList(theme, control, windowItems, 108, 45, 113) { i ->
            open = ""
            s.windowSize = WindowSize.entries[i]
            changed()
        }
        "points" -> DropList(theme, control, pointsItems, rightComboLeft, 65, rightComboWidth) { i ->
            open = ""
            s.buildPoints = BuildPoints.entries[i]
            changed()
        }
        "skillScreen" -> DropList(theme, control, skillScreenItems, rightComboLeft, 113, rightComboWidth) { i ->
            open = ""
            s.skillOrderScreen = i
            state.skillsOrderChanged() // cboSkillOrderScreen_Click (:582): frmSkills.Cascade
            changed()
        }
        "featScreen" -> DropList(theme, control, featScreenItems, rightComboLeft, 161, rightComboWidth) { i ->
            open = ""
            s.featOrder = i
            // cboFeatOrder_Click (:596): IndexFeatDisplay, then frmFeats.OrderChanged (ShowAvailable).
            state.data.indexFeatDisplay(i == 1)
            state.feats.showAvailable()
            changed()
        }
        "skillOutput" -> DropList(theme, control, skillOutputItems, rightComboLeft, ROW2_TOP + 65, rightComboWidth) { i ->
            open = ""
            s.skillOrderOutput = SkillOrderOutput.entries[i]
            changed()
            state.markOutputChanged() // GenerateOutput oeAll
        }
        "featOutput" -> DropList(theme, control, featOutputItems, rightComboLeft, ROW2_TOP + 113, rightComboWidth) { i ->
            open = ""
            s.featOrderOutput = FeatOrderOutput.entries[i]
            changed()
            state.markOutputChanged() // GenerateOutput oeAll
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
        BasicText(caption, style = TextStyle(platformStyle = TEXT_RENDERING, fontSize = FONT_SIZE, color = color, textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth().offset(0.dp, 4.dp))
    }
}
