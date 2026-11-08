package ddolite.dataformat

import kotlin.math.round

/**
 * Re-implementations of VB6 *runtime* functions (built into the language, so there's no VB
 * source to point at) whose behaviour differs from the obvious Kotlin equivalent.
 *
 * Ported code calls these instead of the Kotlin standard library wherever the difference
 * could change a result, so the Kotlin apps behave the same as the VB6 ones. Each function
 * documents which VB quirk it reproduces.
 */
object Vb {
    /**
     * VB `Trim$`: strips leading and trailing **spaces only**. Kotlin's `trim()` also
     * strips tabs and newlines, which would change how data lines parse.
     */
    fun trim(s: String): String = s.trim(' ')

    /**
     * VB `Split(s, delimiter)`: an empty string gives an empty array (UBound = -1),
     * whereas Kotlin's `split` gives `[""]`. Otherwise the same, including empty parts.
     */
    fun split(s: String, delimiter: String): List<String> {
        if (s.isEmpty()) {
            return emptyList()
        }
        return s.split(delimiter)
    }

    private val DECIMAL = Regex("""[+-]?(\d[\d,]*\.?\d*|\.\d+)([eEdD][+-]?\d+)?""")
    private val HEX = Regex("""&[hH][0-9a-fA-F]+""")
    private val OCTAL = Regex("""&[oO]?[0-7]+""")

    /**
     * VB `IsNumeric` applied to a string, for the US-English locale.
     *
     * Quirks reproduced: leading/trailing spaces are allowed; thousands separators are
     * allowed ("1,000"); the exponent may be `E` **or `D`**, so "1d6" counts as numeric
     * (the Cannith `ParseLine` has a special case for exactly this); `&H` hex and `&O`
     * octal literals count.
     */
    fun isNumeric(s: String): Boolean {
        val t = s.trim(' ', '\t')
        return t.isNotEmpty() && (DECIMAL.matches(t) || HEX.matches(t) || OCTAL.matches(t))
    }

    private val VAL_DECIMAL = Regex("""^[+-]?(\d+\.?\d*|\.\d+)?([eEdD][+-]?\d+)?""")
    private val VAL_HEX = Regex("""^&[hH]([0-9a-fA-F]+)""")
    private val VAL_OCTAL = Regex("""^&[oO]?([0-7]+)""")

    /**
     * VB `Val`: reads the longest number at the start of the string and ignores the rest.
     *
     * Quirks reproduced: spaces, tabs and line breaks are ignored *anywhere* ("1 2" = 12);
     * thousands separators are not understood ("1,000" = 1); `D` works as an exponent
     * ("1d6" = 1000000); `&H`/`&O` literals that fit in 16 bits are signed ("&HFFFF" = -1).
     * Returns 0 when there's no number.
     */
    fun valOf(s: String): Double {
        val t = removeWhitespace(s)

        val hex = VAL_HEX.find(t)
        if (hex != null) {
            return radixValue(hex.groupValues[1], 16)
        }
        val octal = VAL_OCTAL.find(t)
        if (octal != null) {
            return radixValue(octal.groupValues[1], 8)
        }

        val match = VAL_DECIMAL.find(t)
        if (match == null || match.groupValues[1].isEmpty()) {
            return 0.0
        }
        // Kotlin only understands "e" as the exponent, so turn VB's "D" into "e".
        val exponent = match.groupValues[2]
        val mantissa = match.value.substring(0, match.value.length - exponent.length)
        return (mantissa + exponent.replace('d', 'e').replace('D', 'e')).toDouble()
    }

    /** The whitespace `Val` skips over: spaces, tabs and line breaks. */
    private fun removeWhitespace(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    /** `&H`/`&O` values: VB types them as Integer (16-bit) or Long (32-bit), both signed. */
    private fun radixValue(digits: String, radix: Int): Double {
        val v = digits.toLongOrNull(radix)
        if (v == null) {
            return 0.0
        }
        if (v <= 0xFFFF) {
            return v.toInt().toShort().toDouble() // Integer: 16-bit signed
        }
        if (v <= 0xFFFFFFFFL) {
            return v.toInt().toDouble() // Long: 32-bit signed
        }
        return v.toDouble()
    }

    /**
     * VB `CBool` applied to a string: "True"/"False" in any case, or a number (non-zero is true).
     * Returns null where VB raises run-time error 13 (Type mismatch), e.g. for "yes".
     */
    fun cBool(s: String): Boolean? {
        val t = s.trim(' ')
        if (t.equals("true", ignoreCase = true)) {
            return true
        }
        if (t.equals("false", ignoreCase = true)) {
            return false
        }
        if (isNumeric(t)) {
            return valOf(t) != 0.0
        }
        return null
    }

    /**
     * VB `CLng`, and VB's implicit conversion when a Double is assigned to a Long variable
     * (e.g. `plngValue = Val(strValue)` in the loaders).
     *
     * Rounds half to even ("banker's rounding"): 2.5 → 2, 3.5 → 4, 0.75 → 1. VB `Long`
     * is 32-bit, so this returns a Kotlin `Int`.
     *
     * @throws ArithmeticException where VB would raise run-time error 6 (Overflow).
     */
    fun cLng(d: Double): Int {
        val r = round(d) // kotlin.math.round also rounds ties to even
        if (r.isNaN() || r < Int.MIN_VALUE || r > Int.MAX_VALUE) {
            throw ArithmeticException("VB overflow converting $d to Long")
        }
        return r.toInt()
    }

    /**
     * The text VB's `xp.File.SaveStringAs` (clsFile.cls:549) puts in a file. It writes with
     * `Print #`, which adds a CRLF; text already ending in two CRLFs loses one first, so it ends
     * the same. Every VB save of builds, exports, settings, formats and the error log goes through it.
     */
    fun saveStringAs(text: String): String {
        if (text.endsWith("\r\n\r\n")) {
            return text
        }
        return text + "\r\n"
    }
}
