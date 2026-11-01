package ddolite.dataformat

import kotlin.test.Test
import kotlin.test.assertEquals

class RecordsTest {
    private val sample = """
        ' comment before the first record
        FeatName: Acrobatic
        Group: Heroic, Misc

        FeatName: Toughness
        Descrip: +3 hit points

    """.trimIndent()

    @Test
    fun splitsOnMarker() {
        val split = splitRecords(sample, "FeatName: ")
        assertEquals("' comment before the first record\n", split.preamble)
        assertEquals(2, split.records.size)
        assertEquals(listOf("Acrobatic", "Group: Heroic, Misc"), split.records[0].lines())
        assertEquals(listOf("Toughness", "Descrip: +3 hit points"), split.records[1].lines())
        assertEquals(2, split.records[0].line)
        assertEquals(5, split.records[1].line)
    }

    @Test
    fun numberedLinesMatchFileLines() {
        val record = splitRecords(sample, "FeatName: ").records[1]
        assertEquals(NumberedLine(5, "Toughness"), record.numberedLines()[0])
        assertEquals(NumberedLine(6, "Descrip: +3 hit points"), record.numberedLines()[1])
    }

    @Test
    fun markerInsideALineAlsoSplitsLikeVb() {
        val text = "FeatName: A\nDescrip: see FeatName: B\n"
        val split = splitRecords(text, "FeatName: ")
        assertEquals(2, split.records.size)
        assertEquals("A\nDescrip: see ", split.records[0].body)
        assertEquals("B\n", split.records[1].body)
        assertEquals(listOf(2), markersNotAtLineStart(text, "FeatName: "))
    }

    @Test
    fun noMarkers() {
        val split = splitRecords("nothing here", "FeatName: ")
        assertEquals("nothing here", split.preamble)
        assertEquals(emptyList(), split.records)
    }

    /** The basUtils.bas ports. TrimAll keeps the space that the leading tab hid from Trim$. */
    @Test
    fun basUtilsHelpers() {
        assertEquals(" ab", trimAll("\t a\tb  "))
        assertEquals("a\nb", cleanText("  \n \na\nb\n \n "))
        assertEquals("x\ny", stripTrailingNewlines("x\ny\n\n"))
    }

    @Test
    fun mapDataSourceLists() {
        val src = MapDataSource.ofText("Builder/Feats.txt" to "x", "Builder/Races.txt" to "y", "Tomes.txt" to "z")
        assertEquals(listOf("Builder/Feats.txt", "Builder/Races.txt"), src.list("Builder"))
        assertEquals(listOf("Tomes.txt"), src.list(""))
        assertEquals("y", src.readText("Builder/Races.txt")?.text)
    }
}
