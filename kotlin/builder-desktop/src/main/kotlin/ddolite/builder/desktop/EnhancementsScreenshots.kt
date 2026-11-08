package ddolite.builder.desktop

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import ddolite.builder.build.BuildPoints
import ddolite.builder.build.GuideFilter
import ddolite.builder.build.readBuildFile
import ddolite.builder.ui.AbilityRowKey
import ddolite.builder.ui.EnhancementDrag
import ddolite.builder.ui.EnhancementsWindowContent
import ddolite.builder.ui.PointerButton
import ddolite.builder.ui.BuilderSession
import ddolite.builder.ui.GUIDE_ROWS
import org.jetbrains.skia.EncodedImageFormat
import java.nio.file.Files
import java.nio.file.Path

/**
 * The Enhancements window off-screen: Warlock Blaster's trees and its tier 5 tree's abilities, an
 * ability chosen, one added by double-click and one by drag, a rank lowered. Uses the same calls as
 * the window's mouse handlers. Part of the screenshots tool.
 */
fun enhancementsScreenshots(prototype: BuilderSession, repoRoot: Path, outDir: Path) {
    val file = oldData(repoRoot).resolve("Warlock Blaster.build")
    prototype.openBuild(readBuildFile(Files.readAllBytes(file), prototype.data, BuildPoints.ADVENTURER))
    val screen = prototype.enhancements
    screen.opened()
    ImageComposeScene(width = 1223, height = 833, density = Density(1.5f)) { EnhancementsWindowContent(prototype) {} }.use { scene ->
        shot(scene, outDir.resolve("30b-enh-trees-150.png"))
    }
    ImageComposeScene(width = 815, height = 555, density = Density(1f)) { EnhancementsWindowContent(prototype) {} }.use { scene ->
        shot(scene, outDir.resolve("30-enh-trees.png"))
        screen.chooseTab(1)
        println("Enhancements tab: " + screen.comboTree + ", " + screen.slotRows().size + " slots, " + screen.abilities.size + " offered; " + screen.apCaption().text)
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
                val item = EnhancementDrag.ListAbility(screen.abilities[i].tier, screen.abilities[i].ability)
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
        println("After: " + screen.apCaption().text + ", dirty " + prototype.dirty)
    }
    guideScreenshots(prototype, repoRoot, outDir)
}

/**
 * The Leveling Guide tab: Kensei Warpriest (whose saved guide is dropped on loading, as in VB),
 * a guide built from the lists (the Kensei core, then the first few offered, a bank and a reset),
 * rows selected, and the row menu.
 */
