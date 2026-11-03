package ddolite.builder.output

import ddolite.dataformat.Vb

/** An open and close tag that can be switched off. Ported from `TagsType` (clsConfig.cls). */
data class Tags(val enabled: Boolean, val open: String, val close: String)

/** List tags: bulleted and numbered. Ported from `ListTagsType` (clsConfig.cls). */
data class ListTags(
    val enabled: Boolean,
    val bulletOpen: String,
    val bulletClose: String,
    val numberedOpen: String,
    val numberedClose: String,
)

/**
 * How exported output is formatted: BBCodes or plain text, dots for spaces, colours, and the tags
 * for each style. Ported from `OutputType` (clsConfig.cls) with `InitFormat` (:847),
 * `LoadFormat` (:889) and `ParseOutputData` (:812).
 *
 * The screen uses [textColors] and [color] too: coloured text on screen, and bold for the
 * preferred stat column only when colours aren't shown (`StatColumnBold`).
 *
 * @property codes write tags at all ("Plain Text" doesn't).
 * @property dots fill runs of spaces with dots, for forums that collapse spaces.
 * @property reddit Reddit tables instead of the usual layout (export only).
 */
data class OutputFormat(
    val codes: Boolean,
    val dots: Boolean,
    val textColors: Boolean,
    val reddit: Boolean,
    val bold: Tags,
    val underline: Tags,
    val fixed: Tags,
    val lists: ListTags,
    val color: Tags,
    val wrapper: Tags,
) {
    companion object {
        /** The formats every install has, in VB's order (`GetFormatNames`, clsConfig.cls:2583). */
        val BUILT_IN = listOf("DDO Forums", "Code Tags", "Plain Text")

        /**
         * The built-in settings for a format name, before any .bbcodes file is read. Ported from
         * `InitFormat` (clsConfig.cls:847): "Default" (and any other name) is the forum format.
         */
        fun builtIn(name: String): OutputFormat {
            var format = OutputFormat(
                codes = true,
                dots = true,
                textColors = true,
                reddit = false,
                bold = Tags(true, "[b]", "[/b]"),
                underline = Tags(true, "[u]", "[/u]"),
                fixed = Tags(true, "[font=courier]", "[/font]"),
                lists = ListTags(true, "[list]", "[/list]", "[list=1]", "[/list]"),
                color = Tags(true, "[color=#\$]", "[/color]"),
                wrapper = Tags(false, "[code]", "[/code]"),
            )
            if (name == "Code Tags") {
                format = format.copy(dots = false, fixed = format.fixed.copy(enabled = false), wrapper = format.wrapper.copy(enabled = true))
            } else if (name == "Plain Text") {
                format = format.copy(
                    codes = false,
                    dots = false,
                    textColors = false,
                    bold = format.bold.copy(enabled = false),
                    underline = format.underline.copy(enabled = false),
                    fixed = format.fixed.copy(enabled = false),
                    lists = format.lists.copy(enabled = false),
                    color = format.color.copy(enabled = false),
                    wrapper = format.wrapper.copy(enabled = false),
                )
            } else if (name == "Reddit") {
                format = format.copy(
                    reddit = true,
                    dots = false,
                    bold = Tags(false, "", ""),
                    underline = Tags(false, "", ""),
                    fixed = Tags(false, "", ""),
                    lists = ListTags(false, "", "", "", ""),
                    color = Tags(false, "", ""),
                    wrapper = Tags(false, "", ""),
                )
            }
            return format
        }

        /** VB's default (`cfg.BBCodes = "Default"`): the forum format. */
        val DEFAULT: OutputFormat = builtIn("Default")

        /**
         * A format from its name and, if there is one, its .bbcodes file's text: the built-in
         * settings with the file's lines over them. Ported from `InitFormat` with `LoadFormat`.
         */
        fun load(name: String, fileText: String?): OutputFormat {
            val format = builtIn(name)
            if (fileText == null) {
                return format
            }
            return parse(fileText, format)
        }

        /**
         * Reads "Field=value" lines (tags are tab-separated) over [start]. Ported from
         * `ParseOutputData` (clsConfig.cls:816) with `ParseLine` (:1105), `ParseTags` and
         * `ParseListTags`: a tag line without exactly three (or five) parts is ignored. As VB's
         * Trim$, only spaces are trimmed, so tabs at the ends of a tag line stay.
         *
         * Not copied: VB splits only at CRLF; LF line ends (as git checks the file out off Windows)
         * are read too.
         */
        fun parse(text: String, start: OutputFormat): OutputFormat {
            var format = start
            for (rawLine in text.split("\r\n", "\n")) {
                val equals = rawLine.indexOf('=')
                if (equals < 0) {
                    continue
                }
                val field = Vb.trim(rawLine.substring(0, equals)).lowercase()
                val value = Vb.trim(rawLine.substring(equals + 1))
                when (field) {
                    "codes" -> format = format.copy(codes = value.lowercase() == "true")
                    "dots" -> format = format.copy(dots = value.lowercase() == "true")
                    "textcolors" -> format = format.copy(textColors = value.lowercase() == "true")
                    "reddit" -> format = format.copy(reddit = value.lowercase() == "true")
                    "bold" -> format = format.copy(bold = parseTags(value, format.bold))
                    "underline" -> format = format.copy(underline = parseTags(value, format.underline))
                    "fixed" -> format = format.copy(fixed = parseTags(value, format.fixed))
                    "color" -> format = format.copy(color = parseTags(value, format.color))
                    "wrapper" -> format = format.copy(wrapper = parseTags(value, format.wrapper))
                    "lists" -> format = format.copy(lists = parseListTags(value, format.lists))
                }
            }
            return format
        }

        private fun parseTags(value: String, old: Tags): Tags {
            val parts = value.split('\t')
            if (parts.size != 3) {
                return old
            }
            return Tags(parts[0].lowercase() == "true", parts[1], parts[2])
        }

        private fun parseListTags(value: String, old: ListTags): ListTags {
            val parts = value.split('\t')
            if (parts.size != 5) {
                return old
            }
            return ListTags(parts[0].lowercase() == "true", parts[1], parts[2], parts[3], parts[4])
        }
    }
}
