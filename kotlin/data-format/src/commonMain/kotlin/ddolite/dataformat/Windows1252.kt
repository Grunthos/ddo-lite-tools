package ddolite.dataformat

/**
 * Windows-1252 ("ANSI") codec. VB6 reads and writes text files in the system ANSI code
 * page, which for the existing user base is Windows-1252. Kotlin Multiplatform has no
 * built-in charset for it, so the 32 characters that differ from ISO-8859-1 are mapped here.
 *
 * Bytes undefined in Windows-1252 (0x81, 0x8D, 0x8F, 0x90, 0x9D) map to the matching C1
 * control character, as Windows' own conversion does.
 */
object Windows1252 {
    /** The characters for bytes 0x80 to 0x9F. Every other byte maps to the same code point. */
    private val HIGH = charArrayOf(
        '€', '\u0081', '‚', 'ƒ', '„', '…', '†', '‡',
        'ˆ', '‰', 'Š', '‹', 'Œ', '\u008D', 'Ž', '\u008F',
        '\u0090', '‘', '’', '“', '”', '•', '–', '—',
        '˜', '™', 'š', '›', 'œ', '\u009D', 'ž', 'Ÿ',
    )

    /** The reverse of [HIGH]: character to byte, for encoding. */
    private val REVERSE: Map<Char, Byte> = buildReverseMap()

    private fun buildReverseMap(): Map<Char, Byte> {
        val map = HashMap<Char, Byte>()
        for (i in HIGH.indices) {
            map[HIGH[i]] = (0x80 + i).toByte()
        }
        return map
    }

    /** Decodes bytes [from]..[to] as Windows-1252, as VB6 does when it reads a file into a String. */
    fun decode(bytes: ByteArray, from: Int = 0, to: Int = bytes.size): String {
        val chars = CharArray(to - from)
        for (i in from until to) {
            val b = bytes[i].toInt() and 0xFF
            if (b in 0x80..0x9F) {
                chars[i - from] = HIGH[b - 0x80]
            } else {
                chars[i - from] = b.toChar()
            }
        }
        return chars.concatToString()
    }

    /** Encodes text as Windows-1252. Characters it can't represent become '?', as VB6 does. */
    fun encode(text: String): ByteArray {
        val out = ByteArray(text.length)
        for (i in text.indices) {
            val c = text[i]
            if (c.code < 0x80 || c.code in 0xA0..0xFF) {
                out[i] = c.code.toByte()
            } else {
                val b = REVERSE[c]
                out[i] = if (b != null) b else '?'.code.toByte()
            }
        }
        return out
    }
}
