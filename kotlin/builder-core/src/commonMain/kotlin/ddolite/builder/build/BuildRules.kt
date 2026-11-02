package ddolite.builder.build

import ddolite.builder.data.BuilderData
import ddolite.builder.data.resolveFeatPointer
import ddolite.builder.model.Bab
import ddolite.builder.model.BonusFeatSlot
import ddolite.builder.model.CharacterClass
import ddolite.builder.model.Feat
import ddolite.builder.model.Tree
import ddolite.builder.model.TreeStyle

/**
 * One class in the build's class split: how many heroic levels it has. Ported from the parts of
 * `ClassSplitType` (basBuildDefs.bas:23) that the rules use; colours and initials come with the screens.
 */
class ClassSplit(val characterClass: CharacterClass, val levels: Int)

/**
 * A feat slot in the order the Feats screen lists them. The parts of VB's `FeatDetailType`
 * (basBuildDefs.bas:73) that the rules use so far.
 *
 * @property effectiveType for alternates and exchanges, the kind of slot they belong to.
 */
class FeatListEntry(val actualType: BuildFeatType, val effectiveType: BuildFeatType, val index: Int, val slot: BuildFeat) {
    val level: Int get() = slot.level
}

/**
 * A tree the leveling guide can use. Ported from the parts of `GuideTreeType` (basBuildDefs.bas:233)
 * that loading and saving use.
 *
 * @property tree the tree in the data, or null if the name isn't found (VB's TreeID 0).
 * @property characterClass the class whose levels open the tree; null for race and global trees.
 * @property display the name the guide shows and saves: the tree's abbreviation, plus
 *   " (<class initials>)" when two classes both have the tree.
 * @property buildGuideTree 1-based position in [BuildGuide.trees], 0 if none.
 * @property buildTree 1-based position in [Build.trees], 0 if none.
 */
class GuideTree(val treeName: String, val tree: Tree?, val characterClass: CharacterClass?, val classLevels: Int) {
    var display = ""
    var duplicate = false
    var maxTier = 0
    var buildGuideTree = 0
    var buildTree = 0
}

/**
 * The build rules that keep a [Build] consistent: base attack bonus, feat slots, spell slots,
 * trees and leveling guide trees. Ported from Source/Modules/Build/basBuild.bas and the parts of
 * basMain.CascadeChanges that call it.
 *
 * VB keeps the feat list and leveling guide trees in globals (`Feat`, `Guide`) next to the global
 * `build`; here they're [featList] and [guideTrees], kept with the build they belong to.
 * Only what loading and saving need is ported so far: errors, display text and the Feats screen's
 * channels come with their screens.
 */
class BuildRules(val data: BuilderData, val build: Build) {
    /** VB's `Feat.List`: the feat slots as last listed by [initFeatList]. */
    var featList: List<FeatListEntry> = emptyList()
        private set

    /** VB's `Guide.Tree`. */
    var guideTrees: List<GuideTree> = emptyList()
        private set

    /**
     * VB's `Guide.TreeLookup`: for each [BuildGuide.trees] entry (1-based, index 0 unused), the
     * 1-based position in [guideTrees], 0 if it isn't there.
     */
    var guideTreeLookup: IntArray = IntArray(0)
        private set

    // ---------------------------------------------------------------- defaults and levels

    /**
     * A new build's settings. Ported from `basDocuments.SetBuildDefaults` (basDocuments.bas:196);
     * [preferred] is VB's `cfg.BuildPoints` setting.
     */
    fun setBuildDefaults(preferred: BuildPoints) {
        build.buildName = "New Build"
        build.maxLevels = MAX_LEVEL
        build.buildPoints = preferred
        for (i in build.includePoints.indices) {
            build.includePoints[i] = true
        }
    }

    /** Heroic levels: 20, or fewer if the build stops earlier. Ported from `GetHeroicLevels` (basBuild.bas:110). */
    fun heroicLevels(): Int = if (build.maxLevels > 19) 20 else build.maxLevels

    /**
     * The classes taken in heroic levels, most levels first (ties in class order). Ported from
     * `GetClassSplit` (basBuild.bas:139) without the colours and initials.
     */
    fun classSplit(): List<ClassSplit> {
        val counts = classLevelCounts(heroicLevels())
        val result = ArrayList<ClassSplit>()
        for (c in data.classes) {
            val levels = counts[c] ?: 0
            if (levels != 0) {
                result.add(ClassSplit(c, levels))
            }
        }
        // VB sorts the (at most three) entries by hand with ClassSplitCompare, which swaps only on "fewer levels".
        if (result.size > 1) {
            compareSplit(result, 0, 1)
        }
        if (result.size == 3) {
            compareSplit(result, 1, 2)
            compareSplit(result, 0, 1)
        }
        return result
    }

    private fun compareSplit(split: ArrayList<ClassSplit>, one: Int, two: Int) {
        if (split[one].levels < split[two].levels) {
            val hold = split[one]
            split[one] = split[two]
            split[two] = hold
        }
    }

