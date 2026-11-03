package ddolite.builder.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ddolite.builder.build.Build
import ddolite.builder.build.BuildRules
import ddolite.builder.build.CascadeChange
import ddolite.builder.build.DeprecationMessage
import ddolite.builder.build.DeprecationMessages
import ddolite.builder.build.Deprecations
import ddolite.builder.build.DetailLine
import ddolite.builder.build.MessageGroup
import ddolite.builder.model.Feat

/**
 * The state behind the Messages window: VB's frmDeprecate (Source/Forms/Build/frmDeprecate.frm),
 * which lists what an opened build file had that the current data doesn't, and lets each missing
 * feat be replaced. The rules are in builder-core's [DeprecationMessages].
 *
 * VB sets a ListBox's ListIndex from code and that raises its Click event; the same calls are
 * made here at the same points. The controls' Enabled flags are set only where VB's code sets
 * them, so they can be stale as in VB (e.g. Choose stays enabled after unticking Best Guess).
 *
 * VB's global `gtypDeprecate` lives here as [deprecations]. As in VB it's only replaced when a
 * build file is opened, so after File › New or Close, Tools › Messages still offers the old
 * build's messages.
 */
class MessagesScreenState(private val session: BuilderSession) {
    /** gtypDeprecate. */
    var deprecations = Deprecations()
        private set

    /** gtypDeprecate.Deprecated: Tools › Messages is shown (frmMain.UpdateToolsMenu, :603). */
    var deprecated by mutableStateOf(false)
        private set

    /** The window is open. */
    var isOpen by mutableStateOf(false)
        private set

    /** lblIssues. */
    var label by mutableStateOf("")
        private set

    /** lstMessage and its highlighted row (-1 = none). */
    var messages: List<DeprecationMessage> by mutableStateOf(emptyList())
        private set
    var messageIndex by mutableIntStateOf(-1)
        private set

    /** usrDetails. */
    var details: List<DetailLine> by mutableStateOf(emptyList())
        private set

    /** usrchkGuess ("Best Guess"); ticked when the window opens (the control's default). */
    var bestGuess by mutableStateOf(true)
        private set

    /** lstGuess (the feats offered) and its highlighted row. */
    var guesses: List<Feat> by mutableStateOf(emptyList())
        private set
    var guessIndex by mutableIntStateOf(-1)
        private set

    /** lstSelector (the chosen feat's selectors) and its highlighted row. */
    var selectors: List<String> by mutableStateOf(emptyList())
        private set
    var selectorIndex by mutableIntStateOf(-1)
        private set

    /** chkIgnore's caption: "Ignore" for a feat, "OK" for a notice. */
    var ignoreCaption by mutableStateOf("Ignore")
        private set

    // Visible (ShowControls, :502); the form's defaults until a message is shown.
    var chooseVisible by mutableStateOf(true)
        private set
    var bestGuessVisible by mutableStateOf(true)
        private set
    var selectorButtonVisible by mutableStateOf(true)
        private set
    var selectorsLabelVisible by mutableStateOf(true)
        private set
    var selectorListVisible by mutableStateOf(true)
        private set

    // Enabled (EnableControls, :530, and the Click handlers); the form's defaults to start with.
    var detailsLabelEnabled by mutableStateOf(true)
        private set
    var ignoreEnabled by mutableStateOf(false)
        private set
    var bestGuessEnabled by mutableStateOf(true)
        private set
    var chooseEnabled by mutableStateOf(true)
        private set
    var guessListEnabled by mutableStateOf(false)
        private set
    var selectorButtonEnabled by mutableStateOf(false)
        private set
    var selectorsLabelEnabled by mutableStateOf(true)
        private set
    var selectorListEnabled by mutableStateOf(false)
        private set

    /** The rules for the open build; with none open, an empty build, as VB's cleared `build`. */
    private fun rules(): DeprecationMessages {
        val rules = session.rules ?: BuildRules(session.data, Build())
        return DeprecationMessages(rules, deprecations)
    }

    private fun selected(): DeprecationMessage? {
        if (messageIndex < 0 || messageIndex >= messages.size) {
            return null
        }
        return messages[messageIndex]
    }

    /**
     * A build file was opened: LoadBuild (basDocuments.bas:521) closes this window and starts a
     * new gtypDeprecate (DeprecateInit); once the build is shown, tmrDeprecate (frmMain.frm:673)
     * calls ShowDeprecated.
     */
    fun buildOpened(loaded: Deprecations) {
        close()
        deprecations = loaded
        deprecated = loaded.any
        showDeprecated()
    }

    /** Tools › Messages: ShowDeprecated (basDeprecate.bas:97). */
    fun showDeprecated() {
        if (deprecated) {
            open()
        } else {
            close()
        }
    }

    /** OpenForm "frmDeprecate": Form_Load (:217) the first time; a window already open is left as it is. */
    private fun open() {
        if (isOpen) {
            return
        }
        bestGuess = true
        showMessages()
        isOpen = true
    }

    /** The window closed (by the user, CloseForm "All", or with nothing left to resolve). */
    fun close() {
        isOpen = false
    }

    /** ShowMessages (:239). */
    private fun showMessages() {
        messages = emptyList()
        messageIndex = -1
        details = emptyList()
        guesses = emptyList()
        guessIndex = -1
        selectors = emptyList()
        selectorIndex = -1
        val rules = rules()
        label = rules.label()
        messages = rules.messages()
        if (messages.isNotEmpty()) {
            clickMessage(0)
        }
        enableControls()
    }

