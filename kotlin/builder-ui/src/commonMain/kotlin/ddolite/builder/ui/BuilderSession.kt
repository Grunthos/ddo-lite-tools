package ddolite.builder.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ddolite.builder.build.Build
import ddolite.builder.build.BuildLoadResult
import ddolite.builder.build.BuildPoints
import ddolite.builder.build.BuildRules
import ddolite.builder.build.CascadeChange
import ddolite.builder.build.Deprecations
import ddolite.builder.build.HEROIC_LEVELS
import ddolite.builder.build.StatRules
import ddolite.builder.model.Stat
import ddolite.builder.data.BuilderData

/**
 * Questions and notices a screen needs to show. VB calls `AskAlways` (yes/no) and `Notice`
 * (a message box) from anywhere; each platform supplies its own dialogs.
 */
interface BuilderPrompts {
    /** A yes/no question; true for yes. */
    fun confirm(message: String): Boolean

    /** A message with an OK button. */
    fun notice(message: String)

    /** Yes, No or Cancel (the default), titled [title]: true, false, or null for Cancel. */
    fun yesNoCancel(message: String, title: String): Boolean?

    /**
     * frmLevel ("Exchange Feat"): a level from [min] to [max], starting at [min]; null for Cancel.
     * VB's dialog returns the shown level if closed with the window's X (only Cancel gives 0).
     */
    fun chooseLevel(min: Int, max: Int): Int?
}

/**
 * The open build and the screens' state: VB's global `build` plus the document state in
 * basDocuments (mblnOpen, cfg.Dirty).
 *
 * Every change to the build goes through [buildChanged], which bumps [revision]; the windows read
 * it so they redraw, which stands in for VB's CascadeChanges calling each open form's Cascade.
 */
