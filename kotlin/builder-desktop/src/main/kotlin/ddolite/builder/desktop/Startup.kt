package ddolite.builder.desktop

import ddolite.builder.build.readBuildFile
import ddolite.builder.build.writeBuildFile
import ddolite.builder.data.BuilderData
import ddolite.builder.ui.BuilderSession
import java.awt.Frame
import java.io.File
import java.nio.file.Path
import javax.swing.JOptionPane

/**
 * The autosaved backup. Ported from basDocuments.bas: SaveBackup (:302), LoadBackup (:313),
 * DeleteBackup (:348) and BackupName (:355).
 *
 * Difference from VB: VB's backup is Backup.bld, a binary build; this version writes its own,
 * Backup-Kotlin.build, as a text build, and leaves VB's alone (as it does Settings.txt).
 */
class BuildBackup(root: Path, private val data: BuilderData) {
    val file: File = root.resolve("Backup-Kotlin.build").toFile()

    /** When the last backup was written (VB's msngLastBackup), in milliseconds. */
    private var last = 0L

    /** SaveBackup: the build, no more than once a second (the caller checks it has changes). */
    fun save(state: BuilderSession) {
        val now = System.currentTimeMillis()
        if (now >= last && now - last < 1000) {
            return
        }
        last = now
        val rules = state.prepareSave() ?: return
        try {
            if (file.exists()) {
                file.delete()
            }
            file.writeBytes(writeBuildFile(rules))
        } catch (e: Exception) {
            // VB's SaveBuild reports nothing for a backup it can't write either.
        }
    }

    /** DeleteBackup. */
    fun delete() {
        if (file.exists()) {
            file.delete()
        }
    }

    /**
     * LoadBackup: with a backup there, "A backup build was autosaved. Open it now?" (AskAlways, Yes
     * the default); yes opens it unsaved and with no file, no deletes it. True if opened.
     */
    fun load(parent: Frame, state: BuilderSession): Boolean {
        if (!file.exists()) {
            return false
        }
        val message = "A backup build was autosaved. Open it now?\n\nIf you select No, the backup build will be deleted."
        val answer = JOptionPane.showOptionDialog(
            parent, message, "Notice", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, arrayOf("Yes", "No"), "Yes",
        )
        if (answer != 0) {
            delete()
            return false
        }
        val bytes: ByteArray
        try {
            bytes = file.readBytes()
        } catch (e: Exception) {
            return false
        }
        state.restoreBackup(readBuildFile(bytes, data, state.settings.buildPoints))
        return true
    }
}

/**
 * The build named on the command line, as CheckCommandLine (basDocuments.bas:327) finds it: at
 * least 5 characters ending ".bld" or ".build"; a name with no drive or leading "\" is looked for
 * in the program's folder, then in Save; a ".bld" with no such file is tried as ".build"
 * (cfg.CheckExtension, clsConfig.cls:2788). Null if there's no such file.
 *
 * Differences from VB: VB gets the command line as one string, quotes and all, so a quoted name
 * (as Windows passes a double-clicked file) never matched; here the arguments come without
 * quotes. A ".bld" found here can't be opened (binary builds aren't supported).
 */
fun commandLineBuild(command: String, root: File): File? {
    if (command.length < 5) {
        return null
    }
    val lower = command.lowercase()
    if (!lower.endsWith(".bld") && !lower.endsWith(".build")) {
        return null
    }
    var name = command
    if (!name.contains(":") && !name.startsWith("\\") && !name.startsWith("/")) {
        var folder = root
        if (!checkExtension(File(folder, name)).exists()) {
            folder = File(root, "Save")
        }
        name = File(folder, name).path
    }
    val found = checkExtension(File(name))
    if (!found.exists()) {
        return null
    }
    return found
}

/** cfg.CheckExtension: a missing ".bld" file as ".build" if that exists. */
private fun checkExtension(file: File): File {
    if (file.exists() || !file.name.lowercase().endsWith(".bld")) {
        return file
    }
    val build = File(file.path.substring(0, file.path.length - 3) + "build")
    return if (build.exists()) build else file
}
