package ddolite.prototype.skills.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ddolite.builder.build.BuildPoints
import ddolite.builder.build.readBuildFile
import ddolite.builder.build.writeBuildFile
import ddolite.builder.data.BuilderData
import ddolite.prototype.skills.PrototypeState
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JOptionPane

/**
 * File > Open, Save and Save As for .build files. Ported from basDocuments.BuildOpen (Build/basDocuments.bas:45),
 * BuildSave (:112), BuildSaveAs (:126) and SaveAsDialog (:142), without what the prototype doesn't have yet:
 * the "save changes?" check (CheckDirty), the recent-files list (MRU), .bld files, and the reserved
 * Save\Binary folder. The file dialogs are AWT's, which are the system's own and ask before overwriting.
 *
 * @property folder the folder the dialogs start in (VB's cfg.LitePath), remembered for this run only.
 */
class BuildFiles(private val data: BuilderData, var folder: File) {
    /** The file the open build came from or was last saved to (VB's mstrFile); null if none. */
    var file: File? by mutableStateOf(null)
        private set

    /** Asks for a .build file and opens it. VB's BuildOpen → OpenDialog → LoadBuild. */
    fun open(parent: Frame, state: PrototypeState) {
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
        state.openBuild(result)
        file = chosen
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

    /** Saves to the file the build came from, or asks for one. VB's BuildSave. */
    fun save(parent: Frame, state: PrototypeState) {
        val current = file
        if (current == null) {
            saveAs(parent, state)
        } else {
            write(parent, state, current)
        }
    }

    /** Asks for a file name, starting from the build's name, and saves. VB's BuildSaveAs → SaveAsDialog. */
    fun saveAs(parent: Frame, state: PrototypeState) {
        val opened = state.opened
        if (opened == null) {
            return
        }
        val dialog = FileDialog(parent, "Save As", FileDialog.SAVE)
        dialog.directory = folder.path
        dialog.file = dosName(opened.build.buildName) + ".build"
        dialog.isVisible = true
        if (dialog.file == null) {
            return
        }
        var name = dialog.file
        if (!name.lowercase().endsWith(".build")) {
            name = name + ".build"
        }
        val chosen = File(dialog.directory, name)
        folder = chosen.parentFile
        if (write(parent, state, chosen)) {
            file = chosen
        }
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
 * A build name made safe for a file name. Ported from clsFile.MakeNameDOS (Source/Classes/clsFile.cls:391).
 * Quirk kept: "+" is removed although Windows allows it.
 */
private fun dosName(name: String): String {
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
