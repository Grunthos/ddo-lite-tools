package ddolite.builder.ui

import ddolite.builder.build.BLANK_ABILITY
import ddolite.builder.build.BuildFeatType
import ddolite.builder.build.BuildTree
import ddolite.builder.build.DropState
import ddolite.builder.build.FeatTab
import ddolite.builder.model.TreeStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Destinies window, the Enhancements window's Trees tab and the Feats window's slots, driven as
 * the windows drive them. Written before sharing code between the Enhancements and Destinies
 * windows, and before moving the Feats slots onto UserList (CODE-REVIEW.md), to catch any change in
 * behaviour.
 */
class TreeAndSlotScreensTest {
    // ---------------------------------------------------------------- helpers

    private fun destinyNames(session: BuilderSession): List<String> {
        val names = ArrayList<String>()
        for (t in session.rules!!.build.destinies) {
            names.add(t.treeName)
        }
        return names
    }

    private fun treeNames(session: BuilderSession): List<String> {
        val names = ArrayList<String>()
        for (t in session.rules!!.build.trees) {
            names.add(t.treeName)
        }
        return names
    }

    /** The race tree's row on the Trees tab (1-based). */
    private fun raceTreeRow(session: BuilderSession): Int {
        val trees = session.rules!!.build.trees
        for (i in trees.indices) {
            if (trees[i].treeType == TreeStyle.RACE) {
                return i + 1
            }
        }
        return 0
    }

    private fun undoAll(session: BuilderSession) {
        while (session.history.canUndo) {
            session.undo()
        }
    }

    // ---------------------------------------------------------------- Destiny Trees tab

    @Test
    fun destinyTreesRemoveAddSwapAndUndo() {
        val session = TestSession.open("Kensei Warpriest.build")
        val screen = session.destiny
        screen.opened()
        val before = destinyNames(session)
        assertEquals(3, before.size, "the sample build has three destinies")
        assertEquals(3, screen.treeRows().size)

        // Double-clicking a destiny removes it; it's offered in the list again.
        screen.doubleClickTree(3)
        assertEquals(before.subList(0, 2), destinyNames(session))
        assertTrue(screen.availableDestinies().contains(before[2]))
        assertTrue(session.dirty)

        // Double-clicking a listed destiny adds it after the others.
        val listed = screen.availableDestinies()
        screen.pressListTree(0)
        screen.doubleClickListTree()
        assertEquals(3, destinyNames(session).size)
        assertEquals(listed[0], destinyNames(session)[2])
        assertFalse(screen.availableDestinies().contains(listed[0]))

        // A fourth can't be added.
        screen.pressListTree(0)
        screen.doubleClickListTree()
        assertEquals(3, destinyNames(session).size)

        // Dragging row 1 onto row 2 swaps them.
        val order = destinyNames(session)
        assertTrue(screen.startTreeDrag(EnhancementDrag.TreeRow(1)))
        screen.treeDropped(EnhancementDrag.TreeRow(1), TreeRowKey(2))
        assertEquals(listOf(order[1], order[0], order[2]), destinyNames(session))

        // Dropping a listed destiny on a row replaces that row's destiny.
        screen.pressListTree(0)
        val replacement = screen.availableDestinies()[0]
        assertTrue(screen.startTreeDrag(EnhancementDrag.ListTree(replacement)))
        screen.treeDropped(EnhancementDrag.ListTree(replacement), TreeRowKey(3))
        assertEquals(replacement, destinyNames(session)[2])

        undoAll(session)
        assertEquals(before, destinyNames(session))
        assertFalse(session.dirty)
    }

    @Test
    fun destinyTreeMenuResetsAndDeletes() {
        val session = TestSession.open("Kensei Warpriest.build")
        val screen = session.destiny
        screen.opened()
        val names = destinyNames(session)
        assertNotEquals("0", screen.treeColumns(1).ap, "the first destiny has AP spent")

        // A right-click opens the menu for that row.
        screen.clickTree(1, PointerButton.SECONDARY)
        val items = screen.treeMenuItems()
        assertTrue(items[0].startsWith("Delete "))
        assertEquals("Reset All Trees", items[4])

        // Reset clears its abilities but keeps the destiny.
        screen.chooseTreeMenu(3)
        assertEquals("0", screen.treeColumns(1).ap)
        assertEquals(names, destinyNames(session))

        // Delete removes it.
        screen.clickTree(1, PointerButton.SECONDARY)
        screen.chooseTreeMenu(0)
        assertEquals(names.subList(1, 3), destinyNames(session))
    }

