package ddolite.builder.ui

import ddolite.builder.build.BuildPoints
import ddolite.builder.build.readBuildFile
import ddolite.builder.data.LoadLog
import ddolite.builder.data.loadBuilderData
import ddolite.common.theme.ThemeFileKind
import ddolite.common.theme.loadTheme
import ddolite.dataformat.DirectoryDataSource
import ddolite.dataformat.Windows1252
import ddolite.dataformat.decodeText
import java.nio.file.Files
import java.nio.file.Path

/** A session for the screen tests: the real data and theme, prompts that answer yes, and a sample build open. */
object TestSession {
    private val dataDir = Path.of(System.getProperty("ddolite.dataDir"))
    private val source = DirectoryDataSource(dataDir)
    private val data = loadBuilderData(source, LoadLog())

    private object YesPrompts : BuilderPrompts {
        override fun confirm(message: String): Boolean = true
        override fun notice(message: String) {}
        override fun yesNoCancel(message: String, title: String): Boolean? = null
        override fun chooseLevel(min: Int, max: Int): Int? = null
    }

    /** A new session with [buildName] (a file in Save/) open, its text changed by [edit] first. */
    fun open(buildName: String, edit: (String) -> String = { it }): BuilderSession {
        val file = dataDir.resolveSibling("Settings").resolve("Black.screen")
        val theme = loadTheme(decodeText(Files.readAllBytes(file)).text, "Black", ThemeFileKind.SCREEN).theme
        val skills = SkillsScreenState(data, listOf(theme), prototypePresets())
        val session = BuilderSession(data, skills, TreeSlotsState(data, readTreeHeaders(source), skills), YesPrompts)
        val build = Path.of(System.getProperty("ddolite.saveDir")).resolve(buildName)
        val text = edit(Windows1252.decode(Files.readAllBytes(build)))
        session.openBuild(readBuildFile(Windows1252.encode(text), data, BuildPoints.ADVENTURER))
        return session
    }
}
