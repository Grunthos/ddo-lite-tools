package ddolite.builder.android

import android.content.res.AssetManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import ddolite.builder.data.LoadLog
import ddolite.builder.data.loadBuilderData
import ddolite.common.theme.Theme
import ddolite.common.theme.ThemeFileKind
import ddolite.common.theme.loadTheme
import ddolite.dataformat.DataSource
import ddolite.dataformat.decodeText
import ddolite.builder.ui.BuilderPrompts
import ddolite.builder.ui.PrototypeScreen
import ddolite.builder.ui.BuilderSession
import ddolite.builder.ui.SkillsScreenState
import ddolite.builder.ui.TreeSlotsState
import ddolite.builder.ui.prototypePresets
import ddolite.builder.ui.readTreeHeaders

/**
 * The skills screen prototype on Android (plan F1, prototype 4). Uses exactly the same
 * SkillsScreen and SkillsScreenState as the desktop prototype; only the data source differs.
 *
 * Touch: a tap acts as a left click (add a point; on a skill name, fill), and holding repeats.
 * Removing points needs the touch alternatives from plan §2a.5, which aren't built yet.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dataSource = AssetDataSource(assets)
        val data = loadBuilderData(dataSource, LoadLog())
        val skills = SkillsScreenState(data, loadThemes(assets), prototypePresets())
        val state = BuilderSession(data, skills, TreeSlotsState(data, readTreeHeaders(dataSource), skills), AndroidPrompts)
        setContent {
            PrototypeScreen(state)
        }
    }
}

/**
 * The Android prototype has no Overview screen yet, so nothing asks questions: everything is
 * answered yes and notices go to the log. Real dialogs come with the Android screens (phase G).
 */
object AndroidPrompts : BuilderPrompts {
    override fun confirm(message: String): Boolean = true

    override fun notice(message: String) {
        android.util.Log.i("Builder", message)
    }

    override fun yesNoCancel(message: String, title: String): Boolean? = true

    override fun chooseLevel(min: Int, max: Int): Int? = min
}

/**
 * Reads data files bundled in the app's assets: the mobile [DataSource] from plan §2a.3.
 * The build copies the repository's Data folder into the assets, so paths are the same as on
 * desktop ("Builder/Classes.txt").
 */
class AssetDataSource(private val assets: AssetManager) : DataSource {
    override fun read(path: String): ByteArray? {
        try {
            assets.open(path).use { input ->
                return input.readBytes()
            }
        } catch (e: java.io.IOException) {
            return null
        }
    }

    override fun list(directory: String): List<String> {
        val names = assets.list(directory)
        val result = ArrayList<String>()
        if (names == null) {
            return result
        }
        for (name in names) {
            if (directory.isEmpty()) {
                result.add(name)
            } else {
                result.add(directory.trimEnd('/') + "/" + name)
            }
        }
        result.sort()
        return result
    }
}

/** The bundled .screen themes, with "Black" (VB's default) first. */
private fun loadThemes(assets: AssetManager): List<Theme> {
    val source = AssetDataSource(assets)
    val themes = ArrayList<Theme>()
    for (path in source.list("")) {
        if (!path.endsWith(".screen")) {
            continue
        }
        val bytes = source.read(path)
        if (bytes == null) {
            continue
        }
        val result = loadTheme(decodeText(bytes).text, path.removeSuffix(".screen"), ThemeFileKind.SCREEN)
        if (result.valid) {
            if (result.theme.name == "Black") {
                themes.add(0, result.theme)
            } else {
                themes.add(result.theme)
            }
        }
    }
    if (themes.isEmpty()) {
        themes.add(Theme("Default"))
    }
    return themes
}
