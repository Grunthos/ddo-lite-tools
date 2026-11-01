package ddolite.prototype.skills.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import ddolite.builder.data.LoadLog
import ddolite.builder.data.loadBuilderData
import ddolite.common.theme.Theme
import ddolite.common.theme.ThemeFileKind
import ddolite.common.theme.loadTheme
import ddolite.dataformat.DirectoryDataSource
import ddolite.dataformat.decodeText
import ddolite.prototype.skills.SkillsScreen
import ddolite.prototype.skills.SkillsScreenState
import ddolite.prototype.skills.prototypePresets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Desktop window for the skills screen prototype (plan step F1).
 *
 * Reads Data/ and Settings/ from the repository root (passed by Gradle as `ddolite.repoRoot`,
 * or the first argument). Run with `./gradlew :prototypes:skills-desktop:run`.
 */
fun main(args: Array<String>) {
    var root = System.getProperty("ddolite.repoRoot", "..")
    if (args.isNotEmpty()) {
        root = args[0]
    }
    val repoRoot = Path.of(root)

    val log = LoadLog()
    val data = loadBuilderData(DirectoryDataSource(repoRoot.resolve("Data")), log)
    for (issue in log.issues) {
        println(issue.message)
    }
    val themes = loadThemes(repoRoot.resolve("Settings"))
    val state = SkillsScreenState(data, themes, prototypePresets())

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Skills – Kotlin prototype",
            state = rememberWindowState(width = 1100.dp, height = 820.dp),
        ) {
            SkillsScreen(state)
        }
    }
}

/** Every .screen file in [settings], with "Black" first (VB's default theme, clsConfig.LoadColors). */
private fun loadThemes(settings: Path): List<Theme> {
    val themes = ArrayList<Theme>()
    Files.newDirectoryStream(settings, "*.screen").use { files ->
        for (file in files) {
            val name = file.fileName.toString().removeSuffix(".screen")
            val result = loadTheme(decodeText(Files.readAllBytes(file)).text, name, ThemeFileKind.SCREEN)
            if (result.valid) {
                themes.add(result.theme)
            }
        }
    }
    themes.sortBy { it.name }
    for (i in themes.indices) {
        if (themes[i].name == "Black") {
            themes.add(0, themes.removeAt(i))
            break
        }
    }
    if (themes.isEmpty()) {
        themes.add(Theme("Default"))
    }
    return themes
}