    /** Heroic levels per class in levels 1..[upTo]. */
    private fun classLevelCounts(upTo: Int): HashMap<CharacterClass, Int> {
        val counts = HashMap<CharacterClass, Int>()
        for (level in 1..upTo) {
            val c = build.levelClass(level)
            if (c != null) {
                counts[c] = (counts[c] ?: 0) + 1
            }
        }
        return counts
    }

    /**
     * The character level at which [characterClass] reaches [classLevel] class levels; 99 if never.
     * Ported from `GetCharacterLevel` (basBuild.bas:824).
     */
    fun characterLevel(characterClass: CharacterClass, classLevel: Int): Int {
        var count = 0
        for (level in 1..heroicLevels()) {
            if (build.levelClass(level) == characterClass) {
                count++
            }
            if (count == classLevel) {
                return level
            }
        }
        return 99
    }

    /** Everything VB recalculates after a build is opened: [cascadeChanges] with [CascadeChange.ALL]. */
    fun cascadeAll() {
        cascadeChanges(CascadeChange.ALL)
    }

    /**
     * Recalculates what depends on [change]. Ported from the build part of `basMain.CascadeChanges`
     * (basMain.bas:250); refreshing or closing the open windows is the screens' job.
     *
     * Not ported here: `InitBuildSkills` only fills the Skills screen's own grid (the screen
     * builds its grid from the build), and `CheckSlotErrors` marks feat slots in error for the
     * Feats screen, which comes with that screen.
     */
    fun cascadeChanges(change: CascadeChange) {
        val hasClass = build.levelClass(1) != null
        when (change) {
            CascadeChange.ALL, CascadeChange.MAX_LEVEL -> {
                if (hasClass) {
                    calculateBab()
                    initBuildSpells()
                    initBuildFeats()
                    initLevelingGuide()
                }
                initBuildTrees()
            }
            CascadeChange.CLASS -> {
                if (hasClass) {
                    initBuildSpells()
                    initBuildFeats()
                    initLevelingGuide()
                }
                initBuildTrees()
            }
            CascadeChange.RACE -> {
                if (hasClass) {
                    initBuildFeats()
                }
                initBuildTrees()
                initLevelingGuide()
            }
            CascadeChange.ALIGNMENT, CascadeChange.STATS, CascadeChange.SKILL -> {
                // VB: CheckSlotErrors (and InitBuildSkills for stats); see above.
            }
            CascadeChange.FEAT -> {
                if (hasClass) {
                    initBuildSpells()
                }
                initLevelingGuide()
            }
            CascadeChange.ENHANCEMENTS -> initLevelingGuide()
        }
    }

    /**
     * The points a point-buy column has to spend: 28/32/34/36, 4 fewer for Drow except Adventurer.
     * Ported from `GetBuildPoints` (basEnum.bas:281).
     */
    fun buildPointsFor(points: BuildPoints): Int {
        var offset = 0
        val race = build.race
        if (race != null && race.vbIndex == RACE_DROW) {
            offset = -4
        }
        return when (points) {
            BuildPoints.ADVENTURER -> 28
            BuildPoints.CHAMPION -> 32 + offset
            BuildPoints.HERO -> 34 + offset
            BuildPoints.LEGEND -> 36 + offset
        }
    }

    /**
     * Empties the leveling guide. Ported from frmOverview.ClearLevelingGuide (frmOverview.frm:1632),
     * which every change of race or class calls; VB's `Guide` (here [guideTrees]) is rebuilt by
     * the next [initLevelingGuide].
     */
    fun clearLevelingGuide() {
        build.guide.trees.clear()
        build.guide.enhancements.clear()
        guideTrees = emptyList()
        guideTreeLookup = IntArray(0)
    }

    // ---------------------------------------------------------------- BAB

    /** Base attack bonus at every level. Ported from `CalculateBAB` (basBuild.bas:351). */
    fun calculateBab() {
        val classLevels = HashMap<CharacterClass, Int>()
        var bab: Bab? = null
        var total = 0
        for (level in 1..HEROIC_LEVELS) {
            val c = build.levelClass(level)
            if (c != null) {
                bab = c.bab
                val classLevel = (classLevels[c] ?: 0) + 1
                classLevels[c] = classLevel
                if (babIncreases(bab, classLevel)) {
                    total++
                }
                build.bab[level - 1] = total
            }
        }
        // Epic levels use the last heroic level's class progression (VB reuses enBAB).
        for (level in HEROIC_LEVELS + 1..MAX_LEVEL) {
            if (babIncreases(bab, level)) {
                total++
            }
            if (total > 25) {
                total = 25 // capped at 25 (U56)
            }
            build.bab[level - 1] = total
        }
    }

    /** True if BAB goes up at [level] (class level, or character level above 20). Ported from `GetBAB` (basBuild.bas:377). */
    private fun babIncreases(bab: Bab?, level: Int): Boolean {
        when (level) {
            21, 23, 25, 27, 29 -> return true
            22, 24, 26, 28, 30 -> return false
        }
        return when (bab) {
            Bab.FULL -> true
            Bab.THREE_QUARTERS -> (level - 1) % 4 != 0
            Bab.HALF -> level % 2 == 0
            null -> false
        }
    }

