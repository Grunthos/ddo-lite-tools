package ddolite.builder.ui

import ddolite.builder.model.TreeStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The screens' state driven as the windows drive it (clicks, double-clicks, menus), checking what
 * the build and the lists show afterwards. The windows themselves are drawn by the screenshot tool.
 */
class ScreenStateTest {
    @Test
    fun enhancementsAddRankClearAndUndo() {
        val session = TestSession.open("Kensei Warpriest.build")
        val screen = session.enhancements
        screen.opened()
        screen.chooseTab(1)
        val before = screen.slotRows().size
        assertTrue(before > 0)

        // Double-click the first offered ability that needs no selector.
        var added = false
        for (i in screen.abilities.indices) {
            if (screen.clickAbility(i)) {
                screen.doubleClickList(fromSelectors = false)
                added = true
                break
            }
        }
        assertTrue(added, "an ability without selectors is offered")
        assertEquals(before + 1, screen.slotRows().size)
        assertTrue(session.dirty)

        // A rank arrow lowers a multi-rank ability.
        val rows = screen.slotRows()
        var row = 0
        for (i in rows.indices) {
            if (rows[i].maxRanks > 1 && rows[i].ranks > 1) {
                row = i + 1
                break
            }
        }
        assertTrue(row > 0, "a slot with more than one rank")
        val ranks = rows[row - 1].ranks
        screen.changeRank(row, ranks - 1)
        assertEquals(ranks - 1, screen.slotRows()[row - 1].ranks)

        // Double-clicking a slot clears it.
        val count = screen.slotRows().size
        screen.doubleClickSlot(count)
        assertEquals(count - 1, screen.slotRows().size)

        // Undo all three changes: back to the build as opened.
        while (session.history.canUndo) {
            session.undo()
        }
        assertFalse(session.dirty)
        val build = session.rules!!.build
        var total = 0
        for (t in build.trees) {
            total += t.abilities.size
        }
        assertTrue(total > 0)
    }

    @Test
    fun theRaceTreeCantBeRemovedFromTheTreesTab() {
        val session = TestSession.open("Kensei Warpriest.build")
        val screen = session.enhancements
        screen.opened()
        val trees = screen.treeRows()
        var raceRow = 0
        for (i in trees.indices) {
            if (trees[i]?.treeType == TreeStyle.RACE) {
                raceRow = i + 1
            }
        }
        assertTrue(raceRow > 0)
        val count = session.rules!!.build.trees.size
        screen.doubleClickTree(raceRow)
        assertEquals(count, session.rules!!.build.trees.size)
        assertFalse(session.dirty)
    }

    @Test
    fun featsFillAnEmptySlotFromTheList() {
        val session = TestSession.open("Kensei Warpriest.build")
        val screen = session.feats
        screen.opened()
        // The first empty slot offering a feat without selectors (a Deity slot's feats all have them).
        val rows = screen.rows()
        var empty = 0
        var chosen = -1
        for (i in rows.indices) {
            if (rows[i].feat != null) {
                continue
            }
            screen.clickSlot(i + 1, PointerButton.PRIMARY)
            for (f in screen.feats.indices) {
                if (screen.clickFeat(f)) {
                    chosen = f
                    break
                }
            }
            if (chosen >= 0) {
                empty = i + 1
                break
            }
        }
        assertTrue(empty > 0, "an empty slot offering a feat without selectors")
        val name = screen.feats[chosen].name
        screen.doubleClickList()
        assertEquals(name, screen.rows()[empty - 1].feat?.name)
        assertTrue(session.dirty)
        session.undo()
        assertNull(screen.rows()[empty - 1].feat)
    }

    @Test
    fun guideListFollowsADeletedRow() {
        val session = TestSession.open("Kensei Warpriest.build")
        val enhancements = session.enhancements
        enhancements.opened()
        enhancements.chooseTab(2)
        val guide = enhancements.guide
        var kensei = -1
        for (i in guide.trees.indices) {
            if (guide.trees[i].text == "Kensei") {
                kensei = i
            }
        }
        assertTrue(kensei >= 0)
        guide.clickTree(kensei)
        // Add the first offered ability that needs no selector.
        var text = ""
        for (i in guide.choices.indices) {
            guide.clickChoice(i)
            if (!guide.selectorsShowing) {
                text = guide.choices[i].text
                guide.chooseAbility()
                break
            }
        }
        assertTrue(text.isNotEmpty(), "an ability without selectors")
        assertEquals(1, guide.rows().size)
        assertFalse(guide.choices.any { it.text == text }, "taken, so no longer offered")

        // Right-click the row and delete it: the list offers it again, with Kensei still selected.
        guide.pressRow(1, right = true, ctrl = false, shift = false)
        var delete = -1
        val items = guide.menuItems()
        for (i in items.indices) {
            if (items[i].caption.startsWith("Delete Row")) {
                delete = i
            }
        }
        assertTrue(delete >= 0, "a Delete Rows item")
        guide.chooseMenu(delete)
        assertEquals(0, guide.rows().size)
        assertEquals(kensei, guide.treeIndex)
        assertTrue(guide.choices.any { it.text == text }, "offered again after the delete")
    }

    @Test
    fun spellsClearAndChooseASpell() {
        val session = TestSession.open("Kensei Warpriest.build")
        val screen = session.spells
        screen.opened()
        assertTrue(screen.classes.isNotEmpty())
        screen.chooseClass(screen.classes[0])
        val rows = screen.rows()
        var row = 0
        for (i in rows.indices) {
            if (rows[i].spell.isNotEmpty() && rows[i].type == ddolite.builder.build.SpellSlotType.STANDARD) {
                row = i + 1
                break
            }
        }
        assertTrue(row > 0, "a standard slot with a spell")
        screen.doubleClickSlot(row)
        assertEquals("", screen.rows()[row - 1].spell)
        assertTrue(screen.spells.isNotEmpty())
        screen.pressSpell(0)
        screen.chooseSpell()
        assertNotNull(screen.rows().getOrNull(row - 1))
        assertTrue(screen.rows()[row - 1].spell.isNotEmpty(), "the first empty slot is filled")
    }
}
