package ddolite.prototype.skills.desktop

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import ddolite.builder.build.BuildPoints
import ddolite.builder.build.readBuildFile
import ddolite.builder.build.writeBuildFile
import ddolite.builder.data.LoadLog
import ddolite.builder.data.loadBuilderData
import ddolite.builder.model.Skill
import ddolite.common.theme.Theme
import ddolite.common.theme.ThemeFileKind
import ddolite.common.theme.loadTheme
import ddolite.dataformat.DirectoryDataSource
import ddolite.dataformat.decodeText
import ddolite.prototype.skills.MainWindowContent
import ddolite.prototype.skills.PrototypeState
import ddolite.prototype.skills.SkillsScreen
import ddolite.prototype.skills.SkillsScreenState
import ddolite.prototype.skills.TreeSlotsState
import ddolite.prototype.skills.TreeSlotsWindowContent
import ddolite.prototype.skills.prototypePresets
import ddolite.prototype.skills.readTreeHeaders
import org.jetbrains.skia.EncodedImageFormat
import java.nio.file.Files
import java.nio.file.Path

/**
 * Renders the prototype off-screen and saves PNGs, so it can be checked without opening a window.
 * Also drives it with simulated mouse clicks, to check the interaction code paths.
 *
 * Usage: `./gradlew :prototypes:skills-desktop:screenshots` (writes to build/screenshots).
 * Not part of the app; a tool for the F1 test drive.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    val repoRoot = Path.of(System.getProperty("ddolite.repoRoot", ".."))
    var outDir = Path.of("build", "screenshots")
    if (args.isNotEmpty()) {
        outDir = Path.of(args[0])
    }
    Files.createDirectories(outDir)

    val data = loadBuilderData(DirectoryDataSource(repoRoot.resolve("Data")), LoadLog())
    val themes = ArrayList<Theme>()
    for (name in listOf("Black", "Tan")) {
        val file = repoRoot.resolve("Settings").resolve("$name.screen")
        themes.add(loadTheme(decodeText(Files.readAllBytes(file)).text, name, ThemeFileKind.SCREEN).theme)
    }
    val state = SkillsScreenState(data, themes, prototypePresets())
    state.selectPreset(1) // Fighter 12 / Rogue 6 / Wizard 2: shows class initials and trapper levels

    ImageComposeScene(width = 1100, height = 820, density = Density(1f)) { SkillsScreen(state) }.use { scene ->
        save(scene, outDir.resolve("1-start.png"))

        // Click the Tumble name (fill half ranks), then three level-1 Jump points, then right-click one off.
        val layoutLeft = 12f // the screen's padding
        val gridTop = findGridTop(scene)
        val nameX = layoutLeft + 20f
        val tumbleRow = Skill.TUMBLE.ordinal + 1
        val jumpRow = Skill.JUMP.ordinal + 1
        click(scene, Offset(nameX, gridTop + rowCentre(tumbleRow)), PointerButton.Primary)
        val level1X = layoutLeft + nameColumnWidth() + 10f
        for (i in 0 until 3) {
            click(scene, Offset(level1X, gridTop + rowCentre(jumpRow)), PointerButton.Primary)
        }
        click(scene, Offset(level1X, gridTop + rowCentre(jumpRow)), PointerButton.Secondary)
        // Leave the mouse over a cell, to show the highlight.
        scene.sendPointerEvent(PointerEventType.Move, Offset(level1X + 60f, gridTop + rowCentre(jumpRow)))
        save(scene, outDir.resolve("2-after-clicks.png"))

        println("Tumble: " + state.grid.totalRanks(state.spend, Skill.TUMBLE) + " half ranks; Jump at level 1: " + state.spend.get(Skill.JUMP, 1) + " points")

        state.selectTheme(1)
        save(scene, outDir.resolve("3-tan-theme.png"))
        state.selectTheme(0)
    }

    // The enhancement trees tab: drag two trees into slots, then swap them.
    val trees = TreeSlotsState(data, readTreeHeaders(DirectoryDataSource(repoRoot.resolve("Data"))), state)
    val prototype = PrototypeState(data, state, trees, ScriptPrompts)
    ImageComposeScene(width = 900, height = 640, density = Density(1f)) { TreeSlotsWindowContent(prototype) }.use { scene ->
        save(scene, outDir.resolve("4-trees-start.png"))

        // Press on the first tree and hold still: it should show as selected before any drag starts.
        val pressed = PointerButtons(isPrimaryPressed = true)
        scene.sendPointerEvent(PointerEventType.Move, Offset(60f, treeListRowY(0)))
        scene.sendPointerEvent(PointerEventType.Press, Offset(60f, treeListRowY(0)), buttons = pressed, button = PointerButton.Primary)
        save(scene, outDir.resolve("4b-tree-pressed.png"))
        scene.sendPointerEvent(PointerEventType.Release, Offset(60f, treeListRowY(0)), buttons = PointerButtons(), button = PointerButton.Primary)
        scene.render()
        val listX = 60f
        val firstTreeY = treeListRowY(0)
        val secondTreeY = treeListRowY(1)
        val slotX = 500f

        // Drag the first available tree to slot 2 (slot 1 is the race tree), and hold the next one over slot 3.
        drag(scene, Offset(listX, firstTreeY), Offset(slotX, slotY(1)), release = true)
        drag(scene, Offset(listX, firstTreeY), Offset(slotX, slotY(2)), release = false)
        save(scene, outDir.resolve("5-trees-dragging.png"))
        scene.sendPointerEvent(PointerEventType.Release, Offset(slotX, slotY(2)), buttons = PointerButtons(), button = PointerButton.Primary)
        scene.render() // let the slot recompose with its new tree (and drag handler) before the next press

        // Swap slots 2 and 3 by dragging slot 3 onto slot 2.
        drag(scene, Offset(slotX, slotY(2)), Offset(slotX, slotY(1)), release = true)
        save(scene, outDir.resolve("6-trees-swapped.png"))
        println("Slots: " + trees.slots.toList())
        check(secondTreeY > 0)
    }

    // A real build from Save/: the main window's summary, its skills and its trees.
    val bytes = Files.readAllBytes(repoRoot.resolve("Save").resolve("Kensei Warpriest.build"))
    prototype.openBuild(readBuildFile(bytes, data, BuildPoints.ADVENTURER))
    ImageComposeScene(width = 900, height = 1500, density = Density(1f)) { MainWindowContent(prototype) }.use { scene ->
        save(scene, outDir.resolve("7-opened-main.png"))
        // The Feats window active: only the header and feats.
        prototype.outputView = ddolite.builder.output.OutputView.FEATS
        save(scene, outDir.resolve("7b-opened-main-feats.png"))
        prototype.outputView = ddolite.builder.output.OutputView.ALL
    }
    ImageComposeScene(width = 1100, height = 820, density = Density(1f)) { SkillsScreen(state) }.use { scene ->
        save(scene, outDir.resolve("8-opened-skills.png"))
    }
    ImageComposeScene(width = 900, height = 640, density = Density(1f)) { TreeSlotsWindowContent(prototype) }.use { scene ->
        save(scene, outDir.resolve("9-opened-trees.png"))
    }
    val rules = prototype.prepareSave()!!
    val resaved = writeBuildFile(rules)
    println("Kensei Warpriest re-saved: " + resaved.size + " bytes, same as a direct save: " +
        resaved.contentEquals(writeBuildFile(readBuildFile(bytes, data, BuildPoints.ADVENTURER).rules)))

    overviewScreenshots(prototype, bytes, repoRoot, outDir)
    statsScreenshots(prototype, bytes, outDir)
    featsScreenshots(prototype, bytes, repoRoot, outDir)
    enhancementsScreenshots(prototype, repoRoot, outDir)
    spellsScreenshots(prototype, repoRoot, outDir)
    destinyScreenshots(prototype, repoRoot, outDir)
    messagesScreenshots(prototype, repoRoot, outDir)
    println("Screenshots written to " + outDir.toAbsolutePath())
}

// Positions for the simulated clicks, measured from the rendered screen at density 1
// (two bars of controls above the grid, then the grid's two header rows).
private fun findGridTop(scene: ImageComposeScene): Float = 84f // measured from 1-start.png

private fun nameColumnWidth(): Float = 136f

private fun rowCentre(row: Int): Float {
    val rowHeight = 24.55f // measured from 1-start.png
    return rowHeight * 2 + rowHeight * (row - 1) + rowHeight / 2
}

// Positions on the trees tab, measured from 4-trees-start.png.
private fun treeListRowY(index: Int): Float = 111f + index * 25f

private fun slotY(index: Int): Float = 111f + index * 35f

/** Presses at [from], moves in steps to [to] (so the drag gesture starts), and optionally releases. */
@OptIn(ExperimentalComposeUiApi::class)
private fun drag(scene: ImageComposeScene, from: Offset, to: Offset, release: Boolean) {
    val pressed = PointerButtons(isPrimaryPressed = true)
    scene.sendPointerEvent(PointerEventType.Move, from)
    scene.sendPointerEvent(PointerEventType.Press, from, buttons = pressed, button = PointerButton.Primary)
    val steps = 10
    for (i in 1..steps) {
        val at = Offset(from.x + (to.x - from.x) * i / steps, from.y + (to.y - from.y) * i / steps)
        scene.sendPointerEvent(PointerEventType.Move, at, buttons = pressed)
        scene.render() // let layout and gestures catch up between moves
    }
    if (release) {
        scene.sendPointerEvent(PointerEventType.Release, to, buttons = PointerButtons(), button = PointerButton.Primary)
        scene.render()
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun click(scene: ImageComposeScene, at: Offset, button: PointerButton) {
    var pressed = PointerButtons(isPrimaryPressed = true)
    if (button == PointerButton.Secondary) {
        pressed = PointerButtons(isSecondaryPressed = true)
    }
    scene.sendPointerEvent(PointerEventType.Move, at)
    scene.sendPointerEvent(PointerEventType.Press, at, buttons = pressed, button = button)
    scene.sendPointerEvent(PointerEventType.Release, at, buttons = PointerButtons(), button = button)
}

private fun save(scene: ImageComposeScene, file: Path) {
    val image = scene.render()
    val png = image.encodeToData(EncodedImageFormat.PNG)
    if (png != null) {
        Files.write(file, png.bytes)
    }
}
