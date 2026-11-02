package ddolite.builder.build

import ddolite.builder.model.Alignment
import ddolite.builder.model.CharacterClass
import ddolite.builder.model.Race
import ddolite.builder.model.Skill
import ddolite.builder.model.Stat
import ddolite.builder.model.TreeStyle

/** Highest character level (`MAX_LEVEL`, Source/Modules/basCONST.bas:12). */
const val MAX_LEVEL = 36

/** Highest level-up index: level-ups at 0, 4, … 36 (`MAX_LEVELUPS`, basCONST.bas:14). */
const val MAX_LEVELUPS = 9

/** Heroic levels, which are the only ones with a class (`build.Class(1 To 20)`). */
const val HEROIC_LEVELS = 20

/**
 * Which point-buy the build is planned for. Ported from `BuildPointsEnum` (basEnum.bas) with
 * `GetBuildPointsName`/`GetBuildPointsID` (basFormatLite.bas:274, :875). Order matters: VB uses the
 * ordinal as the column of `StatPoints` and `IncludePoints`.
 */
enum class BuildPoints(val displayName: String) {
    ADVENTURER("Adventurer"),
    CHAMPION("Champion"),
    HERO("Hero"),
    LEGEND("Legend");

    companion object {
        /** `GetBuildPointsID`: any case; Adventurer for anything else (VB returns 0). */
        fun find(name: String): BuildPoints {
            for (points in entries) {
                if (name.lowercase() == points.displayName.lowercase()) {
                    return points
                }
            }
            return ADVENTURER
        }
    }
}

/**
 * The kinds of feat slot. Ported from `BuildFeatTypeEnum` (basEnum.bas:192); VB's `bftUnknown` is
 * null here. Order matters: VB loops over the kinds in this order and sorts the feat list by it.
 */
enum class BuildFeatType(val fileName: String) {
    GRANTED("Granted"),
    STANDARD("Standard"),
    LEGEND("Legend"),
    RACE("Race"),
    CLASS1("Class"),
    CLASS2("Class"),
    CLASS3("Class"),
    DEITY("Deity"),
    ALTERNATE("Alternate"),
    EXCHANGE("Exchange");

    /** 0, 1 or 2 for the three class kinds (VB `penType - bftClass1`), else -1. */
    val classIndex: Int
        get() = when (this) {
            CLASS1 -> 0
            CLASS2 -> 1
            CLASS3 -> 2
            else -> -1
        }
}

/**
 * Where a feat slot comes from, shown in the Feats screen's Source column. Ported from
 * `BuildFeatSourceEnum` (basEnum.bas:180). VB stores 0 (Heroic) for "none" too.
 */
enum class BuildFeatSource { HEROIC, EPIC, DESTINY, RACE, CLASS, CLASS_ONLY, DEITY, LEGEND }

/** Spell slot kinds. Ported from `SpellSlotEnum` (basEnum.bas:134). */
enum class SpellSlotType { STANDARD, FREE, CLERIC_CURE, WARLOCK_PACT }

/**
 * One feat slot and the feat in it. Ported from `BuildFeatType` (basBuildDefs.bas:158).
 *
 * @property level character level (99 for a slot the build never reaches).
 * @property selector the feat's selector, numbered from 1 as in VB; 0 for none.
 * @property child for alternates and exchanges: the slot they belong to, as [childType] and a
 *   0-based index into that kind's list.
 */
class BuildFeat(var type: BuildFeatType) {
    var source = BuildFeatSource.HEROIC
    var level = 0
    var classLevel = 0
    var featName = ""
    var selector = 0
    var child = 0
    var childType: BuildFeatType? = null

    fun copy(): BuildFeat {
        val result = BuildFeat(type)
        result.source = source
        result.level = level
        result.classLevel = classLevel
        result.featName = featName
        result.selector = selector
        result.child = child
        result.childType = childType
        return result
    }
}

/** One spell slot. Ported from `BuildSpellSlotType` (basBuildDefs.bas:114); [level] is the character level it's gained. */
class BuildSpellSlot {
    var type = SpellSlotType.STANDARD
    var level = 0
    var spell = ""

    fun copy(): BuildSpellSlot {
        val result = BuildSpellSlot()
        result.type = type
        result.level = level
        result.spell = spell
        return result
    }
}

/** The slots for one spell level of one class. Ported from `BuildClassLevelType` (basBuildDefs.bas:120). */
class BuildSpellLevel {
    val slots = ArrayList<BuildSpellSlot>()
    var baseSlots = 0
    var freeSlots = 0
    var mandatory = SpellSlotType.STANDARD
}

/**
 * One class's spells. Ported from `BuildClassSpellType` (basBuildDefs.bas:128).
 * [levels] holds spell levels 1..n at index 0..n-1; it can be longer than [maxSpellLevel], because
 * VB keeps the old array when the class loses spell levels.
 */
class BuildClassSpells(val characterClass: CharacterClass) {
    var classLevels = 0
    var maxSpellLevel = 0
    val levels = ArrayList<BuildSpellLevel>()

    /** Spell level [spellLevel] (1-based, as VB's `.Level(n)`). */
    fun level(spellLevel: Int): BuildSpellLevel = levels[spellLevel - 1]
}

