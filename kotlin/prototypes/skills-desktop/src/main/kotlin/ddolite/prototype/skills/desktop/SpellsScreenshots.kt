package ddolite.prototype.skills.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import ddolite.builder.build.BuildPoints
import ddolite.builder.build.readBuildFile
import ddolite.prototype.skills.PointerButton
import ddolite.prototype.skills.PrototypeState
import ddolite.prototype.skills.SpellDrag
import ddolite.prototype.skills.SpellSlotKey
import ddolite.prototype.skills.SpellsWindowContent
import org.jetbrains.skia.EncodedImageFormat
import java.nio.file.Files
import java.nio.file.Path

/**
 * The Spells window off-screen: Warlock Blaster (pact slots), a spell added by double-click and one
 * by drag, then Eresh's Wizard spells (rare scrolls) if that file is there. Part of the screenshots tool.
 */
fun spellsScreenshots(prototype: PrototypeState, repoRoot: Path, outDir: Path) {
    val save = repoRoot.resolve("Save")
    prototype.openBuild(readBuildFile(Files.readAllBytes(save.resolve("Warlock Blaster.build")), prototype.data, BuildPoints.ADVENTURER))
    val screen = prototype.spells
    screen.opened()
    ImageComposeScene(width = 815, height = 518, density = Density(1f)) { SpellsWindowContent(prototype) {} }.use { scene ->
        println("Spells: " + screen.classes.map { it.name } + ", level " + screen.spellLevel + " of " + screen.maxSpellLevel() +
            ": " + screen.rows().map { it.type.toString() + "@" + it.level + "=" + it.spell })
        shot(scene, outDir.resolve("40-spells-warlock.png"))
        screen.chooseLevel(2)
        screen.clickSlot(1, PointerButton.PRIMARY)
        shot(scene, outDir.resolve("41-spells-level2.png"))
        // Empty a slot, then fill it again by double-click and by drag.
        screen.doubleClickSlot(1)
        if (screen.spells.isNotEmpty()) {
            screen.pressSpell(0)
            screen.chooseSpell()
            println("Double-click: " + screen.rows().map { it.spell })
        }
        screen.doubleClickSlot(1)
        if (screen.spells.isNotEmpty()) {
            screen.pressSpell(0)
            val item = SpellDrag.FromList(screen.spells[0].display)
            println("Drag allowed: " + screen.startDrag(item) + ", states " + screen.dropStates)
            screen.dropped(item, SpellSlotKey(1))
            println("Dropped: " + screen.rows().map { it.spell } + ", dirty " + prototype.dirty)
        }
        shot(scene, outDir.resolve("42-spells-after.png"))
    }
    val eresh = save.resolve("Eresh WFWizRog-v4.15.build")
    if (Files.exists(eresh)) {
        prototype.openBuild(readBuildFile(Files.readAllBytes(eresh), prototype.data, BuildPoints.ADVENTURER))
        screen.opened()
        screen.chooseLevel(6)
        ImageComposeScene(width = 815, height = 518, density = Density(1f)) { SpellsWindowContent(prototype) {} }.use { scene ->
            println("Eresh: " + screen.classes.map { it.name } + ", rare note " + screen.rareNote())
            shot(scene, outDir.resolve("43-spells-wizard.png"))
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