    /** A click on lstMessage, or its ListIndex set from code: lstMessage_Click (:420). */
    fun clickMessage(index: Int) {
        messageIndex = index
        val message = selected() ?: return
        var passive = true
        var caption = "OK"
        details = rules().details(message)
        if (message.group == MessageGroup.FEAT) {
            listFeats()
            caption = "Ignore"
            passive = false
        }
        ignoreCaption = caption
        showControls(message.group, passive)
        enableControls()
    }

    /** ShowControls (:502). */
    private fun showControls(group: MessageGroup, passive: Boolean) {
        if (passive) {
            guesses = emptyList()
            guessIndex = -1
            selectors = emptyList()
            selectorIndex = -1
        }
        val feat = group == MessageGroup.FEAT || group == MessageGroup.NAME_CHANGE
        chooseVisible = !passive
        bestGuessVisible = feat && !passive
        selectorButtonVisible = feat && !passive
        selectorsLabelVisible = !passive
        selectorListVisible = feat
        if (guesses.isNotEmpty()) {
            selectGuess(0)
        }
    }

    /** EnableControls (:530). */
    private fun enableControls() {
        val enabled = selected() != null
        detailsLabelEnabled = enabled
        ignoreEnabled = enabled
        bestGuessEnabled = guesses.isNotEmpty()
        chooseEnabled = guessIndex != -1
        guessListEnabled = guesses.isNotEmpty()
        selectorButtonEnabled = selectorIndex != -1
        selectorsLabelEnabled = selectors.isNotEmpty() // the caption is always "Selectors" here
        selectorListEnabled = selectors.isNotEmpty()
    }

    /** ListFeats (:561): Best Guess's nearest names, or every selectable feat with none highlighted. */
    private fun listFeats() {
        guesses = emptyList()
        guessIndex = -1
        val message = selected()
        if (bestGuess) {
            if (message == null) {
                return
            }
            guesses = rules().similarFeats(deprecations.feats[message.index - 1].featName)
            selectGuess(0)
        } else {
            guesses = rules().selectableFeats()
        }
    }

    /** Setting lstGuess.ListIndex from code: Click is raised only when it changes. */
    private fun selectGuess(index: Int) {
        if (index == guessIndex) {
            return
        }
        guessIndex = index
        guessClicked()
    }

    /** "Best Guess" ticked or unticked: usrchkGuess_UserChange (:731). */
    fun toggleBestGuess() {
        bestGuess = !bestGuess
        listFeats()
    }

    /** A click on lstGuess. */
    fun clickGuess(index: Int) {
        guessIndex = index
        guessClicked()
    }

    /** lstGuess_Click (:735). */
    private fun guessClicked() {
        val message = selected()
        if (message != null && message.group == MessageGroup.FEAT) {
            clickFeat()
        }
        enableControls()
    }

    /** ClickFeat (:747): list the highlighted feat's selectors. */
    private fun clickFeat() {
        chooseEnabled = guessIndex != -1
        val names = ArrayList<String>()
        if (guessIndex >= 0 && guessIndex < guesses.size) {
            for (selector in guesses[guessIndex].selectors) {
                names.add(selector.name)
            }
        }
        selectors = names
        selectorIndex = -1
        chooseEnabled = guessIndex != -1
    }

    /** A click on lstSelector: lstSelector_Click (:778). */
    fun clickSelector(index: Int) {
        selectorIndex = index
        selectorButtonEnabled = selectorIndex != -1
    }

    /** chkIgnore ("Ignore" or "OK"): chkIgnore_Click (:782). */
    fun ignore() {
        resolveMessage()
        if (isOpen) {
            showMessages()
        }
    }

    /** chkChoose, under the feats: chkChoose_Click (:788). */
    fun choose() {
        val message = selected() ?: return
        if (message.group == MessageGroup.FEAT) {
            chooseFeat(message)
        }
    }

    /** chkSelector, under the selectors: chkSelector_Click (:800), the same as Choose for a feat. */
    fun chooseSelector() {
        val message = selected() ?: return
        if (message.group == MessageGroup.FEAT) {
            chooseFeat(message)
        }
    }

    /** ChooseFeat (:654). */
    private fun chooseFeat(message: DeprecationMessage) {
        var notice = ""
        if (guessIndex == -1) {
            notice = "Choose a feat."
        } else if (selectors.isNotEmpty() && selectorIndex == -1) {
            notice = "Choose a selector."
        }
        if (notice.isNotEmpty()) {
            session.prompts.notice(notice)
            return
        }
        rules().chooseFeat(message.index, guesses[guessIndex].name, selectorIndex + 1)
        updateBuild()
    }

    /**
     * UpdateBuild (:811) with cceFeat: CascadeChanges, then resolve the message and list the rest,
     * and SetDirty.
     *
     * VB quirk, kept: CascadeChanges cceFeat doesn't refresh an open Feats window (it skips the
     * window that made a feat change), so the Feats window still shows the slot empty until
     * something else refreshes it.
     */
    private fun updateBuild() {
        val rules = session.rules
        if (rules != null) {
            rules.cascadeChanges(CascadeChange.FEAT)
            session.buildChanged(CascadeChange.FEAT)
        } else {
            session.markDirty()
        }
        resolveMessage()
        if (isOpen) {
            showMessages()
        }
    }

    /**
     * ResolveMessage (:819): drop the message; with none left, Tools › Messages goes and the
     * window closes.
     *
     * Not copied: VB goes on to call ShowMessages on the form it has just unloaded, which loads it
     * again, hidden.
     */
    private fun resolveMessage() {
        val message = selected()
        val rules = rules()
        if (message != null) {
            rules.resolve(message)
        }
        val (issues, notices) = rules.countMessages()
        val none = issues + notices == 0
        details = emptyList()
        guesses = emptyList()
        guessIndex = -1
        selectors = emptyList()
        selectorIndex = -1
        if (none) {
            deprecated = false
            close()
        }
    }
}