@OptIn(ExperimentalComposeUiApi::class)
private fun guideScreenshots(prototype: BuilderSession, repoRoot: Path, outDir: Path) {
    val file = oldData(repoRoot).resolve("Kensei Warpriest.build")
    prototype.openBuild(readBuildFile(Files.readAllBytes(file), prototype.data, BuildPoints.ADVENTURER))
    val screen = prototype.enhancements
    screen.opened()
    val guide = screen.guide
    ImageComposeScene(width = 815, height = 555, density = Density(1f)) { EnhancementsWindowContent(prototype) {} }.use { scene ->
        // Kensei's tree: the lines the data no longer has as red placeholders, one selected.
        screen.chooseTab(1)
        screen.chooseComboTree("Kensei")
        val kenseiRows = screen.slotRows()
        var placeholderRow = 0
        for (i in kenseiRows.indices) {
            if (placeholderRow == 0 && kenseiRows[i].cost == "?") {
                placeholderRow = i + 1
            }
        }
        screen.clickSlot(placeholderRow, ddolite.builder.ui.PointerButton.PRIMARY)
        scene.render() // a first frame, so the slot list knows it overflows (its scroll bar)
        shot(scene, outDir.resolve("37-enh-placeholders.png"))
        screen.chooseTab(2)
        println("Guide: trees " + guide.trees.map { it.text } + "; " + guide.totals?.levelCaption() + ", " + guide.totals?.apCaption())
        // The saved guide's first step names an ability the data no longer has: a red row.
        guide.pressRow(1, right = false, ctrl = false, shift = false)
        guide.releaseRow(1)
        println("  row 1: " + guide.rows()[0].style + " " + guide.detailsCaption)
        shot(scene, outDir.resolve("38-guide-missing-row.png"))
        // "Available" on a Cleric tree at level 1 (the build's first Cleric level is 3).
        val rules = prototype.rules
        if (rules != null) {
            for (i in guide.trees.indices) {
                val number = guide.trees[i].number
                if (number != 0 && rules.guideTrees[number - 1].characterClass?.name == "Cleric") {
                    guide.chooseFilter(GuideFilter.ALL_AVAILABLE)
                    guide.clickTree(i)
                    println("  " + guide.trees[i].text + " Available: " + guide.nothingAvailable)
                    shot(scene, outDir.resolve("39-guide-nothing-available.png"))
                    guide.chooseFilter(GuideFilter.SELECTED_AVAILABLE)
                    break
                }
            }
        }
        guide.clickTree(guide.trees.indexOfFirst { it.text == "Kensei" })
        println("  Kensei offers " + guide.choices.size + ": " + guide.choices.take(4).map { it.text })
        guide.clickChoice(0)
        println("  selectors showing " + guide.selectorsShowing + ": " + guide.selectors.map { it.text })
        shot(scene, outDir.resolve("40-guide-selectors.png"))
        guide.clickSelector(0)
        guide.chooseAbility()
        for (n in 0 until 9) {
            var added = false
            for (i in guide.choices.indices) {
                guide.clickChoice(i)
                if (!guide.selectorsShowing) {
                    guide.chooseAbility()
                    added = true
                    break
                }
            }
            if (!added) {
                break
            }
        }
        guide.clickTree(guide.trees.indexOfFirst { it.number == 0 })
        println("  commands: " + guide.choices.map { it.text })
        guide.clickChoice(0)
        guide.chooseAbility()
        println("  " + guide.rows().size + " rows; " + guide.totals?.levelCaption() + ", " + guide.totals?.apCaption() + ", " + guide.totals?.spentCaption())
        for (e in guide.rows()) {
            println("    L" + e.level + " " + e.display + e.rankText + " " + e.errorText)
        }
        guide.pressRow(2, right = false, ctrl = false, shift = false)
        guide.releaseRow(2)
        shot(scene, outDir.resolve("41-guide-grid.png"))
        guide.pressRow(5, right = false, ctrl = false, shift = true)
        guide.pressRow(4, right = true, ctrl = false, shift = false)
        println("  menu: " + guide.menuItems())
        guide.chooseMenu(0) // Move Rows
        guide.pressRow(9, right = true, ctrl = false, shift = false)
        println("  menu now: " + guide.menuItems())
        guide.chooseMenu(0) // Insert Rows Here
        println("  after moving rows 2-5 before row 9: " + guide.rows().map { it.display + it.rankText })
        shot(scene, outDir.resolve("42-guide-moved.png"))
        // Past 20 rows (a bank a level): the grid's scroll bar comes on, scrolled to the end.
        guide.clickTree(guide.trees.indexOfFirst { it.number == 0 })
        while (guide.rows().size <= GUIDE_ROWS + 3) {
            guide.clickChoice(0)
            guide.chooseAbility()
        }
        println("  " + guide.rows().size + " rows, first shown " + guide.scroll)
        shot(scene, outDir.resolve("43-guide-scrolling.png"))
        // Right-click the bottom row's Ability cell: the menu opens upward, inside the window.
        scene.render()
        val bottomRow = Offset(500f, 49f + 19 * 22f + 11f) // the grid starts at 49 (content top 32, a line under its header)
        scene.sendPointerEvent(PointerEventType.Move, bottomRow)
        scene.sendPointerEvent(PointerEventType.Press, bottomRow, buttons = PointerButtons(isSecondaryPressed = true), button = androidx.compose.ui.input.pointer.PointerButton.Secondary)
        scene.sendPointerEvent(PointerEventType.Release, bottomRow, buttons = PointerButtons(), button = androidx.compose.ui.input.pointer.PointerButton.Secondary)
        println("  menu at the bottom row: " + guide.menuItems().map { it.caption })
        shot(scene, outDir.resolve("44-guide-menu-bottom.png"))
    }
}

private fun shot(scene: ImageComposeScene, file: Path) {
    val image = scene.render()
    val png = image.encodeToData(EncodedImageFormat.PNG)
    if (png != null) {
        Files.write(file, png.bytes)
    }
}