    @Test
    fun permanentDestinyPoints() {
        val session = TestSession.open("Kensei Warpriest.build")
        val screen = session.destiny
        screen.opened()
        val before = screen.pdp()
        val target = if (before == screen.pdpMax()) screen.pdpMin() else screen.pdpMax()
        screen.setPdp(target)
        assertEquals(target, screen.pdp())
        assertTrue(screen.apCaption().text.contains("pdp"))
        session.undo()
        assertEquals(before, screen.pdp())
    }

    // ---------------------------------------------------------------- Destinies tab

    @Test
    fun destinyAbilitiesAddRankClearAndUndo() {
        val session = TestSession.open("Kensei Warpriest.build")
        val screen = session.destiny
        screen.opened()
        screen.chooseTab(1)
        assertEquals(1, screen.tab)
        assertTrue(screen.comboTree.isNotEmpty())
        val before = screen.slotRows().size

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

        // The slot menu's "Clear this ability", then a double-click, each clear one.
        val count = screen.slotRows().size
        screen.clickSlot(count, PointerButton.SECONDARY)
        assertEquals(count, screen.slotMenuRow)
        screen.chooseSlotMenu(0)
        assertEquals(count - 1, screen.slotRows().size)
        screen.doubleClickSlot(count - 1)
        assertEquals(count - 2, screen.slotRows().size)

        // Choosing another destiny in the combo shows its abilities.
        val combo = screen.comboTrees()
        if (combo.size > 1) {
            screen.chooseComboTree(combo[1])
            assertEquals(combo[1], screen.comboTree)
        }

        undoAll(session)
        assertFalse(session.dirty)
    }

    // ---------------------------------------------------------------- Enhancements Trees tab

    @Test
    fun enhancementTreesAddSwapRemoveAndUndo() {
        val session = TestSession.open("Kensei Warpriest.build")
        val screen = session.enhancements
        screen.opened()
        val before = treeNames(session)
        assertTrue(before.size < TREE_SLOTS, "room for another tree")

        // Double-clicking a listed tree adds it after the others.
        val listed = screen.availableTrees()
        screen.pressListTree(0)
        screen.doubleClickListTree()
        assertEquals(before.size + 1, treeNames(session).size)
        assertEquals(listed[0].name, treeNames(session)[before.size])

        // Dragging row 1 onto row 2 swaps them.
        val order = treeNames(session)
        assertTrue(screen.startTreeDrag(EnhancementDrag.TreeRow(1)))
        screen.treeDropped(EnhancementDrag.TreeRow(1), TreeRowKey(2))
        assertEquals(order[1], treeNames(session)[0])
        assertEquals(order[0], treeNames(session)[1])

        // Double-clicking the added tree removes it; the race tree stays.
        screen.doubleClickTree(treeNames(session).indexOf(listed[0].name) + 1)
        assertEquals(before.size, treeNames(session).size)
        val raceRow = raceTreeRow(session)
        screen.doubleClickTree(raceRow)
        assertEquals(before.size, treeNames(session).size)

        undoAll(session)
        assertEquals(before, treeNames(session))
        assertFalse(session.dirty)
    }

    @Test
    fun enhancementTreeMenuReset() {
        val session = TestSession.open("Kensei Warpriest.build")
        val screen = session.enhancements
        screen.opened()
        // A tree that isn't the race tree: Reset leaves it with no abilities.
        val row = if (raceTreeRow(session) == 1) 2 else 1
        assertTrue(session.rules!!.build.trees[row - 1].abilities.isNotEmpty())
        screen.clickTree(row, PointerButton.SECONDARY)
        val items = screen.treeMenuItems()
        var reset = -1
        for (i in items.indices) {
            if (items[i].caption.startsWith("Reset ") && items[i].caption != "Reset All Trees" && items[i].enabled) {
                reset = i
            }
        }
        assertTrue(reset >= 0, "a Reset item for the tree")
        screen.chooseTreeMenu(reset)
        assertTrue(session.rules!!.build.trees[row - 1].abilities.isEmpty())
    }

