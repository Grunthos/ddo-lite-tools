package ddolite.builder.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ddolite.builder.build.BuildPoints
import ddolite.builder.build.BuildLoadResult
import ddolite.builder.build.readBuildFile
import ddolite.builder.build.writeBuildFile
import ddolite.builder.data.BuilderData
import ddolite.builder.imports.PlannerImport
import ddolite.dataformat.Windows1252
import ddolite.builder.ui.BuilderPrompts
import ddolite.builder.ui.BuilderSession
import java.awt.Component
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

/**
 * File > New, Open, Close, Save and Save As for .build files. Ported from basDocuments
 * (Build/basDocuments.bas): BuildNew (:32), BuildOpen (:45), BuildClose (:99), BuildSave (:112),
 * BuildSaveAs (:126), SaveAsDialog (:142), CheckDirty (:228) and PromptName (:236). Not yet: the
 * recent-files list (MRU), .bld files, the reserved Save\Binary folder and the autosaved backup.
 * The file dialogs are AWT's, which are the system's own and ask before overwriting.
 *
 * @property folder the folder the dialogs start in (VB's cfg.LitePath), remembered for this run only.
 */
class BuildFiles(private val data: BuilderData, var folder: File) {
    /** The file the open build came from or was last saved to (VB's mstrFile); null if none. */
    var file: File? by mutableStateOf(null)
        private set

    /** A new build. Returns false if the user cancelled at "Save changes?". */
    fun new(parent: Frame, state: BuilderSession): Boolean {
        if (checkDirty(parent, state)) {
            return false
        }
        file = null
        state.newBuild(state.settings.buildPoints) // cfg.BuildPoints
        return true
    }

    /** Asks for a .build file and opens it. VB's BuildOpen → OpenDialog → LoadBuild. */
    fun open(parent: Frame, state: BuilderSession) {
        if (checkDirty(parent, state)) {
            return
        }
        val dialog = FileDialog(parent, "Open", FileDialog.LOAD)
        dialog.directory = folder.path
        dialog.file = "*.build"
        dialog.setFilenameFilter { _, name -> name.lowercase().endsWith(".build") }
        dialog.isVisible = true
        if (dialog.file == null) {
            return
        }
        val chosen = File(dialog.directory, dialog.file)
        folder = chosen.parentFile
        openFile(parent, state, chosen)
    }

    /**
     * Opens a recent file: File › 1., 2. … (frmMain.MRUOpen, :703, and BuildOpenMRU,
     * basDocuments.bas:78). A file that's gone asks "File not found. Remove from MRU list?".
     */
    fun openRecent(parent: Frame, state: BuilderSession, path: String) {
        if (checkDirty(parent, state)) {
            return
        }
        val chosen = File(path)
        if (!chosen.isFile) {
            if (state.prompts.confirm("File not found. Remove from MRU list?")) {
                state.settings.mruList.remove(path)
            }
            return
        }
        openFile(parent, state, chosen)
    }

    /**
     * The build named on the command line (CheckCommandLine → BuildOpen, basDocuments.bas:45): opened
     * as File › Open opens one. A binary .bld can't be read yet.
     */
    fun openCommandLine(parent: Frame, state: BuilderSession, chosen: File) {
        if (checkDirty(parent, state)) {
            return
        }
        if (chosen.name.lowercase().endsWith(".bld")) {
            state.prompts.notice("Binary builds (.bld) can't be opened by this version.")
            return
        }
        openFile(parent, state, chosen)
    }

    /** Reads [chosen] and shows it, adding it to the recent files (LoadBuild, :521, with cfg.AddMRU). */
    private fun openFile(parent: Frame, state: BuilderSession, chosen: File) {
        val bytes: ByteArray
        try {
            bytes = chosen.readBytes()
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(parent, "Can't read " + chosen.name + ":\n" + e.message, "Open", JOptionPane.ERROR_MESSAGE)
            return
        }
        val result: BuildLoadResult
        try {
            result = readBuildFile(bytes, data, state.settings.buildPoints)
        } catch (e: Exception) {
            // VB's LoadBuild error handler shows the error; the reader reports VB's own errors as problems,
            // so this is only for the unexpected.
            JOptionPane.showMessageDialog(parent, "Can't open " + chosen.name + ":\n" + e, "Open", JOptionPane.ERROR_MESSAGE)
            return
        }
        file = chosen
        state.settings.addRecent(chosen.path)
        state.openBuild(result)
        if (result.problems.isNotEmpty()) {
            // VB asks "Ignore error and load anyway?" for each one; the prototype carries on and lists them.
            JOptionPane.showMessageDialog(
                parent,
                result.problems.size.toString() + " line(s) couldn't be read; VB would have asked whether to carry on.\n" +
                    "They're listed in the main window.",
                "Open",
                JOptionPane.WARNING_MESSAGE,
            )
        }
    }