    // ---------------------------------------------------------------- feat slots

    /** Sets up every kind of feat slot. Ported from `InitBuildFeats` (basBuild.bas:480). */
    fun initBuildFeats() {
        initGrantedFeats()
        initStandardFeats()
        initRaceFeats()
        initClassFeats()
        initDeityFeats()
        initFeatList()
    }

    /**
     * Granted feats: auto-granted, race and class feats, and a dwarf's first Dwarven Axe.
     * Ported from `InitGrantedFeats` (basBuild.bas:490).
     */
    private fun initGrantedFeats() {
        if (build.levelClass(1) == null) {
            return
        }
        val granted = build.feats(BuildFeatType.GRANTED)
        granted.clear()
        var dwarvenAxe = build.race?.name == "Dwarf"
        val classLevels = HashMap<CharacterClass, Int>()
        for (level in 1..HEROIC_LEVELS) {
            grantAutoFeats(level, BuildFeatSource.HEROIC)
            val race = build.race
            if (race != null) {
                for (feat in race.grantedFeats) {
                    if (feat.level == level) {
                        grantFeat("Feat: " + feat.featName, level, BuildFeatSource.RACE)
                    }
                }
            }
            val c = build.levelClass(level)
            if (c != null) {
                val classLevel = (classLevels[c] ?: 0) + 1
                classLevels[c] = classLevel
                for (feat in c.grantedFeats) {
                    if (feat.level == classLevel) {
                        grantFeat("Feat: " + feat.featName, level, BuildFeatSource.CLASS)
                    }
                }
            }
            if (dwarvenAxe && c != null) {
                when (c.name) {
                    "Barbarian", "Fighter", "Paladin", "Ranger" -> {
                        grantDwarvenAxe(level)
                        dwarvenAxe = false
                    }
                }
            }
        }
        for (level in HEROIC_LEVELS + 1..30) {
            grantAutoFeats(level, BuildFeatSource.EPIC)
        }
        for (level in 31..MAX_LEVEL) {
            grantAutoFeats(level, BuildFeatSource.LEGEND)
        }
    }

    /** AutoGrantedFeats.txt entries for [level]. VB's pointer never sets a selector. */
    private fun grantAutoFeats(level: Int, source: BuildFeatSource) {
        for (auto in data.autoGrantedFeats) {
            if (auto.level == level) {
                val feat = data.findFeat(auto.featName)
                if (feat != null) {
                    addGranted(feat.name, 0, level, source)
                }
            }
        }
    }

    /** A race or class granted feat, resolved as basProcess resolves it at load. Ported from `GrantFeat` (basBuild.bas:589). */
    private fun grantFeat(raw: String, level: Int, source: BuildFeatSource) {
        val target = resolveFeatPointer(raw) { name -> data.findFeat(name) }.target
        if (target == null) {
            return
        }
        val feat = data.findFeat(target.featName) ?: return
        addGranted(feat.name, selectorNumber(feat, target.selectorName), level, source)
    }

    private fun addGranted(featName: String, selector: Int, level: Int, source: BuildFeatSource) {
        val slot = BuildFeat(BuildFeatType.GRANTED)
        slot.featName = featName
        slot.selector = selector
        slot.source = source
        slot.level = level
        build.feats(BuildFeatType.GRANTED).add(slot)
    }

    /** Exotic Weapon: Dwarven Axe. Ported from `GrantDwarvenAxe` (basBuild.bas:609). */
    private fun grantDwarvenAxe(level: Int) {
        val feat = data.findFeat("Exotic Weapon") ?: return
        val selector = selectorNumber(feat, "Dwarven Axe")
        if (selector != 0) {
            addGranted(feat.name, selector, level, BuildFeatSource.RACE)
        }
    }

    /**
     * The heroic, epic, destiny and legendary feat slots. Ported from `InitStandardFeats`
     * (basBuild.bas:631).
     *
     * VB quirk, kept: one index counts through both kinds, so the Legend slot is the 15th entry of
     * the Legend list (the first 14 stay blank at level 0) and the 15th Standard entry is never set.
     */
    private fun initStandardFeats() {
        allocateFeatSlots(BuildFeatType.STANDARD, 19, false)
        var index = 0
        val heroic = intArrayOf(1, 3, 6, 9, 12, 15, 18)
        for (level in heroic) {
            index = initBuildFeatSlot(BuildFeatType.STANDARD, BuildFeatSource.HEROIC, index, level, 0)
        }
        index = initBuildFeatSlot(BuildFeatType.STANDARD, BuildFeatSource.EPIC, index, 21, 0)
        index = initBuildFeatSlot(BuildFeatType.STANDARD, BuildFeatSource.DESTINY, index, 22, 0)
        index = initBuildFeatSlot(BuildFeatType.STANDARD, BuildFeatSource.EPIC, index, 24, 0)
        index = initBuildFeatSlot(BuildFeatType.STANDARD, BuildFeatSource.DESTINY, index, 25, 0)
        index = initBuildFeatSlot(BuildFeatType.STANDARD, BuildFeatSource.EPIC, index, 27, 0)
        index = initBuildFeatSlot(BuildFeatType.STANDARD, BuildFeatSource.DESTINY, index, 28, 0)
        index = initBuildFeatSlot(BuildFeatType.STANDARD, BuildFeatSource.EPIC, index, 30, 0)
        index = initBuildFeatSlot(BuildFeatType.LEGEND, BuildFeatSource.LEGEND, index, 30, 0)
        index = initBuildFeatSlot(BuildFeatType.STANDARD, BuildFeatSource.DESTINY, index, 31, 0)
        index = initBuildFeatSlot(BuildFeatType.STANDARD, BuildFeatSource.EPIC, index, 33, 0)
        index = initBuildFeatSlot(BuildFeatType.STANDARD, BuildFeatSource.DESTINY, index, 34, 0)
        initBuildFeatSlot(BuildFeatType.STANDARD, BuildFeatSource.EPIC, index, 36, 0)
    }

