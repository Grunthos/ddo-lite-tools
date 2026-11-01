package ddolite.dataformat

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/** Checks against the real repository Data folder (passed in by Gradle as ddolite.dataDir). */
class DataFolderTest {
    private val source = DirectoryDataSource(Path.of(System.getProperty("ddolite.dataDir")))

    @Test
    fun dataFolderExists() {
        assertTrue(Files.isDirectory(source.root),"Data folder not found at ${source.root}")
    }

    /** Every file in Data/ (top level and the three app folders) decodes, with newlines normalised. */
    @Test
    fun everyDataFileDecodes() {
        val files = ArrayList<String>()
        for (folder in listOf("", "Builder", "Cannith", "Compendium")) {
            files.addAll(source.list(folder))
        }
        assertTrue(files.size >= 40, "expected the ~44 data files, found ${files.size}")
        for (path in files) {
            val text = source.readText(path)!!.text
            assertTrue("\r\n" !in text, "$path still contains CRLF after decoding")
        }
    }

    /**
     * Spells.txt is the one data file saved with a UTF-8 BOM. VB6 survives it only because
     * the BOM lands in the ignored text before the first "SpellName: ", and we must too.
     */
    @Test
    fun spellsFileHasBomAndStillSplits() {
        val spells = source.readText("Builder/Spells.txt")!!
        assertTrue(spells.encoding == SourceEncoding.UTF8_BOM)
        val split = splitRecords(spells.text, "SpellName: ")
        assertTrue(split.preamble.isEmpty())
        assertTrue(split.records.size > 500)
    }
}
