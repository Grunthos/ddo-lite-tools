package ddolite.prototype.skills

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ddolite.builder.build.BuildPoints
import ddolite.builder.build.CascadeChange
import ddolite.builder.build.StatRules
import ddolite.builder.data.LoadLog
import ddolite.builder.model.CharacterClass
import ddolite.builder.model.Stat
import ddolite.builder.model.StatTemplate

/**
 * The state behind the Stats window: frmStats' module variables and event handlers
 * (Source/Forms/Build/frmStats.frm) and the userStats columns' (Source/Controls/userStats.ctl).
 * The rules are in builder-core's [StatRules]; every change ends with VB's StatsChanged
 * (frmStats.frm:984): CascadeChanges cceStats, SetDirty.
 */
class StatsScreenState(private val session: PrototypeState) {
    /** The Schedule tab is showing (picSchedule.Visible). */
    var scheduleTab by mutableStateOf(false)

    /** The class whose templates are offered (VB's menTemplateClass). */
    var templateClass: CharacterClass? by mutableStateOf(null)
        private set

    /** The template button whose warning is showing in place of its description (ShowWarning); -1 = none. */
    var warningIndex by mutableIntStateOf(-1)

    /** Bumped when a warning appears, so the screen restarts its 5-second timer (tmrWarning). */
    var warningRevision by mutableIntStateOf(0)
        private set

    /** Bumped when the templates are reloaded (Customize › Reload Templates). */
    var templatesRevision by mutableIntStateOf(0)
        private set

    val rules: StatRules?
        get() {
            val r = session.rules ?: return null
            return StatRules(r)
        }

    /**
     * A build was opened, or a change cascaded to this window: VB's Cascade (frmStats.frm:977)
     * with LoadData (:1075), EnforceLevelMax (:1116) and DefaultTemplates (:1255).
     */
    fun refresh() {
        val r = rules ?: return
        r.recountPoints()
        r.loadDataIncludes()
        r.enforceTomeMax()
        templateClass = r.defaultTemplateClass()
        warningIndex = -1
    }

    private fun statsChanged() {
        val r = session.rules ?: return
        r.cascadeChanges(CascadeChange.STATS)
        session.buildChanged(CascadeChange.STATS)
    }

    /** usrHeader_Click (:995). */
    fun showTab(schedule: Boolean) {
        scheduleTab = schedule
    }

    // ---------------------------------------------------------------- point-buy columns

    /** A stat spinner in [column] asked to go up or down (userStats.usrSpinner_RequestChange, userStats.ctl:548). */
    fun changeStat(column: BuildPoints, stat: Stat, up: Boolean) {
        val r = rules ?: return
        val changed = if (up) r.increase(column, stat) else r.decrease(column, stat)
        if (changed) {
            statsChanged()
        }
    }

    /** cboBuildPoints_Click (:1154). */
    fun choosePreferred(column: BuildPoints) {
        val r = rules ?: return
        r.setPreferred(column)
        r.enforceTomeMax()
        statsChanged()
    }

    /** A column's Include box (userStats.usrchkInclude_UserChange, :536). */
    fun setIncluded(column: BuildPoints, include: Boolean) {
        val r = rules ?: return
        r.setIncluded(column, include)
        statsChanged()
    }

    // ---------------------------------------------------------------- level-ups and tomes

    /** cboLevelup_Click (:1202). */
    fun chooseLevelup(index: Int, stat: Stat?) {
        val r = rules ?: return
        r.setLevelup(index, stat)
        statsChanged()
    }

    /** usrSpinner_Change (:1237); [stat] null is the Supreme tome. */
    fun setTome(stat: Stat?, value: Int) {
        val r = rules ?: return
        r.setTome(stat, value)
        statsChanged()
    }

    /**
     * The footer's "Clear Stats" (tmrFocus_Timer, :1024): "Keep tomes?" (Yes / No / Cancel) if
     * there are tomes, otherwise "Clear stats and levelups?".
     */
    fun clearStats() {
        val r = rules ?: return
        var keepTomes = true
        if (r.hasTomes()) {
            val answer = session.prompts.yesNoCancel("Keep tomes?", "Clear Stats") ?: return
            keepTomes = answer
        } else if (!session.ask("Clear stats and levelups?")) {
            return
        }
        // TODO: the preferred point-buy column comes from the settings (cfg.BuildPoints); Adventurer for now.
        r.clearStats(keepTomes, BuildPoints.ADVENTURER)
        r.enforceTomeMax()
        warningIndex = -1
        statsChanged()
    }

    // ---------------------------------------------------------------- templates

    /** mnuCustomize (frmStats.frm:904), in order; "-" is a separator. */
    val customizeItems = listOf("Edit Custom Templates", "Reload Templates", "-", "Delete Custom Templates", "-", "Edit System Templates")

    /** lnkCustomize_MouseDown (:1429): Delete Custom Templates only when there are custom templates. */
    fun customizeEnabled(index: Int): Boolean {
        if (customizeItems[index] == "Delete Custom Templates") {
            return session.templateFiles?.userExists() == true
        }
        return true
    }

    /** mnuCustomize_Click (:1439). */
    fun customize(index: Int) {
        val files = session.templateFiles ?: return
        when (customizeItems[index]) {
            "Edit Custom Templates" -> files.editUser()
            "Delete Custom Templates" -> if (files.deleteUser()) {
                reloadTemplates()
            }
            "Edit System Templates" -> files.editSystem()
            "Reload Templates" -> reloadTemplates()
        }
    }

    /**
     * ReloadTemplates (:1497): the templates read again (LoadTemplates, through DataFile's choice of
     * file), the error log written and shown if there were errors (CreateErrorLog True), then
     * DefaultTemplates.
     */
    private fun reloadTemplates() {
        val files = session.templateFiles ?: return
        val log = LoadLog()
        session.data.reloadTemplates(files.templatesText(), log)
        files.showErrorLog(log)
        val r = rules
        if (r != null) {
            templateClass = r.defaultTemplateClass()
        }
        warningIndex = -1
        templatesRevision++
    }

    fun templates(): List<StatTemplate> = rules?.templatesFor(templateClass) ?: emptyList()

    /** mnuTemplate_Click (:1407): show another class's templates. */
    fun chooseTemplateClass(c: CharacterClass) {
        templateClass = c
        warningIndex = -1
    }

    /** chkTemplate_Click (:1318): apply the template, and show its warning for a few seconds. */
    fun applyTemplate(index: Int) {
        val r = rules ?: return
        val list = templates()
        if (index < 0 || index >= list.size) {
            session.prompts.notice("Unknown template")
            return
        }
        r.applyTemplate(list[index])
        r.enforceTomeMax()
        statsChanged()
        warningIndex = -1
        if (list[index].warning.isNotEmpty()) {
            warningIndex = index
        }
        warningRevision++
    }

    /** tmrWarning_Timer (:1354). */
    fun warningTimedOut() {
        warningIndex = -1
    }
}