class BuilderSession(
    val data: BuilderData,
    val skills: SkillsScreenState,
    val trees: TreeSlotsState,
    val prompts: BuilderPrompts,
) {
    /** 0 = skills grid, 1 = enhancement tree slots (the single-window prototype on Android). */
    var tab by mutableIntStateOf(0)

    /** The open build, with what loading it reported; null when no build is open (VB: mblnOpen False). */
    var opened: BuildLoadResult? by mutableStateOf(null)
        private set

    /** Bumped after every change to the open build. */
    var revision by mutableIntStateOf(0)
        private set

    private var dirtyState by mutableStateOf(false)

    /**
     * Unsaved changes (VB's cfg.Dirty). Setting it false is SetDirty False (basDocuments.bas:214),
     * which deletes the autosaved backup ([onDeleteBackup]).
     */
    var dirty: Boolean
        get() = dirtyState
        set(value) {
            dirtyState = value
            if (!value) {
                history.markSaved()
                onDeleteBackup()
            }
        }

    /** Undo and redo for the open build (new: VB has none). */
    val history = ddolite.builder.build.BuildHistory(data)

    /** Bumped when the history changes, so Edit › Undo and Redo enable and disable. */
    var historyRevision by mutableIntStateOf(0)
        private set

    /** The platform's autosave: write the backup (SaveBackup's SaveBuild), and delete it (DeleteBackup). */
    var onSaveBackup: () -> Unit = {}
    var onDeleteBackup: () -> Unit = {}

    /**
     * SaveBackup (basDocuments.bas:302): autosaves the build when it has unsaved changes. VB calls it
     * when a build window closes (UnloadForm) and when some tabs, lists and combos change.
     */
    fun saveBackup() {
        if (dirty) {
            onSaveBackup()
        }
    }

    /**
     * Which window's sections the main window's output shows (basOutput's menRemember): each Edit
     * window sets it when it's activated (ActivateForm), and it goes back to all of them when the
     * last one closes or a build is opened, started or closed.
     */
    var outputView by mutableStateOf(ddolite.builder.output.OutputView.ALL)

    /** The Enhancements window is open (the output puts the leveling guide first when it's on that tab). */
    var enhancementsOpen by mutableStateOf(false)

    /** The program's settings (VB's cfg): the app reads and saves them; the screens read them here. */
    var settings = ddolite.builder.settings.BuilderSettings()

    /** The output formats there are (the .bbcodes files in Settings); the app sets these at start. */
    var formats = OutputFormats(emptyList())

    /** The output format in use (VB's `mtypOutput`, "Default" to start) and its name (`cfg.BBCodes`). */
    var outputFormat by mutableStateOf(ddolite.builder.output.OutputFormat.DEFAULT)
    var formatName by mutableStateOf("Default")

    /** The Export window (frmExport). */
    val export = ExportScreenState(this)

    /** The Help window (frmHelp). */
    val help = HelpScreenState(this)

    /** A link in the main window'"'"'s output was clicked: the section name ("Feats", "Skills", …); the app opens its window. */
    var onOutputLink: (String) -> Unit = {}

    /** The template files (Stats › Customize); the app sets this. */
    var templateFiles: TemplateFiles? = null

    /** The format editor (frmFormat). */
    val format = FormatScreenState(this)

    /** File › Import › from Forums (frmImport). */
    val imports = ImportScreenState(this)

    /** Called after a change, with what changed, so the app can close windows that no longer make sense. */
    var onCascade: (CascadeChange) -> Unit = {}

    val overview = OverviewScreenState(this)

    val stats = StatsScreenState(this)

    val feats = FeatsScreenState(this)

    val enhancements = EnhancementsScreenState(this)

    val spells = SpellsScreenState(this)

    val destiny = DestinyScreenState(this)

    /** The Messages window (frmDeprecate) and VB's gtypDeprecate. */
    val messages = MessagesScreenState(this)

    init {
        skills.onChanged = { skillsChanged() }
        trees.onChanged = { storeTrees() }
    }

    /** The open build's rules, or null. */
    val rules: BuildRules?
        get() = opened?.rules

    /**
     * A new, empty build. Ported from basDocuments.BuildNew (Build/basDocuments.bas:32) with
     * ClearBuild and SetBuildDefaults; the app then opens the Overview window, as VB does.
     */
    fun newBuild(preferred: BuildPoints) {
        val build = Build()
        val rules = BuildRules(data, build)
        rules.setBuildDefaults(preferred)
        rules.cascadeAll()
        show(BuildLoadResult(build, rules, Deprecations(), emptyList()))
        dirty = false
    }

    /**
     * Shows an imported build: basDocuments.BuildWasImported (basDocuments.bas:290), or frmImport's
     * ShowBuild: open, CascadeChanges cceAll, and unsaved (SetDirty True).
     */
    fun importBuild(result: BuildLoadResult) {
        show(result)
        dirty = true
        history.markUnsaved()
    }

    /** Shows an opened build in every screen. */
    fun openBuild(result: BuildLoadResult) {
        show(result)
        dirty = false
        messages.buildOpened(result.deprecations)
    }

    /**
     * Shows the autosaved backup: LoadBackup (basDocuments.bas:313) opens it with LoadBuild, whose
     * SetDirty False deletes the backup file, then SetDirty marks the build unsaved. The next autosave
     * writes the file again.
     */
    fun restoreBackup(result: BuildLoadResult) {
        openBuild(result)
        dirty = true
        history.markUnsaved()
    }

    /** No build open (basDocuments.BuildClose, :99). */
    fun closeBuild() {
        opened = null
        dirty = false
        history.clear()
        historyRevision++
        revision++
    }

    /** Shows a newly opened, new or imported build; its undo history starts again. */
    private fun show(result: BuildLoadResult) {
        history.reset(result.build)
        historyRevision++
        display(result)
    }

    /** Every screen shows [result]. */
    private fun display(result: BuildLoadResult) {
        opened = result
        overview.opened()
        stats.refresh()
        refreshScreens()
        feats.cascade()
        enhancements.cascade()
        spells.cascade()
        destiny.cascade()
        revision++
        onCascade(CascadeChange.ALL)
    }

    /**
     * After every change to the build: its state goes into the undo history. The Skills grid keeps
     * its own copy of the points, so they'"'"'re copied back first.
     */
    private fun recordChange(mergeKey: String? = null) {
        val result = opened ?: return
        skills.storeInto(result.build)
        history.record(result.build, mergeKey)
        historyRevision++
    }

    /** Edit › Undo: the build as it was before the last change. */
    fun undo() {
        showHistory(history.undo())
    }

    /** Edit › Redo: the change undone last, again. */
    fun redo() {
        showHistory(history.redo())
    }

    /**
     * Shows a state from the history in every window, as if just opened (selections are cleared and
     * windows that no longer apply close). It'"'"'s unsaved unless it'"'"'s the state last saved.
     */
    private fun showHistory(state: BuildRules?) {
        if (state == null) {
            return
        }
        display(BuildLoadResult(state.build, state, Deprecations(), emptyList()))
        if (history.atSaved) {
            dirtyState = false
            onDeleteBackup()
        } else {
            dirtyState = true
        }
        export.refreshIfChanged()
        historyRevision++
    }

    /**
     * The build changed. [change] is what VB passes to CascadeChanges, or null for a change that
     * doesn't cascade (the name, the notes). The build rules have already run; this refreshes the
     * screens, marks the build dirty and lets the app close windows that no longer apply.
     */
    fun buildChanged(change: CascadeChange?, mergeKey: String? = null) { // [mergeKey]: see BuildHistory.record
        // VB cascades to the Stats window only for these (CascadeChanges, basMain.bas:282).
        if (change == CascadeChange.ALL || change == CascadeChange.CLASS || change == CascadeChange.RACE || change == CascadeChange.MAX_LEVEL) {
            stats.refresh()
        }
        refreshScreens()
        // VB cascades to the Feats window after every change but its own.
        if (change != CascadeChange.FEAT) {
            feats.cascade()
        }
        // VB cascades to the Spells window after every change (basMain.bas:309).
        spells.cascade()
        // And to the Destinies window (basMain.bas:315).
        destiny.cascade()
        // And to the Enhancements window for these (basMain.bas:311).
        if (change == CascadeChange.ALL || change == CascadeChange.RACE || change == CascadeChange.CLASS ||
            change == CascadeChange.MAX_LEVEL || change == CascadeChange.FEAT || change == CascadeChange.ENHANCEMENTS
        ) {
            enhancements.cascade()
        }
        dirty = true
        recordChange(mergeKey)
        revision++
        export.refreshIfChanged() // SetDirty: frmExport.Cascade
        if (change != null) {
            onCascade(change)
        }
    }

    /** The Skill Order option changed: the Skills screen's rows again. */
    fun skillsOrderChanged() {
        refreshScreens()
        revision++
    }

    private fun refreshScreens() {
        val result = opened
        if (result == null) {
            return
        }
        // InitBuildSkills (basBuild.bas:394) uses the Intelligence modifier at each level.
        val statRules = StatRules(result.rules)
        val intModifiers = ArrayList<Int>()
        for (level in 1..HEROIC_LEVELS) {
            intModifiers.add(statRules.stat(Stat.INT, level, modifier = true))
        }
        // frmSkills.Cascade: InitRows, in the Skill Order setting's order.
        val split = ArrayList<ddolite.builder.model.CharacterClass>()
        for (s in result.rules.classSplit()) {
            split.add(s.characterClass)
        }
        skills.orderRows(split, settings.skillOrderScreen == 1)
        skills.openBuild(result.build, result.rules.heroicLevels(), intModifiers)
        trees.openBuild(result.build)
        overview.refreshChoices()
    }

    /**
     * The Skills screen changed: SkillsChanged (frmSkills.frm:1177), CascadeChanges cceSkill, which
     * rechecks the feat slots and refreshes the Feats window.
     */
    private fun skillsChanged() {
        val result = opened ?: return
        skills.storeInto(result.build)
        dirty = true
        recordChange()
        revision++
        export.refreshIfChanged() // SetDirty: frmExport.Cascade
        val r = rules ?: return
        r.cascadeChanges(CascadeChange.SKILL)
        feats.cascade()
    }

    /**
     * The Android demo's tree slots changed: copy them into the build. (Only that screen keeps its
     * own copy of the trees; the Enhancements window edits the build directly.)
     */
    private fun storeTrees() {
        val result = opened ?: return
        trees.storeInto(result.rules)
        dirty = true
        recordChange()
        revision++
        export.refreshIfChanged() // SetDirty: frmExport.Cascade
    }

    /**
     * VB's `Ask` (basUtils.bas:60): the question only with Confirmation Prompts on (Options), else
     * yes. Questions VB always asks (`AskAlways`) call [prompts] directly.
     */
    fun ask(message: String): Boolean {
        if (!settings.confirm) {
            return true
        }
        return prompts.confirm(message)
    }

    /** Bumped when the settings change, so menus that show them (recent files) redraw. */
    var settingsRevision by mutableIntStateOf(0)
        private set

    /** The settings changed (the Options window). */
    fun settingsChanged() {
        settingsRevision++
    }

    /** Something the output depends on changed (colours, format, options) but not the build: redraw it. */
    fun markOutputChanged() {
        revision++
        export.refreshIfChanged()
    }

    /** A window changed the build without a cascade (VB's SetDirty alone). */
    fun markDirty() {
        dirty = true
        recordChange()
        revision++
        export.refreshIfChanged() // SetDirty: frmExport.Cascade
    }

    /** The Enhancements window changed the trees or AP: SetDirty, and keep the demo's copy in step. */
    fun enhancementsEdited() {
        val result = opened ?: return
        trees.openBuild(result.build)
        dirty = true
        recordChange()
        revision++
        export.refreshIfChanged() // SetDirty: frmExport.Cascade
    }

    /**
     * The open build's rules with the screens' changes copied back into the build, ready for
     * writeBuildFile; null if no build is open.
     */
    fun prepareSave(): BuildRules? {
        val result = opened
        if (result == null) {
            return null
        }
        skills.storeInto(result.build)
        return result.rules
    }
}

/**
 * The platform's part of Stats › Customize: the template files. Ported from frmStats.frm
 * (EditUserTemplates :1448, EditSystemTemplates :1467, DeleteCustomTemplates :1477) and
 * basDataLoad (DataFile :28, UserTemplateOlder :47, SystemTemplates :60, UserTemplates :64).
 */
interface TemplateFiles {
    /** The user's own Templates.txt exists. */
    fun userExists(): Boolean

    /** Edit Custom Templates: opens the user's file, first copying the shipped one if there's none. */
    fun editUser()

    /** Edit System Templates: opens the shipped file. */
    fun editSystem()

    /** Delete Custom Templates: asks "Delete your custom templates?"; true if it deleted the file. */
    fun deleteUser(): Boolean

    /** The text DataFile picks: the user's file unless it's older than the shipped one; null if none. */
    fun templatesText(): String?

    /** CreateErrorLog True: writes the error log and shows it, if there were errors. */
    fun showErrorLog(log: ddolite.builder.data.LoadLog)
}
