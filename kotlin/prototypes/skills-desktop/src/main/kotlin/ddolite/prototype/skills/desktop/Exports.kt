package ddolite.prototype.skills.desktop

import ddolite.builder.output.GuideExport
import ddolite.dataformat.Windows1252
import ddolite.prototype.skills.PrototypeState
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import javax.swing.JOptionPane

/**
 * The desktop's part of the exports: the clipboard, save dialogs and files. Text is saved as
 * Windows-1252, as VB's `xp.File.SaveStringAs` writes it.
 */
class Exports(private val files: BuildFiles) {
    /** Copy to Clipboard (frmExport.lnkNav_MouseDown, :311). */
    fun copy(text: String) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }

    /** Save to File (frmExport, :317): "<build name>.txt" in the Save folder. */
    fun saveText(parent: Frame, state: PrototypeState, text: String) {
        val name = dosName(state.opened?.build?.buildName ?: "") + ".txt"
        val chosen = askSaveFile(parent, name, "txt") ?: return
        write(parent, chosen, text)
    }

    /**
     * File › Export › Leveling Guide › csv or txt, and the guide tab's Export link. Ported from
     * ExportGuide (basDocuments.bas:363): asks before overwriting, writes csv or txt by the name
     * chosen, then opens the file.
     */
    fun exportGuide(parent: Frame, state: PrototypeState, extension: String) {
        val rules = state.rules
        if (rules == null || rules.guideEnhancements.isEmpty()) {
            state.prompts.notice("Nothing to Export")
            return
        }
        val suggested = GuideExport.suggestedName(files.file?.nameWithoutExtension, rules.build.buildName, extension)
        val chosen = askSaveFile(parent, suggested, extension) ?: return
        if (chosen.exists() && !state.prompts.confirm(chosen.name + " exists. Overwrite?")) {
            return
        }
        val text = when (chosen.extension.lowercase()) {
            "txt" -> GuideExport.txt(rules)
            "csv" -> GuideExport.csv(rules)
            else -> ""
        }
        if (write(parent, chosen, text)) {
            try {
                Desktop.getDesktop().open(chosen)
            } catch (e: Exception) {
                // VB's xp.File.Run ignores failures too.
            }
        }
    }

    private fun askSaveFile(parent: Frame, suggested: String, extension: String): File? {
        val dialog = FileDialog(parent, "Save As", FileDialog.SAVE)
        dialog.directory = files.folder.path
        dialog.file = suggested
        dialog.isVisible = true
        if (dialog.file == null) {
            return null
        }
        var name = dialog.file
        if (!name.contains('.')) {
            name = "$name.$extension"
        }
        return File(dialog.directory, name)
    }

    private fun write(parent: Frame, target: File, text: String): Boolean {
        try {
            target.writeBytes(Windows1252.encode(text))
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(parent, "Can't save " + target.name + ":\n" + e.message, "Save", JOptionPane.ERROR_MESSAGE)
            return false
        }
        return true
    }
}
