package ddolite.builder.model

/**
 * One option of a feat (or enhancement ability) that has choices, e.g. "Weapon Focus: Slashing".
 * Ported from `SelectorType` (Build/basDataDefs.bas:111), which VB shares between feats and
 * enhancement abilities. VB's RaceBonus is never set for either, so it's left out.
 *
 * VB marks some arrays as "present" with element 0 (e.g. `Class(0) = True`); those are nullable
 * lists here: null means the data had no such line, which VB code tests before using the list.
 *
 * @property name `SelectorName`.
 * @property all true for a selector called "All", which counts as taking every selector.
 * @property classBonus classes that get this as a bonus feat. A copy of the feat's own list at the
 *   time its `Selector:` line was read, unless the selector section sets its own.
 * @property classes `Class: Fighter 4, …` requirements; null if none (`Class(0)` false).
 * @property notClass `NotClass`: a class that can't take it (Acolyte and lawful-only pacts).
 * @property alignments allowed alignments; null if unrestricted (`Alignment(0)` false).
 * @property race race restriction; copied from the feat unless the selector section sets its own.
 * @property hide `Hide`: never offered.
 * @property notRaces races that can't take it; null if none (`NotRace(0)` false).
 * @property raceOnly `RaceOnly`: only this race can take it (as a racial feat).
 * @property classOnly `ClassOnly`: only this class can take it (added for Acolyte).
 * @property cost ability selectors only: AP cost per rank (VB copies the ability's cost; 0 for feats).
 * @property rankReqs ability selectors only: has rank 2/3 requirements (`Rank2All:` etc.).
 * @property rankDetails ability selectors only: ranks 2 and 3; null if never set (see [Ability.rankDetails]).
 */
data class Selector(
    val name: String,
    val wiki: String,
    val description: String,
    val all: Boolean,
    val classBonus: List<String>,
    val stat: Stat?,
    val statValue: Int,
    val skill: Skill?,
    val skillValue: Int,
    val classes: List<ClassLevel>?,
    val notClass: String?,
    val requirements: Requirements,
    val alignments: List<Alignment>?,
    val race: RaceRequirement,
    val hide: Boolean,
    val notRaces: List<String>?,
    val raceOnly: String?,
    val classOnly: String?,
    val cost: Int = 0,
    val rankReqs: Boolean = false,
    val rankDetails: List<RankDetail>? = null,
)
