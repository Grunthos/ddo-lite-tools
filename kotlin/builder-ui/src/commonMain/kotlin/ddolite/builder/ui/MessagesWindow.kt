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

/** frmDeprecate's client area, pixels at 100%. */
private const val MESSAGES_WIDTH = 815
private const val MESSAGES_HEIGHT = 517

/*
 * The layout, from the content corner (WindowFrame): the caption and the messages list across the
 * window; under it three columns a gap apart (the details, the best guesses, the selectors), each
 * with its caption and button above its box. The boxes are all the same height.
 */
private const val MESSAGES_LIST_TOP = LINE + TIGHT
private const val MESSAGES_LIST_HEIGHT = 156
private const val COLUMN_BUTTON_TOP = MESSAGES_LIST_TOP + MESSAGES_LIST_HEIGHT + 24
private const val COLUMN_LABEL_TOP = COLUMN_BUTTON_TOP + 10
private const val COLUMN_BOX_TOP = COLUMN_BUTTON_TOP + 28
private const val COLUMN_BOX_HEIGHT = 218
private const val PUSH_BUTTON_WIDTH = 89

/**
 * The Messages window (Source/Forms/Build/frmDeprecate.frm): what the opened build file had that
 * the current data doesn't. Each missing feat can be replaced: Best Guess offers the nearest
 * names (or, unticked, every selectable feat), then Choose (or Choose under the selectors) puts
 * it in the slot; Ignore leaves the slot empty. Notices (renamed feats, missing enhancements, a
 * cut-short leveling guide) only have OK.
 *
 * In a [WindowFrame] (no footer); the client area is 815 x 517 at 100%. VB's positions are in
 * frmDeprecate.frm; here the three columns share the width.
 */
@Composable
fun MessagesWindowContent(state: BuilderSession) {
    val screen = state.messages
    val theme = state.skills.theme
    val drag = remember { DragDropState<Int>() }
    val workspace = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT)
    val dim = theme.color(ColorGroup.WORKSPACE, ColorValue.TEXT_DIM)

    WindowFrame(
        theme, MESSAGES_WIDTH, MESSAGES_HEIGHT,
        header = {
            // usrHeader_Click (:229): Help only.
            HeaderBar(theme, emptyList(), null, listOf("Help"), isEnabled = { true }, onClick = { state.help.show("Messages") })
        },
        footer = null,
    ) { area ->
        val columnWidth = (area.width - 2 * GAP) / 3
        val guessLeft = columnWidth + GAP
        val selectorLeft = 2 * (columnWidth + GAP)

        Label(screen.label, TextStyle(platformStyle = TEXT_RENDERING, fontSize = FONT_SIZE, color = workspace), 0, 0)
        val texts = ArrayList<String>()
        for (message in screen.messages) {
            texts.add(message.text)
        }
        ListBox(
            theme, texts, isSelected = { it == screen.messageIndex }, left = 0, top = MESSAGES_LIST_TOP, width = area.width, height = MESSAGES_LIST_HEIGHT,
            drag = drag, onPress = { i, _ -> screen.clickMessage(i) },
        )

        // lblDetails (EnableLabel dims it with no message chosen), chkIgnore, the details.
        Label("Details", TextStyle(platformStyle = TEXT_RENDERING, fontSize = FONT_SIZE, color = if (screen.detailsLabelEnabled) workspace else dim), 0, COLUMN_LABEL_TOP)
        PushButton(theme, screen.ignoreCaption, screen.ignoreEnabled, columnWidth - PUSH_BUTTON_WIDTH, COLUMN_BUTTON_TOP) { screen.ignore() }
        DetailsBox(theme, screen.details, 0, COLUMN_BOX_TOP, columnWidth, COLUMN_BOX_HEIGHT)

        // usrchkGuess, chkChoose and lstGuess.
        if (screen.bestGuessVisible) {
            CheckBox(theme, "Best Guess", screen.bestGuess, screen.bestGuessEnabled, guessLeft, COLUMN_LABEL_TOP - 2, 137) { screen.toggleBestGuess() }
        }
        if (screen.chooseVisible) {
            PushButton(theme, "Choose", screen.chooseEnabled, guessLeft + columnWidth - PUSH_BUTTON_WIDTH, COLUMN_BUTTON_TOP) { screen.choose() }
        }
        val names = ArrayList<String>()
        for (feat in screen.guesses) {
            names.add(feat.name)
        }
        ListBox(
            theme, names, isSelected = { it == screen.guessIndex }, left = guessLeft, top = COLUMN_BOX_TOP, width = columnWidth, height = COLUMN_BOX_HEIGHT,
            drag = drag, onPress = { i, _ -> screen.clickGuess(i) }, enabled = screen.guessListEnabled,
        )

        // lblSelectors, chkSelector, and lstSelector over usrInfo (which only binary builds' War Soul uses).
        if (screen.selectorsLabelVisible) {
            Label("Selectors", TextStyle(platformStyle = TEXT_RENDERING, fontSize = FONT_SIZE, color = if (screen.selectorsLabelEnabled) workspace else dim), selectorLeft, COLUMN_LABEL_TOP)
        }
        if (screen.selectorButtonVisible) {
            PushButton(theme, "Choose", screen.selectorButtonEnabled, selectorLeft + columnWidth - PUSH_BUTTON_WIDTH, COLUMN_BUTTON_TOP) { screen.chooseSelector() }
        }
        DetailsBox(theme, emptyList(), selectorLeft, COLUMN_BOX_TOP, columnWidth, COLUMN_BOX_HEIGHT)
        if (screen.selectorListVisible) {
            ListBox(
                theme, screen.selectors, isSelected = { it == screen.selectorIndex }, left = selectorLeft, top = COLUMN_BOX_TOP, width = columnWidth, height = COLUMN_BOX_HEIGHT,
                drag = drag, onPress = { i, _ -> screen.clickSelector(i) }, enabled = screen.selectorListEnabled,
            )
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
            style = TextStyle(platformStyle = TEXT_RENDERING, fontSize = FONT_SIZE, color = color, textAlign = TextAlign.Center),
            modifier = Modifier.fillMaxWidth().offset(0.dp, 6.dp),
        )
    }
}
