package ddolite.prototype.skills.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import ddolite.builder.build.BuildPoints
import ddolite.builder.build.readBuildFile
import ddolite.prototype.skills.DestinyWindowContent
import ddolite.prototype.skills.PointerButton
import ddolite.prototype.skills.PrototypeState
import org.jetbrains.skia.EncodedImageFormat
import java.nio.file.Files
import java.nio.file.Path

/**
 * The Destinies window off-screen: Kensei Warpriest's destinies (level 30) and TEST.build's
 * (level 36), the Destinies tab with a slot chosen and an ability added. Part of the screenshots tool.
 */
fun destinyScreenshots(prototype: PrototypeState, repoRoot: Path, outDir: Path) {
    val screen = prototype.destiny
    // TEST.build (level 36) is kept in kotlin/test-builds since it left Save/.
    val builds = listOf(repoRoot.resolve("Save").resolve("Kensei Warpriest.build"), repoRoot.resolve("kotlin").resolve("test-builds").resolve("TEST.build"))
    for (path in builds) {
        val name = path.fileName.toString().removeSuffix(".build")
        prototype.openBuild(readBuildFile(Files.readAllBytes(path), prototype.data, BuildPoints.ADVENTURER))
        screen.opened()
        val file = name.replace(" ", "-").lowercase()
        ImageComposeScene(width = 815, height = 518, density = Density(1f)) { DestinyWindowContent(prototype) {} }.use { scene ->
            println(name + ": " + screen.comboTrees() + "; " + screen.apCaption().first)
            shot(scene, outDir.resolve("50-destiny-$file-trees.png"))
            screen.chooseTab(1)
            println("  " + screen.comboTree + ": " + screen.slotRows().size + " slots, " + screen.abilities.size + " offered")
            screen.clickSlot(1, PointerButton.PRIMARY)
            shot(scene, outDir.resolve("51-destiny-$file-abilities.png"))
            for (i in screen.abilities.indices) {
                if (screen.clickAbility(i)) {
                    val text = screen.abilityText(screen.abilities[i])
                    screen.doubleClickList(fromSelectors = false)
                    println("  added " + text + ": " + screen.slotRows().size + " slots; " + screen.apCaption().first)
                    break
                }
            }
        }
    }
}

private fun shot(scene: ImageComposeScene, file: Path) {
    val image = scene.render()
    val png = image.encodeToData(EncodedImageFormat.PNG)
    if (png != null) {
        Files.write(file, png.bytes)
    }
}
