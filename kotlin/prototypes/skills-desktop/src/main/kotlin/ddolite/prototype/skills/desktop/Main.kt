package ddolite.prototype.skills.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
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
import ddolite.builder.output.OutputView
import ddolite.builder.settings.BuilderSettings
import ddolite.builder.settings.Coords
import ddolite.builder.settings.WindowSize
import ddolite.common.theme.ColorGroup
import ddolite.common.theme.ColorValue
import ddolite.common.theme.themeToText
import ddolite.prototype.skills.Contrast
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import java.io.File
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
import ddolite.prototype.skills.MessagesWindowContent
import ddolite.prototype.skills.ExportWindowContent
import ddolite.prototype.skills.OptionsWindowContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ddolite.prototype.skills.OutputFormats
import ddolite.prototype.skills.OverviewWindowContent
import ddolite.prototype.skills.PrototypeState
import ddolite.prototype.skills.SkillsScreenState
import ddolite.prototype.skills.FeatsWindowContent
import ddolite.prototype.skills.SkillsWindowContent
import ddolite.prototype.skills.SpellsWindowContent
import ddolite.prototype.skills.StatsWindowContent
import ddolite.prototype.skills.TreeSlotsState
import ddolite.prototype.skills.DestinyWindowContent
import ddolite.prototype.skills.EnhancementsWindowContent
import ddolite.prototype.skills.prototypePresets
import ddolite.prototype.skills.readTreeHeaders
import java.awt.KeyboardFocusManager
import java.nio.file.Files
import java.nio.file.Path
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
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
    val store = SettingsStore(repoRoot)
    val settings = store.load()
    println("Settings: " + store.source)

    val log = LoadLog()
    val dataSource = DirectoryDataSource(repoRoot.resolve("Data"))
    val data = loadBuilderData(dataSource, log)
    for (issue in log.issues) {
        println(issue.message)
    }
    val themes = withSettingsColors(loadThemes(repoRoot.resolve("Settings")), settings)
    val skills = SkillsScreenState(data, themes, prototypePresets())
    for (i in themes.indices) {
        if (themes[i].name == settings.screenColors) {
            skills.selectTheme(i)
        }
    }
    skills.contrast = Contrast.entries.getOrElse(settings.contrast) { Contrast.MEDIUM }
    val prompts = DesktopPrompts { KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow }
    val state = PrototypeState(data, skills, TreeSlotsState(data, readTreeHeaders(dataSource), skills), prompts)
    val files = BuildFiles(data, repoRoot.resolve("Save").toFile())
    val exports = Exports(files)
    state.formats = loadFormats(repoRoot.resolve("Settings"))
    state.settings = settings
    state.outputFormat = settings.output
    state.formatName = settings.bbCodes
    val icons = loadClassIcons()

    application {
        BuilderWindows(state, files, exports, store, icons)
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
    FEATS(BuildScreen.FEATS, "Feats...", 'F', Key.F, true),
    SPELLS(BuildScreen.SPELLS, "Spells...", 'P', Key.P, true),
    ENHANCEMENTS(BuildScreen.ENHANCEMENTS, "Enhancements...", 'E', Key.E, true),
    DESTINY(BuildScreen.DESTINY, "Destiny...", 'D', Key.D, true),
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
    if (screen == EditScreen.FEATS && !windows.isOpen(BuildScreen.FEATS)) {
        state.feats.opened() // frmFeats.Form_Load
    }
    if (screen == EditScreen.ENHANCEMENTS && !windows.isOpen(BuildScreen.ENHANCEMENTS)) {
        state.enhancements.opened() // frmEnhancements.Form_Load
    }
    if (screen == EditScreen.SPELLS && !windows.isOpen(BuildScreen.SPELLS)) {
        state.spells.opened() // frmSpells.Form_Load
    }
    if (screen == EditScreen.DESTINY && !windows.isOpen(BuildScreen.DESTINY)) {
        state.destiny.opened() // frmDestiny2.Form_Load
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
private fun ApplicationScope.BuilderWindows(state: PrototypeState, files: BuildFiles, exports: Exports, store: SettingsStore, icons: Map<String, ImageBitmap>) {
    val windows = remember { OpenWindows() }
    val mainWindow = remember { arrayOfNulls<ComposeWindow>(1) }
    val messagesWindow = remember { arrayOfNulls<ComposeWindow>(1) }
    val exportWindow = remember { arrayOfNulls<ComposeWindow>(1) }
    val optionsWindow = remember { arrayOfNulls<ComposeWindow>(1) }
    var optionsOpen by remember { mutableStateOf(false) }
    @Suppress("UNUSED_VARIABLE")
    val settingsRevision = state.settingsRevision // read so the menus and icons follow the Options window
    SideEffect {
        state.enhancementsOpen = windows.isOpen(BuildScreen.ENHANCEMENTS)
    }
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
                quit(state, store, mainWindow[0])
                exitApplication()
            }
        },
        title = appCaption(files, state),
        state = mainWindowState(state.settings),
        onKeyEvent = { event -> mainWindowKey(event, windows, state) },
    ) {
        mainWindow[0] = window
        MenuBar {
            // frmMain's File menu (frmMain.frm:160), with the items the prototype has.
            Menu("File", mnemonic = 'F') {
                Item("New...", mnemonic = 'N', shortcut = KeyShortcut(Key.N, ctrl = true), onClick = {
                    windows.closeAll()
                    state.messages.close() // CloseForm "All" closes frmDeprecate too
                    state.export.close()
                    if (files.new(window, state)) {
                        openScreen(EditScreen.OVERVIEW, state, windows)
                    }
                })
                Item("Open...", mnemonic = 'O', shortcut = KeyShortcut(Key.O, ctrl = true), onClick = { files.open(window, state) })
                Item("Close", mnemonic = 'C', enabled = state.opened != null, onClick = {
                    if (files.close(window, state)) {
                        windows.closeAll()
                        state.messages.close()
                        state.export.close()
                    }
                })
                Separator()
                Item("Save", mnemonic = 'S', shortcut = KeyShortcut(Key.S, ctrl = true), enabled = state.opened != null, onClick = { files.save(window, state) })
                Item("Save As...", mnemonic = 'A', enabled = state.opened != null, onClick = { files.saveAs(window, state) })
                Separator()
                // The Export submenu (frmMain.frm:219).
                Menu("Export", mnemonic = 'E', enabled = state.opened != null) {
                    Item("to Forums...", mnemonic = 'F', onClick = { state.export.open() })
                    Item("to Ron's Planner...", mnemonic = 'R', onClick = { exports.exportPlanner(window, state, ron = true) })
                    Item("to DDO Builder...", mnemonic = 'D', onClick = { exports.exportPlanner(window, state, ron = false) })
                    Separator()
                    // EnableMenus (basDocuments.bas:266): only with a guide.
                    Menu("Leveling Guide", mnemonic = 'G', enabled = (state.rules?.build?.guide?.enhancements?.size ?: 0) > 0) {
                        Item("csv", mnemonic = 'c', onClick = { exports.exportGuide(window, state, "csv") })
                        Item("txt", mnemonic = 't', onClick = { exports.exportGuide(window, state, "txt") })
                    }
                }
                Separator()
                Item("Exit", mnemonic = 'x', onClick = {
                    if (!files.checkDirty(window, state)) {
                        quit(state, store, window)
                        exitApplication()
                    }
                })
                // The recent files (ShowMRU, clsConfig.cls): "1. Name" …, after a separator.
                val recent = ArrayList(state.settings.mruList)
                if (recent.isNotEmpty()) {
                    Separator()
                    for (i in recent.indices) {
                        Item((i + 1).toString() + ". " + File(recent[i]).nameWithoutExtension, mnemonic = ('1' + i), onClick = {
                            files.openRecent(window, state, recent[i])
                        })
                    }
                }
            }
            Menu("Edit", mnemonic = 'E') {
                for (screen in EditScreen.entries) {
                    Item(screen.title, mnemonic = screen.mnemonic, enabled = menuEnabled(screen, state), onClick = { openScreen(screen, state, windows) })
                }
            }
            // frmMain's Tools menu (frmMain.frm:357). VB's Colors... opens a dialog for editing colours;
            // here it lists the themes in Settings/ to choose from.
            Menu("Tools", mnemonic = 'T') {
                // Messages... shows only while there are messages to resolve (UpdateToolsMenu, frmMain.frm:603).
                if (state.messages.deprecated) {
                    Item("Messages...", mnemonic = 'M', onClick = { state.messages.showDeprecated() })
                    Separator()
                }
                Menu("Colors", mnemonic = 'C') {
                    val skills = state.skills
                    for (i in skills.themes.indices) {
                        RadioButtonItem(skills.themes[i].name, selected = skills.themeIndex == i, onClick = { chooseTheme(state, i) })
                    }
                }
                Item("Options...", mnemonic = 'O', onClick = {
                    optionsOpen = true
                    optionsWindow[0]?.toFront()
                })
            }
            // frmMain's Window menu lists the open screens (tmrWindowMenu_Timer, frmMain.frm:819).
            Menu("Window", mnemonic = 'W') {
                for (screen in EditScreen.entries) {
                    if (windows.isOpen(screen.screen)) {
                        Item(screen.title.removeSuffix("..."), onClick = { windows.show(screen.screen) })
                    }
                }
                if (state.export.isOpen) {
        // frmExport: the client area is 815 x 518 at 100%.
        Window(
            onCloseRequest = {
                saveDialogPosition(state.settings, exportWindow[0])
                state.export.close()
            },
            title = "Export",
            resizable = false,
            state = rememberWindowState(position = dialogPosition(state.settings, 520, 100), width = 831.dp, height = 561.dp),
        ) {
            exportWindow[0] = window
            ExportWindowContent(state, onCopy = { exports.copy(it) }, onSave = { exports.saveText(window, state, it) })
        }
    }
    if (optionsOpen) {
        // frmOptions: the client area is 474 x 428 at 100%; closing saves the settings (Form_Unload).
        Window(
            onCloseRequest = {
                val w = optionsWindow[0]
                if (w != null) {
                    state.settings.optionsCoords = Coords(w.x * TWIPS, w.y * TWIPS)
                }
                store.save(state.settings)
                optionsOpen = false
            },
            title = "Options",
            resizable = false,
            state = rememberWindowState(position = optionsPosition(state.settings), width = 490.dp, height = 471.dp),
        ) {
            optionsWindow[0] = window
            OptionsWindowContent(state)
        }
    }
    if (state.messages.isOpen) {
                    Item("Messages", onClick = { messagesWindow[0]?.toFront() })
                }
            }
        }
        MainWindowContent(state)
    }

    if (windows.isOpen(BuildScreen.OVERVIEW)) {
        // The form's client area is 866 x 609 at 100%; the window adds its frame and title bar.
        ScreenWindow(windows, state, BuildScreen.OVERVIEW, "Overview", 520, 40, 884, 652) {
            OverviewWindowContent(state, if (state.settings.useIcons && state.settings.iconOverview) icons else emptyMap()) { link -> footerLink(link, BuildScreen.OVERVIEW, state, windows) }
        }
    }
    if (windows.isOpen(BuildScreen.STATS)) {
        // The form's client area is 815 x 549 at 100%.
        ScreenWindow(windows, state, BuildScreen.STATS, "Stats", 540, 60, 831, 592) {
            StatsWindowContent(state) { link -> footerLink(link, BuildScreen.STATS, state, windows) }
        }
    }
    if (windows.isOpen(BuildScreen.SKILLS)) {
        // The form's client area is 870 x 517 at 100%.
        ScreenWindow(windows, state, BuildScreen.SKILLS, "Skills", 520, 40, 886, 560) {
            SkillsWindowContent(state, if (state.settings.useIcons && state.settings.iconSkills) icons else emptyMap()) { link -> footerLink(link, BuildScreen.SKILLS, state, windows) }
        }
    }
    if (windows.isOpen(BuildScreen.FEATS)) {
        // The form's client area is 815 x 593 at 100%.
        ScreenWindow(windows, state, BuildScreen.FEATS, "Feats", 540, 60, 831, 636) {
            FeatsWindowContent(state) { link -> footerLink(link, BuildScreen.FEATS, state, windows) }
        }
    }
    if (windows.isOpen(BuildScreen.SPELLS)) {
        // The form's client area is 815 x 518 at 100%.
        ScreenWindow(windows, state, BuildScreen.SPELLS, "Spells", 560, 80, 831, 561) {
            SpellsWindowContent(state) { link -> footerLink(link, BuildScreen.SPELLS, state, windows) }
        }
    }
    if (windows.isOpen(BuildScreen.DESTINY)) {
        // The form's client area is 815 x 518 at 100%.
        ScreenWindow(windows, state, BuildScreen.DESTINY, "Destinies", 580, 100, 831, 561) {
            DestinyWindowContent(state) { link -> footerLink(link, BuildScreen.DESTINY, state, windows) }
        }
    }
    if (windows.isOpen(BuildScreen.ENHANCEMENTS)) {
        // The form's client area is 815 x 555 at 100%.
        ScreenWindow(windows, state, BuildScreen.ENHANCEMENTS, "Enhancements", 560, 80, 831, 598) {
            EnhancementsWindowContent(state, onExportGuide = { exports.exportGuide(windows.awtWindows[BuildScreen.ENHANCEMENTS] ?: mainWindow[0]!!, state, "csv") }) { link -> footerLink(link, BuildScreen.ENHANCEMENTS, state, windows) }
        }
    }
    if (state.messages.isOpen) {
        // frmDeprecate: the client area is 815 x 517 at 100%; a fixed dialog, opened after a build file
        // with things the data no longer has (tmrDeprecate, frmMain.frm:673) or from Tools › Messages.
        Window(
            onCloseRequest = {
                saveDialogPosition(state.settings, messagesWindow[0])
                state.messages.close()
            },
            title = "Messages",
            resizable = false,
            state = rememberWindowState(position = dialogPosition(state.settings, 500, 120), width = 831.dp, height = 560.dp),
        ) {
            messagesWindow[0] = window
            MessagesWindowContent(state)
        }
    }
}