    /**
     * Makes the list of [type] exactly [count] long, keeping the first entries (and emptying it
     * first if [eraseFirst]). Ported from `AllocateFeatSlots` (basBuild.bas:687).
     */
    private fun allocateFeatSlots(type: BuildFeatType, count: Int, eraseFirst: Boolean) {
        val list = build.feats(type)
        if (eraseFirst) {
            list.clear()
        }
        resizeFeats(list, type, count)
    }

    private fun resizeFeats(list: ArrayList<BuildFeat>, type: BuildFeatType, count: Int) {
        while (list.size > count) {
            list.removeAt(list.size - 1)
        }
        while (list.size < count) {
            list.add(BuildFeat(type))
        }
    }

    /**
     * Sets up slot number [index] + 1 of [type] (growing the list if needed), keeping the feat in it
     * unless it's a class or race bonus feat the build can't have. Returns the new index (VB passes it
     * ByRef). Ported from `InitBuildFeatSlot` (basBuild.bas:700).
     */
    private fun initBuildFeatSlot(type: BuildFeatType, source: BuildFeatSource, index: Int, level: Int, classLevel: Int): Int {
        val newIndex = index + 1
        val list = build.feats(type)
        if (list.size < newIndex) {
            resizeFeats(list, type, newIndex)
        }
        val slot = list[newIndex - 1]
        slot.level = level
        slot.classLevel = classLevel
        slot.type = type
        slot.source = source
        if (type.classIndex >= 0) {
            val feat = findFeatOrNull(slot.featName)
            val c = build.buildClasses[type.classIndex]
            if (c != null && feat != null) {
                var valid = false
                if (listsName(feat.classBonus, c.name)) {
                    valid = true
                } else if (feat.classOnly) {
                    valid = listsName(feat.classOnlyClasses, c.name)
                }
                if (!valid) {
                    slot.featName = ""
                    slot.selector = 0
                }
            }
        } else if (type == BuildFeatType.RACE) {
            val feat = findFeatOrNull(slot.featName)
            val race = build.race
            if (race != null && feat != null) {
                var valid = false
                val raceBonus = feat.raceBonus
                if (raceBonus != null) {
                    for (name in raceBonus) {
                        if (data.findRace(name) == race) {
                            valid = true
                        }
                    }
                }
                if (!valid) {
                    slot.featName = ""
                    slot.selector = 0
                }
            }
        }
        return newIndex
    }

    /** The racial bonus feat slot. Ported from `InitRaceFeats` (basBuild.bas:747). */
    private fun initRaceFeats() {
        val list = build.feats(BuildFeatType.RACE)
        if (build.race?.bonusFeat == true) {
            allocateFeatSlots(BuildFeatType.RACE, 1, false)
            initBuildFeatSlot(BuildFeatType.RACE, BuildFeatSource.RACE, 0, 1, 0)
        } else if (list.size == 1) {
            list[0].level = 99
        }
    }

    /**
     * Class bonus feat slots for each of the three build classes, keeping the feats of slots at the
     * same level and class level. Ported from `InitClassFeats` (basBuild.bas:758) and
     * `MapClassFeats` (:838).
     */
    private fun initClassFeats() {
        for (i in 0 until 3) {
            val c = build.buildClasses[i] ?: break
            val type = BuildFeatType.entries[BuildFeatType.CLASS1.ordinal + i]
            val old = ArrayList<BuildFeat>()
            for (slot in build.feats(type)) {
                old.add(slot.copy())
            }
            allocateFeatSlots(type, classBonusFeatCount(c), true)
            var index = 0
            for (classLevel in 1..HEROIC_LEVELS) {
                val source = classBonusFeatSource(c, classLevel)
                if (source != null) {
                    index = initBuildFeatSlot(type, source, index, characterLevel(c, classLevel), classLevel)
                }
            }
            for (oldSlot in old) {
                for (slot in build.feats(type)) {
                    if (slot.level == oldSlot.level && slot.classLevel == oldSlot.classLevel) {
                        slot.featName = oldSlot.featName
                        slot.selector = oldSlot.selector
                        break
                    }
                }
            }
        }
    }

