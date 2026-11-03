package ddolite.prototype.skills.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import ddolite.builder.build.BuildScreen
import ddolite.builder.build.closesAfterChange
import ddolite.builder.build.openScreenNotice
import ddolite.builder.data.LoadLog
import ddolite.builder.data.loadBuilderData
import ddolite.common.resources.RT_BITMAP
import ddolite.common.resources.bitmapFile
import ddolite.common.resources.readResFile
import ddolite.common.theme.Theme
import ddolite.common.theme.ThemeFileKind
import ddolite.common.theme.loadTheme
import ddolite.dataformat.DirectoryDataSource
import ddolite.dataformat.decodeText
import ddolite.prototype.skills.MainWindowContent
import ddolite.prototype.skills.OverviewWindowContent
import ddolite.prototype.skills.PrototypeState
import ddolite.prototype.skills.SkillsScreenState
import ddolite.prototype.skills.SkillsWindowContent
import ddolite.prototype.skills.StatsWindowContent
import ddolite.prototype.skills.TreeSlotsState
import ddolite.prototype.skills.TreeSlotsWindowContent
import ddolite.prototype.skills.prototypePresets
import ddolite.prototype.skills.readTreeHeaders
import java.awt.KeyboardFocusManager
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

/**
 * Desktop prototype, arranged like the VB Builder (PLAN.md §8.0): a main window with File / Edit /
 * Window menus and the output view, and each Edit-menu screen in its own window. Overview, Skills
 * and Enhancements exist so far; the other Edit items are shown disabled.
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
    val prompts = DesktopPrompts { KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow }
    val state = PrototypeState(data, skills, TreeSlotsState(data, readTreeHeaders(dataSource), skills), prompts)
    val files = BuildFiles(data, repoRoot.resolve("Save").toFile())
    val icons = loadClassIcons()

    application {
        BuilderWindows(state, files, icons)
    }
}

/**
 * The Edit-menu screens with the menu mnemonic and the main window's shortcut key
 * (frmMain.frm:311, Form_KeyDown :751). [built] = exists in the prototype.
 */
private enum class EditScreen(val screen: BuildScreen, val title: String, val mnemonic: Char, val key: Key, val built: Boolean) {
    OVERVIEW(BuildScreen.OVERVIEW, "Overview...", 'O', Key.O, true),
    STATS(BuildScreen.STATS, "Stats...", 'A', Key.T, true),
    SKILLS(BuildScreen.SKILLS, "Skills...", 'K', Key.K, true),
    FEATS(BuildScreen.FEATS, "Feats...", 'F', Key.F, false),
    SPELLS(BuildScreen.SPELLS, "Spells...", 'P', Key.P, false),
    ENHANCEMENTS(BuildScreen.ENHANCEMENTS, "Enhancements...", 'E', Key.E, true),
    DESTINY(BuildScreen.DESTINY, "Destiny...", 'D', Key.D, false),
}

/** Which screen windows are open, and their AWT windows (for bringing one to the front). */
private class OpenWindows {
    private val open = mutableStateMapOf<BuildScreen, Boolean>()
    val awtWindows = HashMap<BuildScreen, ComposeWindow>()

    fun isOpen(screen: BuildScreen): Boolean = open[screen] == true

    fun show(screen: BuildScreen) {
        open[screen] = true
        val existing = awtWindows[screen]
        if (existing != null) {
            existing.toFront()
        }
    }

    fun close(screen: BuildScreen) {
        open.remove(screen)
        awtWindows.remove(screen)
    }

    /** CloseForm "All" (basMain.bas:210). */
    fun closeAll() {
        for (screen in BuildScreen.entries) {
            close(screen)
        }
    }
}

/**
 * Opens a screen's window, or brings it to the front, if the build is ready for it. Ported from
 * basMain.OpenForm (Build/basMain.bas:131): the checks are in builder-core's openScreenNotice. [from]
 * is the window asking (VB's Screen.ActiveForm; null for the main window): on "Select a Race first."
 * the Overview drops down its race list, as VB's does. Returns false if the screen didn't open.
 */
