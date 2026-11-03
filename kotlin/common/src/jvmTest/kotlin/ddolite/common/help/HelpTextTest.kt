package ddolite.common.help

import ddolite.dataformat.decodeText
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HelpTextTest {
    private val sample = "Header\r\nHelpTopic: Example\r\nskipped\r\nHelpTopic: First_Topic\r\n" +
        "Plain {b}bold {url=https://x.y}the_link {error}oops\r\n - indented {hlp=Second}Second\r\n" +
        "HelpTopic: Second\n   - deeper {iu}both\n"

    @Test
    fun skipsTheHeaderAndExample() {
        val help = HelpText.parse(sample)
        assertEquals(listOf("First_Topic", "Second"), help.topics.map { it.code })
        assertNull(help.find("Example"))
        assertNull(help.find("first_topic"), "case matters")
    }

    @Test
    fun wordsTakeTheirStyleOrLink() {
        val topic = assertNotNull(HelpText.parse(sample).find("First_Topic"))
        val words = topic.paragraphs[0].words
        assertEquals("Plain", words[0].text)
        assertTrue(words[1].bold)
        assertEquals("the link", words[2].text)
        assertEquals("url=https://x.y", words[2].code)
        assertTrue(words[3].error)
        assertEquals(1, topic.paragraphs[1].indent)
        assertEquals("hlp=Second", topic.paragraphs[1].words[3].code)
    }

    @Test
    fun lfLinesAndDeeperIndent() {
        val topic = assertNotNull(HelpText.parse(sample).find("Second"))
        assertEquals(2, topic.paragraphs[0].indent)
        val both = topic.paragraphs[0].words.last()
        assertTrue(both.italics && both.underline && !both.bold)
    }

    @Test
    fun captions() {
        assertEquals("Leveling Guide Help", HelpTopic("Leveling_Guide", emptyList()).caption())
        assertEquals("What's New?", HelpTopic("What's New?", emptyList()).caption())
        assertEquals("Save File Specification: Stats", HelpTopic("Save_File_Specification:_Stats", emptyList()).caption())
    }

    /** The shipped Help.txt has every topic the windows and Help menu ask for, except VB's "Destinies" (VB-BUGS 22h). */
    @Test
    fun shippedHelpHasTheTopicsAskedFor() {
        val file = Path.of(System.getProperty("ddolite.dataDir")).resolve("Builder").resolve("Help.txt")
        val help = HelpText.parse(decodeText(Files.readAllBytes(file)).text)
        val asked = listOf(
            "Overview", "Stats", "Skills", "Feats", "Spells", "Enhancements", "Leveling_Guide", "Messages",
            "Options", "Export", "What's New?", "Frequently Asked Questions", "Save_File_Specifications",
        )
        for (code in asked) {
            assertNotNull(help.find(code), code)
        }
        assertNull(help.find("Destinies"))
    }
}
