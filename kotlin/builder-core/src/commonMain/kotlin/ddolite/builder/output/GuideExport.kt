package ddolite.builder.output

import ddolite.builder.build.BuildRules
import ddolite.builder.build.GuideStepStyle

/**
 * The leveling guide as a csv or txt file (File › Export › Leveling Guide, and the guide tab's
 * Export link). Ported from basDocuments.bas: ExportGuideCSV (:395) and ExportGuideTXT (:437).
 * Lines are joined with CRLF, with no line break after the last.
 */
object GuideExport {
    /** The file name VB suggests: the build file's name, else the build's name, else "Leveling Guide". ExportGuide (:363). */
    fun suggestedName(buildFileName: String?, buildName: String, extension: String): String {
        if (!buildFileName.isNullOrEmpty()) {
            return "$buildFileName.$extension"
        }
        if (buildName.isNotEmpty()) {
            return "$buildName.$extension"
        }
        return "Leveling Guide.$extension"
    }

    /**
     * Level, Tree, Tier, Enhancement, AP, Prog, Order: one line per step, and an empty line (with
     * its order number) before each new level. ExportGuideCSV (:395).
     */
    fun csv(rules: BuildRules): String {
        val lines = ArrayList<String>()
        lines.add(quotes("Level") + "," + quotes("Tree") + "," + quotes("Tier") + "," + quotes("Enhancement") + "," + quotes("AP") + "," + quotes("Prog") + "," + quotes("Order"))
        var level = 1
        var index = 0
        for (e in rules.guideEnhancements) {
            if (e.style == GuideStepStyle.MISSING) {
                continue // a step the data hasn't (Kotlin only) isn't exported
            }
            index++
            if (e.level > level) {
                lines.add(",,,,,,$index")
                level = e.level
                index++
            }
            var line = e.level.toString() + ","
            if (e.style == GuideStepStyle.ENHANCEMENT) {
                line = line + quotes(rules.guideTrees[e.guideTree - 1].display) + ","
                line += if (e.tier == 0) quotes("Core " + (e.ability + 1)) + "," else quotes("Tier " + e.tier) + ","
                line = line + quotes(e.display + e.rankText) + "," + e.cost + "," + e.spentInTree + ","
            } else {
                line = line + ",," + quotes(e.display) + ",,,"
            }
            lines.add(line + index)
        }
        return lines.joinToString("\r\n")
    }

    private fun quotes(text: String): String = "\"" + text + "\""

    /**
     * Columns padded to the longest tree and enhancement names, a dashed line under the headings,
     * and a blank line before each new level. ExportGuideTXT (:437) with InitCol (:505).
     *
     * Not copied: VB centres the headings with `AlignText(…, vbCenter)`, which stops with "Invalid
     * procedure call" when a heading is no more than one character narrower than its column (a guide
     * using only trees with names of 5 letters or fewer, say); here the heading starts at the left.
     */
    fun txt(rules: BuildRules): String {
        val margin = "  "
        var treeWidth = "Tree".length
        var enhancementWidth = "Enhancement".length
        for (e in rules.guideEnhancements) {
            if (e.style == GuideStepStyle.MISSING) {
                continue
            }
            if (enhancementWidth < (e.display + e.rankText).length) {
                enhancementWidth = (e.display + e.rankText).length
            }
            if (e.style == GuideStepStyle.ENHANCEMENT) {
                val display = rules.guideTrees[e.guideTree - 1].display
                if (treeWidth < display.length) {
                    treeWidth = display.length
                }
            }
        }
        val lines = ArrayList<String>()
        lines.add(listOf("Level", center("Tree", treeWidth), " Tier ", center("Enhancement", enhancementWidth), "AP", "Prog").joinToString(margin))
        lines.add(blankColumns('-', treeWidth, enhancementWidth).joinToString(margin))
        var level = 1
        for (e in rules.guideEnhancements) {
            if (e.style == GuideStepStyle.MISSING) {
                continue
            }
            val columns = blankColumns(' ', treeWidth, enhancementWidth)
            if (e.level > level) {
                lines.add(columns.joinToString(margin))
                level = e.level
            }
            columns[0] = right(e.level.toString(), 4) + " "
            if (e.style == GuideStepStyle.ENHANCEMENT) {
                columns[1] = left(rules.guideTrees[e.guideTree - 1].display, treeWidth)
                columns[2] = if (e.tier == 0) "Core " + (e.ability + 1) else "Tier " + e.tier
                columns[3] = left(e.display + e.rankText, enhancementWidth)
                columns[4] = " " + e.cost
                columns[5] = right("  " + e.spentInTree, 3) + " "
            } else {
                columns[3] = left(e.display, enhancementWidth)
            }
            lines.add(columns.joinToString(margin))
        }
        return lines.joinToString("\r\n")
    }

    /** InitCol (:505): each column filled with [c]. */
    private fun blankColumns(c: Char, treeWidth: Int, enhancementWidth: Int): Array<String> = arrayOf(
        c.toString().repeat(5),
        c.toString().repeat(treeWidth),
        c.toString().repeat(6),
        c.toString().repeat(enhancementWidth),
        c.toString().repeat(2),
        c.toString().repeat(4),
    )

    /** AlignText's left alignment: padded, or cut to [width]. */
    private fun left(text: String, width: Int): String = if (text.length > width) text.substring(0, width) else text + " ".repeat(width - text.length)

    /** AlignText's right alignment: padded on the left, or the last [width] characters. */
    private fun right(text: String, width: Int): String = if (text.length > width) text.substring(text.length - width) else " ".repeat(width - text.length) + text

    /** AlignText's centring: VB writes the text from position (gap \ 2), 1-based. */
    private fun center(text: String, width: Int): String {
        if (text.length > width) {
            return text.substring(0, width)
        }
        val start = maxOf((width - text.length) / 2 - 1, 0)
        return " ".repeat(start) + text + " ".repeat(width - start - text.length)
    }
}
