package ddolite.builder.data

import ddolite.builder.model.AutoGrantedFeat
import ddolite.builder.model.FeatMapEntry
import ddolite.dataformat.MapDataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeatListsLoaderTest {
    private fun messages(log: LoadLog): List<String> {
        val result = ArrayList<String>()
        for (issue in log.issues) {
            var text = issue.message
            if (issue.detail != null) {
                text = text + " / " + issue.detail
            }
            result.add(text)
        }
        return result
    }

    @Test
    fun featMapSkipsTheHeadingAndKeepsBadLinesAsBlanks() {
        val log = LoadLog()
        val entries = loadFeatMap(
            MapDataSource.ofText(FEAT_MAP_PATH to "Lite\tRon\tBuilder\nDodge\tDodge\tDodge\nbad line\nAdept of Forms\tMonk Feats: Adept of Forms\t"),
            log,
        )
        assertEquals(
            listOf(FeatMapEntry("Dodge", "Dodge", "Dodge"), FeatMapEntry("", "", ""), FeatMapEntry("Adept of Forms", "Monk Feats: Adept of Forms", "")),
            entries,
        )
        assertEquals(listOf("FeatMap.txt: Line 3 is invalid: / bad line"), messages(log))
    }

    @Test
    fun emptyFeatMap() {
        val log = LoadLog()
        loadFeatMap(MapDataSource.ofText(FEAT_MAP_PATH to "Lite\tRon\tBuilder"), log)
        assertEquals(listOf("FeatMap.txt: File is empty"), messages(log))
    }

    @Test
    fun featMapNamesAreChecked() {
        val log = LoadLog()
        loadBuilderData(
            MapDataSource.ofText(
                "Builder/Feats.txt" to "FeatName: Weapon Focus\nGroup: Melee\nSelector: Slashing\n",
                FEAT_MAP_PATH to "h\th\th\nWeapon Focus: Slashing\ta\tb\nWeapon Focus: Wooden\ta\tb\nToughness\ta\tb\n\tonly Ron\t",
            ),
            log,
        )
        val found = messages(log)
        assertTrue(found.contains("FeatMap.txt: Weapon Focus selector not found: Wooden"), found.toString())
        assertTrue(found.contains("FeatMap.txt: Feat not found: Toughness"), found.toString())
        assertEquals(2, found.count { it.startsWith("FeatMap.txt") })
    }

    @Test
    fun autoGrantedFeats() {
        val log = LoadLog()
        val data = loadBuilderData(
            MapDataSource.ofText(
                "Builder/Feats.txt" to "FeatName: Attack\nGroup: Misc\n\nFeatName: Heroic Durability\nGroup: Misc\n",
                "Builder/AutoGrantedFeats.txt" to "GrantedFeats1: Attack, Heroic Durability\nGrantedFeats2: Heroic Durability, Sneak\nGrantedFeatsX: Attack\n",
            ),
            log,
        )
        assertEquals(
            listOf(AutoGrantedFeat("Attack", 1), AutoGrantedFeat("Heroic Durability", 1), AutoGrantedFeat("Heroic Durability", 2)),
            data.autoGrantedFeats,
        )
        val found = messages(log)
        assertTrue(found.contains("Invalid section in AutoGrantedFeats.txt / Sneak"), found.toString())
        assertTrue(found.any { it.startsWith("AutoGrantedFeats.txt: level isn't a number") }, found.toString())
    }
}