/**
 * One ability taken in a tree. Ported from `BuildAbilityType` (basBuildDefs.bas:135).
 *
 * @property ability 0-based position in the tier (VB stores it 1-based).
 * @property selector numbered from 1 as in VB; 0 for none.
 */
data class BuildAbility(val tier: Int, val ability: Int, val rank: Int, val selector: Int)

/**
 * A tree in the build. Ported from `BuildTreeType` (basBuildDefs.bas:142).
 *
 * @property source the class the tree comes from, or null for race and global trees.
 */
class BuildTree(var treeName: String) {
    var treeType: TreeStyle? = null
    var source: CharacterClass? = null
    var classLevels = 0
    val abilities = ArrayList<BuildAbility>()
}

/** A tree the leveling guide uses. Ported from `BuildGuideTreeType` (basBuildDefs.bas:286). */
class BuildGuideTree(val treeName: String, val characterClass: CharacterClass?)

/**
 * One leveling guide step. Ported from `BuildGuideEnhancementType` (basBuildDefs.bas:292).
 *
 * @property id 0 = unknown, 1-99 = an ability in [BuildGuide.trees] entry `id` (1-based),
 *   100 = reset all trees, 101-199 = reset tree `id - 100`, 200 = bank remaining AP.
 * @property ability 0-based position in the tier.
 */
class BuildGuideEnhancement(val id: Int) {
    var tier = 0
    var ability = 0
    var selector = 0
    var rank = 0
}

/** The leveling guide. Ported from `BuildGuideType` (basBuildDefs.bas:300). */
class BuildGuide {
    val trees = ArrayList<BuildGuideTree>()
    val enhancements = ArrayList<BuildGuideEnhancement>()
}

/**
 * A character build: what a .build file holds. Ported from `BuildType6` (basBuildDefs.bas:401),
 * VB's global `build`.
 *
 * Like VB's, this is one mutable structure that the build rules update in place (the screens
 * will edit it the same way). Differences from VB:
 * - Races and classes are references to the loaded data; null where VB stores 0 (reAny, ceAny).
 * - Positions in lists are 0-based ([levelClasses] index 0 is level 1); selectors keep VB's
 *   1-based numbers with 0 for none.
 * - Arrays indexed by stat or skill are indexed by `ordinal`; VB's `StatPoints(i, 0)`, the
 *   column total, is [statTotals].
 *
 * @property buildClasses the three classes chosen on the Overview screen (`BuildClass(0 To 2)`).
 * @property levelClasses the class taken at each heroic level (`Class(1 To 20)`).
 * @property bab base attack bonus at each level (`BAB(1 To MAX_LEVEL)`).
 * @property statPoints points spent per [BuildPoints] column and stat.
 * @property includePoints which point-buy columns are shown (VB stores 1 or 0).
 * @property levelups the stat raised at levels 0, 4, … (index = level / 4); null for none.
 * @property tomes stat tomes, by stat.
 * @property skills skill points spent per skill and heroic level (points, not ranks: see
 *   [ddolite.builder.rules.SkillGrid]).
 * @property feats feat slots of each kind.
 * @property canCastSpell the first level each spell level can be cast (index 0 = healing spells), 0 if never.
 * @property classSpells spell slots, one per class in data order; empty until the first
 *   `InitBuildSpells` (VB's unallocated `build.Spell`).
 */
class Build {
    var notes = ""
    var buildName = ""
    var race: Race? = null
    var alignment: Alignment? = null
    var maxLevels = 0
    val buildClasses = arrayOfNulls<CharacterClass>(3)
    val levelClasses = arrayOfNulls<CharacterClass>(HEROIC_LEVELS)
    val bab = IntArray(MAX_LEVEL)
    val statPoints = Array(BuildPoints.entries.size) { IntArray(Stat.entries.size) }
    val statTotals = IntArray(BuildPoints.entries.size)
    var buildPoints = BuildPoints.ADVENTURER
    val includePoints = BooleanArray(BuildPoints.entries.size)
    val levelups = arrayOfNulls<Stat>(MAX_LEVELUPS + 1)
    val tomes = IntArray(Stat.entries.size)
    val skills = Array(Skill.entries.size) { IntArray(HEROIC_LEVELS) }
    val skillTomes = IntArray(Skill.entries.size)
    val feats = Array(BuildFeatType.entries.size) { ArrayList<BuildFeat>() }
    val canCastSpell = IntArray(10)
    val classSpells = ArrayList<BuildClassSpells>()
    val trees = ArrayList<BuildTree>()
    val guide = BuildGuide()
    var tier5 = ""
    var racialAP = 0
    var universalAP = 0
    val destinies = ArrayList<BuildTree>()
    var destinyTier5 = ""
    var destinyAP = 0
    var permDestinyPoints = 0

    /** The class at heroic [level] (1-20), as VB's `build.Class(level)`. */
    fun levelClass(level: Int): CharacterClass? = levelClasses[level - 1]

    /** The feat slots of one kind (`build.Feat(type)`). */
    fun feats(type: BuildFeatType): ArrayList<BuildFeat> = feats[type.ordinal]

    /** This class's spells, or null before spells are set up (or for a class not in the data). */
    fun spellsFor(characterClass: CharacterClass): BuildClassSpells? {
        for (spells in classSpells) {
            if (spells.characterClass == characterClass) {
                return spells
            }
        }
        return null
    }
}
