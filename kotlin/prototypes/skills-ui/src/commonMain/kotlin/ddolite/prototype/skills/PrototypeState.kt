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

    /** Called after a change, with what changed, so the app can close windows that no longer make sense. */
    var onCascade: (CascadeChange) -> Unit = {}

    val overview = OverviewScreenState(this)

    val stats = StatsScreenState(this)

    init {
        skills.onChanged = { storeScreens() }
        trees.onChanged = { storeScreens() }
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
        dirty = true
        revision++
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

    /** The Skills and Enhancements screens changed: copy their state into the build. */
    private fun storeScreens() {
        val result = opened
        if (result == null) {
            return
        }
        skills.storeInto(result.build)
        trees.storeInto(result.rules)
        dirty = true
        revision++
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
        trees.storeInto(result.rules)
        return result.rules
    }
}
