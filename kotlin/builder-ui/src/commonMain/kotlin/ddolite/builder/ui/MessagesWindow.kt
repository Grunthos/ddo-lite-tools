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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ddolite.common.theme.ColorGroup
import ddolite.common.theme.ColorValue
import ddolite.common.theme.Theme

/**
 * The Messages window (Source/Forms/Build/frmDeprecate.frm): what the opened build file had that
 * the current data doesn't. Each missing feat can be replaced: Best Guess offers the nearest
 * names (or, unticked, every selectable feat), then Choose (or Choose under the selectors) puts
 * it in the slot; Ignore leaves the slot empty. Notices (renamed feats, missing enhancements, a
 * cut-short leveling guide) only have OK.
 *
 * Laid out from the form's positions: the client area is 815 x 517 at 100%. The Help link isn't
 * active yet.
 */
@Composable
fun MessagesWindowContent(state: BuilderSession) {
    val screen = state.messages
    val theme = state.skills.theme
    val drag = remember { DragDropState<Int>() }
    val workspace = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT)
    val dim = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT_DIM)

    Box(modifier = Modifier.fillMaxSize().background(theme.color(ColorGroup.WORKSPACE, ColorValue.BACKGROUND))) {
        Label(screen.label, TextStyle(fontSize = FONT_SIZE, color = workspace), 32, 40)
        val texts = ArrayList<String>()
        for (message in screen.messages) {
            texts.add(message.text)
        }
        ListBox(
            theme, texts, isSelected = { it == screen.messageIndex }, left = 28, top = 60, width = 761, height = 156,
            drag = drag, onPress = { i, _ -> screen.clickMessage(i) },
        )

        // lblDetails (EnableLabel dims it with no message chosen), chkIgnore, the details.
        Label("Details", TextStyle(fontSize = FONT_SIZE, color = if (screen.detailsLabelEnabled) workspace else dim), 32, 250)
        PushButton(theme, screen.ignoreCaption, screen.ignoreEnabled, 180, 240) { screen.ignore() }
        DetailsBox(theme, screen.details, 28, 268, 241, 218)

        // usrchkGuess, chkChoose and lstGuess.
        if (screen.bestGuessVisible) {
            CheckBox(theme, "Best Guess", screen.bestGuess, screen.bestGuessEnabled, 292, 248, 137) { screen.toggleBestGuess() }
        }
        if (screen.chooseVisible) {
            PushButton(theme, "Choose", screen.chooseEnabled, 440, 240) { screen.choose() }
        }
        val names = ArrayList<String>()
        for (feat in screen.guesses) {
            names.add(feat.name)
        }
        ListBox(
            theme, names, isSelected = { it == screen.guessIndex }, left = 288, top = 268, width = 241, height = 212,
            drag = drag, onPress = { i, _ -> screen.clickGuess(i) }, enabled = screen.guessListEnabled,
        )

        // lblSelectors, chkSelector, and lstSelector over usrInfo (which only binary builds' War Soul uses).
        if (screen.selectorsLabelVisible) {
            Label("Selectors", TextStyle(fontSize = FONT_SIZE, color = if (screen.selectorsLabelEnabled) workspace else dim), 552, 250)
        }
        if (screen.selectorButtonVisible) {
            PushButton(theme, "Choose", screen.selectorButtonEnabled, 700, 240) { screen.chooseSelector() }
        }
        DetailsBox(theme, emptyList(), 548, 268, 241, 218)
        if (screen.selectorListVisible) {
            ListBox(
                theme, screen.selectors, isSelected = { it == screen.selectorIndex }, left = 548, top = 268, width = 241, height = 212,
                drag = drag, onPress = { i, _ -> screen.clickSelector(i) }, enabled = screen.selectorListEnabled,
            )
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            // usrHeader_Click (:229): Help only.
            HeaderBar(theme, emptyList(), null, listOf("Help"), isEnabled = { true }, onClick = { state.help.show("Messages") })
        }
    }
}

/** A graphical CheckBox used as a push button (VB's UncheckButton pattern), 89 x 29; grey text when disabled. */
@Composable
private fun PushButton(theme: Theme, caption: String, enabled: Boolean, left: Int, top: Int, onClick: () -> Unit) {
    var modifier = Modifier.offset(left.dp, top.dp).size(89.dp, 29.dp)
        .background(theme.color(ColorGroup.CONTROLS, ColorValue.BACKGROUND))
        .border(1.dp, theme.color(ColorGroup.CONTROLS, ColorValue.BORDER_EXTERIOR))
    if (enabled) {
        modifier = modifier.clickable(onClick = onClick)
    }
    var color = theme.color(ColorGroup.CONTROLS, ColorValue.TEXT)
    if (!enabled) {
        color = theme.color(ColorGroup.CONTROLS, ColorValue.TEXT_DIM)
    }
    Box(modifier = modifier) {
        BasicText(
            caption,
            style = TextStyle(fontSize = FONT_SIZE, color = color, textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth().offset(0.dp, 6.dp),
        )
    }
}
