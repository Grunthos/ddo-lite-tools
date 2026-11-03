package ddolite.builder.settings

import ddolite.builder.build.BuildPoints
import ddolite.builder.output.FeatOrderOutput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Reading and writing the settings file ([BuilderSettings]) in VB's Settings.txt format. */
class BuilderSettingsTest {
    private val vbFile = listOf(
        "Section: Character Builder Lite",
        "",
        "WindowSize=0",
        "Maximized=False",
        "WindowCoords=1200\t900\t15000\t12000",
        "DialogCoords=3000\t1500\t0\t0",
        "MRUCount=3",
        "MRUList=C:\\Builds\\One.build\tC:\\Builds\\Two.build",
        "BuildPoints=2",
        "FeatOrderOutput=0",
        "FeatShowAll=True",
        "BBCodes=Plain Text",
        "Contrast=3",
        "",
        "",
        "Section: Compendium",
        "",
        "DataFile=Main",
        "",
        "",
        "Section: Output",
        "",
        "Codes=False",
        "Dots=False",
        "TextColors=True",
        "",
        "",
        "Section: Shared",
        "",
        "ScreenColors=Steam",
        "OutputColors=Default",
        "",
        "",
        "Section: Colors",
        "",
        "WrkBackground=1,2,3",
        "",
    ).joinToString("\r\n")

    @Test
    fun readsVbSettings() {
        val s = BuilderSettings.parse(vbFile)
        assertFalse(s.maximized)
        assertEquals(Coords(1200, 900, 15000, 12000), s.windowCoords)
        assertEquals(Coords(3000, 1500, 0, 0), s.dialogCoords)
        assertEquals(3, s.mruCount)
        assertEquals(listOf("C:\\Builds\\One.build", "C:\\Builds\\Two.build"), s.mruList)
        assertEquals(BuildPoints.HERO, s.buildPoints)
        assertEquals(FeatOrderOutput.LEVEL, s.featOrderOutput)
        assertTrue(s.featShowAll)
        assertEquals("Plain Text", s.bbCodes)
        assertEquals(3, s.contrast)
        assertFalse(s.output.codes)
        assertEquals("Steam", s.screenColors)
        assertTrue(s.colorsText!!.contains("WrkBackground=1,2,3"))
    }

    @Test
    fun writesWhatItReads() {
        val s = BuilderSettings.parse(vbFile)
        val again = BuilderSettings.parse(s.write())
        assertEquals(s.write(), again.write())
        // Sections this program doesn't use are kept.
        assertTrue(s.write().contains("Section: Compendium\r\n\r\nDataFile=Main"))
        assertTrue(s.write().startsWith("Section: Character Builder Lite\r\n\r\nWindowSize=0\r\nMaximized=False\r\n"))
    }

    @Test
    fun recentFiles() {
        val s = BuilderSettings.parse(vbFile)
        s.addRecent("D:\\Other\\Two.build") // same name, other folder: replaces it
        assertEquals(listOf("D:\\Other\\Two.build", "C:\\Builds\\One.build"), s.mruList)
        s.addRecent("C:\\x.txt") // not a .build: ignored
        assertEquals(2, s.mruList.size)
        s.addRecent("C:\\a.build")
        s.addRecent("C:\\b.build")
        assertEquals(listOf("C:\\b.build", "C:\\a.build", "D:\\Other\\Two.build"), s.mruList)
    }

    @Test
    fun defaults() {
        val s = BuilderSettings.defaults()
        assertTrue(s.maximized)
        assertEquals(9, s.mruCount)
        assertEquals(2, s.contrast)
        assertEquals(FeatOrderOutput.CHANNEL, s.featOrderOutput)
    }
}