    /** Ported from `ClassBonusFeatCount` (basBuild.bas:790). */
    private fun classBonusFeatCount(c: CharacterClass): Int {
        var count = 0
        for (classLevel in 1..HEROIC_LEVELS) {
            if (classBonusFeatSource(c, classLevel) != null) {
                count++
            }
        }
        return count
    }

    /**
     * The kind of class bonus feat at [classLevel], or null. Ported from `IsClassBonusFeat`
     * (basBuild.bas:800).
     *
     * VB quirk, kept: the check meant to drop the deity classes' slots only takes effect at class
     * levels 1, 3, 6, 12 and 20, because at other levels VB leaves with `Exit Function` after
     * already setting the result.
     */
    private fun classBonusFeatSource(c: CharacterClass, classLevel: Int): BuildFeatSource? {
        val source = when (c.bonusFeatAt(classLevel)) {
            BonusFeatSlot.BONUS -> BuildFeatSource.CLASS
            BonusFeatSlot.CLASS_ONLY -> BuildFeatSource.CLASS_ONLY
            BonusFeatSlot.NONE -> return null
        }
        when (classLevel) {
            1, 3, 6, 12, 20 -> {}
            else -> return source
        }
        when (c.name) {
            "Cleric", "Favored Soul", "Paladin", "Dark Apostate" -> return null
        }
        return source
    }

    /**
     * Deity feat slots: Cleric, Paladin and Dark Apostate at class levels 1 and 6, Favored Soul at
     * 1, 6 and 20. Feats stay in their position in the list. Ported from `InitDeityFeats`
     * (basBuild.bas:863).
     */
    private fun initDeityFeats() {
        compactDeityFeats()
        val classLevels = HashMap<CharacterClass, Int>()
        var count = 0
        for (level in 1..HEROIC_LEVELS) {
            val c = build.levelClass(level) ?: continue
            val classLevel = (classLevels[c] ?: 0) + 1
            classLevels[c] = classLevel
            val slot = when (c.name) {
                "Cleric", "Paladin", "Dark Apostate" -> classLevel == 1 || classLevel == 6
                "Favored Soul" -> classLevel == 1 || classLevel == 6 || classLevel == 20
                else -> false
            }
            if (slot) {
                count = initBuildFeatSlot(BuildFeatType.DEITY, BuildFeatSource.DEITY, count, level, classLevel)
            }
        }
        val list = build.feats(BuildFeatType.DEITY)
        if (list.size != count) {
            resizeFeats(list, BuildFeatType.DEITY, count)
        }
    }

    /**
     * Moves deity feats with a level to the front, over blank slots: builds saved by v2.4 and earlier
     * have a blank slot before each real one. Ported from `CompactDeityFeats` (basBuild.bas:935),
     * including its first step, which treats slots at class level 3, 6, 12 or 20 as blank.
     */
    private fun compactDeityFeats() {
        val list = build.feats(BuildFeatType.DEITY)
        for (slot in list) {
            val cl = slot.classLevel
            if (cl == 3 || cl == 6 || cl == 12 || cl == 20) {
                slot.level = 0
            }
        }
        // VB's loop, with 1-based i and j (j = 0: no blank slot found yet).
        var j = 0
        for (i in 1..list.size) {
            if (j == 0 && list[i - 1].level == 0) {
                j = i
            } else if (j != 0 && list[i - 1].level != 0) {
                list[j - 1] = list[i - 1]
                list[i - 1] = BuildFeat(BuildFeatType.GRANTED) // VB's blank BuildFeatType has Type 0
                j++
                while (j <= i) {
                    if (list[j - 1].level == 0) {
                        break
                    }
                    j++
                }
            }
        }
    }

    /**
     * Lists the feat slots within the build's levels, sorted by level and then kind. Ported from
     * the parts of `InitFeatList` (basBuild.bas:1002), `AddFeatToList` (:1141) and `SortFeatList`
     * (:1267) the rules use so far.
     */
    fun initFeatList() {
        val list = ArrayList<FeatListEntry>()
        if (build.levelClass(1) == null) {
            featList = list
            return
        }
        for (type in BuildFeatType.entries) {
            val slots = build.feats(type)
            for (i in slots.indices) {
                val slot = slots[i]
                if (slot.level < 1 || slot.level > build.maxLevels) {
                    continue
                }
                var effective = type
                if (type == BuildFeatType.ALTERNATE || type == BuildFeatType.EXCHANGE) {
                    effective = slot.childType ?: type
                }
                list.add(FeatListEntry(type, effective, i, slot))
            }
        }
        // Insertion sort, as SortFeatList: by level, then by effective kind; equal entries keep their order.
        for (i in 1 until list.size) {
            val swap = list[i]
            var j = i
            while (j > 0 && compareSlot(swap, list[j - 1]) < 0) {
                list[j] = list[j - 1]
                j--
            }
            list[j] = swap
        }
        featList = list
    }

    /** Ported from `CompareSlot` (basBuild.bas:1284). */
    private fun compareSlot(one: FeatListEntry, two: FeatListEntry): Int {
        if (one.level != two.level) {
            return if (one.level < two.level) -1 else 1
        }
        return one.effectiveType.ordinal.compareTo(two.effectiveType.ordinal)
    }

