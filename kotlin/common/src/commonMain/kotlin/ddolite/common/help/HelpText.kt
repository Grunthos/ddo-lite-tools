package ddolite.common.help

/**
 * One word of help text, with its style or link. Ported from `WordType` (basHelp.bas:8).
 *
 * @property code a link, "url=…", "run=…", "hlp=…" or "frm=…"; empty for plain text.
 * @property error shown in the error colour ({error}).
 */
class HelpWord(
    val text: String,
    val code: String,
    val bold: Boolean,
    val italics: Boolean,
    val underline: Boolean,
    val error: Boolean,
)

/** One line of a help topic: its words, and its indent (1 for " - ", 2 for "   - "). `ParagraphType` (:16). */
class HelpParagraph(val words: List<HelpWord>, val indent: Int)

/** A help topic: its code ("Stats", "Leveling_Guide") and lines. `HelpItemType` (:23). */
class HelpTopic(val code: String, val paragraphs: List<HelpParagraph>) {
    /**
     * The window's title: the code with spaces, plus " Help" except for a few topics.
     * DrawText (frmHelp.frm), end.
     */
    fun caption(): String {
        val caption = code.replace("_", " ")
        if (caption == "What's New?" || caption == "Frequently Asked Questions" || caption == "Getting Started" || caption == "Table of Contents") {
            return caption
        }
        if (caption.startsWith("Save File Specification")) {
            return caption
        }
        return "$caption Help"
    }
}

/**
 * The help file (Data/Builder/Help.txt). Ported from basHelp.bas: InitHelp (:39), ProcessParagraph
 * (:68) and ProcessWord (:87).
 *
 * The file is split at "HelpTopic: "; the first two pieces (the header and the example in its
 * comments) are skipped, as VB starts at the third. Lines may end in CRLF (as VB needs)
 * or LF. Each line is a paragraph and each space-separated
 * piece a word; a word starting "{code}" takes that style or link, and its underscores become spaces.
 */
class HelpText(val topics: List<HelpTopic>) {
    /** The topic with this code (case matters, as VB's), or null: VB says "Help Code '…' not found." */
    fun find(code: String): HelpTopic? {
        for (t in topics) {
            if (t.code == code) {
                return t
            }
        }
        return null
    }

    companion object {
        fun parse(text: String): HelpText {
            val pieces = text.split("HelpTopic: ")
            val topics = ArrayList<HelpTopic>()
            for (i in 2 until pieces.size) {
                val piece = pieces[i]
                val end = piece.indexOf('\n')
                if (end < 0) {
                    continue
                }
                val code = piece.substring(0, end).removeSuffix("\r")
                val paragraphs = ArrayList<HelpParagraph>()
                for (line in piece.substring(end + 1).split("\r\n", "\n")) {
                    paragraphs.add(paragraph(line))
                }
                topics.add(HelpTopic(code, paragraphs))
            }
            return HelpText(topics)
        }

        private fun paragraph(raw: String): HelpParagraph {
            var indent = 0
            if (raw.startsWith(" - ")) {
                indent = 1
            }
            if (raw.startsWith("   - ")) {
                indent = 2
            }
            val words = ArrayList<HelpWord>()
            for (w in raw.split(" ")) {
                words.add(word(w))
            }
            return HelpParagraph(words, indent)
        }

        /** ProcessWord: anything before the "{" is dropped, as VB keeps only the text after "}". */
        private fun word(raw: String): HelpWord {
            val open = raw.indexOf('{')
            if (open < 0) {
                return HelpWord(raw, "", false, false, false, false)
            }
            val close = raw.indexOf('}', open)
            if (close < 0) {
                return HelpWord(raw, "", false, false, false, false)
            }
            var code = raw.substring(open + 1, close)
            var text = raw.substring(close + 1)
            if (text.contains("_")) {
                text = text.replace("_", " ")
            }
            if (code.contains("=")) {
                return HelpWord(text, code, false, false, false, false)
            }
            code = code.lowercase()
            if (code == "error") {
                return HelpWord(text, "", false, false, false, true)
            }
            return HelpWord(text, "", code.contains("b"), code.contains("i"), code.contains("u"), false)
        }
    }
}