private fun openScreen(screen: EditScreen, state: PrototypeState, windows: OpenWindows, from: BuildScreen? = null): Boolean {
    val rules = state.rules
    if (!screen.built || rules == null) {
        return false
    }
    val notice = openScreenNotice(rules, screen.screen, from)
    if (notice != null) {
        state.prompts.notice(notice)
        if (from == BuildScreen.OVERVIEW && notice == "Select a Race first.") {
            state.overview.showRaceList()
        }
        return false
    }
    windows.show(screen.screen)
    return true
}

/**
 * A footer link: open the neighbouring window and close this one, as each form's usrFooter_Click
 * does (e.g. frmStats.frm:1003). Moving on ("Skills >") stops if the next window can't open;
 * going back ("< Overview") closes this window either way.
 */
private fun footerLink(link: String, from: BuildScreen, state: PrototypeState, windows: OpenWindows) {
    var target: EditScreen? = null
    for (screen in EditScreen.entries) {
        if (link == "< " + screen.screen.title || link == screen.screen.title + " >") {
            target = screen
        }
    }
    if (target == null) {
        return
    }
    val opened = openScreen(target, state, windows, from)
    if (!opened && link.endsWith(">")) {
        return
    }
    windows.close(from)
}

/**
 * Whether an Edit menu item is enabled. Ported from basDocuments.EnableMenus (:249): everything
 * once a build is open, except Spells (casters only) and Destiny (20 or more levels).
 */
private fun menuEnabled(screen: EditScreen, state: PrototypeState): Boolean {
    val rules = state.rules
    if (!screen.built || rules == null) {
        return false
    }
    if (screen == EditScreen.SPELLS) {
        return rules.build.canCastSpell[1] > 0
    }
    if (screen == EditScreen.DESTINY) {
        return rules.build.maxLevels > 19
    }
    return true
}

