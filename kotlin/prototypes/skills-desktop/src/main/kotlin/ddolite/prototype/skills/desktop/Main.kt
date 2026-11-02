package ddolite.prototype.skills.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import ddolite.builder.data.LoadLog
import ddolite.builder.data.loadBuilderData
import ddolite.common.theme.Theme
import ddolite.common.theme.ThemeFileKind
import ddolite.common.theme.loadTheme
import ddolite.dataformat.DirectoryDataSource
import ddolite.dataformat.decodeText
import ddolite.prototype.skills.MainWindowContent
import ddolite.prototype.skills.PrototypeState
import ddolite.prototype.skills.SkillsScreenState
import ddolite.prototype.skills.SkillsWindowContent
import ddolite.prototype.skills.TreeSlotsState
import ddolite.prototype.skills.TreeSlotsWindowContent
import ddolite.prototype.skills.prototypePresets
import ddolite.prototype.skills.readTreeHeaders
import java.nio.file.Files
import java.nio.file.Path

/**
 * Desktop prototype (plan step F1), arranged like the VB Builder (PLAN.md §8.0): a main window
 * with File / Edit / Window menus and the output view, and each Edit-menu screen in its own window.
 * Only Skills and Enhancements exist in the prototype; the other Edit items are shown disabled.
 *
 * Reads Data/ and Settings/ from the folder found by [findAppRoot]. Run with
 * `./gradlew :prototypes:skills-desktop:run`, or build the portable Windows app with
 * `./gradlew :prototypes:skills-desktop:portableZip`.
 */
fun main(args: Array<String>) {
    val repoRoot = findAppRoot(args)

    val log = LoadLog()
    val dataSource = DirectoryDataSource(repoRoot.resolve("Data"))
    val data = loadBuilderData(dataSource, log)
    for (issue in log.issues) {
        println(issue.message)
    }
    val themes = loadThemes(repoRoot.resolve("Settings"))
    val skills = SkillsScreenState(data, themes, prototypePresets())
    val state = PrototypeState(skills, TreeSlotsState(data, readTreeHeaders(dataSource), skills))
    val files = BuildFiles(data, repoRoot.resolve("Save").toFile())

    application {
        BuilderWindows(state, files)
    }
}

/**
 * The Edit-menu screens, in frmMain's Edit menu order (frmMain.frm:311), with the menu mnemonic and
 * the main window's shortcut key (frmMain.Form_KeyDown, :751). [built] = exists in the prototype.
 */
private enum class EditScreen(val title: String, val mnemonic: Char, val key: Key, val built: Boolean) {
    OVERVIEW("Overview...", 'O', Key.O, false),
    STATS("Stats...", 'A', Key.T, false),
    SKILLS("Skills...", 'K', Key.K, true),
    FEATS("Feats...", 'F', Key.F, false),
    SPELLS("Spells...", 'P', Key.P, false),
    ENHANCEMENTS("Enhancements...", 'E', Key.E, true),
    DESTINY("Destiny...", 'D', Key.D, false),
}

/** Which screen windows are open, and their AWT windows (for bringing one to the front). */
private class OpenWindows {
    var skills by mutableStateOf(false)
    var enhancements by mutableStateOf(false)
    val awtWindows = HashMap<EditScreen, ComposeWindow>()

    /**
     * Opens a screen's window, or brings it to the front if it's already open.
     * Ported from basMain.OpenForm (Build/basMain.bas:131), without its gating checks: the
     * prototype's builds always have a race, classes and stats.
     */
    fun open(screen: EditScreen) {
        if (screen == EditScreen.SKILLS) {
            skills = true
        } else if (screen == EditScreen.ENHANCEMENTS) {
            enhancements = true
        }
        val existing = awtWindows[screen]
        if (existing != null) {
            existing.toFront()
        }
    }

    fun isOpen(screen: EditScreen): Boolean {
        if (screen == EditScreen.SKILLS) {
            return skills
        }
        if (screen == EditScreen.ENHANCEMENTS) {
            return enhancements
        }
        return false
    }
}

/**
 * Whether a screen can be opened: it exists in the prototype, and the Skills grid has levels to
 * show. (VB's OpenForm, basMain.bas:131, also refuses screens until the build has what they need.)
 */
private fun canOpen(screen: EditScreen, state: PrototypeState): Boolean {
    if (!screen.built) {
        return false
    }
    if (screen == EditScreen.SKILLS) {
        return state.skills.hasLevels()
    }
    return true
}

/**
 * The main window's title: the build's name, with the file name if it differs; the program's name
 * if the build has no name. Ported from basDocuments.SetAppCaption (Build/basDocuments.bas:172),
 * without the "*" for unsaved changes (the prototype doesn't track them yet).
 */
