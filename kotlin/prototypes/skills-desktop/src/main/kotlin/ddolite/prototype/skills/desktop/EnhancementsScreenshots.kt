package ddolite.prototype.skills.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import ddolite.builder.build.BuildPoints
import ddolite.builder.build.readBuildFile
import ddolite.prototype.skills.AbilityRowKey
import ddolite.prototype.skills.EnhancementDrag
import ddolite.prototype.skills.EnhancementsWindowContent
import ddolite.prototype.skills.PointerButton
import ddolite.prototype.skills.PrototypeState
import org.jetbrains.skia.EncodedImageFormat
import java.nio.file.Files
import java.nio.file.Path

/**
 * The Enhancements window off-screen: Warlock Blaster's trees and its tier 5 tree's abilities, an
 * ability chosen, one added by double-click and one by drag, a rank lowered. Uses the same calls as
 * the window's mouse handlers. Part of the screenshots tool.
 */
fun enhancementsScreenshots(prototype: PrototypeState, repoRoot: Path, outDir: Path) {
    val file = repoRoot.resolve("Save").resolve("Warlock Blaster.build")
    prototype.openBuild(readBuildFile(Files.readAllBytes(file), prototype.data, BuildPoints.ADVENTURER))
    val screen = prototype.enhancements
    screen.opened()
    ImageComposeScene(width = 815, height = 555, density = Density(1f)) { EnhancementsWindowContent(prototype) {} }.use { scene ->
        shot(scene, outDir.resolve("30-enh-trees.png"))
        screen.chooseTab(1)
        println("Enhancements tab: " + screen.comboTree + ", " + screen.slotRows().size + " slots, " + screen.abilities.size + " offered; " + screen.apCaption().first)
        shot(scene, outDir.resolve("31-enh-abilities.png"))
        screen.clickSlot(2, PointerButton.PRIMARY)
        shot(scene, outDir.resolve("32-enh-slot-details.png"))
        // Add the first offered ability with no selectors by double-click, the next by drag.
        var added = 0
        for (i in screen.abilities.indices) {
            if (added == 2) {
                break
            }
            if (!screen.clickAbility(i)) {
                continue
            }
            val text = screen.abilityText(screen.abilities[i])
            if (added == 0) {
                screen.doubleClickList(fromSelectors = false)
                println("Double-clicked " + text + ": " + screen.slotRows().size + " slots")
            } else {
                val item = EnhancementDrag.ListAbility(screen.abilities[i].first, screen.abilities[i].second)
                println("Drag allowed: " + screen.startAbilityDrag(item) + ", " + screen.slotRows().size + " slots with the blank")
                shot(scene, outDir.resolve("33-enh-dragging.png"))
                var blank = 0
                for (r in screen.slotStates.indices) {
                    if (screen.slotStates[r] != ddolite.builder.build.DropState.DEFAULT) {
                        blank = r + 1
                    }
                }
                screen.abilityDropped(item, AbilityRowKey(blank))
                println("Dropped " + text + " on row " + blank + ": " + screen.slotRows().size + " slots")
            }
            added++
        }
        // Lower the rank of the first ability with more than one rank.
        val rows = screen.slotRows()
        for (r in rows.indices) {
            if (rows[r].maxRanks > 1 && rows[r].ranks > 1) {
                screen.changeRank(r + 1, rows[r].ranks - 1)
                println("Rank of " + rows[r].caption + " now " + screen.slotRows()[r].ranks)
                break
            }
        }
        shot(scene, outDir.resolve("34-enh-after.png"))
        println("After: " + screen.apCaption().first + ", dirty " + prototype.dirty)
    }
}

private fun shot(scene: ImageComposeScene, file: Path) {
    val image = scene.render()
    val png = image.encodeToData(EncodedImageFormat.PNG)
    if (png != null) {
        Files.write(file, png.bytes)
    }
}
