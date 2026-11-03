package ddolite.builder.desktop

import ddolite.builder.settings.BuilderSettings
import ddolite.dataformat.Windows1252
import ddolite.dataformat.decodeText
import java.nio.file.Files
import java.nio.file.Path

/**
 * Where the settings live. VB keeps them in Settings.txt next to the program (clsConfig.SettingsFile,
 * :401), shared by the tools. This version reads its own file, Settings-Kotlin.txt, in the same
 * place; with no such file yet it starts from VB's Settings.txt, if there is one, else the defaults.
 * It only ever writes its own file, so VB's settings are never changed.
 */
class SettingsStore(private val root: Path) {
    val kotlinFile: Path = root.resolve("Settings-Kotlin.txt")
    val vbFile: Path = root.resolve("Settings.txt")

    /** Where the settings came from, for the record: this program's file, VB's, or none. */
    var source = "defaults"
        private set

    fun load(): BuilderSettings {
        for (file in listOf(kotlinFile, vbFile)) {
            if (Files.isRegularFile(file)) {
                try {
                    val settings = BuilderSettings.parse(decodeText(Files.readAllBytes(file)).text)
                    source = file.fileName.toString()
                    return settings
                } catch (e: Exception) {
                    println("Can't read " + file + ": " + e.message)
                }
            }
        }
        source = "defaults"
        return BuilderSettings.defaults()
    }

    /** SaveSettings: the whole file, as Windows-1252 text (as VB's xp.File.SaveStringAs). */
    fun save(settings: BuilderSettings) {
        try {
            Files.write(kotlinFile, Windows1252.encode(settings.write()))
        } catch (e: Exception) {
            println("Can't save " + kotlinFile + ": " + e.message)
        }
    }
}