private fun appCaption(files: BuildFiles, state: PrototypeState): String {
    val opened = state.opened
    if (opened == null || opened.build.buildName.isEmpty()) {
        return "Character Builder Lite (Kotlin prototype)"
    }
    var caption = opened.build.buildName
    val file = files.file
    if (file != null && caption != file.nameWithoutExtension) {
        caption = caption + " (" + file.name + ")"
    }
    return caption
}

/**
 * Keyboard shortcuts on the main window: a plain letter opens a screen.
 * Ported from frmMain.Form_KeyDown (frmMain.frm:751); Escape (VB: unload frmMain) isn't copied.
 */
private fun mainWindowKey(event: KeyEvent, windows: OpenWindows, state: PrototypeState): Boolean {
    if (event.type != KeyEventType.KeyDown) {
        return false
    }
    if (event.isCtrlPressed || event.isAltPressed || event.isShiftPressed) {
        return false // VB: If Shift <> 0 Then Exit Sub
    }
    for (screen in EditScreen.entries) {
        if (canOpen(screen, state) && event.key == screen.key) {
            windows.open(screen)
            return true
        }
    }
    return false
}

/** The main window (menus + output view) and whichever screen windows are open. */
@Composable
private fun ApplicationScope.BuilderWindows(state: PrototypeState, files: BuildFiles) {
    val windows = remember { OpenWindows() }

    Window(
        onCloseRequest = ::exitApplication,
        title = appCaption(files, state),
        state = rememberWindowState(position = WindowPosition(40.dp, 40.dp), width = 460.dp, height = 640.dp),
        onKeyEvent = { event -> mainWindowKey(event, windows, state) },
    ) {
        MenuBar {
            // frmMain's File menu (frmMain.frm:160), with the items the prototype has.
            Menu("File", mnemonic = 'F') {
                Item("Open...", mnemonic = 'O', shortcut = KeyShortcut(Key.O, ctrl = true), onClick = { files.open(window, state) })
                Separator()
                Item("Save", mnemonic = 'S', shortcut = KeyShortcut(Key.S, ctrl = true), enabled = state.opened != null, onClick = { files.save(window, state) })
                Item("Save As...", mnemonic = 'A', enabled = state.opened != null, onClick = { files.saveAs(window, state) })
                Separator()
                Item("Exit", mnemonic = 'x', onClick = ::exitApplication)
            }
            Menu("Edit", mnemonic = 'E') {
                for (screen in EditScreen.entries) {
                    Item(screen.title, mnemonic = screen.mnemonic, enabled = canOpen(screen, state), onClick = { windows.open(screen) })
                }
            }
            // frmMain's Window menu lists the open screens (tmrWindowMenu_Timer, frmMain.frm:819).
            Menu("Window", mnemonic = 'W') {
                for (screen in EditScreen.entries) {
                    if (windows.isOpen(screen)) {
                        Item(screen.title.removeSuffix("..."), onClick = { windows.open(screen) })
                    }
                }
            }
        }
        MainWindowContent(state)
    }

    if (windows.skills) {
        ScreenWindow(windows, EditScreen.SKILLS, "Skills", 520, 40, 1000, 760, onClose = { windows.skills = false }) {
            SkillsWindowContent(state)
        }
    }
    if (windows.enhancements) {
        ScreenWindow(windows, EditScreen.ENHANCEMENTS, "Enhancements", 560, 80, 900, 640, onClose = { windows.enhancements = false }) {
            TreeSlotsWindowContent(state)
        }
    }
}

/** One Edit-menu screen in its own window, registered for the Window menu while it's open. */
@Composable
private fun ScreenWindow(
    windows: OpenWindows,
    screen: EditScreen,
    title: String,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    Window(
        onCloseRequest = {
            windows.awtWindows.remove(screen)
            onClose()
        },
        title = title,
        state = rememberWindowState(position = WindowPosition(x.dp, y.dp), width = width.dp, height = height.dp),
    ) {
        windows.awtWindows[screen] = window
        content()
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

/**
 * The folder holding Data/ and Settings/. In order: the first argument; the `ddolite.repoRoot`
 * property (set by `gradlew run`); the folder of the .exe and its parents (the portable app keeps
 * Data/ next to the .exe, like the VB release); the working folder and its parents.
 * New code: VB6 always used App.Path.
 */
private fun findAppRoot(args: Array<String>): Path {
    if (args.isNotEmpty()) {
        return Path.of(args[0])
    }
    val property = System.getProperty("ddolite.repoRoot")
    if (property != null) {
        return Path.of(property)
    }
    val starts = ArrayList<Path>()
    val appPath = System.getProperty("jpackage.app-path") // set by the native launcher
    if (appPath != null) {
        starts.add(Path.of(appPath).toAbsolutePath().parent)
    }
    starts.add(Path.of("").toAbsolutePath())
    for (start in starts) {
        var dir: Path? = start
        while (dir != null) {
            if (Files.isDirectory(dir.resolve("Data").resolve("Builder"))) {
                return dir
            }
            dir = dir.parent
        }
    }
    return Path.of("..")
}
