package ddolite.builder.data

import ddolite.builder.model.PointerStyle
import ddolite.builder.model.Requirement
import ddolite.builder.model.SelectorStyle
import ddolite.builder.model.Stat
import ddolite.builder.model.Tree
import ddolite.builder.model.TreeColor
import ddolite.builder.model.TreeStyle
import ddolite.dataformat.MapDataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TreesLoaderTest {
    private fun load(text: String): Pair<List<Tree>, LoadLog> {
        val log = LoadLog()
        val trees = loadEnhancements(MapDataSource.ofText("Builder/Enhancements.txt" to text), log)
        return Pair(trees, log)
    }

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

    private val kensei =
        "TreeName: Kensei\nType: Class\nInitial: Ken\nColor: Red\nStats: Strength, Wisdom\n\n" +
            "AbilityName: Focus\nTier: 0\nSelector: Axes, Bows\nSelectorName: Axes\nDescrip: Chop\nCost: 2\n\n" +
            "AbilityName: Second Core\nTier: 0\nAll: Tier 1: Strike\n\n" +
            "AbilityName: Strike\nTier: 1\nRanks: 3\nCost: 2\nAll: Tier 0: Focus\nRank3None: Tier 2: Other\nRank3Cost: 1\n\n" +
            "AbilityName: Other\nAbbreviation: Oth\nTier: 2\nFlags: SelectorOnly\nSiblings: Strike\nParent: Focus\n"

    @Test
    fun headerAndAbilities() {
        val (trees, log) = load(kensei)
        assertEquals(emptyList(), messages(log))
        val t = trees[0]
        assertEquals("Kensei", t.name)
        assertEquals(TreeStyle.CLASS, t.style)
        assertEquals(listOf("Ken", "Ken"), t.initials)
        assertEquals(TreeColor.RED, t.color)
        assertEquals(setOf(Stat.STR, Stat.WIS), t.stats)
        assertEquals("Kensei enhancements", t.wiki)
        assertEquals(6, t.tiers.size) // tiers 0..5

        val focus = t.tiers[0][0]
        assertEquals(SelectorStyle.ROOT, focus.selectorStyle)
        val axes = assertNotNull(focus.findSelector("Axes"))
        assertEquals("Chop", axes.description)
        assertEquals(2, axes.cost)
        assertEquals(1, assertNotNull(focus.findSelector("Bows")).cost) // the ability's cost when Selector: was read

        // Cores after the first require the one before; the data's own requirements come first.
        val second = t.tiers[0][1]
        assertEquals(
            listOf(Requirement("Tier 1: Strike", null), Requirement("Tier 0: Focus", PointerStyle.ENHANCEMENT)),
            second.requirements.all,
        )

        val strike = t.tiers[1][0]
        assertEquals(3, strike.ranks)
        assertTrue(strike.rankReqs)
        val details = assertNotNull(strike.rankDetails)
        assertEquals(1, details[1].cost)
        assertEquals(listOf(Requirement("Tier 2: Other", null)), details[1].requirements.none)
        assertEquals(0, details[0].cost)

        val other = t.tiers[2][0]
        assertEquals("Oth", other.abbreviation)
        assertTrue(other.selectorOnly)
        assertEquals(SelectorStyle.EXCLUSIVE, other.selectorStyle)
        assertEquals(Requirement("Focus", null), other.parent)
        assertEquals(listOf(Requirement("Strike", null)), other.siblings)
    }

    @Test
    fun automaticStatAbilities() {
        val (trees, _) = load(kensei)
        val t = trees[0]
        val tier3 = t.tiers[3].last()
        val tier4 = t.tiers[4].last()
        assertEquals("Stat", tier3.name)
        assertEquals(listOf("Strength", "Wisdom"), tier3.selectors.map { it.name })
        assertTrue(tier3.selectorOnly)
        assertEquals(2, tier3.cost)
        assertEquals(emptyList(), tier3.requirements.all)
        assertEquals(listOf(Requirement("Tier 3: Stat", null)), tier4.requirements.all)
        assertEquals(listOf(Requirement("Tier 3: Stat", null)), tier4.selectors[0].requirements.all)

        val (single, _) = load("TreeName: Solo\nType: Global\nStats: Charisma\n\nAbilityName: A\nTier: 1\n")
        val ability = single[0].tiers[3].last()
        assertEquals("Charisma", ability.name)
        assertEquals(SelectorStyle.NONE, ability.selectorStyle)

        // Race trees get none.
        val (race, _) = load("TreeName: Human\nType: Race\nStats: Charisma\n")
        assertEquals(5, race[0].tiers.size)
        assertTrue(race[0].tiers[3].isEmpty())
    }

    @Test
    fun treesAreSortedAndDestiniesKeepTheirWiki() {
        val log = LoadLog()
        val trees = loadEnhancements(
            MapDataSource.ofText("Builder/Enhancements.txt" to "TreeName: b\nType: Global\n\nTreeName: B\nType: Global\n\nTreeName: A\nType: Global\n"),
            log,
        )
        assertEquals(listOf("A", "B", "b"), trees.map { it.name })
        val destinies = loadDestinies(
            MapDataSource.ofText("Builder/Destinies.txt" to "DestinyName: Divine Crusader\nAbbreviation: Crusader\nType: Destiny\n\nAbilityName: One\nTier: 0\n\nAbilityName: Two\nTier: 0\n"),
            log,
        )
        val d = destinies[0]
        assertEquals("Divine Crusader", d.wiki)
        assertEquals(7, d.tiers.size)
        assertEquals(listOf(Requirement("Tier 0: One", PointerStyle.DESTINY)), d.tiers[0][1].requirements.all)
    }

    @Test
    fun errors() {
        val (trees, log) = load(
            "TreeName: Missing type\nAbbreviation: x\n\n" +
                "TreeName: Bad\nType: Wizardry\n\n" +
                "TreeName: Odd\nType: Class\nStats: Luck\nColor: Mauve\n\n" +
                "AbilityName: First\nTier: 2\nRanks: 5\n\n" +
                "AbilityName: Second\nTier: 1\nFlags: Shiny\n\n" +
                "AbilityName: Third\nTier: 9\nNonsense: 1\n",
        )
        assertEquals(listOf("Odd"), trees.map { it.name })
        assertEquals(
            listOf(
                "Invalid section in Enhancements.txt ('Type: ' not found): / Missing type",
                "Enhancements.txt: Bad header has invalid Type: Wizardry",
                "Enhancements.txt: Bad failed to load",
                "Enhancements.txt: Odd header has invalid stat: Luck / Stats: Luck",
                "Enhancements.txt: Odd header has invalid line: / Color: Mauve",
                "Enhancements.txt: Odd Tier 2: First has invalid line: / Ranks: 5",
                "Enhancements.txt: Odd Tier 1(2?): Second not in Tier order",
                "Enhancements.txt: Odd Tier 1: Second has invalid line: / Flags: Shiny",
                // Tier 9 is out of range: reported with the previous tier, then the previous tier is used.
                "Enhancements.txt: Odd Tier 1: Third has invalid line: / Tier: 9",
                "Enhancements.txt: Odd Tier 1: Third has invalid line: / Nonsense: 1",
            ),
            messages(log),
        )
        val odd = trees[0]
        assertEquals(5, odd.tiers[2][0].ranks) // kept, as VB does
        assertEquals("Third", odd.tiers[1][1].name)
        assertNull(odd.color)
        assertFalse(odd.tiers[0].isNotEmpty())
    }
}
