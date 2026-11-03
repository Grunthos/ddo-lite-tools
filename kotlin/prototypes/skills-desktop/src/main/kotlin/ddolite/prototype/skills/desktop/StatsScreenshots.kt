@file:OptIn(ExperimentalComposeUiApi::class)

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
import ddolite.prototype.skills.PrototypeState
import ddolite.prototype.skills.SkillsWindowContent
import ddolite.prototype.skills.StatsWindowContent
import org.jetbrains.skia.EncodedImageFormat
import java.nio.file.Files
import java.nio.file.Path

/**
 * The Stats window off-screen: Kensei Warpriest's stats and schedule, its Skills window with the
 * Intelligence modifiers from those stats, then a template applied to the new build the Overview
 * screenshots made. Part of the screenshots tool (Screenshots.kt).
 */
fun statsScreenshots(prototype: PrototypeState, kensei: ByteArray, outDir: Path) {
    val newBuild = prototype.opened!!
    prototype.openBuild(readBuildFile(kensei, prototype.data, BuildPoints.ADVENTURER))
    ImageComposeScene(width = 815, height = 549, density = Density(1f)) { StatsWindowContent(prototype) {} }.use { scene ->
        save(scene, outDir.resolve("15-stats-kensei.png"))
        prototype.stats.showTab(schedule = true)
        save(scene, outDir.resolve("16-stats-schedule.png"))
        prototype.stats.showTab(schedule = false)
    }
    val icons = loadClassIcons()
    ImageComposeScene(width = 870, height = 517, density = Density(1f)) { SkillsWindowContent(prototype, icons) {} }.use { scene ->
        save(scene, outDir.resolve("17-skills-kensei.png"))
        // A right-click on the third skill name (Concentration) opens its menu. The grid's rows
        // are about 17 pixels from y = 83 with the icon row; the names start near x = 97.
        scene.render()
        val name = Offset(120f, 83f + 17f * 2.5f)
        scene.sendPointerEvent(PointerEventType.Move, name)
        scene.sendPointerEvent(PointerEventType.Press, name, buttons = PointerButtons(isSecondaryPressed = true), button = PointerButton.Secondary)
        scene.sendPointerEvent(PointerEventType.Release, name, buttons = PointerButtons(), button = PointerButton.Secondary)
        save(scene, outDir.resolve("17b-skills-menu.png"))
        println("Skill menu for row " + prototype.skills.menuRow)
        prototype.skills.menuRow = 0
        prototype.skills.tomesTab = true
        save(scene, outDir.resolve("17c-skills-tomes.png"))
        prototype.skills.tomesTab = false
    }

    prototype.openBuild(newBuild)
    ImageComposeScene(width = 815, height = 549, density = Density(1f)) { StatsWindowContent(prototype) {} }.use { scene ->
        save(scene, outDir.resolve("18-stats-new.png"))
        prototype.stats.applyTemplate(0)
        save(scene, outDir.resolve("19-stats-template.png"))
        val build = prototype.opened!!.build
        println("Template " + prototype.stats.templates().map { it.caption } + " applied: Adventurer " +
            build.statPoints[0].toList() + " = " + build.statTotals[0] + ", level-ups " + build.levelups.toList())
    }
}

private fun save(scene: ImageComposeScene, file: Path) {
    val image = scene.render()
    val png = image.encodeToData(EncodedImageFormat.PNG)
    if (png != null) {
        Files.write(file, png.bytes)
    }
}
