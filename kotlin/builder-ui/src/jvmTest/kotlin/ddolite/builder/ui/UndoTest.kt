package ddolite.builder.ui

import ddolite.builder.build.BuildPoints
import ddolite.builder.build.readBuildFile
import ddolite.builder.build.writeBuildFile
import ddolite.builder.data.LoadLog
import ddolite.builder.data.loadBuilderData
import ddolite.builder.model.Stat
import ddolite.common.theme.ThemeFileKind
import ddolite.common.theme.loadTheme
import ddolite.dataformat.DirectoryDataSource
import ddolite.dataformat.decodeText
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Edit › Undo and Redo through the session, as the windows use them. */
class UndoTest {
    private val dataDir = Path.of(System.getProperty("ddolite.dataDir"))
    private val source = DirectoryDataSource(dataDir)
    private val data = loadBuilderData(source, LoadLog())

    private object NoPrompts : BuilderPrompts {
        override fun confirm(message: String): Boolean = true
        override fun notice(message: String) {}
        override fun yesNoCancel(message: String, title: String): Boolean? = null
        override fun chooseLevel(min: Int, max: Int): Int? = null
    }

    private fun session(): BuilderSession {
        val file = dataDir.resolveSibling("Settings").resolve("Black.screen")
        val theme = loadTheme(decodeText(Files.readAllBytes(file)).text, "Black", ThemeFileKind.SCREEN).theme
        val skills = SkillsScreenState(data, listOf(theme), prototypePresets())
        val state = BuilderSession(data, skills, TreeSlotsState(data, readTreeHeaders(source), skills), NoPrompts)
        val build = Path.of(System.getProperty("ddolite.saveDir")).resolve("Kensei Warpriest.build")
        state.openBuild(readBuildFile(Files.readAllBytes(build), data, BuildPoints.ADVENTURER))
        return state
    }

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
