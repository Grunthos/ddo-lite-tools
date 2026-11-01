package ddolite.dataformat

// Ports of the shared text helpers in Source/Modules/basUtils.bas, used by every VB6 loader.
// Text is assumed to be newline-normalised (see decodeText), so VB's vbNewLine is "\n" here.

/**
 * Ported from `basUtils.TrimAll` (basUtils.bas:27).
 *
 * Strips spaces with `Trim$`, *then* removes every tab, including tabs inside the text.
 * The order matters: "\t a" becomes " a" (the leading space survives because the
 * tab in front of it hid it from `Trim$`). The Builder's `ParseLine` relies on this.
 */
fun trimAll(s: String): String = Vb.trim(s).replace("\t", "")

/**
 * Ported from `basUtils.CleanText` (basUtils.bas:17); Cannith has an identical copy
 * (Cannith/basData.bas:1911).
 *
 * Strips spaces and any blank lines from both ends of a record before the loaders
 * split it into lines.
 */
fun cleanText(s: String): String {
    var t = Vb.trim(s)
    while (t.startsWith("\n")) t = Vb.trim(t.substring(1))
    while (t.endsWith("\n")) t = Vb.trim(t.substring(0, t.length - 1))
    return t
}

/**
 * Ported from the end of `clsFile.LoadToString` (Classes/clsFile.cls:374), which every
 * loader uses to read a file: the contents without trailing line breaks.
 */
fun stripTrailingNewlines(s: String): String = s.trimEnd('\n')
