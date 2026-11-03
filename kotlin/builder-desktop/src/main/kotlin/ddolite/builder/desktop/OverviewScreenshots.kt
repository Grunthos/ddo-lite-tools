package ddolite.builder.desktop

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
import ddolite.builder.ui.BuilderPrompts
import ddolite.builder.ui.BuildSlotKey
import ddolite.builder.ui.MainWindowContent
import ddolite.builder.ui.OverviewDrag
import ddolite.builder.ui.OverviewWindowContent
import ddolite.builder.ui.BuilderSession
import org.jetbrains.skia.EncodedImageFormat
import java.nio.file.Files
import java.nio.file.Path

/** Answers yes to every question and prints notices, for the off-screen screenshots. */
object ScriptPrompts : BuilderPrompts {
    override fun confirm(message: String): Boolean {
        println("Asked: $message -> Yes")
        return true
    }

    override fun notice(message: String) {
        println("Notice: $message")
    }

    override fun yesNoCancel(message: String, title: String): Boolean? {
        println("Asked: $message -> Yes")
        return true
    }

    override fun chooseLevel(min: Int, max: Int): Int? {
        println("Asked for a level from $min to $max -> $min")
        return min
    }
}

/**
 * The Overview window off-screen: an opened build, then a new build filled in through the same
 * calls the window's mouse handlers make, with one real click on the level grid to check its
 * hit-testing. Part of the screenshots tool (Screenshots.kt).
 */
@OptIn(ExperimentalComposeUiApi::class)
fun overviewScreenshots(prototype: BuilderSession, kensei: ByteArray, repoRoot: Path, outDir: Path) {
    val icons = loadClassIcons()
    println("Class icons: " + icons.size)
    val data = prototype.data
    prototype.openBuild(readBuildFile(kensei, data, BuildPoints.ADVENTURER))
    ImageComposeScene(width = 866, height = 609, density = Density(1f)) { OverviewWindowContent(prototype, icons) {} }.use { scene ->
        saveScene(scene, outDir.resolve("10-overview-opened.png"))
        prototype.overview.showTab(notes = true)
        saveScene(scene, outDir.resolve("10b-overview-notes.png"))
        prototype.overview.showTab(notes = false)
    }

    prototype.newBuild(BuildPoints.ADVENTURER)
    val overview = prototype.overview
    ImageComposeScene(width = 866, height = 609, density = Density(1f)) { OverviewWindowContent(prototype, icons) {} }.use { scene ->
        saveScene(scene, outDir.resolve("11-overview-new.png"))
        overview.toggleRaceList()
        saveScene(scene, outDir.resolve("12-overview-race-list.png"))
        var human = overview.raceRows[0].race
        for (row in overview.raceRows) {
            if (row.race?.name == "Human") {
                human = row.race
            }
        }
        overview.chooseRace(human)
        overview.dropped(OverviewDrag.ClassIcon(data.findClass("Fighter")!!), BuildSlotKey(0))
        overview.dropped(OverviewDrag.ClassIcon(data.findClass("Cleric")!!), BuildSlotKey(1))
        scene.render()
        // Click level 3 in the second build class column (the grid starts at 540, 36).
        val levelThree = Offset(540f + 220f, 36f + 115f)
        scene.sendPointerEvent(PointerEventType.Move, levelThree)
        scene.sendPointerEvent(PointerEventType.Press, levelThree, buttons = PointerButtons(isPrimaryPressed = true), button = PointerButton.Primary)
        scene.sendPointerEvent(PointerEventType.Release, levelThree, buttons = PointerButtons(), button = PointerButton.Primary)
        saveScene(scene, outDir.resolve("13-overview-fighter-cleric.png"))
        val build = prototype.opened!!.build
        val levels = StringBuilder()
        for (level in 1..20) {
            levels.append(build.levelClass(level)?.name?.substring(0, 2)).append(' ')
        }
        println("New build: " + build.race?.name + ", " + build.buildClasses.map { it?.name } + ", alignment " + build.alignment +
            ", levels " + levels + ", BAB at 20: " + build.bab[19])
    }
    ImageComposeScene(width = 460, height = 900, density = Density(1f)) { MainWindowContent(prototype) }.use { scene ->
        saveScene(scene, outDir.resolve("14-new-build-main.png"))
    }
    check(Files.isDirectory(repoRoot))
}

private fun saveScene(scene: ImageComposeScene, file: Path) {
    val image = scene.render()
    val png = image.encodeToData(EncodedImageFormat.PNG)
    if (png != null) {
        Files.write(file, png.bytes)
    }
}
