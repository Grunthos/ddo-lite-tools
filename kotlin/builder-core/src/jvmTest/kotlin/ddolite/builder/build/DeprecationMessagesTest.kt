package ddolite.builder.build

import ddolite.builder.data.LoadLog
import ddolite.builder.data.loadBuilderData
import ddolite.dataformat.DirectoryDataSource
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Messages screen's rules ([DeprecationMessages]) on Warlock Blaster with two feats changed
 * as an old build file might have them: one feat renamed, one selector that no longer exists.
 */
class DeprecationMessagesTest {
    private val data = loadBuilderData(DirectoryDataSource(Path.of(System.getProperty("ddolite.dataDir"))), LoadLog())

    private fun open(): BuildLoadResult {
        var text = String(SampleBuilds.file("Warlock Blaster.build").readBytes(), Charsets.ISO_8859_1)
        text = text.replace("\tEmpower Spell\r\n", "\tEmpowered Spell\r\n")
        text = text.replace("\tSpell Focus: Conjuration\r\n", "\tSpell Focus: Conjurer\r\n")
        return readBuildFile(text.toByteArray(Charsets.ISO_8859_1), data, BuildPoints.ADVENTURER)
    }

    @Test
    fun listsIssuesThenNotices() {
        val loaded = open()
        val messages = DeprecationMessages(loaded.rules, loaded.deprecations)
        val list = messages.messages()
        assertEquals("Feats: Empowered Spell not found (taken as a Heroic feat at level 9)", list[0].text)
        assertEquals("Feats: Spell Focus: Conjurer not found (taken as a Heroic feat at level 12)", list[1].text)
        assertEquals(MessageGroup.FEAT, list[1].group)
        assertEquals(2, list[1].index)
        val (issues, notices) = messages.countMessages()
        assertTrue(issues >= 2)
        assertEquals(issues + notices, list.size)
        assertTrue(messages.label().startsWith("$issues Issues and "))
        for (message in list) {
            if (message.group == MessageGroup.NAME_CHANGE) {
                assertEquals(listOf("Name changes are automatic.", "Nothing to resolve."), messages.details(message).map { it.text })
            }
        }
    }

    @Test
    fun featDetails() {
        val loaded = open()
        val messages = DeprecationMessages(loaded.rules, loaded.deprecations)
        val list = messages.messages()
        assertEquals(listOf("Feat not found", "", "Feat: Empowered Spell", "Type: Heroic", "Level: 9"), messages.details(list[0]).map { it.text })
        assertEquals(
            listOf("Selector not found", "", "Feat: Spell Focus", "Selector: Conjurer", "", "Type: Heroic", "Level: 12"),
            messages.details(list[1]).map { it.text },
        )
    }

    @Test
    fun bestGuessAndChoose() {
        val loaded = open()
        val messages = DeprecationMessages(loaded.rules, loaded.deprecations)
        val guesses = messages.similarFeats("Empowered Spell")
        assertEquals("Empower Spell", guesses[0].name)
        assertTrue(guesses.size <= 10)
        // Spell Focus: the colon cuts the name.
        assertEquals("Spell Focus", messages.similarFeats("Spell Focus: Conjurer")[0].name)
        for (feat in messages.selectableFeats()) {
            assertTrue(feat.selectable)
        }

        val list = messages.messages()
        val focus = data.findFeat("Spell Focus")!!
        var conjuration = 0
        for (i in focus.selectors.indices) {
            if (focus.selectors[i].name == "Conjuration") {
                conjuration = i + 1
            }
        }
        messages.chooseFeat(list[1].index, "Spell Focus", conjuration)
        val slot = loaded.build.feats(BuildFeatType.STANDARD).first { it.level == 12 }
        assertEquals("Spell Focus", slot.featName)
        assertEquals(conjuration, slot.selector)
        val before = messages.messages().size
        messages.resolve(list[1])
        assertEquals(before - 1, messages.messages().size)
        assertEquals("Feats: Empowered Spell not found (taken as a Heroic feat at level 9)", messages.messages()[0].text)
    }

    @Test
    fun featIssueQuirks() {
        val loaded = open()
        val messages = DeprecationMessages(loaded.rules, loaded.deprecations)
        // No slot: a blank type, and "an" because InStr finds "" in "aeiou".
        val missing = DeprecatedFeat(BuildFeatType.DEITY, 99, "Old Feat", "", null, 0)
        assertEquals("Old Feat not found (taken as an  feat at level 0)", messages.featIssue(missing))
        assertEquals("Epic", messages.featTypeName(BuildFeatType.STANDARD, 21))
        assertEquals("Destiny", messages.featTypeName(BuildFeatType.STANDARD, 26))
        assertEquals("Standard", messages.featTypeName(BuildFeatType.STANDARD, 22))
    }

    @Test
    fun levenshtein() {
        assertEquals(3, levenshteinDistance("kitten", "sitting"))
        assertEquals(0, levenshteinDistance("Spell", "SPELL"))
        assertEquals(5, levenshteinDistance("", "Spell"))
    }

    @Test
    fun quickSortMatchesVbOrder() {
        // Equal distances: VB's QuickSort2 isn't stable; this is the order its code gives.
        val ids = intArrayOf(0, 1, 2, 3, 4, 5)
        val distances = intArrayOf(0, 2, 1, 2, 1, 0)
        quickSort2(ids, distances, 1, 5)
        assertEquals(listOf(0, 1, 1, 2, 2), distances.drop(1))
        assertEquals(listOf(5, 4, 2, 1, 3), ids.drop(1))
    }
}
