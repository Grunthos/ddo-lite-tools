package ddolite.builder.build

import ddolite.builder.data.LoadLog
import ddolite.builder.data.loadBuilderData
import ddolite.builder.model.TreeStyle
import ddolite.dataformat.DirectoryDataSource
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Enhancements and Destinies windows' edits ([TreeEdits]). */
class TreeEditsTest {
    private val data = loadBuilderData(DirectoryDataSource(Path.of(System.getProperty("ddolite.dataDir"))), LoadLog())

    private fun open(name: String): BuildRules = readBuildFile(SampleBuilds.file(name).readBytes(), data, BuildPoints.ADVENTURER).rules

    private fun raceRow(rules: BuildRules): Int {
        for (i in rules.build.trees.indices) {
            if (rules.build.trees[i].treeType == TreeStyle.RACE) {
                return i + 1
            }
        }
        return 0
    }

    @Test
    fun theRaceTreeCantBeRemoved() {
        val rules = open("Kensei Warpriest.build")
        val edits = TreeEdits(rules, destinies = false)
        val race = raceRow(rules)
        assertTrue(race > 0)
        assertFalse(edits.deleteTree(race))
        assertFalse(edits.removeTree(race))
        val count = rules.build.trees.size
        edits.deleteAllTrees()
        assertEquals(1, rules.build.trees.size, "Delete All Trees starts again with the race tree")
        assertTrue(count > 1)
        assertEquals(0, rules.build.trees[0].abilities.size, "VB quirk: the race tree's abilities go")
    }

    @Test
    fun swapAndRanks() {
        val rules = open("Kensei Warpriest.build")
        val edits = TreeEdits(rules, destinies = false)
        val first = rules.build.trees[0].treeName
        val second = rules.build.trees[1].treeName
        edits.swapTrees(1, 2)
        assertEquals(second, rules.build.trees[0].treeName)
        assertEquals(first, rules.build.trees[1].treeName)
        val bt = rules.build.trees[0]
        val before = assertNotNull(edits.setRanks(bt, 1, 1))
        assertEquals(1, bt.abilities[0].rank)
        assertEquals(before.ability, bt.abilities[0].ability)
        assertNull(edits.setRanks(bt, 99, 1))
    }

    @Test
    fun clearingTheLastTier5AbilityClearsTheTier5Tree() {
        val rules = open("Kensei Warpriest.build")
        val edits = TreeEdits(rules, destinies = false)
        val tier5 = rules.build.tier5
        assertTrue(tier5.isNotEmpty())
        var bt: BuildTree? = null
        for (t in rules.build.trees) {
            if (t.treeName == tier5) {
                bt = t
            }
        }
        assertNotNull(bt)
        // Clear from the end until no tier 5 ability is left.
        while (bt.abilities.isNotEmpty() && bt.abilities[bt.abilities.size - 1].tier == 5) {
            assertTrue(edits.clearSlot(bt, bt.abilities.size))
        }
        assertEquals("", rules.build.tier5)
        assertFalse(edits.clearSlot(bt, 0))
    }

    @Test
    fun aTier5AbilityAddedMakesItsTreeTheTier5Tree() {
        val rules = open("Kensei Warpriest.build")
        val edits = TreeEdits(rules, destinies = false)
        val other = rules.build.trees.first { it.treeName != rules.build.tier5 && it.treeType != TreeStyle.RACE }
        val question = edits.tier5Question(other, 5)
        assertNotNull(question)
        assertTrue(question.startsWith("Make " + other.treeName + " your Tier 5 tree?"))
        val at = edits.insertAbility(other, 5, 0, 1, 0, blank = false)
        assertEquals(5, other.abilities[at].tier)
        assertEquals(other.treeName, rules.build.tier5)
        // A blank slot changes nothing but the list.
        val blankAt = edits.insertAbility(other, 1, 0, 1, 0, blank = true)
        assertEquals(BLANK_ABILITY, other.abilities[blankAt].ability)
        edits.removeBlanks(other)
        for (a in other.abilities) {
            assertTrue(a.ability != BLANK_ABILITY)
        }
    }

    @Test
    fun destinies() {
        val rules = open("Kensei Warpriest.build")
        val edits = TreeEdits(rules, destinies = true)
        val count = rules.build.destinies.size
        assertTrue(count > 0)
        assertNull(edits.tier5Question(rules.build.destinies[0], 4))
        val added = edits.newDestiny("Grandmaster of Flowers")
        assertEquals(TreeStyle.DESTINY, added.treeType)
        edits.putTree(count + 1, added)
        assertEquals(count + 1, rules.build.destinies.size)
        assertTrue(edits.removeTree(count + 1))
        edits.deleteAllTrees()
        assertEquals(0, rules.build.destinies.size, "destinies have no race tree")
    }

    @Test
    fun loadLimitsDropTheTier5TreeOnLowLevelBuilds() {
        val rules = open("Kensei Warpriest.build")
        rules.build.maxLevels = 11
        TreeEdits(rules, destinies = false).loadDataLimits()
        assertEquals("", rules.build.tier5)
        rules.build.permDestinyPoints = -5
        TreeEdits(rules, destinies = true).loadDataLimits()
        assertEquals(data.tomes.permDestinyPointMin, rules.build.permDestinyPoints)
    }
}
