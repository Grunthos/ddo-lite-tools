package ddolite.prototype.skills.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import ddolite.builder.build.BuildFeatType
import ddolite.builder.build.BuildPoints
import ddolite.builder.build.readBuildFile
import ddolite.prototype.skills.MessagesWindowContent
import ddolite.prototype.skills.PrototypeState
import org.jetbrains.skia.EncodedImageFormat
import java.nio.file.Files
import java.nio.file.Path

/**
 * The Messages window off-screen, on a build whose feats and enhancements the data no longer has:
 * Warlock Blaster with two feats renamed, as an old build file would have them. Also any
 * `Save/` build with "old" in its name. Part of the screenshots tool.
 */
fun messagesScreenshots(prototype: PrototypeState, repoRoot: Path, outDir: Path) {
    val save = repoRoot.resolve("Save")
    val builds = ArrayList<Pair<String, ByteArray>>()
    var text = String(Files.readAllBytes(save.resolve("Warlock Blaster.build")), Charsets.ISO_8859_1)
    text = text.replace("\tEmpower Spell\r\n", "\tEmpowered Spell\r\n").replace("\tToughness\r\n", "\tToughness: Extra\r\n")
    builds.add(Pair("warlock-renamed", text.toByteArray(Charsets.ISO_8859_1)))
    Files.newDirectoryStream(save, "*old*.build").use { files ->
        for (file in files) {
            builds.add(Pair(file.fileName.toString().removeSuffix(".build").replace(" ", "-").lowercase(), Files.readAllBytes(file)))
        }
    }
    val screen = prototype.messages
    for ((name, bytes) in builds) {
        prototype.openBuild(readBuildFile(bytes, prototype.data, BuildPoints.ADVENTURER))
        println("$name: " + screen.label + "; open " + screen.isOpen)
        for (message in screen.messages) {
            println("  " + message.text)
        }
        ImageComposeScene(width = 815, height = 517, density = Density(1f)) { MessagesWindowContent(prototype) }.use { scene ->
            shot(scene, outDir.resolve("60-messages-$name.png"))
            println("  guesses: " + screen.guesses.joinToString { it.name } + "; selectors: " + screen.selectors)
            // Choose the first guess (with its first selector) for the first message.
            if (screen.guesses.isNotEmpty()) {
                if (screen.selectors.isNotEmpty()) {
                    screen.clickSelector(0)
                }
                shot(scene, outDir.resolve("61-messages-$name-chosen.png"))
                screen.choose()
                println("  after Choose: " + screen.label + "; open " + screen.isOpen + "; dirty " + prototype.dirty)
                shot(scene, outDir.resolve("62-messages-$name-next.png"))
            }
        }
        val rules = prototype.rules
        if (rules != null) {
            for (slot in rules.build.feats(BuildFeatType.STANDARD)) {
                if (slot.level == 9 || slot.level == 12 || slot.level == 21 || slot.level == 24) {
                    println("  level " + slot.level + ": " + slot.featName + " " + slot.selector)
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