    /** The folders the Ron's Planner and DDO Builder import dialogs start in (VB's cfg.RonPath, cfg.BuilderPath), for this run. */
    private var ronFolder = folder
    private var builderFolder = folder

    /**
     * File › Import › from Ron's Planner or from DDO Builder: ImportFileRon (basFormatRon.bas:333) and
     * ImportFileBuilder (basFormatBuilder.bas:286) with OpenDialog (basDocuments.bas:59). The build
     * replaces the open one, unsaved and with no file (ClearBuild).
     */
    fun importPlanner(parent: Frame, state: BuilderSession, builder: Boolean) {
        if (checkDirty(parent, state)) {
            return
        }
        val extension = if (builder) "ddocp" else "txt"
        val dialog = FileDialog(parent, if (builder) "DDO Builder Files" else "Ron's Planner Files", FileDialog.LOAD)
        dialog.directory = (if (builder) builderFolder else ronFolder).path
        dialog.file = "*.$extension"
        dialog.setFilenameFilter { _, name -> name.lowercase().endsWith(".$extension") }
        dialog.isVisible = true
        if (dialog.file == null) {
            return
        }
        val chosen = File(dialog.directory, dialog.file)
        if (builder) {
            builderFolder = chosen.parentFile
        } else {
            ronFolder = chosen.parentFile
        }
        val text: String
        try {
            text = Windows1252.decode(chosen.readBytes())
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(parent, "Can't read " + chosen.name + ":\n" + e.message, "Import", JOptionPane.ERROR_MESSAGE)
            return
        }
        val preferred = state.settings.buildPoints
        val result: BuildLoadResult
        try {
            result = if (builder) PlannerImport.builder(text, data, preferred) else PlannerImport.ron(text, chosen.path, data, preferred)
        } catch (e: Exception) {
            // VB has no error handler here and stops; this reports it instead (crashes aren't copied).
            JOptionPane.showMessageDialog(parent, "Can't import " + chosen.name + ":\n" + e, "Import", JOptionPane.ERROR_MESSAGE)
            return
        }
        file = null
        state.importBuild(result)
    }

    /** The open build no longer has a file (ClearBuild's mstrFile), as after an import. */
    fun forgetFile() {
        file = null
    }

    /** No build open. Returns false if the user cancelled at "Save changes?". */
    fun close(parent: Frame, state: BuilderSession): Boolean {
        if (checkDirty(parent, state)) {
            return false
        }
        file = null
        state.closeBuild()
        return true
    }

    /** Saves to the file the build came from, or asks for one. VB's BuildSave; true if cancelled. */
    fun save(parent: Frame, state: BuilderSession): Boolean {
        val current = file
        if (current == null) {
            return saveAs(parent, state)
        }
        if (!write(parent, state, current)) {
            return true
        }
        state.settings.addRecent(current.path) // BuildSave (basDocuments.bas:120)
        state.dirty = false
        return false
    }

    /** Asks for a file name, starting from the build's name, and saves. VB's BuildSaveAs → SaveAsDialog; true if cancelled. */
    fun saveAs(parent: Frame, state: BuilderSession): Boolean {
        val opened = state.opened
        if (opened == null) {
            return true
        }
        val dialog = FileDialog(parent, "Save As", FileDialog.SAVE)
        dialog.directory = folder.path
        dialog.file = dosName(opened.build.buildName) + ".build"
        dialog.isVisible = true
        if (dialog.file == null) {
            return true
        }
        var name = dialog.file
        if (!name.lowercase().endsWith(".build")) {
            name = name + ".build"
        }
        val chosen = File(dialog.directory, name)
        folder = chosen.parentFile
        if (!write(parent, state, chosen)) {
            return true
        }
        file = chosen
        state.settings.addRecent(chosen.path) // BuildSaveAs (basDocuments.bas:138)
        state.dirty = false
        return false
    }

