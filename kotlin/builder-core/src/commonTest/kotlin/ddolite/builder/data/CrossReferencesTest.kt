package ddolite.builder.data

import ddolite.builder.model.AbilityTarget
import ddolite.builder.model.FeatTarget
import ddolite.builder.model.PointerStyle
import ddolite.dataformat.MapDataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CrossReferencesTest {
    private val races = "RaceName: Human\nStats: 8, 8, 8, 8, 8, 8\nTrees: Human Wizard\n"
    private val classes = "ClassName: Fighter\nBAB: 1\nTrees: Kensei\nGrantedFeats1: Toughness, Missing Feat\n"
    private val feats =
        "FeatName: Toughness\nGroup: Misc\n\n" +
            "FeatName: Bond\nGroup: Misc\nSelector: Protector, Fallen\nSelectorName: Protector\nDescrip: Guards\n\n" +
            "FeatName: Power Attack\nGroup: Melee\nAll: Toughness, Nonexistent\n"
    private val trees =
        "TreeName: Human\nType: Race\n\nAbilityName: Core\nTier: 0\n\n" +
            "TreeName: Human Wizard\nType: RaceClass\n\n" +
            "TreeName: Kensei\nType: Class\nLockout: Human\n\n" +
            "AbilityName: Focus\nTier: 1\nSelector: Axes, Bows\nSelectorName: Axes\nDescrip: Chop\n\n" +
            "AbilityName: Strike\nTier: 1\nRanks: 3\nAll: Tier 1: Focus: Axes, Feat: Bond: Fallen, Human Tier 0: Core Rank 2\n" +
            "Rank3All: Tier 1: Focus, Tier 1: Nope\n\n" +
            "AbilityName: Focus Again\nTier: 2\nParent: Tier 1: Focus\nSelector: Axes, Bows\nSelectorName: Bows\nDescrip: Shoot\n\n" +
            "AbilityName: Shared Focus\nTier: 2\nSharedSelector: Tier 1: Focus\n\n" +
            "AbilityName: Broken\nTier: 2\nAll: Tier 1: Missing, Feat: Imaginary\n\n" +
            "AbilityName: Bonded\nTier: 3\nSharedSelector: Feat: Bond\n"

    private fun load(): Pair<BuilderData, LoadLog> {
        val log = LoadLog()
        val data = loadBuilderData(
            MapDataSource.ofText(
                "Builder/Races.txt" to races,
                "Builder/Classes.txt" to classes,
                "Builder/Feats.txt" to feats,
                "Builder/Enhancements.txt" to trees,
            ),
            log,
        )
        return Pair(data, log)
    }

    private fun messages(log: LoadLog): List<String> {
        val result = ArrayList<String>()
        for (issue in log.issues) {
            if (issue.message.startsWith("File not found")) {
                continue
            }
            var text = issue.message
            if (issue.detail != null) {
                text = text + " / " + issue.detail
            }
            result.add(text)
        }
        return result
    }

    @Test
    fun requirementsResolve() {
        val (data, _) = load()
        val kensei = assertNotNull(data.findTree("Kensei"))
        val strike = kensei.tiers[1][1]
        val all = strike.requirements.all
        assertEquals(AbilityTarget(PointerStyle.ENHANCEMENT, "Kensei", 1, 0, "Focus", "Axes", 0), all[0].target)
        assertEquals(FeatTarget("Bond", "Fallen"), all[1].target)
        // Another tree, with a rank; the style comes from this tree.
        assertEquals(AbilityTarget(PointerStyle.ENHANCEMENT, "Human", 0, 0, "Core", null, 2), all[2].target)
        // Rank requirements go the ProcessPointer way.
        val rank3 = assertNotNull(strike.rankDetails)[1].requirements.all
        assertEquals(AbilityTarget(PointerStyle.ENHANCEMENT, "Kensei", 1, 0, "Focus", null, 0), rank3[0].target)
        assertNull(rank3[1].target)

        val power = assertNotNull(data.findFeat("Power Attack"))
        assertEquals(FeatTarget("Toughness", null), power.requirements.all[0].target)
        assertNull(power.requirements.all[1].target)
    }

    @Test
    fun parentSelectors() {
        val (data, _) = load()
        val kensei = assertNotNull(data.findTree("Kensei"))
        // Same names as the parent, by position; own descriptions kept, missing ones taken from the parent.
        val again = kensei.tiers[2][0]
        assertEquals(listOf("Axes", "Bows"), again.selectors.map { it.name })
        assertEquals("Chop", again.selectors[0].description)
        assertEquals("Shoot", again.selectors[1].description)
        assertEquals(AbilityTarget(PointerStyle.ENHANCEMENT, "Kensei", 1, 0, "Focus", null, 0), again.parent?.target)
        // No selectors of its own: gets the parent's.
        val shared = kensei.tiers[2][1]
        assertEquals(listOf("Axes", "Bows"), shared.selectors.map { it.name })
        assertEquals("Chop", shared.selectors[0].description)
        // From a feat.
        val bonded = kensei.tiers[3][0]
        assertEquals(listOf("Protector", "Fallen"), bonded.selectors.map { it.name })
        assertEquals("Guards", bonded.selectors[0].description)
        assertEquals(FeatTarget("Bond", null), bonded.parent?.target)
    }

    @Test
    fun problemsAreReported() {
        val (_, log) = load()
        val found = messages(log)
        val expected = listOf(
            "Feat: Power Attack / Invalid requirement: Feat: Nonexistent",
            "Class: Fighter / Invalid granted feat (level 1): Feat: Missing Feat",
            "Enhancement: Kensei Tier 1: Strike  / Invalid requirement: Tier 1: Nope",
            "Enhancements.txt: Enhancement: Kensei Tier 2: Broken : requirement's ability not found: Tier 1: Missing (VB6 doesn't report this)",
            "Enhancements.txt: Enhancement: Kensei Tier 2: Broken : requirement's feat not found: Feat: Imaginary (VB6 doesn't report this)",
            "Tree: Kensei / Invalid lockout tree: Human",
        )
        for (message in expected) {
            assertTrue(found.contains(message), "missing: $message\nfound: $found")
        }
        assertEquals(expected.size, found.size, found.toString())
    }

    @Test
    fun treeChecks() {
        val log = LoadLog()
        loadBuilderData(
            MapDataSource.ofText(
                "Builder/Races.txt" to "RaceName: Elf\nStats: 8, 8, 8, 8, 8, 8\nTrees: Kensei\n",
                "Builder/Classes.txt" to "ClassName: Fighter\nBAB: 1\nTrees: Elf\n",
                "Builder/Enhancements.txt" to "TreeName: Elf\nType: Global\n\nTreeName: Kensei\nType: Class\n",
            ),
            log,
        )
        val found = messages(log)
        assertTrue(found.contains("Race: Elf / Racial tree not found"), found.toString())
        assertTrue(found.contains("Race: Elf / Invalid RaceClass tree: Kensei"), found.toString())
        assertTrue(found.contains("Class: Fighter / Invalid class tree: Elf"), found.toString())
    }

    @Test
    fun rankSuffix() {
        assertEquals(Pair("Energy of the Wild", 3), splitRank("Energy of the Wild Rank 3"))
        assertEquals(Pair("Focus", 0), splitRank("Focus"))
        assertEquals(Pair("Rank 2", 0), splitRank("Rank 2")) // too short
    }
}