    @Test
    fun placeholderSlotsShowExplainAndClear() {
        // Kensei Warpriest's Kensei tree has four "Weapon Group Specialization: Axes" lines the data
        // no longer has: red placeholder slots after the last ability of their tier.
        val session = TestSession.open("Kensei Warpriest.build")
        val screen = session.enhancements
        screen.opened()
        screen.chooseTab(1)
        screen.chooseComboTree("Kensei")
        fun kensei(): BuildTree = session.rules!!.build.trees[session.rules!!.findBuildTree("Kensei") - 1]
        val bt = kensei()
        val abilities = bt.abilities.size
        val rows = screen.slotRows()
        assertEquals(abilities + bt.missing.size, rows.size)
        var row = 0
        for (i in rows.indices) {
            if (rows[i].cost == "?") {
                row = i + 1
                break
            }
        }
        assertTrue(row in 1..abilities, "the first placeholder is among the abilities")
        val first = rows[row - 1]
        assertEquals("Weapon Group Specialization: Axes", first.caption)
        assertTrue(first.error)
        // Where the file had it, in tier order: no ability before it has a higher tier, none after a lower one.
        val tier = first.tier.toInt()
        for (i in rows.indices) {
            if (rows[i].cost != "?") {
                assertTrue(if (i < row - 1) rows[i].tier.toInt() <= tier else rows[i].tier.toInt() >= tier, "row ${i + 1}")
            }
        }
        // Clicking it explains; a right-click on it (selected) opens the menu rather than clearing it.
        screen.clickSlot(row, PointerButton.PRIMARY)
        assertEquals("Not in the current data", screen.details?.caption)
        val count = bt.missing.size
        screen.clickSlot(row, PointerButton.SECONDARY)
        assertEquals(row, screen.slotMenuRow)
        assertEquals(count, bt.missing.size)
        screen.closeSlotMenu()
        // Double-clicking removes it (undoably).
        screen.doubleClickSlot(row)
        assertEquals(count - 1, bt.missing.size)
        assertEquals(abilities, bt.abilities.size)
        session.undo()
        assertEquals(count, kensei().missing.size)
    }

    @Test
    fun abilityDroppedOnAPlaceholderReplacesIt() {
        val session = TestSession.open("Kensei Warpriest.build")
        val screen = session.enhancements
        screen.opened()
        screen.chooseTab(1)
        screen.chooseComboTree("Kensei")
        fun kensei(): BuildTree = session.rules!!.build.trees[session.rules!!.findBuildTree("Kensei") - 1]
        val count = kensei().missing.size
        val abilities = kensei().abilities.size
        var chosen = -1
        for (i in screen.abilities.indices) {
            if (screen.clickAbility(i)) {
                chosen = i
                break
            }
        }
        assertTrue(chosen >= 0, "an ability without selectors is offered")
        val item = EnhancementDrag.ListAbility(screen.abilities[chosen].tier, screen.abilities[chosen].ability)
        assertTrue(screen.startAbilityDrag(item))
        // The placeholders light up as drop targets along with the blank slot.
        val rows = screen.slotRows()
        var target = 0
        for (i in rows.indices) {
            if (rows[i].cost == "?") {
                assertTrue(screen.slotStates[i] != DropState.DEFAULT, "placeholder row ${i + 1}")
                target = i + 1
            }
        }
        screen.abilityDropped(item, AbilityRowKey(target))
        assertEquals(count - 1, kensei().missing.size)
        assertEquals(abilities + 1, kensei().abilities.size)
        for (a in kensei().abilities) {
            assertTrue(a.ability != BLANK_ABILITY)
        }
        session.undo()
        assertEquals(count, kensei().missing.size)
        assertEquals(abilities, kensei().abilities.size)
    }