    /**
     * "Save changes to <name>?" with Yes, No and Cancel, if there are unsaved changes. Returns
     * true if the user cancelled (or cancelled the save). Ported from CheckDirty (basDocuments.bas:228),
     * which always asks (AskCancel with pblnAlwaysAsk, basUtils.bas:80: No is the default).
     */
    fun checkDirty(parent: Frame, state: BuilderSession): Boolean {
        if (!state.dirty) {
            return false
        }
        val options = arrayOf("Yes", "No", "Cancel")
        val answer = JOptionPane.showOptionDialog(
            parent,
            "Save changes to " + promptName(state) + "?",
            "Notice",
            JOptionPane.YES_NO_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[1],
        )
        if (answer == 0) {
            return save(parent, state)
        }
        return answer != 1
    }

    /** The build's name, or the file's if it differs. Ported from PromptName (basDocuments.bas:236). */
    private fun promptName(state: BuilderSession): String {
        val buildName = state.opened?.build?.buildName ?: ""
        val current = file
        if (current == null || current.nameWithoutExtension == buildName) {
            return buildName
        }
        return current.name
    }

    private fun write(parent: Frame, state: BuilderSession, target: File): Boolean {
        val rules = state.prepareSave()
        if (rules == null) {
            return false
        }
        try {
            target.writeBytes(writeBuildFile(rules))
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(parent, "Can't save " + target.name + ":\n" + e.message, "Save", JOptionPane.ERROR_MESSAGE)
            return false
        }
        return true
    }
}

/**
 * VB's message boxes as Swing dialogs over the window [parent] returns. Ported from
 * basUtils.AskAlways (:72, Yes/No with No as the default) and Notice (:91); both are titled "Notice".
 */
class DesktopPrompts(private val parent: () -> Component?) : BuilderPrompts {
    override fun confirm(message: String): Boolean {
        val options = arrayOf("Yes", "No")
        val answer = JOptionPane.showOptionDialog(
            parent(),
            message,
            "Notice",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[1],
        )
        return answer == 0
    }

    override fun notice(message: String) {
        JOptionPane.showMessageDialog(parent(), message, "Notice", JOptionPane.INFORMATION_MESSAGE)
    }

    override fun yesNoCancel(message: String, title: String): Boolean? {
        val options = arrayOf("Yes", "No", "Cancel")
        val answer = JOptionPane.showOptionDialog(
            parent(),
            message,
            title,
            JOptionPane.YES_NO_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[2],
        )
        if (answer == 0) {
            return true
        }
        if (answer == 1) {
            return false
        }
        return null
    }

    override fun chooseLevel(min: Int, max: Int): Int? {
        // frmLevel: "Exchange feat at level:" with a spinner from min to max; Cancel gives none,
        // closing the window keeps the level shown, as VB.
        val spinner = JSpinner(SpinnerNumberModel(min, min, max, 1))
        val panel = JPanel()
        panel.add(JLabel("Exchange feat at level:"))
        panel.add(spinner)
        val options = arrayOf("OK", "Cancel")
        val answer = JOptionPane.showOptionDialog(parent(), panel, "Exchange Feat", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0])
        if (answer == 1) {
            return null
        }
        return spinner.value as Int
    }
}

/**
 * A build name made safe for a file name. Ported from clsFile.MakeNameDOS (Source/Classes/clsFile.cls:391).
 * Quirk kept: "+" is removed although Windows allows it.
 */
internal fun dosName(name: String): String {
    var file = name.replace(":", "-")
    file = file.replace("\\", "")
    file = file.replace("/", "")
    file = file.replace("*", "")
    file = file.replace("?", "")
    file = file.replace("+", "")
    file = file.replace("\"", "'")
    file = file.replace("<", "(")
    file = file.replace(">", ")")
    file = file.replace("|", "")
    return file
}