/**
 * The main window's title: the build's name, with the file name if it differs, and "*" for unsaved
 * changes; the program's name if the build has no name. Ported from basDocuments.SetAppCaption
 * (Build/basDocuments.bas:172).
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
    if (state.dirty) {
        caption = caption + "*"
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
    if (state.opened == null) {
        return false // VB: If Not BuildIsOpen() Then Exit Sub
    }
    for (screen in EditScreen.entries) {
        if (event.key == screen.key) {
            openScreen(screen, state, windows)
            return true
        }
    }
    return false
}

/** The main window (menus + output view) and whichever screen windows are open. */
@Composable
private fun ApplicationScope.BuilderWindows(state: PrototypeState, files: BuildFiles, icons: Map<String, ImageBitmap>) {
    val windows = remember { OpenWindows() }
    val mainWindow = remember { arrayOfNulls<ComposeWindow>(1) }
    // The window part of CascadeChanges: close the screens that no longer make sense.
    state.onCascade = { change ->
        for (screen in BuildScreen.entries) {
            val rules = state.rules
            if (windows.isOpen(screen) && (rules == null || closesAfterChange(rules, screen, change))) {
                windows.close(screen)
            }
        }
    }

    Window(
        onCloseRequest = {
            val parent = mainWindow[0]
            if (parent == null || !files.checkDirty(parent, state)) {
                exitApplication()
            }
        },
        title = appCaption(files, state),
        state = rememberWindowState(position = WindowPosition(40.dp, 40.dp), width = 460.dp, height = 640.dp),
        onKeyEvent = { event -> mainWindowKey(event, windows, state) },
    ) {
        mainWindow[0] = window
        MenuBar {
            // frmMain's File menu (frmMain.frm:160), with the items the prototype has.
            Menu("File", mnemonic = 'F') {
                Item("New...", mnemonic = 'N', shortcut = KeyShortcut(Key.N, ctrl = true), onClick = {
                    windows.closeAll()
                    if (files.new(window, state)) {
                        openScreen(EditScreen.OVERVIEW, state, windows)
                    }
                })
                Item("Open...", mnemonic = 'O', shortcut = KeyShortcut(Key.O, ctrl = true), onClick = { files.open(window, state) })
                Item("Close", mnemonic = 'C', enabled = state.opened != null, onClick = {
                    if (files.close(window, state)) {
                        windows.closeAll()
                    }
                })
                Separator()
                Item("Save", mnemonic = 'S', shortcut = KeyShortcut(Key.S, ctrl = true), enabled = state.opened != null, onClick = { files.save(window, state) })
                Item("Save As...", mnemonic = 'A', enabled = state.opened != null, onClick = { files.saveAs(window, state) })
                Separator()
                Item("Exit", mnemonic = 'x', onClick = {
                    if (!files.checkDirty(window, state)) {
                        exitApplication()
                    }
                })
            }
            Menu("Edit", mnemonic = 'E') {
                for (screen in EditScreen.entries) {
                    Item(screen.title, mnemonic = screen.mnemonic, enabled = menuEnabled(screen, state), onClick = { openScreen(screen, state, windows) })
                }
            }
            // frmMain's Tools menu (frmMain.frm:357). VB's Colors... opens a dialog for editing colours;
            // here it lists the themes in Settings/ to choose from.
            Menu("Tools", mnemonic = 'T') {
                Menu("Colors", mnemonic = 'C') {
                    val skills = state.skills
                    for (i in skills.themes.indices) {
                        RadioButtonItem(skills.themes[i].name, selected = skills.themeIndex == i, onClick = { skills.selectTheme(i) })
                    }
                }
            }
            // frmMain's Window menu lists the open screens (tmrWindowMenu_Timer, frmMain.frm:819).
            Menu("Window", mnemonic = 'W') {
                for (screen in EditScreen.entries) {
                    if (windows.isOpen(screen.screen)) {
                        Item(screen.title.removeSuffix("..."), onClick = { windows.show(screen.screen) })
                    }
                }
            }
        }
        MainWindowContent(state)
    }

    if (windows.isOpen(BuildScreen.OVERVIEW)) {
        // The form's client area is 866 x 609 at 100%; the window adds its frame and title bar.
        ScreenWindow(windows, BuildScreen.OVERVIEW, "Overview", 520, 40, 884, 652) {
            OverviewWindowContent(state, icons) { link -> footerLink(link, BuildScreen.OVERVIEW, state, windows) }
        }
    }
    if (windows.isOpen(BuildScreen.STATS)) {
        // The form's client area is 815 x 549 at 100%.
        ScreenWindow(windows, BuildScreen.STATS, "Stats", 540, 60, 831, 592) {
            StatsWindowContent(state) { link -> footerLink(link, BuildScreen.STATS, state, windows) }
        }
    }
    if (windows.isOpen(BuildScreen.SKILLS)) {
        // The form's client area is 870 x 517 at 100%.
        ScreenWindow(windows, BuildScreen.SKILLS, "Skills", 520, 40, 886, 560) {
            SkillsWindowContent(state, icons) { link -> footerLink(link, BuildScreen.SKILLS, state, windows) }
        }
    }
    if (windows.isOpen(BuildScreen.ENHANCEMENTS)) {
        ScreenWindow(windows, BuildScreen.ENHANCEMENTS, "Enhancements", 560, 80, 900, 640) {
            TreeSlotsWindowContent(state)
        }
    }
}

/** One Edit-menu screen in its own window, registered for the Window menu while it's open. */
@Composable
private fun ScreenWindow(
    windows: OpenWindows,
    screen: BuildScreen,
    title: String,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    content: @Composable () -> Unit,
) {
    Window(
        onCloseRequest = { windows.close(screen) },
        title = title,
        state = rememberWindowState(position = WindowPosition(x.dp, y.dp), width = width.dp, height = height.dp),
    ) {
        windows.awtWindows[screen] = window
        content()
    }
}

/**
 * The class icons from CharacterBuilderLite.RES (copied onto the classpath by the build), by
 * resource name. VB reads them with LoadResPicture; empty if the file is missing.
 */
internal fun loadClassIcons(): Map<String, ImageBitmap> {
    val result = HashMap<String, ImageBitmap>()
    val stream = BuilderIcons::class.java.getResourceAsStream("/CharacterBuilderLite.RES")
    if (stream == null) {
        println("CharacterBuilderLite.RES not found: class icons won't show")
        return result
    }
    val resources = stream.use { readResFile(it.readBytes()) }
    for (resource in resources) {
        if (resource.type == RT_BITMAP && resource.name.startsWith("CLS")) {
            val image = ImageIO.read(bitmapFile(resource.data).inputStream())
            if (image != null) {
                result[resource.name] = image.toComposeImageBitmap()
            }
        }
    }
    return result
}

/** Something in this module to find resources from. */
private object BuilderIcons

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
