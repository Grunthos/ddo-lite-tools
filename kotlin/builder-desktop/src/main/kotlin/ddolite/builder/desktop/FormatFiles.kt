package ddolite.builder.desktop

import ddolite.dataformat.Vb
import ddolite.dataformat.Windows1252
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JOptionPane

/**
 * The format editor's file dialogs (frmFormat.FormatLoad, :484, and FormatSave, :499): "Output
 * Formats" (*.bbcodes) in the Settings folder. [mode] is FileDialog.LOAD or SAVE; a saved name gets
 * ".bbcodes" if it has no extension (the dialog's default extension). The system's save dialog asks
 * before overwriting, as VB's AskAlways does. Null if cancelled.
 */
internal fun chooseFormatFile(parent: Frame?, folder: File, mode: Int): File? {
    val dialog = FileDialog(parent, "Output Formats", mode)
    dialog.directory = folder.path
    dialog.file = "*.bbcodes"
    dialog.setFilenameFilter { _, name -> name.lowercase().endsWith(".bbcodes") }
    dialog.isVisible = true
    val name = dialog.file ?: return null
    var chosen = File(dialog.directory, name)
    if (mode == FileDialog.SAVE && !name.contains('.')) {
        chosen = File(dialog.directory, "$name.bbcodes")
    }
    return chosen
}

/** SaveFormat (clsConfig.cls:2678): replaces the file with the text, in Windows-1252 as xp.File.SaveStringAs writes. */
internal fun writeFormatFile(file: File, text: String) {
    try {
        if (file.exists()) {
            file.delete()
        }
        file.writeBytes(Windows1252.encode(Vb.saveStringAs(text)))
    } catch (e: Exception) {
        JOptionPane.showMessageDialog(null, "Can't save " + file.name + ":\n" + e.message, "Save As", JOptionPane.ERROR_MESSAGE)
    }
}
