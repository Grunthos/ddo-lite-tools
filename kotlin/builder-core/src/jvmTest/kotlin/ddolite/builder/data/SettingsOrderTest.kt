package ddolite.builder.data

import ddolite.dataformat.DirectoryDataSource
import ddolite.dataformat.decodeText
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Feat Order setting (IndexFeatDisplay) and reloading the stat templates (Stats › Customize). */
class SettingsOrderTest {
    private val dataDir = Path.of(System.getProperty("ddolite.dataDir"))
    private val data = loadBuilderData(DirectoryDataSource(dataDir), LoadLog())

    @Test
    fun groupRelatedSortsBySortNameAndIndents() {
        data.indexFeatDisplay(true)
        val order = data.featsInDisplayOrder
        for (i in 1 until order.size) {
            assertTrue(order[i - 1].sortName <= order[i].sortName, order[i].name)
        }
        var indented = 0
        for (feat in order) {
            if (data.featListCaption(feat).startsWith("  ")) {
                indented++
            }
        }
        assertTrue(indented > 0, "some feats group under a related one")
        data.indexFeatDisplay(false)
        for (feat in data.featsInDisplayOrder) {
            assertEquals(feat.abbreviation, data.featListCaption(feat))
        }
    }

    @Test
    fun reloadTemplatesReadsTheText() {
        val count = data.templates.size
        val text = decodeText(Files.readAllBytes(dataDir.resolve("Builder").resolve("Templates.txt"))).text
        val log = LoadLog()
        data.reloadTemplates(text, log)
        assertEquals(count, data.templates.size)
        data.reloadTemplates(null, log)
        assertEquals(0, data.templates.size)
        assertTrue(log.issues.isNotEmpty(), "a missing file is logged")
    }
}
