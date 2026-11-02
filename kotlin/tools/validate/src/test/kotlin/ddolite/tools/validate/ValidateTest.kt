package ddolite.tools.validate

import ddolite.builder.data.BuilderRecordFile
import ddolite.builder.data.FEAT_MAP_PATH
import ddolite.dataformat.MapDataSource
import kotlin.test.Test
import kotlin.test.assertEquals

/** Checks that the validator reports each kind of problem, using small made-up data sets. */
class ValidateTest {
    /**
     * Every Builder record file present, with CRLF endings: a valid race and class, a placeholder
     * record in the files that aren't loaded yet, and [feats] as Feats.txt.
     */
    private fun minimalData(): HashMap<String, ByteArray> {
        val files = HashMap<String, ByteArray>()
        for (file in BuilderRecordFile.entries) {
            files[file.path] = "${file.marker}Example\r\n".encodeToByteArray()
        }
        files[BuilderRecordFile.RACES.path] = "RaceName: Human\r\nType: Free\r\nStats: 8, 8, 8, 8, 8, 8\r\n".encodeToByteArray()
        files[BuilderRecordFile.CLASSES.path] = "ClassName: Fighter\r\nBAB: 1\r\nSkillPoints: 2\r\n".encodeToByteArray()
        files[BuilderRecordFile.FEATS.path] = "FeatName: Example\r\nGroup: Misc\r\n".encodeToByteArray()
        files[BuilderRecordFile.AUTO_GRANTED_FEATS.path] = "GrantedFeats1: Example\r\n".encodeToByteArray()
        files[FEAT_MAP_PATH] = "Lite\tRon\tBuilder\r\nExample\tExample\tExample".encodeToByteArray()
        // A template must name a real class and add up to 28-36 points.
        files[BuilderRecordFile.TEMPLATES.path] =
            "Class: Fighter\r\nCaption: Example\r\nSTR: 16 16 16 16 16\r\nDEX:  6  8  8 10 10\r\nCON:  6  6  8  8 10\r\n".encodeToByteArray()
        return files
    }

    private fun dataWithFeats(feats: String): MapDataSource {
        val files = minimalData()
        // Keep the "Example" feat that the minimal FeatMap.txt and AutoGrantedFeats.txt refer to.
        val text = feats + "\n\nFeatName: Example\nGroup: Misc\n"
        files[BuilderRecordFile.FEATS.path] = text.replace("\n", "\r\n").encodeToByteArray()
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
        val files = minimalData()
        files[BuilderRecordFile.RACES.path] = "RaceName: Human\nType: Free\nStats: 8, 8, 8, 8, 8, 8\n".encodeToByteArray()
        val report = validate(MapDataSource(files))
        assertEquals(listOf("WARNING null line endings are LF; VB6 expects CRLF and will misread this file"), messages(report))
        assertEquals(BuilderRecordFile.RACES.path, report.issues[0].path)
    }

    @Test
    fun loaderErrorsAreReported() {
        val files = minimalData()
        files[BuilderRecordFile.CLASSES.path] = "ClassName: Fighter\r\nBAB: 2\r\nSkillPoints: 2\r\n".encodeToByteArray()
        val report = validate(MapDataSource(files))
        assertEquals(listOf("ERROR 2 Classes.txt: Fighter has invalid BAB: 2"), messages(report))
    }

    @Test
    fun missingFileIsAnError() {
        val report = validate(MapDataSource(HashMap()))
        // Each Builder record file is reported missing once by the structure check; the
        // loaders report the ones ported so far (Races, Templates, Spells, Classes, Feats, AutoGrantedFeats)
        // again in VB's wording, plus FeatMap.txt, which the structure check doesn't cover.
        assertEquals(BuilderRecordFile.entries.size + 7, report.errorCount())
    }
}
