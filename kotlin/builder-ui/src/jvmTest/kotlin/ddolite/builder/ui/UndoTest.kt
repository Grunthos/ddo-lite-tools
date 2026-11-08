package ddolite.builder.ui

import ddolite.builder.build.writeBuildFile
import ddolite.builder.model.Stat
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Edit › Undo and Redo through the session, as the windows use them. */
class UndoTest {
    private fun session(): BuilderSession = TestSession.open("old-data/Kensei Warpriest.build")

    @Test
    fun undoAndRedoAChange() {
        val state = session()
        val before = writeBuildFile(state.prepareSave()!!)
        assertFalse(state.history.canUndo)
        state.stats.setTome(Stat.STR, 3)
        assertTrue(state.dirty)
        assertTrue(state.history.canUndo)
        state.undo()
        assertContentEquals(before, writeBuildFile(state.prepareSave()!!))
        assertFalse(state.dirty, "back at the state as opened")
        state.redo()
        assertEquals(3, state.rules!!.build.tomes[Stat.STR.ordinal])
        assertTrue(state.dirty)
    }

    @Test
    fun typingTheNameIsOneStep() {
        val state = session()
        val name = state.rules!!.build.buildName
        state.overview.setName("K")
        state.overview.setName("Ke")
        state.overview.setName("Kel")
        state.undo()
        assertEquals(name, state.rules!!.build.buildName)
        assertFalse(state.history.canUndo)
    }

    @Test
    fun aNewChangeAfterUndoDropsRedo() {
        val state = session()
        state.stats.setTome(Stat.STR, 3)
        state.undo()
        state.stats.setTome(Stat.DEX, 2)
        assertFalse(state.history.canRedo)
        assertEquals(0, state.rules!!.build.tomes[Stat.STR.ordinal])
    }
}
