package ddolite.prototype.skills.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ddolite.builder.build.BuildPoints
import ddolite.builder.build.readBuildFile
import ddolite.builder.build.writeBuildFile
import ddolite.builder.data.BuilderData
import ddolite.prototype.skills.BuilderPrompts
import ddolite.prototype.skills.PrototypeState
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
    fun new(parent: Frame, state: PrototypeState): Boolean {
        if (checkDirty(parent, state)) {
            return false
        }
        file = null
        // TODO: the preferred point-buy column comes from the settings (cfg.BuildPoints); Adventurer for now.
        state.newBuild(BuildPoints.ADVENTURER)
        return true
    }

    /** Asks for a .build file and opens it. VB's BuildOpen → OpenDialog → LoadBuild. */
    fun open(parent: Frame, state: PrototypeState) {
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
        val bytes: ByteArray
        try {
            bytes = chosen.readBytes()
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(parent, "Can't read " + chosen.name + ":\n" + e.message, "Open", JOptionPane.ERROR_MESSAGE)
            return
        }
        // TODO: the preferred point-buy column comes from the settings (cfg.BuildPoints); Adventurer for now.
        val result = readBuildFile(bytes, data, BuildPoints.ADVENTURER)
        file = chosen
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

    /** No build open. Returns false if the user cancelled at "Save changes?". */
    fun close(parent: Frame, state: PrototypeState): Boolean {
        if (checkDirty(parent, state)) {
            return false
        }
        file = null
        state.closeBuild()
        return true
    }

    /** Saves to the file the build came from, or asks for one. VB's BuildSave; true if cancelled. */
    fun save(parent: Frame, state: PrototypeState): Boolean {
        val current = file
        if (current == null) {
            return saveAs(parent, state)
        }
        if (!write(parent, state, current)) {
            return true
        }
        state.dirty = false
        return false
    }

    /** Asks for a file name, starting from the build's name, and saves. VB's BuildSaveAs → SaveAsDialog; true if cancelled. */
    fun saveAs(parent: Frame, state: PrototypeState): Boolean {
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
        state.dirty = false
        return false
    }

    /**
     * "Save changes to <name>?" with Yes, No and Cancel, if there are unsaved changes. Returns
     * true if the user cancelled (or cancelled the save). Ported from CheckDirty (basDocuments.bas:228),
     * which always asks (AskCancel with pblnAlwaysAsk, basUtils.bas:80: No is the default).
     */
    fun checkDirty(parent: Frame, state: PrototypeState): Boolean {
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
    private fun promptName(state: PrototypeState): String {
        val buildName = state.opened?.build?.buildName ?: ""
        val current = file
        if (current == null || current.nameWithoutExtension == buildName) {
            return buildName
        }
        return current.name
    }

    private fun write(parent: Frame, state: PrototypeState, target: File): Boolean {
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