    // ---------------------------------------------------------------- spells

    /**
     * Spell slots for every class with levels in the build, keeping chosen spells and free slots.
     * Ported from `InitBuildSpells` (basBuild.bas:1952).
     */
    fun initBuildSpells() {
        if (build.levelClass(1) == null) {
            return
        }
        initBuildSpellsClassLevels()
        for (spells in build.classSpells) {
            val c = spells.characterClass
            if (spells.classLevels > 0 && c.canCastSpell[1] > 0) {
                initMaxSpellLevel(spells)
                for (spellLevel in 1..spells.maxSpellLevel) {
                    initSpellSlots(c, spells.classLevels, spellLevel, spells.level(spellLevel))
                    initSpellSlotLevels(c, spellLevel, spells.level(spellLevel), spells.classLevels)
                }
            }
        }
        val cleric = spellsNamed("Cleric")
        if (cleric != null && cleric.classLevels > 0) {
            addClericCures(cleric)
        }
        val warlock = spellsNamed("Warlock")
        if (warlock != null && warlock.classLevels > 1) {
            addWarlockPactSpells(warlock)
        }
    }

    private fun spellsNamed(className: String): BuildClassSpells? {
        val c = data.findClass(className) ?: return null
        return build.spellsFor(c)
    }

    /**
     * Class levels per class and the first level for each spell level. Ported from
     * `InitBuildSpellsClassLevels` (basBuild.bas:1980). VB also enables the Edit menu's Spells item here.
     */
    private fun initBuildSpellsClassLevels() {
        for (i in build.canCastSpell.indices) {
            build.canCastSpell[i] = 0
        }
        // ReDim Preserve build.Spell(1 To ceClasses - 1): one entry per class, keeping existing ones.
        if (build.classSpells.size != data.classes.size) {
            val old = ArrayList(build.classSpells)
            build.classSpells.clear()
            for (c in data.classes) {
                var existing: BuildClassSpells? = null
                for (spells in old) {
                    if (spells.characterClass == c) {
                        existing = spells
                    }
                }
                build.classSpells.add(existing ?: BuildClassSpells(c))
            }
        }
        for (spells in build.classSpells) {
            spells.classLevels = 0
            spells.maxSpellLevel = 0
        }
        for (level in 1..heroicLevels()) {
            val c = build.levelClass(level) ?: continue
            val spells = build.spellsFor(c) ?: continue
            spells.classLevels++
            for (spellLevel in 0..9) {
                if (build.canCastSpell[spellLevel] == 0 && spells.classLevels == c.canCastSpell[spellLevel]) {
                    build.canCastSpell[spellLevel] = level
                }
            }
        }
    }

    /** The highest spell level with slots at the class's levels. Ported from `InitBuildSpellsMaxSpellLevel` (basBuild.bas:2008). */
    private fun initMaxSpellLevel(spells: BuildClassSpells) {
        val c = spells.characterClass
        for (spellLevel in c.maxSpellLevel downTo 1) {
            if (c.spellSlots(spells.classLevels, spellLevel) > 0) {
                spells.maxSpellLevel = spellLevel
                // ReDim Preserve .Level(1 To spellLevel)
                while (spells.levels.size > spellLevel) {
                    spells.levels.removeAt(spells.levels.size - 1)
                }
                while (spells.levels.size < spellLevel) {
                    spells.levels.add(BuildSpellLevel())
                }
                return
            }
        }
    }

    /** Ported from `InitBuildSpellsSlots` (basBuild.bas:2022). */
    private fun initSpellSlots(c: CharacterClass, classLevels: Int, spellLevel: Int, level: BuildSpellLevel) {
        level.baseSlots = c.spellSlots(classLevels, spellLevel)
        level.mandatory = SpellSlotType.STANDARD
        if (c.name == "Cleric") {
            if (spellLevel < 9) {
                level.mandatory = SpellSlotType.CLERIC_CURE
            }
        } else if (c.name == "Warlock") {
            if (classLevels >= warlockPactLevel(spellLevel)) {
                level.mandatory = SpellSlotType.WARLOCK_PACT
            }
        }
        if (level.mandatory != SpellSlotType.STANDARD) {
            level.baseSlots++
        }
        val count = level.baseSlots + level.freeSlots
        while (level.slots.size > count) {
            level.slots.removeAt(level.slots.size - 1)
        }
        while (level.slots.size < count) {
            level.slots.add(BuildSpellSlot())
        }
    }

