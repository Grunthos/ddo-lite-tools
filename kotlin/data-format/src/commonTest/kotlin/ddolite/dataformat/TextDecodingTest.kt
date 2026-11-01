package ddolite.dataformat

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class TextDecodingTest {
    private fun bytes(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }

    @Test
    fun windows1252HighCharacters() {
        assertEquals("€‚ƒ…‘’“”–—™", Windows1252.decode(bytes(0x80, 0x82, 0x83, 0x85, 0x91, 0x92, 0x93, 0x94, 0x96, 0x97, 0x99)))
        assertEquals("Aé ÿ", Windows1252.decode(bytes(0x41, 0xE9, 0xA0, 0xFF)))
        assertEquals("\u0081\u008D", Windows1252.decode(bytes(0x81, 0x8D)))
    }

    @Test
    fun windows1252RoundTrip() {
        val all = ByteArray(256) { it.toByte() }
        assertContentEquals(all, Windows1252.encode(Windows1252.decode(all)))
        assertContentEquals(bytes('?'.code), Windows1252.encode("中"))
    }

    @Test
    fun plainFileIsWindows1252WithNormalisedNewlines() {
        val d = decodeText(bytes('a'.code, 0x0D, 0x0A, 0xE9, 0x0D, 0x0A))
        assertEquals("a\né\n", d.text)
        assertEquals(SourceEncoding.WINDOWS_1252, d.encoding)
        assertEquals(LineEndings.CRLF, d.lineEndings)
    }

    @Test
    fun bomSelectsUtf8AndIsDropped() {
        val d = decodeText(bytes(0xEF, 0xBB, 0xBF) + "Spell: é\n".encodeToByteArray())
        assertEquals("Spell: é\n", d.text)
        assertEquals(SourceEncoding.UTF8_BOM, d.encoding)
        assertEquals(LineEndings.LF, d.lineEndings)
    }

    @Test
    fun lineEndingDetection() {
        assertEquals(LineEndings.NONE, detectLineEndings("abc"))
        assertEquals(LineEndings.MIXED, detectLineEndings("a\r\nb\nc"))
        assertEquals("a\nb\r", decodeText("a\r\nb\r".encodeToByteArray()).text)
    }
}
