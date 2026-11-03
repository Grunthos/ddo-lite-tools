package ddolite.prototype.skills.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import ddolite.builder.build.BuildPoints
import ddolite.builder.build.FeatTab
import ddolite.builder.build.readBuildFile
import ddolite.prototype.skills.FeatDrag
import ddolite.prototype.skills.FeatSlotKey
import ddolite.prototype.skills.FeatsWindowContent
import ddolite.prototype.skills.PointerButton
import ddolite.prototype.skills.PrototypeState
import org.jetbrains.skia.EncodedImageFormat
import java.nio.file.Files
import java.nio.file.Path

/**
 * The Feats window off-screen: Kensei Warpriest's slots, a slot chosen with the feats that fit,
 * a feat moved by drag, the context menu's alternate, the Granted tab and the channel tabs, then
 * Eresh (if present). Uses the same calls as the window's mouse handlers. Part of the screenshots tool.
 */
fun featsScreenshots(prototype: PrototypeState, kensei: ByteArray, repoRoot: Path, outDir: Path) {
    prototype.openBuild(readBuildFile(kensei, prototype.data, BuildPoints.ADVENTURER))
    val feats = prototype.feats
    feats.opened()
    ImageComposeScene(width = 815, height = 593, density = Density(1f)) { FeatsWindowContent(prototype) {} }.use { scene ->
        shot(scene, outDir.resolve("20-feats-kensei.png"))
        // Choose the level 21 epic slot (an empty Destiny one is at 22): row of "Epic 21".
        val rows = feats.rows()
        var destinyRow = 0
        for (i in rows.indices) {
            if (rows[i].level == 22 && rows[i].feat == null) {
                destinyRow = i + 1
            }
        }
        feats.clickSlot(destinyRow, PointerButton.PRIMARY)
        println("Destiny 22 slot: " + feats.feats.size + " feats offered: " + feats.feats.take(8).map { it.abbreviation })
        if (feats.feats.isNotEmpty()) {
            feats.clickFeat(0)
        }
        shot(scene, outDir.resolve("21-feats-slot-chosen.png"))
        // Drag the highlighted feat onto the slot.
        if (feats.feats.isNotEmpty() && feats.selectors.isEmpty()) {
            val item = FeatDrag.FromList(feats.feats[0])
            println("Drag allowed: " + feats.startDrag(item) + ", drop states " + feats.dropStates.count { it != ddolite.builder.build.DropState.DEFAULT })
            shot(scene, outDir.resolve("22-feats-dragging.png"))
            feats.dropped(item, FeatSlotKey(destinyRow))
            println("After drop: " + feats.rows()[destinyRow - 1].display + ", dirty " + prototype.dirty)
        }
        // Right-click the level 1 slot and add an alternate.
        feats.clickSlot(1, PointerButton.SECONDARY)
        println("Menu: " + feats.menuItems())
        feats.chooseMenu("Add Alternate")
        shot(scene, outDir.resolve("23-feats-alternate.png"))
        feats.chooseTab(FeatTab.GRANTED)
        shot(scene, outDir.resolve("24-feats-granted.png"))
        feats.changeChannels(true)
        println("Tabs with channels: " + feats.tabs)
        shot(scene, outDir.resolve("25-feats-channels.png"))
        feats.changeChannels(false)
    }
    val eresh = repoRoot.resolve("Save").resolve("Eresh WFWizRog.build")
    if (Files.exists(eresh)) {
        prototype.openBuild(readBuildFile(Files.readAllBytes(eresh), prototype.data, BuildPoints.ADVENTURER))
        feats.opened()
        ImageComposeScene(width = 815, height = 593, density = Density(1f)) { FeatsWindowContent(prototype) {} }.use { scene ->
            shot(scene, outDir.resolve("26-feats-eresh.png"))
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
