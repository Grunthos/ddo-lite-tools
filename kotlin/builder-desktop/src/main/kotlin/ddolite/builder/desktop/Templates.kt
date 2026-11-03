package ddolite.builder.desktop

import ddolite.builder.data.LoadLog
import ddolite.dataformat.decodeText
import ddolite.builder.ui.BuilderSession
import ddolite.builder.ui.TemplateFiles
import java.awt.Desktop
import java.io.File
import java.nio.file.Path
import javax.swing.JOptionPane

/**
 * The stat template files: the shipped Data\Builder\Templates.txt and the user's own Templates.txt
 * in cfg.LitePath, the folder the Open dialog last used ([userFolder]; the Save folder to start
 * with). Ported from basDataLoad (DataFile :28, UserTemplateOlder :47) and frmStats's Customize
 * menu (:1448-1503).
 *
 * VB detail kept: as LitePath follows the Open dialog, opening a build from another folder moves
 * where the custom templates are looked for.
 */
class DesktopTemplateFiles(
    root: Path,
    private val help: HelpFiles,
    private val userFolder: () -> File,
) : TemplateFiles {
    /** The session, for its notices; set once it exists (the files are read before, to load the data). */
    lateinit var state: BuilderSession

    private val system: File = root.resolve("Data").resolve("Builder").resolve("Templates.txt").toFile()

    private fun user(): File = File(userFolder(), "Templates.txt")

    override fun userExists(): Boolean = user().exists()

    /** UserTemplateOlder: the user's file is older than the shipped one. */
    fun userOlder(): Boolean {
        val user = user()
        return user.exists() && system.exists() && user.lastModified() < system.lastModified()
    }

    /** DataFile: the user's text, if the user's file is there and not older; else null (the shipped file). */
    fun userText(): String? {
        if (!user().exists() || userOlder()) {
            return null
        }
        return read(user())
    }

    override fun templatesText(): String? = userText() ?: read(system)

    private fun read(file: File): String? {
        try {
            return decodeText(file.readBytes()).text
        } catch (e: Exception) {
            return null
        }
    }

    /** EditUserTemplates (:1448). */
    override fun editUser() {
        val user = user()
        if (!user.exists()) {
            if (system.exists()) {
                try {
                    system.copyTo(user)
                } catch (e: Exception) {
                    // xp.File.Copy reports nothing either; the check below says it's not there.
                }
            } else {
                state.prompts.notice("System templates not found")
                return
            }
        }
        if (user.exists()) {
            open(user)
        } else {
            state.prompts.notice("User templates not found")
        }
    }

    /** EditSystemTemplates (:1467). */
    override fun editSystem() {
        if (system.exists()) {
            open(system)
        } else {
            state.prompts.notice("System templates not found")
        }
    }

    /** DeleteCustomTemplates (:1477): AskAlways, No the default. */
    override fun deleteUser(): Boolean {
        val user = user()
        if (!user.exists()) {
            return false
        }
        val options = arrayOf("Yes", "No")
        val answer = JOptionPane.showOptionDialog(
            null, "Delete your custom templates?", "Notice", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[1],
        )
        if (answer != 0) {
            return false
        }
        user.delete()
        return true
    }

    override fun showErrorLog(log: LoadLog) {
        help.createErrorLog(log, show = true)
    }

    private fun open(file: File) {
        try {
            Desktop.getDesktop().open(file)
        } catch (e: Exception) {
            // VB's xp.File.Run ignores failures too.
        }
    }
}