/** One Edit-menu screen in its own window, registered for the Window menu while it's open. */
@Composable
private fun ScreenWindow(
    windows: OpenWindows,
    state: PrototypeState,
    screen: BuildScreen,
    title: String,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    content: @Composable () -> Unit,
) {
    Window(
        onCloseRequest = {
            saveDialogPosition(state.settings, windows.awtWindows[screen])
            windows.close(screen)
            // UnloadForm (basMain.bas:224): with no other Edit window left, the output shows everything again.
            var others = false
            for (s in BuildScreen.entries) {
                if (windows.isOpen(s)) {
                    others = true
                }
            }
            if (!others) {
                state.outputView = OutputView.ALL
            }
        },
        title = title,
        state = rememberWindowState(position = dialogPosition(state.settings, x, y), width = width.dp, height = height.dp),
    ) {
        windows.awtWindows[screen] = window
        // Form_Activate → ActivateForm: the main window's output shows this window's sections.
        DisposableEffect(window) {
            val listener = object : WindowAdapter() {
                override fun windowGainedFocus(e: WindowEvent?) {
                    state.outputView = outputViewFor(screen)
                }
            }
            window.addWindowFocusListener(listener)
            onDispose { window.removeWindowFocusListener(listener) }
        }
        content()
    }
}