    /** Slot kinds and the character level each slot is gained. Ported from `InitBuildSpellsSlotLevels` (basBuild.bas:2039). */
    private fun initSpellSlotLevels(c: CharacterClass, spellLevel: Int, level: BuildSpellLevel, maxClassLevels: Int) {
        var effectiveSlot = 1
        for (slotNumber in 1..level.slots.size) {
            var step = 0
            val type = if (slotNumber <= level.freeSlots) {
                SpellSlotType.FREE
            } else if (slotNumber == 1 && level.mandatory == SpellSlotType.CLERIC_CURE) {
                SpellSlotType.CLERIC_CURE
            } else if (slotNumber == level.slots.size && level.mandatory == SpellSlotType.WARLOCK_PACT) {
                SpellSlotType.WARLOCK_PACT
            } else {
                step = 1
                SpellSlotType.STANDARD
            }
            val slot = level.slots[slotNumber - 1]
            slot.type = type
            val classLevel = if (type == SpellSlotType.WARLOCK_PACT) {
                warlockPactLevel(spellLevel)
            } else {
                spellSlotClassLevel(c, spellLevel, effectiveSlot, maxClassLevels)
            }
            slot.level = characterLevel(c, classLevel)
            effectiveSlot += step
        }
    }

    /** The first class level with at least [slot] slots of [spellLevel]; 0 if none. Ported from `GetSpellSlotClassLevel` (basBuild.bas:2076). */
    private fun spellSlotClassLevel(c: CharacterClass, spellLevel: Int, slot: Int, maxClassLevels: Int): Int {
        for (classLevel in 1..maxClassLevels) {
            if (c.spellSlots(classLevel, spellLevel) >= slot) {
                return classLevel
            }
        }
        return 0
    }

    /** The cleric's mandatory cure spell in slot 1 of spell levels 1-8. Ported from `AddClericCures` (basBuild.bas:2087). */
    private fun addClericCures(spells: BuildClassSpells) {
        val max = if (spells.maxSpellLevel > 7) 8 else spells.maxSpellLevel
        for (spellLevel in 1..max) {
            spells.level(spellLevel).slots[0].spell = spells.characterClass.mandatorySpells[spellLevel - 1]
        }
    }

    /**
     * Fills the warlock's pact slots from the Pact feat in [featList], or empties them if there's none.
     * Ported from `AddWarlockPactSpells` (basBuild.bas:2099).
     *
     * VB quirk, kept: this reads the feat list as it was last listed, and InitBuildSpells runs before
     * InitBuildFeats relists it; see [ddolite.builder.build.BuildFileReader].
     */
    private fun addWarlockPactSpells(spells: BuildClassSpells) {
        var pactName = ""
        for (entry in featList) {
            val feat = findFeatOrNull(entry.slot.featName)
            val selector = entry.slot.selector
            if (feat != null && selector != 0 && feat.name == "Pact") {
                pactName = feat.selectors[selector - 1].name
                break
            }
        }
        val warlock = spells.characterClass
        var pactIndex = -1
        if (pactName.isNotEmpty()) {
            for (i in warlock.pacts.indices) {
                if (warlock.pacts[i].name == pactName) {
                    pactIndex = i
                    break
                }
            }
        }
        for (spellLevel in 1..spells.maxSpellLevel) {
            val level = spells.level(spellLevel)
            if (level.slots.isNotEmpty()) {
                val slot = level.slots[level.slots.size - 1]
                if (slot.type == SpellSlotType.WARLOCK_PACT) {
                    slot.spell = if (pactIndex < 0) "" else warlock.pacts[pactIndex].spells[spellLevel - 1]
                }
            }
        }
    }

    /** The warlock class level that gives a pact spell of [spellLevel]. Ported from `WarlockPactLevel` (basBuild.bas:2177). */
    fun warlockPactLevel(spellLevel: Int): Int = when (spellLevel) {
        1 -> 2
        2 -> 5
        3 -> 9
        4 -> 14
        5 -> 17
        6 -> 19
        else -> 0
    }

    /** True if [spell] is one of the class's free spells (Classes.txt `FreeSpells:`). Ported from `CheckFree` (basBuild.bas:2188). */
    fun isFreeSpell(c: CharacterClass, spell: String): Boolean = c.freeSpells.contains(spell)

    // ---------------------------------------------------------------- trees

    /**
     * Adds the race tree if missing, drops trees the build can't use, and sets each tree's class
     * levels. Ported from `InitBuildTrees` (basBuild.bas:2254).
     */
    fun initBuildTrees() {
        // VB indexes lngTreeLevels by TreeID, with 0 for a name that isn't found.
        val treeLevels = HashMap<Tree?, Int>()
        val race = build.race
        if (race != null) {
            treeLevels[data.findTree(race.name)] = build.maxLevels
            if (findBuildTree(race.name) == 0) {
                val tree = BuildTree(race.name)
                tree.treeType = TreeStyle.RACE
                build.trees.add(tree)
            }
        }
        val classLevels = classLevelCounts(heroicLevels())
        for (c in build.buildClasses) {
            if (c == null) {
                continue
            }
            val levels = classLevels[c] ?: 0
            for (name in c.trees) {
                val tree = data.findTree(name)
                if ((treeLevels[tree] ?: 0) < levels) {
                    treeLevels[tree] = levels
                }
            }
        }
        for (i in build.trees.size - 1 downTo 0) {
            val tree = data.findTree(build.trees[i].treeName)
            if (tree != null && (tree.style == TreeStyle.RACE_CLASS || tree.style == TreeStyle.GLOBAL)) {
                treeLevels[tree] = build.maxLevels
            }
            val levels = treeLevels[tree] ?: 0
            if (tree == null || levels == 0) {
                build.trees.removeAt(i)
            } else {
                build.trees[i].classLevels = levels
            }
        }
    }

