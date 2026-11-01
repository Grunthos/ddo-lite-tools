package ddolite.dataformat

// Turning file bytes into text. Replaces the reading half of clsFile.LoadToString
// (Classes/clsFile.cls:374), which reads a file into a VB String as ANSI.

/** How a file was encoded on disk. */
enum class SourceEncoding { WINDOWS_1252, UTF8_BOM }

/**
 * The line-break style a file used on disk. VB6 only splits on CRLF, so LF or MIXED files
 * would be misread by the VB6 apps. The validator reports them.
 */
enum class LineEndings { NONE, CRLF, LF, MIXED }

/**
 * A data file decoded to text.
 *
 * [text] always uses "\n" line breaks: VB6 splits on CRLF, but git checkouts outside Windows
 * (CI, mobile build machines) give LF, so the parsers work on normalised text. The original
 * style is kept in [lineEndings] so the validator can flag files VB6 would misread.
 */
class DecodedText(
    val text: String,
    val encoding: SourceEncoding,
    val lineEndings: LineEndings,
)

private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

/**
 * Decodes a data or user file. A UTF-8 byte-order mark selects UTF-8 (and is dropped);
 * anything else is read as Windows-1252, as VB6 does.
 *
 * VB6 reads a BOM file as ANSI too, leaving "ï»¿" glued to the first line. Every current
 * caller ignores the text before the first record marker, so dropping it gives the same result.
 */
fun decodeText(bytes: ByteArray): DecodedText {
    val hasBom = bytes.size >= 3 &&
        bytes[0] == UTF8_BOM[0] && bytes[1] == UTF8_BOM[1] && bytes[2] == UTF8_BOM[2]
    val raw = if (hasBom) bytes.decodeToString(3, bytes.size) else Windows1252.decode(bytes)
    return DecodedText(
        text = raw.replace("\r\n", "\n"),
        encoding = if (hasBom) SourceEncoding.UTF8_BOM else SourceEncoding.WINDOWS_1252,
        lineEndings = detectLineEndings(raw),
    )
}

/** Counts CRLF and bare-LF line breaks to classify the file. No VB equivalent. */
internal fun detectLineEndings(raw: String): LineEndings {
    var crlf = 0
    var lf = 0
    for (i in raw.indices) {
        if (raw[i] == '\n') {
            if (i > 0 && raw[i - 1] == '\r') crlf++ else lf++
        }
    }
    return when {
        crlf == 0 && lf == 0 -> LineEndings.NONE
        lf == 0 -> LineEndings.CRLF
        crlf == 0 -> LineEndings.LF
        else -> LineEndings.MIXED
    }
}