/** The output each Edit window asks for when activated; the Destinies window passes oeDestiny2 (frmDestiny2.frm:583). */
private fun outputViewFor(screen: BuildScreen): OutputView = when (screen) {
    BuildScreen.OVERVIEW -> OutputView.OVERVIEW
    BuildScreen.STATS -> OutputView.STATS
    BuildScreen.SKILLS -> OutputView.SKILLS
    BuildScreen.FEATS -> OutputView.FEATS
    BuildScreen.SPELLS -> OutputView.SPELLS
    BuildScreen.ENHANCEMENTS -> OutputView.ENHANCEMENTS
    BuildScreen.DESTINY -> OutputView.DESTINY2
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

/** The .bbcodes files in [settings] by name, as Dir lists them (sorted). GetFormatNames (clsConfig.cls:2583). */
internal fun loadFormats(settings: Path): OutputFormats {
    val list = ArrayList<Pair<String, String>>()
    try {
        Files.newDirectoryStream(settings, "*.bbcodes").use { files ->
            for (file in files) {
                list.add(Pair(file.fileName.toString().removeSuffix(".bbcodes"), decodeText(Files.readAllBytes(file)).text))
            }
        }
    } catch (e: Exception) {
        // No Settings folder: just the built-in formats.
    }
    list.sortBy { it.first.lowercase() }
    return OutputFormats(list)
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

// ---------------------------------------------------------------- settings

/** Twips (VB's coordinates) per pixel at 100%. */
private const val TWIPS = 15

/**
 * The themes, with the settings' Colors section as the theme named in ScreenColors: VB applies the
 * saved colours, not the .screen file (clsConfig.LoadColors, :948), so any changes made with the
 * Colors tool are kept.
 */
private fun withSettingsColors(themes: List<Theme>, settings: BuilderSettings): List<Theme> {
    val colors = settings.colorsText ?: return themes
    val loaded = loadTheme(colors, settings.screenColors, ThemeFileKind.ALL)
    if (!loaded.valid) {
        return themes
    }
    val result = ArrayList<Theme>()
    var replaced = false
    for (t in themes) {
        if (t.name == settings.screenColors) {
            result.add(loaded.theme)
            replaced = true
        } else {
            result.add(t)
        }
    }
    if (!replaced) {
        result.add(0, loaded.theme)
    }
    return result
}

/**
 * Tools › Colors: a .screen file sets every colour but the Output ones, which stay as they were
 * (LoadColors with cfeScreen); the settings remember the name and the colours.
 */
private fun chooseTheme(state: PrototypeState, index: Int) {
    val skills = state.skills
    val old = skills.theme
    val chosen = skills.themes[index]
    for (value in ColorValue.entries) {
        chosen.set(ColorGroup.OUTPUT, value, old.get(ColorGroup.OUTPUT, value))
    }
    skills.selectTheme(index)
    state.settings.screenColors = chosen.name
    state.settings.colorsText = "\r\n\r\n" + themeToText(chosen) + "\r\n\r\n\r\n"
    state.markOutputChanged()
}

/**
 * The main window as the settings say (cfg.SizeWindow, clsConfig.cls:1541): maximized, or at the
 * saved place and size; the whole desktop for "Fill Desktop" or when nothing is saved.
 */
private fun mainWindowState(settings: BuilderSettings): WindowState {
    if (settings.windowSize == WindowSize.MAXIMIZED || (settings.windowSize == WindowSize.REMEMBER && settings.maximized)) {
        return WindowState(placement = WindowPlacement.Maximized)
    }
    val c = settings.windowCoords
    if (settings.windowSize == WindowSize.FILL_DESKTOP || c.width == 0 || c.height == 0) {
        val desktop = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        return WindowState(position = WindowPosition(desktop.x.dp, desktop.y.dp), width = desktop.width.dp, height = desktop.height.dp)
    }
    return WindowState(position = WindowPosition((c.left / TWIPS).dp, (c.top / TWIPS).dp), width = (c.width / TWIPS).dp, height = (c.height / TWIPS).dp)
}

/** Leaving: the main window's place (SaveWindowSize, :1572), the skills contrast, then SaveSettings. */
private fun quit(state: PrototypeState, store: SettingsStore, window: ComposeWindow?) {
    val settings = state.settings
    if (window != null) {
        settings.maximized = (window.extendedState and java.awt.Frame.MAXIMIZED_BOTH) == java.awt.Frame.MAXIMIZED_BOTH
        if (!settings.maximized) {
            settings.windowCoords = Coords(window.x * TWIPS, window.y * TWIPS, window.width * TWIPS, window.height * TWIPS)
        }
    }
    settings.contrast = state.skills.contrast.ordinal
    store.save(settings)
}

/** Where a dialog opens: the saved DialogCoords (MoveForm, :1599), or [x], [y] when there are none. */
private fun dialogPosition(settings: BuilderSettings, x: Int, y: Int): WindowPosition {
    val c = settings.dialogCoords
    if (c.left == 0 && c.top == 0) {
        return WindowPosition(x.dp, y.dp)
    }
    return WindowPosition((c.left / TWIPS).dp, (c.top / TWIPS).dp)
}

/** Where the Options window opens: OptionsCoords, or the middle of the screen (MoveForm). */
private fun optionsPosition(settings: BuilderSettings): WindowPosition {
    val c = settings.optionsCoords
    if (c.left == 0 && c.top == 0) {
        return WindowPosition(androidx.compose.ui.Alignment.Center)
    }
    return WindowPosition((c.left / TWIPS).dp, (c.top / TWIPS).dp)
}

/** SavePosition (:1655): every Edit window, the Export and the Messages windows share one place. */
private fun saveDialogPosition(settings: BuilderSettings, window: ComposeWindow?) {
    if (window == null) {
        return
    }
    settings.dialogCoords = Coords(window.x * TWIPS, window.y * TWIPS)
}