    /** 1-based position of the build tree called [treeName], 0 if none. Ported from `FindBuildTree` (basBuild.bas:2317). */
    fun findBuildTree(treeName: String): Int {
        for (i in build.trees.indices) {
            if (build.trees[i].treeName == treeName) {
                return i + 1
            }
        }
        return 0
    }

    // ---------------------------------------------------------------- leveling guide

    /**
     * The trees the leveling guide can use. Ported from `InitLevelingGuide` (basBuild.bas:3140)
     * and `InitGuideTrees` (:3145); the step costs, levels and errors of `InitGuideEnhancements`
     * come with the Leveling Guide screen.
     */
    fun initLevelingGuide() {
        val trees = ArrayList<GuideTree>()
        guideTrees = trees
        guideTreeLookup = IntArray(0)
        val race = build.race
        if (build.levelClass(1) == null || race == null) {
            return
        }
        addGuideTree(trees, race.name, null, build.maxLevels)
        val classLevels = classLevelCounts(heroicLevels())
        for (c in data.classes) {
            val levels = classLevels[c] ?: 0
            if (levels > 0) {
                for (name in c.trees) {
                    addGuideTree(trees, name, c, levels)
                }
            }
        }
        for (name in race.trees) {
            addGuideTree(trees, name, null, build.maxLevels)
        }
        for (tree in data.trees) {
            if (tree.style == TreeStyle.GLOBAL) {
                addGuideTree(trees, tree.name, null, build.maxLevels)
            }
        }
        val buildGuideTrees = build.guide.trees
        if (buildGuideTrees.isNotEmpty()) {
            val lookup = IntArray(buildGuideTrees.size + 1)
            for (i in buildGuideTrees.indices) {
                for (j in trees.indices) {
                    if (buildGuideTrees[i].treeName == trees[j].treeName && buildGuideTrees[i].characterClass == trees[j].characterClass) {
                        lookup[i + 1] = j + 1
                        break
                    }
                }
            }
            guideTreeLookup = lookup
        }
    }

    /**
     * Ported from `AddGuideTree` (basBuild.bas:3198). A tree that's already listed is listed again for
     * the new class, and both entries show the class initials.
     */
    private fun addGuideTree(trees: ArrayList<GuideTree>, treeName: String, c: CharacterClass?, classLevels: Int) {
        val tree = data.findTree(treeName)
        var duplicate = false
        for (existing in trees) {
            if (existing.treeName == treeName) {
                existing.duplicate = true
                existing.display = abbreviationOf(existing.tree) + " (" + initial3(existing.characterClass) + ")"
                duplicate = true
                break
            }
        }
        val guideTree = GuideTree(treeName, tree, c, classLevels)
        var maxTier = classLevels
        if (maxTier > 5) {
            maxTier = 5
        }
        if (maxTier == 5 && (tree?.style == TreeStyle.RACE || build.maxLevels < 12)) {
            maxTier = 4
        }
        guideTree.maxTier = maxTier
        guideTree.duplicate = duplicate
        guideTree.display = abbreviationOf(tree)
        if (duplicate) {
            guideTree.display = guideTree.display + " (" + initial3(c) + ")"
        }
        val buildGuideTrees = build.guide.trees
        for (i in buildGuideTrees.indices) {
            if (buildGuideTrees[i].treeName == treeName && buildGuideTrees[i].characterClass == c) {
                guideTree.buildGuideTree = i + 1
            }
        }
        for (i in build.trees.indices) {
            if (build.trees[i].treeName == treeName && build.trees[i].source == c) {
                guideTree.buildTree = i + 1
            }
        }
        trees.add(guideTree)
    }

    private fun abbreviationOf(tree: Tree?): String = tree?.abbreviation ?: ""

    // ---------------------------------------------------------------- lookups

    /** The feat called exactly [name]; null for "" or an unknown name (VB's `SeekFeat` returning 0). */
    fun findFeatOrNull(name: String): Feat? {
        if (name.isEmpty()) {
            return null
        }
        return data.findFeat(name)
    }

    private fun listsName(names: List<String>?, name: String): Boolean {
        if (names == null) {
            return false
        }
        for (n in names) {
            if (n.equals(name, ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}

/** The 1-based number of the selector called [selectorName] (exact), 0 if none. */
fun selectorNumber(feat: Feat, selectorName: String?): Int {
    if (selectorName == null) {
        return 0
    }
    for (i in feat.selectors.indices) {
        if (feat.selectors[i].name == selectorName) {
            return i + 1
        }
    }
    return 0
}

/**
 * A class's fourth initials (`db.Class(c).Initial(3)`), used to tell duplicate guide trees apart;
 * "" for none or for a class whose data gives only one initial (where VB stops with an error).
 */
internal fun initial3(c: CharacterClass?): String {
    if (c == null || c.initials.size < 4) {
        return ""
    }
    return c.initials[3]
}
