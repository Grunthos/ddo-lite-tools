package ddolite.builder.build

import ddolite.builder.data.LoadLog
import ddolite.builder.data.loadBuilderData
import ddolite.dataformat.DirectoryDataSource
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Undo and redo ([BuildHistory]) and the copies they keep ([Build.deepCopy]). */
class BuildHistoryTest {
    private val data = loadBuilderData(DirectoryDataSource(Path.of(System.getProperty("ddolite.dataDir"))), LoadLog())

    private fun open(name: String): BuildRules {
        val rules = readBuildFile(SampleBuilds.file(name).readBytes(), data, BuildPoints.ADVENTURER).rules
        // As the Spells window does on opening: the pact slots filled (the load leaves them empty).
        rules.initBuildSpells()
        return rules
    }

    @Test
    fun undoGivesBackTheSameBuild() {
        for (name in SampleBuilds.names()) {
            val rules = open(name)
            val before = writeBuildFile(rules)
            val history = BuildHistory(data)
            history.reset(rules.build)
            rules.build.buildName = "Changed"
            rules.build.trees.clear()
            rules.cascadeAll()
            history.record(rules.build)
            val treesAfter = rules.build.trees.size // the cascade puts the race tree back
            val undone = assertNotNull(history.undo(), name)
            assertContentEquals(before, writeBuildFile(undone.second), name)
            assertTrue(history.atSaved, name)
            val redone = assertNotNull(history.redo(), name)
            assertEquals("Changed", redone.first.buildName, name)
            assertEquals(treesAfter, redone.first.trees.size, name)
            assertFalse(history.canRedo, name)
        }
    }

    @Test
    fun copiesAreIndependent() {
        val rules = open("Kensei Warpriest.build")
        val copy = rules.build.deepCopy()
        val before = writeBuildFile(BuildRules(data, copy))
        rules.build.skills[0][0] = 9
        rules.build.trees[0].abilities.clear()
        rules.build.feats(BuildFeatType.STANDARD)[0].featName = "Toughness"
        rules.build.statPoints[0][0] = 99
        assertContentEquals(before, writeBuildFile(BuildRules(data, copy)))
    }

    @Test
    fun newChangeDropsTheRedoStatesAndTheLimitHolds() {
        val rules = open("Kensei Warpriest.build")
        val history = BuildHistory(data, limit = 3)
        history.reset(rules.build)
        for (i in 1..5) {
            rules.build.buildName = "Name $i"
            history.record(rules.build)
        }
        // Only three states are kept: names 3, 4 and 5.
        assertEquals("Name 4", history.undo()?.first?.buildName)
        assertEquals("Name 3", history.undo()?.first?.buildName)
        assertFalse(history.canUndo)
        rules.build.buildName = "Other"
        history.record(rules.build)
        assertFalse(history.canRedo)
        assertFalse(history.atSaved)
    }
}
