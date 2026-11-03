package ddolite.prototype.skills

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
class PrototypeState(
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

    /** Unsaved changes (VB's cfg.Dirty). */
    var dirty by mutableStateOf(false)

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

    /** Shows an opened build in every screen. */
    fun openBuild(result: BuildLoadResult) {
        show(result)
        dirty = false
        messages.buildOpened(result.deprecations)
    }

    /** No build open (basDocuments.BuildClose, :99). */
    fun closeBuild() {
        opened = null
        dirty = false
        revision++
    }

    private fun show(result: BuildLoadResult) {
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
     * The build changed. [change] is what VB passes to CascadeChanges, or null for a change that
     * doesn't cascade (the name, the notes). The build rules have already run; this refreshes the
     * screens, marks the build dirty and lets the app close windows that no longer apply.
     */
    fun buildChanged(change: CascadeChange?) {
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
        revision++
        export.refreshIfChanged() // SetDirty: frmExport.Cascade
        if (change != null) {
            onCascade(change)
        }
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
        revision++
        export.refreshIfChanged() // SetDirty: frmExport.Cascade
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
        revision++
        export.refreshIfChanged() // SetDirty: frmExport.Cascade
    }

    /** The Enhancements window changed the trees or AP: SetDirty, and keep the demo's copy in step. */
    fun enhancementsEdited() {
        val result = opened ?: return
        trees.openBuild(result.build)
        dirty = true
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
