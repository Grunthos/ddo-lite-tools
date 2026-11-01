package ddolite.tools.validate

import ddolite.builder.data.BuilderRecordFile
import ddolite.dataformat.MapDataSource
import kotlin.test.Test
import kotlin.test.assertEquals

/** Checks that the validator reports each kind of problem, using small made-up data sets. */
class ValidateTest {
    /** Every Builder record file present (with CRLF endings), with Feats.txt replaced by [feats]. */
    private fun dataWithFeats(feats: String): MapDataSource {
        val files = HashMap<String, ByteArray>()
        for (file in BuilderRecordFile.entries) {
            files[file.path] = "${file.marker}Example\r\n".encodeToByteArray()
        }
        files[BuilderRecordFile.FEATS.path] = feats.replace("\n", "\r\n").encodeToByteArray()
        return MapDataSource(files)
    }

    private fun messages(report: Report): List<String> {
        val result = ArrayList<String>()
        for (issue in report.issues) {
            result.add("${issue.severity} ${issue.line} ${issue.message}")
        }
        return result
    }

    @Test
    fun cleanDataHasNoIssues() {
        val report = validate(dataWithFeats("FeatName: Toughness\nGroup: Heroic\nDescrip: +3 HP\n"))
        assertEquals(emptyList(), messages(report))
    }

    @Test
    fun lineWithoutColonIsAnError() {
        val report = validate(dataWithFeats("FeatName: Toughness\nGroup: Heroic\nthis line has no colon\n"))
        assertEquals(listOf("ERROR 3 no ':' in \"this line has no colon\""), messages(report))
    }

    @Test
    fun markerInsideALineIsAWarning() {
        val report = validate(dataWithFeats("FeatName: A\nDescrip: see FeatName: B\n"))
        assertEquals(1, report.warningCount())
        assertEquals(2, report.issues[0].line)
    }

    @Test
    fun lfLineEndingsAreAWarning() {
        val files = HashMap<String, ByteArray>()
        for (file in BuilderRecordFile.entries) {
            files[file.path] = "${file.marker}Example\r\nKey: value\r\n".encodeToByteArray()
        }
        files[BuilderRecordFile.RACES.path] = "RaceName: Human\nType: Free\n".encodeToByteArray()
        val report = validate(MapDataSource(files))
        assertEquals(1, report.warningCount())
        assertEquals(BuilderRecordFile.RACES.path, report.issues[0].path)
    }

    @Test
    fun missingFileIsAnError() {
        val report = validate(MapDataSource(HashMap()))
        assertEquals(BuilderRecordFile.entries.size, report.errorCount())
    }
}
