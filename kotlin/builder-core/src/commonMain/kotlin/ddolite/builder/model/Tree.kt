package ddolite.builder.model

/**
 * The kind of tree. Ported from `TreeStyleEnum` (Build/basEnum.bas:225) and the `Type:` line
 * in `basDataLoad.LoadTreeHeader`.
 *
 * @property tierCount VB's `Tiers`: the highest tier index, so the tree has tiers 0..tierCount
 *   (tier 0 is the core abilities). Set by `InitTree`.
 * @property maxValidTier the highest tier an ability may name (`basDataLoad.ValidTier`); for
 *   destinies this is one less than [tierCount].
 */
enum class TreeStyle(val dataName: String, val tierCount: Int, val maxValidTier: Int) {
    RACE("race", 4, 4),
    CLASS("class", 5, 5),
    DESTINY("destiny", 6, 5),
    GLOBAL("global", 5, 5),
    RACE_CLASS("raceclass", 5, 5);

    companion object {
        /** Case-insensitive; null for anything else. */
        fun find(name: String): TreeStyle? {
            val lower = name.lowercase()
            for (style in entries) {
                if (style.dataName == lower) {
                    return style
                }
            }
            return null
        }
    }
}

/**
 * A tree's colour in the leveling guide. Ported from the `Color:` line in
 * `basDataLoad.LoadTreeHeader`, which maps to these theme colour values (`cveRed` …).
 */
enum class TreeColor {
    RED, GREEN, BLUE, YELLOW, PURPLE, ORANGE;

    companion object {
        fun find(name: String): TreeColor? {
            val lower = name.lowercase()
            for (color in entries) {
                if (color.name.lowercase() == lower) {
                    return color
                }
            }
            return null
        }
    }
}

/**
 * Cost and requirements for rank 2 or 3 of an ability (rank 1 uses the ability's own).
 * Ported from `RankType` (Build/basDataDefs.bas:97); its Class/ClassLevel arrays are never set for
 * trees (that code is commented out in VB), so they're left out.
 *
 * @property cost from `Rank2Cost:`/`Rank3Cost:`; 0 if not given (VB marks this "isn't complete").
 */
data class RankDetail(val cost: Int, val requirements: Requirements) {
    companion object {
        val EMPTY = RankDetail(0, Requirements.EMPTY)
    }
}

/**
 * An enhancement or destiny ability. Ported from `AbilityType` (Build/basDataDefs.bas:212).
 *
 * @property ranks how many ranks it has (1-3; VB keeps an out-of-range value and reports it).
 * @property cost AP per rank (1-4).
 * @property parent `SharedSelector:` or `Parent:`: the ability (or "Feat: …") whose selectors this uses.
 * @property siblings `Siblings:`: abilities linked to this one.
 * @property selectorOnly show the selector's name alone, not "Ability: Selector".
 * @property rankReqs has rank 2/3 requirements (`Rank2All:`, `Rank3All:`, `Rank3None:`).
 * @property rankDetails ranks 2 and 3 (index 0 and 1), or null if the data never mentions them.
 *   VB creates both together (`InitRanks`).
 */
data class Ability(
    val name: String,
    val abbreviation: String,
    val description: String,
    val ranks: Int,
    val cost: Int,
    val selectorStyle: SelectorStyle,
    val selectors: List<Selector>,
    val parent: Requirement?,
    val siblings: List<Requirement>,
    val selectorOnly: Boolean,
    val requirements: Requirements,
    val rankReqs: Boolean,
    val rankDetails: List<RankDetail>?,
) {
    /** The selector called exactly [name], or null. */
    fun findSelector(name: String): Selector? {
        for (selector in selectors) {
            if (selector.name == name) {
                return selector
            }
        }
        return null
    }
}

/**
 * An enhancement tree (Enhancements.txt) or epic destiny (Destinies.txt): both use VB's `TreeType`
 * (Build/basDataDefs.bas:240).
 *
 * VB's `TreeID` is the tree's position after sorting by name; here that's the index in
 * [ddolite.builder.data.BuilderData.trees] or `destinies`.
 *
 * @property initials the leveling guide's abbreviations, normal and short (`Initial:`); both "" if not given.
 * @property stats stats offered by the tree's automatic stat abilities (`Stats:`).
 * @property tiers abilities by tier: `tiers[0]` holds the cores, then tiers 1..[TreeStyle.tierCount],
 *   each in file order, with the automatic stat abilities at the end of tiers 3 and 4.
 * @property lockout a tree that can't be taken alongside this one.
 * @property wiki DDO wiki page; for enhancement trees VB adds " enhancements" (see the loader).
 */
data class Tree(
    val name: String,
    val abbreviation: String,
    val initials: List<String>,
    val color: TreeColor?,
    val style: TreeStyle,
    val stats: Set<Stat>,
    val tiers: List<List<Ability>>,
    val lockout: String?,
    val wiki: String,
)
