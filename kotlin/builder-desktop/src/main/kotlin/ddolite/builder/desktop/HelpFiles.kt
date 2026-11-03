package ddolite.builder.desktop

import ddolite.builder.data.LoadLog
import ddolite.common.help.HelpText
import ddolite.dataformat.Windows1252
import ddolite.dataformat.decodeText
import ddolite.builder.ui.BuilderSession
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

/**
 * The desktop's part of Help and the error log: the help file, web pages, files opened with their
 * program, and Error-Kotlin.log next to the program (VB's Error.log, which this version leaves alone,
 * as it does Settings.txt).
 */
class HelpFiles(private val root: Path) {
    val errorLog: File = root.resolve("Error-Kotlin.log").toFile()

    /** InitHelp (basHelp.bas:39): Data/Builder/Help.txt, or no topics if it's missing. */
    fun loadHelp(): HelpText {
        val file = root.resolve("Data").resolve("Builder").resolve("Help.txt")
        if (!Files.exists(file)) {
            return HelpText(emptyList())
        }
        return HelpText.parse(decodeText(Files.readAllBytes(file)).text)
    }

    /**
     * CreateErrorLog (basErrorLog.bas:260): deletes the old log, writes the new one if there were
     * errors, and opens it if the settings say to. True when there is a log (Tools › Error Log shows).
     */
    fun createErrorLog(log: LoadLog, show: Boolean): Boolean {
        if (errorLog.exists()) {
            errorLog.delete()
        }
        val text = log.errorLogText()
        if (text.isEmpty()) {
            return false
        }
        try {
            errorLog.writeBytes(Windows1252.encode(text))
        } catch (e: Exception) {
            return false
        }
        if (show) {
            open(errorLog)
        }
        return true
    }

    /** ViewErrorLog (basErrorLog.bas:278): opens the log, or says there were no errors. */
    fun viewErrorLog(state: BuilderSession, notifyWhenMissing: Boolean) {
        if (errorLog.exists()) {
            open(errorLog)
        } else if (notifyWhenMissing) {
            state.prompts.notice("No errors at startup.")
        }
    }

    /** xp.OpenURL. */
    fun openUrl(url: String) {
        try {
            Desktop.getDesktop().browse(URI(url))
        } catch (e: Exception) {
            // VB ignores failures too.
        }
    }

    /**
     * A help "run=" link (frmHelp.lnkLink_MouseDown, frmHelp.frm:357): "\" means the program's folder.
     * The VB files this version doesn't use are swapped for its own: \Settings.txt for
     * Settings-Kotlin.txt and \Error.log for Error-Kotlin.log.
     */
    fun run(state: BuilderSession, value: String) {
        var file: File
        if (value.startsWith("\\")) {
            var path = value.substring(1)
            if (path.equals("Settings.txt", ignoreCase = true)) {
                path = "Settings-Kotlin.txt"
            }
            if (path.equals("Error.log", ignoreCase = true)) {
                path = errorLog.name
            }
            file = root.toFile()
            for (part in path.split("\\")) {
                file = File(file, part)
            }
        } else {
            file = File(value)
        }
        if (!file.exists()) {
            if (file == errorLog) {
                state.prompts.notice("There were no errors on startup.")
            } else {
                state.prompts.notice("$value not found")
            }
            return
        }
        open(file)
    }

    private fun open(file: File) {
        try {
            Desktop.getDesktop().open(file)
        } catch (e: Exception) {
            // VB's xp.File.Run ignores failures too.
        }
    }
}
