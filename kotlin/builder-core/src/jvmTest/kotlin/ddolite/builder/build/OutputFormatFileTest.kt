package ddolite.builder.build

import ddolite.builder.output.OutputFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A .bbcodes file as the format editor saves it (SaveFormat), read back as LoadFormat reads it. */
class OutputFormatFileTest {
    @Test
    fun fileTextRoundTrips() {
        for (name in listOf("Default", "Code Tags", "Plain Text", "Reddit")) {
            val format = OutputFormat.builtIn(name)
            val text = format.fileText()
            assertTrue(text.startsWith("Codes="))
            assertTrue(text.endsWith("\r\n\r\n\r\n"))
            assertEquals(format, OutputFormat.load("Mine", text), name)
        }
        val edited = OutputFormat.DEFAULT.copy(bold = OutputFormat.DEFAULT.bold.copy(open = "<b>", close = "</b>"))
        assertEquals(edited, OutputFormat.load("Mine", edited.fileText()))
    }
}