    // ---------------------------------------------------------------- Feats slots

    /** The first row with a feat whose slot is the given kind, or 0. */
    private fun filledRow(screen: FeatsScreenState, after: Int = 0): Int {
        val rows = screen.rows()
        for (i in after until rows.size) {
            if (rows[i].feat != null && rows[i].actualType != BuildFeatType.GRANTED) {
                return i + 1
            }
        }
        return 0
    }

    @Test
    fun featsDragFromListSwapAndDragBack() {
        val session = TestSession.open("Kensei Warpriest.build")
        val screen = session.feats
        screen.opened()

        // Drag a feat without selectors from the list onto the first empty slot that takes one.
        val rows = screen.rows()
        var target = 0
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
                target = i + 1
                break
            }
        }
        assertTrue(target > 0, "an empty slot offering a feat without selectors")
        val feat = screen.feats[chosen]
        assertTrue(screen.startDrag(FeatDrag.FromList(feat)))
        screen.dropped(FeatDrag.FromList(feat), FeatSlotKey(target))
        assertEquals(feat.name, screen.rows()[target - 1].feat?.name)

        // Drag one filled slot onto another: they swap.
        val one = filledRow(screen)
        val two = filledRow(screen, one)
        assertTrue(one > 0 && two > 0)
        val first = screen.rows()[one - 1].feat?.name
        val second = screen.rows()[two - 1].feat?.name
        assertTrue(screen.startDrag(FeatDrag.FromSlot(one)))
        screen.dropped(FeatDrag.FromSlot(one), FeatSlotKey(two))
        assertEquals(second, screen.rows()[one - 1].feat?.name)
        assertEquals(first, screen.rows()[two - 1].feat?.name)

        // Drag a slot's feat back to the list: the slot is cleared.
        assertTrue(screen.startDrag(FeatDrag.FromSlot(one)))
        screen.dropped(FeatDrag.FromSlot(one), FeatListKey)
        assertNull(screen.rows()[one - 1].feat)

        undoAll(session)
        assertFalse(session.dirty)
    }

    @Test
    fun featsSlotMenu() {
        val session = TestSession.open("Kensei Warpriest.build")
        val screen = session.feats
        screen.opened()
        val row = filledRow(screen)
        assertTrue(row > 0)
        val count = screen.rows().size

        // A right-click opens the menu; Add Alternate adds a slot under it.
        screen.clickSlot(row, PointerButton.SECONDARY)
        assertEquals(row, screen.menuRow)
        val items = screen.menuItems()
        assertTrue(items.contains("Clear This Feat"))
        assertTrue(items.contains("Add Alternate"))
        screen.chooseMenu("Add Alternate")
        assertEquals(count + 1, screen.rows().size)

        // Clear This Feat empties the slot.
        screen.clickSlot(row, PointerButton.SECONDARY)
        screen.chooseMenu("Clear This Feat")
        assertNull(screen.rows()[row - 1].feat)

        // A double-click on a filled slot clears it too.
        val next = filledRow(screen)
        screen.doubleClickSlot(next)
        assertNull(screen.rows()[next - 1].feat)

        undoAll(session)
        assertEquals(count, screen.rows().size)
        assertFalse(session.dirty)
    }

    @Test
    fun featsGrantedTabIsReadOnly() {
        val session = TestSession.open("Kensei Warpriest.build")
        val screen = session.feats
        screen.opened()
        screen.chooseTab(FeatTab.GRANTED)
        val rows = screen.rows()
        assertTrue(rows.isNotEmpty(), "the build has granted feats")
        assertNotNull(rows[0].feat)
        // No menu and no clearing on the Granted tab.
        screen.clickSlot(1, PointerButton.SECONDARY)
        assertEquals(0, screen.menuRow)
        screen.doubleClickSlot(1)
        assertNotNull(screen.rows()[0].feat)
        assertFalse(session.dirty)
        assertFalse(screen.startDrag(FeatDrag.FromSlot(1)))
    }
}
