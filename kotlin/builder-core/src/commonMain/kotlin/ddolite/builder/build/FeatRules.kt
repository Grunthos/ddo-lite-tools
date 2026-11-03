package ddolite.builder.build

import ddolite.builder.model.AbilityTarget
import ddolite.builder.model.CharacterClass
import ddolite.builder.model.ClassLevel
import ddolite.builder.model.Feat
import ddolite.builder.model.FeatChannel
import ddolite.builder.model.FeatGroup
import ddolite.builder.model.FeatTarget
import ddolite.builder.model.PointerStyle
import ddolite.builder.model.Race
import ddolite.builder.model.RaceRequirement
import ddolite.builder.model.RaceRequirementMode
import ddolite.builder.model.RaceType
import ddolite.builder.model.Requirement
import ddolite.builder.model.SelectorStyle
import ddolite.builder.model.Skill

/**
 * The tabs of the Feats screen. Ported from `FeatChannelEnum` (basEnum.bas:207), from
 * `fceSelected` on (VB's `fceUnknown` is null here): every slot is on Selected or Granted, and
 * with "Channels" on, on its own channel's tab too. The middle entries match [FeatChannel].
 */
enum class FeatTab {
    SELECTED,
    GENERAL,
    RACIAL,
    WARLOCK,
    MONK,
    ROGUE,
    FAVORED_ENEMY,
    WILD_SHAPE,
    CLERIC,
    FAVORED_SOUL,
    ENERGY,
    DEITY,
    GRANTED;

    /**
     * The tab's caption. Ported from `GetFeatChannelName` (basEnum.bas:658): the racial tab is
     * named for the race's bonus feats.
     */
    fun caption(race: Race?): String = when (this) {
        SELECTED -> "Selected"
        GENERAL -> "General"
        RACIAL -> when (race?.name) {
            "Half-Elf" -> "Dilettante"
            "Dragonborn" -> "Dragon"
            "Aasimar" -> "Bond"
            else -> "Racial"
        }
        WARLOCK -> "Pact"
        MONK -> "Monk"
        ROGUE -> "Rogue"
        FAVORED_ENEMY -> "Ranger"
        WILD_SHAPE -> "Druid"
        CLERIC -> "Domain"
        FAVORED_SOUL -> "Fav Soul"
        ENERGY -> "Energy"
        DEITY -> "Deity"
        GRANTED -> "Granted"
    }

    companion object {
        /** The tab for a feat's channel. */
        fun of(channel: FeatChannel): FeatTab = valueOf(channel.name)

        /** `GetFeatChannelID` (basEnum.bas:682): a caption back to its tab; null for anything else. */
        fun find(caption: String): FeatTab? = when (caption) {
            "Selected" -> SELECTED
            "General" -> GENERAL
            "Racial", "Dragon", "Dilettante", "Bond" -> RACIAL
            "Pact" -> WARLOCK
            "Monk" -> MONK
            "Rogue" -> ROGUE
            "Ranger" -> FAVORED_ENEMY
            "Druid" -> WILD_SHAPE
            "Domain" -> CLERIC
            "Fav Soul" -> FAVORED_SOUL
            "Energy" -> ENERGY
            "Deity" -> DEITY
            "Granted" -> GRANTED
            else -> null
        }
    }
}

/**
 * How a feat slot looks while choosing or dragging a feat. Ported from `DropStateEnum`
 * (Source/Controls/userSlot.ctl:46): the feat can't go there, can go there, can go there but
 * with an error (a requirement not met yet), or the slot is related to the chosen one.
 */
enum class DropState { DEFAULT, CAN_DROP, CAN_DROP_ERROR, SUBORDINATE }

/** The result of [FeatRules.checkFeatSlot]: VB's return value and the `gstrError` it leaves. */
class SlotCheck(val state: DropState, val error: String)

/**
 * Which feats and selectors are taken by a level. Ported from `FeatTakenType` (basBuildDefs.bas)
 * as filled by `IdentifyTakenFeats`: indexed by feat name instead of VB's feat number.
 */
class TakenFeats {
    /** Times taken and the selectors taken (index 1..n; VB's `Selector()`), per feat name. */
    class Taken(selectors: Int) {
        var times = 0
        val selectors = BooleanArray(selectors + 1)
        var selectorsTaken = 0
    }

    private val byName = HashMap<String, Taken>()

    /** The record for [feat]; an empty one for a feat not taken (or null, VB's feat 0). */
    fun of(feat: Feat?): Taken {
        if (feat == null) {
            return Taken(0)
        }
        return byName[feat.name] ?: Taken(feat.selectors.size)
    }

    internal fun record(feat: Feat): Taken {
        var taken = byName[feat.name]
        if (taken == null) {
            taken = Taken(feat.selectors.size)
            byName[feat.name] = taken
        }
        return taken
    }

    /** True if [feat]'s selector [selector] (1-based) is taken; false for a selector it doesn't have. */
    fun selectorTaken(feat: Feat?, selector: Int): Boolean {
        val taken = of(feat)
        if (selector < 1 || selector >= taken.selectors.size) {
            return false
        }
        return taken.selectors[selector]
    }
}

/**
 * One line of the Feats screen's details box. [link] is the wiki address of a description's last
 * line, which ends "(Wiki)"; [error] lines are drawn in the error colour.
 */
class DetailLine(val text: String, val link: String?, val error: Boolean)

/**
 * Builds the lines of a details box as VB's userDetails control does (Source/Controls/userDetails.ctl):
 * AddText, AddErrorText (with a blank line before), AddDescrip (with a blank line after), and
 * DrawText's dropping of blank lines at the start and end.
 */
class DetailsBuilder {
    private val lines = ArrayList<DetailLine>()

    /** AddText (:58). */
    fun addText(text: String) {
        lines.add(DetailLine(text, null, false))
    }

    /** AddErrorText (:64): a blank line, then [text] in the error colour. */
    fun addErrorText(text: String) {
        lines.add(DetailLine("", null, false))
        lines.add(DetailLine(text, null, true))
    }

    /**
     * AddDescrip (:71): the description is split at "}"; a "{x" inside a part becomes " x " (a
     * bullet), and the last part gets " (Wiki)" and the link. A blank line follows.
     */
    fun addDescrip(description: String, link: String) {
        val parts = description.split("}")
        for (i in parts.indices) {
            var part = parts[i].trim()
            val pos = part.indexOf("{")
            if (pos >= 0) {
                val bullet = part.substring(pos + 1, minOf(pos + 2, part.length))
                part = " " + bullet + " " + part.substring(minOf(pos + 2, part.length)).trim()
            }
            var lineLink: String? = null
            if (link.isNotEmpty()) {
                lineLink = link
                if (i == parts.size - 1) {
                    part = part + " (Wiki)"
                }
            }
            lines.add(DetailLine(part, lineLink, false))
        }
        lines.add(DetailLine("", null, false))
    }

    /**
     * The lines without the blank ones at the start and end (DrawText, :124). Only the last line
     * with a link keeps it: the control has one link label, which ProcessLines puts on the last
     * linked line, so an earlier "(Wiki)" is plain text.
     */
    fun build(): List<DetailLine> {
        var first = 0
        while (first < lines.size && lines[first].text.isEmpty()) {
            first++
        }
        var last = lines.size - 1
        while (last >= first && lines[last].text.isEmpty()) {
            last--
        }
        var linked = -1
        for (i in first..last) {
            if (lines[i].link != null) {
                linked = i
            }
        }
        val result = ArrayList<DetailLine>()
        for (i in first..last) {
            val line = lines[i]
            if (line.link != null && i != linked) {
                result.add(DetailLine(line.text, null, line.error))
            } else {
                result.add(line)
            }
        }
        return result
    }
}

/** The Feats screen's details box: its caption (VB's lblDetails) and lines. */
class FeatDetails(val caption: String, val lines: List<DetailLine>)

/** A requirement's feat and selector (numbered from 1; 0 for none): VB's `.Feat` and `.Selector`. [feat] is null if it isn't a feat. */
class RequiredFeat(val feat: Feat?, val selector: Int)

/** A feat that was found or chosen, with its selector (numbered from 1; 0 for none). */
class FeatChoice(val feat: Feat, val selector: Int)

/** The wiki page for [name]. Ported from `MakeWiki` (Source/Modules/basWiki.bas:7). */
fun makeWiki(name: String): String = "https://ddowiki.com/page/" + name.replace(" ", "_")

/**
 * The feat rules: which feat can go in which slot, which feats are taken, slot errors, and the
 * Feats screen's lists. Ported from the FEATS part of Source/Modules/Build/basBuild.bas
 * (`InitFeatList` onwards, :962-:1947) and the feat lists of frmFeats (Source/Forms/Build/frmFeats.frm).
 *
 * VB's error messages go to the global `gstrError`; here they come back in a [SlotCheck] or are
 * stored on the [FeatListEntry].
 */
class FeatRules(val rules: BuildRules) {
    val build: Build get() = rules.build
    private val data = rules.data
    private val stats = StatRules(rules)

    // ---------------------------------------------------------------- the feat list

    /**
     * Lists the feat slots within the build's levels, sorted by level and then kind, with their
     * display names, sources, exchanges, errors and tabs. Returns the list position of slot
     * [newType]/[newIndex] (the slot [addSpecialFeat] just added), or -1. Ported from
     * `InitFeatList` (basBuild.bas:1002), `AddFeatToList` (:1141), `SortFeatList` (:1267),
     * `SlotIndexes` (:1030), `CheckSlotErrors` and `InitFeatChannels` (:1092).
     */
    fun initFeatList(newType: BuildFeatType?, newIndex: Int): Int {
        val list = ArrayList<FeatListEntry>()
        rules.featList = list
        rules.featTabCounts = IntArray(FeatTab.entries.size)
        if (build.levelClass(1) == null) {
            return -1
        }
        for (type in BuildFeatType.entries) {
            val slots = build.feats(type)
            for (i in slots.indices) {
                val entry = newEntry(type, i)
                if (entry != null) {
                    entry.flag = type == newType && i == newIndex
                    list.add(entry)
                }
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
        linkExchanges()
        checkSlotErrors()
        var result = -1
        for (i in list.indices) {
            if (list[i].flag) {
                result = i
            }
        }
        initTabs()
        return result
    }

    /** One slot as a list entry, or null if it's outside the build's levels. Ported from `AddFeatToList` (basBuild.bas:1141). */
    private fun newEntry(type: BuildFeatType, index: Int): FeatListEntry? {
        val slot = build.feats(type)[index]
        if (slot.level < 1 || slot.level > build.maxLevels) {
            return null
        }
        var effective = type
        val special = type == BuildFeatType.ALTERNATE || type == BuildFeatType.EXCHANGE
        if (special) {
            effective = slot.childType ?: type
        }
        val entry = FeatListEntry(type, effective, index, slot)
        if (special) {
            entry.parentIndex = slot.child
        }
        if (slot.level < 21) {
            entry.characterClass = build.levelClass(slot.level)
            entry.classLevel = classLevelAt(entry.characterClass, slot.level)
        }
        entry.feat = rules.findFeatOrNull(slot.featName)
        updateDisplayNames(entry)
        var sourceLevel = slot.level
        val raceAbbreviation = build.race?.abbreviation ?: ""
        if (type == BuildFeatType.ALTERNATE) {
            entry.sourceForm = "Alternate"
            entry.sourceOutput = " OR "
        } else if (type == BuildFeatType.EXCHANGE) {
            entry.sourceForm = "Exchange"
            entry.sourceOutput = "Swap"
            val parent = parentSlot(slot)
            if (parent != null) {
                sourceLevel = parent.level
            }
        } else {
            when (slot.source) {
                BuildFeatSource.HEROIC -> entry.sourceForm = "Heroic"
                BuildFeatSource.EPIC -> entry.sourceForm = "Epic"
                BuildFeatSource.DESTINY -> entry.sourceForm = "Destiny"
                BuildFeatSource.RACE -> entry.sourceForm = raceAbbreviation
                BuildFeatSource.CLASS, BuildFeatSource.CLASS_ONLY -> {
                    val c = build.levelClass(slot.level)
                    if (type == BuildFeatType.GRANTED) {
                        entry.sourceForm = c?.name ?: ""
                    } else {
                        entry.sourceForm = initial2(c)
                    }
                }
                BuildFeatSource.DEITY -> entry.sourceForm = "Deity"
                BuildFeatSource.LEGEND -> {
                    entry.sourceForm = "Legendary"
                    entry.sourceOutput = "Legend"
                }
            }
            if (slot.source != BuildFeatSource.LEGEND) {
                entry.sourceOutput = entry.sourceForm
            }
        }
        when (slot.source) {
            BuildFeatSource.HEROIC -> entry.sourceFilter = "Level " + slot.level
            BuildFeatSource.EPIC -> entry.sourceFilter = "Epic " + slot.level
            BuildFeatSource.DESTINY -> entry.sourceFilter = "Destiny " + slot.level
            BuildFeatSource.RACE -> entry.sourceFilter = raceAbbreviation
            BuildFeatSource.CLASS, BuildFeatSource.CLASS_ONLY, BuildFeatSource.DEITY -> {
                var c: CharacterClass? = null
                if (sourceLevel in 1..HEROIC_LEVELS) {
                    c = build.levelClass(sourceLevel)
                }
                entry.sourceFilter = initial2(c) + " " + entry.classLevel
            }
            BuildFeatSource.LEGEND -> entry.sourceFilter = "Legend 30"
        }
        return entry
    }

    /** The slot an alternate or exchange belongs to, or null. */
    fun parentSlot(slot: BuildFeat): BuildFeat? {
        val type = slot.childType ?: return null
        val list = build.feats(type)
        if (slot.child < 0 || slot.child >= list.size) {
            return null
        }
        return list[slot.child]
    }

    /** Class levels of [c] taken by [level]. Ported from `GetClassLevel` (basBuild.bas:1257). */
    private fun classLevelAt(c: CharacterClass?, level: Int): Int {
        var count = 0
        for (i in 1..minOf(level, HEROIC_LEVELS)) {
            if (build.levelClass(i) == c) {
                count++
            }
        }
        return count
    }

    /** `db.Class(c).Initial(2)`: the third of a class's initials (e.g. "Ftr"). */
    private fun initial2(c: CharacterClass?): String {
        if (c == null || c.initials.isEmpty()) {
            return ""
        }
        if (c.initials.size > 2) {
            return c.initials[2]
        }
        return c.initials[0]
    }

    /**
     * Sets [entry]'s display names from its slot. Ported from `GetDisplayNames` (basBuild.bas:1220):
     * a feat the data doesn't have shows its saved name.
     */
    fun updateDisplayNames(entry: FeatListEntry) {
        val feat = entry.feat
        if (feat == null) {
            entry.display = entry.slot.featName
            if (entry.actualType == BuildFeatType.ALTERNATE) {
                entry.displayAlternate = entry.slot.featName
            }
            return
        }
        var selectorName = ""
        var display = feat.name
        val s = entry.selector
        if (s != 0 && s <= feat.selectors.size) {
            selectorName = feat.selectors[s - 1].name
            if (feat.selectorOnly) {
                display = selectorName
            } else {
                display = feat.name + ": " + selectorName
            }
        }
        entry.display = display
        if (entry.actualType == BuildFeatType.ALTERNATE) {
            val parent = parentSlot(entry.slot)
            if (parent != null && feat.name == parent.featName) {
                entry.displayAlternate = selectorName
            } else {
                entry.displayAlternate = display
            }
        }
    }

    /** Ported from `CompareSlot` (basBuild.bas:1284). */
    private fun compareSlot(one: FeatListEntry, two: FeatListEntry): Int {
        if (one.level != two.level) {
            return if (one.level < two.level) -1 else 1
        }
        return one.effectiveType.ordinal.compareTo(two.effectiveType.ordinal)
    }

    /**
     * Links each exchange with the slot it exchanges, both ways, and gives it that slot's title.
     * Ported from `SlotIndexes` (basBuild.bas:1030).
     */
    private fun linkExchanges() {
        val list = rules.featList
        for (i in list.indices) {
            val entry = list[i]
            if (entry.actualType != BuildFeatType.EXCHANGE) {
                continue
            }
            for (j in list.indices) {
                if (list[j].actualType == entry.effectiveType && list[j].index == entry.parentIndex) {
                    entry.exchangeIndex = j
                    entry.sourceFilter = list[j].sourceFilter
                    list[j].exchangeIndex = i
                    break
                }
            }
        }
    }

    /**
     * Puts each slot on its tab: Granted, the racial bonus tab (Half-Elf, Dragonborn and Aasimar
     * only), Deity, a class's tab for some class bonus feats, or General; and every slot not on
     * Granted on Selected too. Ported from `InitFeatChannels` (basBuild.bas:1092).
     */
    private fun initTabs() {
        val list = rules.featList
        val counts = rules.featTabCounts
        for (entry in list) {
            var c = entry.characterClass
            var classLevel = entry.classLevel
            if (entry.actualType == BuildFeatType.EXCHANGE && entry.exchangeIndex >= 0) {
                c = list[entry.exchangeIndex].characterClass
                classLevel = list[entry.exchangeIndex].classLevel
            }
            var tab = FeatTab.GENERAL
            when (entry.effectiveType) {
                BuildFeatType.GRANTED -> tab = FeatTab.GRANTED
                BuildFeatType.RACE -> when (build.race?.name) {
                    "Half-Elf", "Dragonborn", "Aasimar" -> tab = FeatTab.RACIAL
                }
                BuildFeatType.DEITY -> tab = FeatTab.DEITY
                BuildFeatType.CLASS1, BuildFeatType.CLASS2, BuildFeatType.CLASS3 -> when (c?.name) {
                    "Cleric" -> tab = FeatTab.CLERIC
                    "Druid" -> tab = FeatTab.WILD_SHAPE
                    "Favored Soul" -> when (classLevel) {
                        5, 10, 15 -> tab = FeatTab.ENERGY
                        2, 7 -> tab = FeatTab.FAVORED_SOUL
                    }
                    "Monk" -> if (classLevel == 3) {
                        tab = FeatTab.MONK
                    }
                    "Ranger" -> tab = FeatTab.FAVORED_ENEMY
                    "Rogue" -> tab = FeatTab.ROGUE
                    "Warlock" -> tab = FeatTab.WARLOCK
                }
                else -> {}
            }
            entry.tab = tab
            counts[tab.ordinal]++
            entry.tabRows[tab.ordinal] = counts[tab.ordinal]
            if (tab != FeatTab.GRANTED) {
                counts[FeatTab.SELECTED.ordinal]++
                entry.tabRows[FeatTab.SELECTED.ordinal] = counts[FeatTab.SELECTED.ordinal]
            }
        }
    }

    /** True if [entry] is listed on [tab]: its own tab, or Selected for any slot not granted (frmFeats.InitSelectedFeatsList). */
    fun isOnTab(entry: FeatListEntry, tab: FeatTab): Boolean {
        if (tab == FeatTab.GRANTED) {
            return entry.actualType == BuildFeatType.GRANTED
        }
        return entry.tab == tab || (tab == FeatTab.SELECTED && entry.actualType != BuildFeatType.GRANTED)
    }

    // ---------------------------------------------------------------- errors

    /**
     * Marks the slots whose feat breaks a rule. Ported from `CheckSlotErrors` (basBuild.bas:1298):
     * an exchange is in error if its slot is locked, any other selected slot if [checkFeatSlot]
     * doesn't allow its feat cleanly.
     */
    fun checkSlotErrors() {
        val list = rules.featList
        for (i in list.indices) {
            val entry = list[i]
            entry.errorState = false
            entry.errorText = ""
            if (entry.actualType == BuildFeatType.GRANTED) {
                continue
            }
            if (entry.actualType == BuildFeatType.EXCHANGE) {
                val locked = slotLocked(i)
                if (locked != null) {
                    entry.errorState = true
                    entry.errorText = locked
                }
            }
            if (!entry.errorState) {
                val check = checkFeatSlot(entry.feat, entry.slot)
                entry.errorState = check.state != DropState.CAN_DROP
                entry.errorText = check.error
            }
        }
    }

    /**
     * Can [feat] go in [slot]? Ported from `CheckFeatSlot` (basBuild.bas:1320). The hard rules
     * (slot kind, level, BAB, class levels, race, stats, skills, past lives) give [DropState.DEFAULT]
     * with the reason; then alignment and the feat requirements decide between
     * [DropState.CAN_DROP] and [DropState.CAN_DROP_ERROR]. Alternates skip the stat, skill,
     * past life, alignment and requirement checks. An exchange is checked as if it were in the slot
     * it exchanges, with tomes counting from level 1 ("Fred"). With [selectorRequired] false, a
     * feat with selectors doesn't need one chosen (used to filter the feat list).
     *
     * VB quirks kept:
     * - The Arcane Trickster exception to "can cast spells" applies to any standard slot at or
     *   after the build's first Arcane Trickster level; without Arcane Trickster levels that's
     *   level 99, so it never applies.
     * - NotClass, ClassOnly, RaceOnly and NotRace selectors come after the requirement checks, so
     *   they downgrade even an alternate to [DropState.CAN_DROP_ERROR].
     */
    fun checkFeatSlot(feat: Feat?, slot: BuildFeat, selectorRequired: Boolean = true): SlotCheck {
        if (feat == null) {
            return SlotCheck(DropState.CAN_DROP, "")
        }
        val fail = DropState.DEFAULT
        var target = slot
        val actualLevel = slot.level
        var fred = false
        if (slot.type == BuildFeatType.EXCHANGE) {
            val parent = parentSlot(slot)
            if (parent != null) {
                target = parent.copy()
                target.featName = slot.featName
                target.selector = slot.selector
            }
            fred = true
        }
        if (!feat.selectable && target.type != BuildFeatType.GRANTED) {
            return SlotCheck(fail, "Not a selectable feat.")
        }
        val grantedBy = feat.grantedBy
        if (grantedBy != null) {
            val c = data.findClass(grantedBy.className)
            var count = 0
            for (level in 1..HEROIC_LEVELS) {
                if (c != null && build.levelClass(level) == c) {
                    count++
                }
            }
            if (count >= grantedBy.level) {
                return SlotCheck(fail, "Chosen at " + (c?.name ?: grantedBy.className) + " level " + grantedBy.level)
            }
        }
        var s = 0
        var hasSelector = false
        if (selectorRequired && feat.selectorStyle != SelectorStyle.NONE) {
            if (target.selector == 0) {
                return SlotCheck(fail, "A selector is required but wasn't specified.")
            }
            s = target.selector
            hasSelector = s <= feat.selectors.size
        }
        val selector = if (hasSelector) feat.selectors[s - 1] else null
        when (target.source) {
            BuildFeatSource.HEROIC -> if (!feat.groups.contains(FeatGroup.HEROIC)) {
                return SlotCheck(fail, "Can't be taken as a Heroic feat.")
            }
            BuildFeatSource.EPIC -> if (!(feat.groups.contains(FeatGroup.HEROIC) || feat.groups.contains(FeatGroup.EPIC))) {
                return SlotCheck(fail, "Can't be taken as an Epic feat.")
            }
            BuildFeatSource.DESTINY -> if (!feat.groups.contains(FeatGroup.DESTINY)) {
                return SlotCheck(fail, "Can't be taken as a Destiny feat.")
            }
            BuildFeatSource.LEGEND -> if (!feat.groups.contains(FeatGroup.LEGEND)) {
                return SlotCheck(fail, "Can't be taken as a Legendary feat.")
            }
            BuildFeatSource.RACE -> if (!isRaceBonus(feat)) {
                return SlotCheck(fail, "Not a valid " + (build.race?.abbreviation ?: "") + " bonus feat.")
            }
            BuildFeatSource.DEITY -> if (!feat.deity) {
                return SlotCheck(fail, "Can't be taken as a Deity feat.")
            }
            BuildFeatSource.CLASS_ONLY -> if (!feat.classOnly) {
                return SlotCheck(fail, "Can't be taken as a class feat.")
            }
            BuildFeatSource.CLASS -> {
                if (target.level > HEROIC_LEVELS) {
                    return SlotCheck(fail, "Class feats must be taken during class levels.")
                }
                val c = build.levelClass(target.level)
                val abbreviation = c?.abbreviation ?: ""
                if (selector != null) {
                    if (!(listsClass(selector.classBonus, c) || feat.classOnly)) {
                        return SlotCheck(fail, "Not a valid $abbreviation bonus feat. (This may not apply to all selectors.)")
                    }
                } else if (!(listsClass(feat.classBonus, c) || feat.classOnly)) {
                    return SlotCheck(fail, "Not a valid $abbreviation bonus feat.")
                }
            }
        }
        if (feat.classOnly) {
            if (target.level > HEROIC_LEVELS) {
                return SlotCheck(fail, "Can't be taken in epic levels.")
            }
            if (!listsClass(feat.classOnlyClasses, build.levelClass(target.level))) {
                return SlotCheck(fail, "Can only be taken by certain classes as a class bonus feat.")
            }
            if (!feat.classOnlyLevels.contains(target.classLevel)) {
                return SlotCheck(fail, "Can only be taken at certain class levels.")
            }
        }
        if (target.level < feat.level || target.level > build.maxLevels) {
            return SlotCheck(fail, "Requires " + feat.level + " character levels.")
        }
        if (babAt(target.level) < feat.bab) {
            return SlotCheck(fail, "Requires BAB " + feat.bab + ".")
        }
        if (feat.raceOnly && target.source != BuildFeatSource.RACE) {
            return SlotCheck(fail, "Can only be taken as a racial bonus feat.")
        }
        val bonusLevel = feat.classBonusLevel
        if (bonusLevel != null && target.source == BuildFeatSource.CLASS) {
            val c = build.levelClass(target.level)
            if (c != null && c == data.findClass(bonusLevel.className) && target.classLevel != bonusLevel.level) {
                return SlotCheck(fail, "Available as " + c.name + " bonus feat only at " + c.name + " level " + bonusLevel.level)
            }
        }
        if (feat.canCastSpell) {
            val trickster = data.findClass("Arcane Trickster")
            var tricksterLevel = 99
            if (trickster != null) {
                tricksterLevel = rules.characterLevel(trickster, 1)
            }
            if (!(slot.type == BuildFeatType.STANDARD && tricksterLevel <= slot.level)) {
                val first = build.canCastSpell[feat.canCastSpellLevel]
                if (first == 0 || first > target.level) {
                    if (feat.canCastSpellLevel == 0) {
                        return SlotCheck(fail, "Can't cast healing spells yet.")
                    }
                    return SlotCheck(fail, "Can't cast Level " + feat.canCastSpellLevel + " spells yet.")
                }
            }
        }
        val featClasses = feat.classes
        if (featClasses != null && !checkClassLevels(target.level, featClasses)) {
            return SlotCheck(fail, "Does not meet class level requirements.")
        }
        val selectorClasses = selector?.classes
        if (selectorClasses != null && !checkClassLevels(target.level, selectorClasses)) {
            return SlotCheck(fail, "Does not meet class level requirements.")
        }
        if (selector != null) {
            if (raceRestricted(selector.race)) {
                return SlotCheck(fail, "Race restricted. (May not apply to all selectors.)")
            }
        } else if (raceRestricted(feat.race)) {
            return SlotCheck(fail, "Race restricted.")
        }
        if (target.type != BuildFeatType.ALTERNATE) {
            if (selector != null) {
                val stat = selector.stat
                if (stat != null && stats.stat(stat, target.level, false, fred) < selector.statValue) {
                    return SlotCheck(fail, "Requires " + stat.displayName + " " + selector.statValue + " (May not apply to all selectors.)")
                }
                val skill = selector.skill
                if (skill != null && calculateSkill(skill, target.level, feat.skillTome) < selector.skillValue) {
                    return SlotCheck(fail, skillError(skill, selector.skillValue, feat.skillTome))
                }
            } else {
                val stat = feat.stat
                if (stat != null && stats.stat(stat, target.level, false, fred) < feat.statValue) {
                    return SlotCheck(fail, "Requires " + stat.displayName + " " + feat.statValue)
                }
                val skill = feat.skill
                if (skill != null && calculateSkill(skill, target.level, feat.skillTome) < feat.skillValue) {
                    return SlotCheck(fail, skillError(skill, feat.skillValue, feat.skillTome))
                }
            }
            if (feat.pastLife && build.buildPoints < BuildPoints.HERO) {
                return SlotCheck(fail, "This is a first life build.")
            }
            if (feat.legend && build.buildPoints < BuildPoints.LEGEND) {
                return SlotCheck(fail, "This is not a legend build.")
            }
        }
        var state: DropState
        var error = ""
        if (target.type == BuildFeatType.ALTERNATE) {
            state = DropState.CAN_DROP
        } else {
            state = DropState.CAN_DROP_ERROR
            // VB tests Alignment(build.Alignment): with no alignment chosen that's element 0, the
            // "has alignments" flag, which is true, so the check passes.
            val alignments = selector?.alignments
            val alignment = build.alignment
            if (alignments != null && alignment != null && !alignments.contains(alignment)) {
                return SlotCheck(state, "Incompatible alignment.")
            }
            val reqError = checkFeatReqs(feat, target, actualLevel)
            if (reqError == null) {
                state = DropState.CAN_DROP
            } else {
                error = reqError
            }
        }
        if (selector != null) {
            val notClass = selector.notClass
            if (notClass != null) {
                val c = data.findClass(notClass)
                if (c != null && hasLevelsIn(c)) {
                    return SlotCheck(DropState.CAN_DROP_ERROR, "Cannot be taken by characters with " + c.name + " levels.")
                }
            }
            val classOnly = selector.classOnly
            if (classOnly != null) {
                val c = data.findClass(classOnly)
                if (c != null && !hasLevelsIn(c)) {
                    return SlotCheck(DropState.CAN_DROP_ERROR, "Must be taken by characters with " + c.name + " levels.")
                }
            }
            val raceOnly = selector.raceOnly
            if (raceOnly != null) {
                val race = data.findRace(raceOnly)
                if (race != null && build.race != race) {
                    return SlotCheck(DropState.CAN_DROP_ERROR, "Must be taken by characters with " + race.name + ".")
                }
            }
            val notRaces = selector.notRaces
            if (notRaces != null && listsRace(notRaces, build.race)) {
                return SlotCheck(DropState.CAN_DROP_ERROR, "Incompatible Race.")
            }
        }
        return SlotCheck(state, error)
    }

    private fun skillError(skill: Skill, value: Int, skillTome: Boolean): String {
        var text = "Requires " + skill.displayName + " " + value + " (skill tomes "
        if (skillTome) {
            text += "apply)"
        } else {
            text += "do not apply)"
        }
        return text
    }

    private fun babAt(level: Int): Int {
        if (level < 1 || level > build.bab.size) {
            return 0
        }
        return build.bab[level - 1]
    }

    /** True if any heroic level is in [c] (VB's `For i = 1 To 20 … If build.Class(i) = enClass`). */
    private fun hasLevelsIn(c: CharacterClass): Boolean {
        for (level in 1..HEROIC_LEVELS) {
            if (build.levelClass(level) == c) {
                return true
            }
        }
        return false
    }

    /** `ptypFeat.RaceBonus(build.Race)`. */
    private fun isRaceBonus(feat: Feat): Boolean {
        val races = feat.raceBonus ?: return false
        return listsRace(races, build.race)
    }

    private fun listsRace(names: List<String>, race: Race?): Boolean {
        if (race == null) {
            return false
        }
        for (name in names) {
            if (data.findRace(name) == race) {
                return true
            }
        }
        return false
    }

    private fun listsClass(names: List<String>?, c: CharacterClass?): Boolean {
        if (names == null || c == null) {
            return false
        }
        for (name in names) {
            if (data.findClass(name) == c) {
                return true
            }
        }
        return false
    }

    /**
     * True if the build's race may NOT take something with this restriction. Ported from
     * `RaceRestricted` (basBuild.bas:1641).
     */
    fun raceRestricted(requirement: RaceRequirement): Boolean {
        val race = build.race
        val listed = listsRace(requirement.races, race)
        return when (requirement.mode) {
            RaceRequirementMode.ANY -> false
            RaceRequirementMode.REQUIRED -> !listed
            RaceRequirementMode.NOT_ALLOWED -> listed
            RaceRequirementMode.STANDARD -> !((race?.type == RaceType.FREE || race?.type == RaceType.PREMIUM) && !listed)
            RaceRequirementMode.ICONIC -> !(race?.type == RaceType.ICONIC && !listed)
        }
    }

    /**
     * True if, by [level] (at most 20), the build has enough levels in any one of [classes].
     * Ported from `CheckClassLevels` (basBuild.bas:1657).
     */
    fun checkClassLevels(level: Int, classes: List<ClassLevel>): Boolean {
        val counts = HashMap<CharacterClass, Int>()
        for (i in 1..minOf(level, HEROIC_LEVELS)) {
            val c = build.levelClass(i) ?: continue
            counts[c] = (counts[c] ?: 0) + 1
        }
        for (classLevel in classes) {
            val c = data.findClass(classLevel.className) ?: continue
            if ((counts[c] ?: 0) >= classLevel.level) {
                return true
            }
        }
        return false
    }

    /**
     * The skill's ranks at [level] (heroic levels only, half ranks rounded down), plus skill tomes
     * if [skillTome]. Ported from `CalculateSkill` (basBuild.bas:321).
     */
    fun calculateSkill(skill: Skill, level: Int, skillTome: Boolean): Int {
        var max = rules.heroicLevels()
        if (max > level) {
            max = level
        }
        var total = 0
        for (i in 1..max) {
            var native = 1
            if (build.levelClass(i)?.isNativeSkill(skill) == true) {
                native = 2
            }
            total += build.skills[skill.ordinal][i - 1] * native
        }
        total = total / 2
        if (skillTome) {
            var tome = skillTomeLevel(level)
            if (build.skillTomes[skill.ordinal] < tome) {
                tome = build.skillTomes[skill.ordinal]
            }
            total += tome
        }
        return total
    }

    /** The highest skill tome usable at [level]. Ported from `SkillTomeLevel` (basBuild.bas:342). */
    fun skillTomeLevel(level: Int): Int {
        val schedule = data.tomes.skill
        var i = schedule.max
        while (i >= 1) {
            if (i <= schedule.levels.size && schedule.level(i) > 0 && schedule.level(i) <= level) {
                break
            }
            i--
        }
        return i
    }

    // ---------------------------------------------------------------- requirements

    /**
     * Null if [feat] in [slot] meets its feat requirements (and its selector's, when the feat is
     * already in the slot); otherwise the reason. Ported from `CheckFeatReqs` (basBuild.bas:1681);
     * taken feats are counted by [level].
     */
    fun checkFeatReqs(feat: Feat, slot: BuildFeat, level: Int): String? {
        val taken = identifyTakenFeats(level)
        if (feat.selectorStyle == SelectorStyle.SHARED && slot.selector != 0 && feat.name == slot.featName) {
            val parent = sharedParent(feat)
            if (!taken.selectorTaken(parent, slot.selector)) {
                var parentDisplay = ""
                if (parent != null) {
                    parentDisplay = featDisplay(parent, slot.selector, abbreviate = true, featPrefix = false)
                }
                return "Requires $parentDisplay"
            }
        }
        for (group in 0..2) {
            val error = checkFeatReq(requirementGroup(feat.requirements, group), group, taken)
            if (error != null) {
                return error
            }
        }
        if (slot.selector != 0 && feat.selectorStyle != SelectorStyle.NONE && feat.name == slot.featName && slot.selector <= feat.selectors.size) {
            val reqs = feat.selectors[slot.selector - 1].requirements
            for (group in 0..2) {
                val error = checkFeatReq(requirementGroup(reqs, group), group, taken)
                if (error != null) {
                    return error
                }
            }
        }
        return null
    }

    /** The feat a `SharedSelector:` feat takes its selectors from (VB's `Parent.Feat`). */
    fun sharedParent(feat: Feat): Feat? {
        val name = feat.parentSelector ?: return null
        return data.findFeat(name)
    }

    private fun requirementGroup(requirements: ddolite.builder.model.Requirements, group: Int): List<Requirement> = when (group) {
        0 -> requirements.all
        1 -> requirements.one
        else -> requirements.none
    }

    /**
     * Null if the requirement list [group] (0 = all of, 1 = one of, 2 = none of) is met by
     * [taken]; otherwise the reason. Ported from `CheckFeatReq` (basBuild.bas:1707). A requirement
     * that isn't a feat (or isn't found) counts as not taken, with an empty name, as VB's feat 0.
     */
    fun checkFeatReq(reqs: List<Requirement>, group: Int, taken: TakenFeats): String? {
        if (reqs.isEmpty()) {
            return null
        }
        var matches = 0
        var takenName = ""
        var missing = ""
        for (req in reqs) {
            val pointer = featPointer(req)
            val feat = pointer.feat
            val selector = pointer.selector
            val abbreviation = feat?.abbreviation ?: ""
            var has: Boolean
            if (selector == 0) {
                has = taken.of(feat).times != 0
            } else {
                has = taken.selectorTaken(feat, selector)
            }
            if (has) {
                if (takenName.isEmpty()) {
                    takenName = abbreviation
                }
                matches++
            } else if (missing.isEmpty()) {
                missing = abbreviation
            }
        }
        if (group == 0 && matches < reqs.size) {
            return "Requires $missing"
        }
        if (group == 1 && matches < 1) {
            return "Requires a feat from the 'One of' list."
        }
        if (group == 2 && matches > 0) {
            return "Antireq for $takenName"
        }
        return null
    }

    /** A requirement's feat and selector number (VB's `.Feat`, `.Selector`); null and 0 if it isn't a feat. */
    fun featPointer(req: Requirement): RequiredFeat {
        val target = req.target
        if (target !is FeatTarget) {
            return RequiredFeat(null, 0)
        }
        val feat = data.findFeat(target.featName) ?: return RequiredFeat(null, 0)
        return RequiredFeat(feat, selectorNumber(feat, target.selectorName))
    }

    /**
     * The feats and selectors taken by [level] (0 = the build's max level). Ported from
     * `IdentifyTakenFeats` (basBuild.bas:1752): alternates don't count, and a slot that's
     * exchanged by then counts as the feat it was exchanged for.
     */
    fun identifyTakenFeats(level: Int): TakenFeats {
        var maxLevel = level
        if (maxLevel == 0) {
            maxLevel = build.maxLevels
        }
        val result = TakenFeats()
        val list = rules.featList
        for (entry in list) {
            if (entry.level > maxLevel) {
                break
            }
            if (entry.feat == null || entry.actualType == BuildFeatType.ALTERNATE || entry.actualType == BuildFeatType.EXCHANGE) {
                continue
            }
            var feat = entry.feat
            var selector = entry.selector
            if (entry.exchangeIndex >= 0) {
                val exchange = list[entry.exchangeIndex]
                if (exchange.level <= maxLevel) {
                    feat = exchange.feat
                    selector = exchange.selector
                }
            }
            if (feat == null) {
                // VB counts the exchange's empty feat 0, which nothing looks up.
                continue
            }
            val taken = result.record(feat)
            taken.times++
            if (selector != 0 && selector <= feat.selectors.size) {
                if (feat.selectors[selector - 1].all) {
                    for (s in 1 until taken.selectors.size) {
                        if (!taken.selectors[s]) {
                            taken.selectors[s] = true
                            taken.selectorsTaken++
                        }
                    }
                } else if (!taken.selectors[selector]) {
                    taken.selectors[selector] = true
                    taken.selectorsTaken++
                }
            }
        }
        return result
    }

    /**
     * Null if the slot exchanged by the exchange at list position [exchangePosition] can be
     * exchanged; otherwise why not. Ported from `SlotLocked` (basBuild.bas:1812): not an empty slot,
     * a deity feat, a pact or a domain; not a feat a later slot (by the exchange's level) requires
     * ("all of", or the last of a "one of"); and only the last of a feat taken more than once.
     */
    fun slotLocked(exchangePosition: Int): String? {
        val list = rules.featList
        if (exchangePosition < 0) {
            return null
        }
        val level = list[exchangePosition].level
        val slotPosition = list[exchangePosition].exchangeIndex
        if (slotPosition < 0) {
            return null
        }
        val slotEntry = list[slotPosition]
        val feat = slotEntry.feat ?: return "Nothing to exchange."
        val selector = slotEntry.selector
        if (slotEntry.actualType == BuildFeatType.DEITY) {
            return "Deity feats cannot be exchanged."
        }
        if (feat.pact) {
            return "Warlock Pacts cannot be exchanged."
        }
        if (feat.domain) {
            return "Cleric Domains cannot be exchanged."
        }
        // "All of" requirements of later slots.
        for (i in slotPosition + 1 until list.size) {
            if (list[i].level > level) {
                break
            }
            if (list[i].actualType == BuildFeatType.GRANTED || list[i].actualType == BuildFeatType.ALTERNATE) {
                continue
            }
            val check = list[i].feat ?: continue
            for (req in check.requirements.all) {
                val pointer = featPointer(req)
                val pointed = pointer.feat
                if (pointed == null || pointed.name != feat.name) {
                    continue
                }
                var locked: Boolean
                if (feat.selectorStyle == SelectorStyle.SHARED) {
                    locked = pointer.selector == selector
                } else {
                    locked = pointer.selector == 0 || pointer.selector == selector
                }
                if (locked) {
                    return slotEntry.display + " is locked by " + list[i].display
                }
            }
        }
        // "One of" requirements.
        for (i in slotPosition + 1 until list.size) {
            if (list[i].level > level) {
                break
            }
            if (list[i].actualType != BuildFeatType.GRANTED) {
                if (oneCount(list[i].feat, list[i].level, feat, selector)) {
                    return slotEntry.display + " is locked by " + list[i].display
                }
            }
        }
        // Repeats: only the last one taken can be exchanged.
        if (feat.times > 1) {
            for (i in slotPosition + 1 until list.size) {
                if (list[i].level > level) {
                    break
                }
                when (list[i].actualType) {
                    BuildFeatType.GRANTED, BuildFeatType.ALTERNATE -> {}
                    BuildFeatType.EXCHANGE -> {
                        val exchanged = list[list[i].exchangeIndex]
                        if (exchanged.level > slotEntry.level && exchanged.feat?.name == feat.name) {
                            return "Only the last " + slotEntry.display + " can be exchanged."
                        }
                    }
                    else -> if (list[i].feat?.name == feat.name) {
                        return "Only the last " + slotEntry.display + " can be exchanged."
                    }
                }
            }
        }
        return null
    }

    /**
     * True if [oneFeat] has [feat] (with [selector]) in its "one of" list and the build has fewer
     * than two of that list by [oneLevel], so exchanging [feat] would break it. Ported from
     * `OneCount` (basBuild.bas:1909).
     */
    private fun oneCount(oneFeat: Feat?, oneLevel: Int, feat: Feat, selector: Int): Boolean {
        if (oneFeat == null) {
            return false
        }
        val reqs = oneFeat.requirements.one
        var found = false
        for (req in reqs) {
            val pointer = featPointer(req)
            if (pointer.feat?.name != feat.name) {
                continue
            }
            if (oneFeat.selectorStyle == SelectorStyle.SHARED) {
                if (pointer.selector == selector) {
                    found = true
                    break
                }
            } else if (pointer.selector == 0 || pointer.selector == selector) {
                found = true
                break
            }
        }
        if (!found) {
            return false
        }
        var count = 0
        for (entry in rules.featList) {
            if (entry.actualType == BuildFeatType.ALTERNATE || entry.level > oneLevel) {
                continue
            }
            for (req in reqs) {
                val pointer = featPointer(req)
                if (entry.feat != null && pointer.feat?.name == entry.feat?.name) {
                    if (pointer.selector == 0 || pointer.selector == entry.selector) {
                        count++
                    }
                }
            }
        }
        return count < 2
    }

    // ---------------------------------------------------------------- display

    /**
     * A feat as text: the name (or abbreviation), ": selector" (or the selector alone for
     * `SelectorOnly` feats), with "Feat: " in front if [featPrefix]. Ported from `GetFeatDisplay`
     * (basBuild.bas:35).
     */
    fun featDisplay(feat: Feat?, selector: Int, abbreviate: Boolean, featPrefix: Boolean): String {
        if (feat == null) {
            return ""
        }
        var name = feat.name
        if (abbreviate) {
            name = feat.abbreviation
        }
        var display = name
        if (selector != 0 && selector <= feat.selectors.size) {
            val selectorName = feat.selectors[selector - 1].name
            if (feat.selectorOnly) {
                display = selectorName
            } else {
                display = "$name: $selectorName"
            }
        }
        if (featPrefix) {
            display = "Feat: $display"
        }
        return display
    }

    /**
     * A requirement as text. Ported from `PointerDisplay` (basBuild.bas:22) with no tree: feats as
     * [featDisplay]; enhancements as "Tree Tier n: Ability" (destinies without the tree), with
     * " (Rank n)".
     */
    fun requirementDisplay(req: Requirement, abbreviate: Boolean): String {
        val target = req.target
        if (target is FeatTarget) {
            val pointer = featPointer(req)
            return featDisplay(pointer.feat, pointer.selector, abbreviate, featPrefix = false)
        }
        if (target is AbilityTarget) {
            var treeText = ""
            val tree = if (target.style == PointerStyle.DESTINY) data.findDestiny(target.treeName) else data.findTree(target.treeName)
            if (target.style == PointerStyle.ENHANCEMENT) {
                treeText = (tree?.abbreviation ?: target.treeName) + " "
            }
            var abilityText = target.abilityName
            if (tree != null && target.tier < tree.tiers.size && target.abilityIndex < tree.tiers[target.tier].size) {
                val ability = tree.tiers[target.tier][target.abilityIndex]
                var name = ability.name
                if (abbreviate) {
                    name = ability.abbreviation
                }
                val selectorName = target.selectorName
                if (selectorName == null) {
                    abilityText = name
                } else if (ability.selectorOnly) {
                    abilityText = selectorName
                } else {
                    abilityText = "$name: $selectorName"
                }
            }
            var rank = ""
            if (target.rank > 0) {
                rank = " (Rank " + target.rank + ")"
            }
            return treeText + "Tier " + target.tier + ": " + abilityText + rank
        }
        return ""
    }

    // ---------------------------------------------------------------- slots

    /**
     * Puts [feat] with [selector] in [entry]'s slot (null to empty it). Ported from frmFeats.SetSlot
     * (frmFeats.frm:805).
     */
    fun setSlot(entry: FeatListEntry, feat: Feat?, selector: Int) {
        entry.feat = feat
        entry.slot.featName = feat?.name ?: ""
        entry.slot.selector = selector
        updateDisplayNames(entry)
    }

    /**
     * Empties every chosen feat slot and removes alternates and exchanges. Ported from
     * frmFeats.ClearAllSlots (frmFeats.frm:837), after its question.
     */
    fun clearAllFeats() {
        build.feats(BuildFeatType.ALTERNATE).clear()
        build.feats(BuildFeatType.EXCHANGE).clear()
        for (type in BuildFeatType.entries) {
            if (type.ordinal < BuildFeatType.STANDARD.ordinal || type.ordinal > BuildFeatType.DEITY.ordinal) {
                continue
            }
            for (slot in build.feats(type)) {
                slot.featName = ""
                slot.selector = 0
            }
        }
        rules.initBuildFeats()
    }

    /**
     * Adds an alternate or exchange slot for slot [parentIndex] of [parentType]: an alternate at
     * the slot's level, an exchange at [exchangeLevel]. Returns its position in the new feat list.
     * Ported from `AddSpecialFeat` (basBuild.bas:962).
     *
     * VB quirk kept: the new slot is meant to be inserted in level order, but the loop compares the
     * shifted entries rather than the new one, so it moves back at most one place.
     */
    fun addSpecialFeat(special: BuildFeatType, parentType: BuildFeatType, parentIndex: Int, exchangeLevel: Int): Int {
        val parent = build.feats(parentType)[parentIndex]
        val slot = BuildFeat(special)
        slot.child = parentIndex
        slot.childType = parentType
        slot.classLevel = parent.classLevel
        if (special == BuildFeatType.ALTERNATE) {
            slot.level = parent.level
        } else {
            slot.level = exchangeLevel
        }
        slot.source = parent.source
        val list = build.feats(special)
        list.add(slot)
        // VB: For i = .Feats To 2 Step -1: If .Feat(i).Level < .Feat(i - 1).Level Then .Feat(i) = .Feat(i - 1) Else Exit For
        var i = list.size - 1
        while (i >= 1) {
            if (list[i].level < list[i - 1].level) {
                list[i] = list[i - 1]
            } else {
                break
            }
            i--
        }
        list[i] = slot
        return rules.initFeatList(special, i)
    }

    /** Removes slot [index] of [type] (an alternate or exchange). Ported from `DeleteFeatSlot` (basBuild.bas:989). */
    fun deleteFeatSlot(type: BuildFeatType, index: Int) {
        build.feats(type).removeAt(index)
        rules.initFeatList()
    }

    // ---------------------------------------------------------------- the Feats screen's lists

    /**
     * Can the feat ever be taken by this build? Race, class levels, spellcasting, stats, skills,
     * past lives and level, all at the build's max level. Ported from frmFeats.FilterFeat
     * (frmFeats.frm:1385).
     *
     * VB quirk kept: for a build with Arcane Trickster as a build class, a feat that needs
     * spellcasting passes at that check, and VB's later `Exit Function`s then return that pass, so
     * none of the remaining checks apply to it.
     */
    fun filterFeat(feat: Feat): Boolean {
        if (raceRestricted(feat.race)) {
            return false
        }
        val classes = feat.classes
        if (classes != null) {
            val counts = HashMap<CharacterClass, Int>()
            for (level in 1..rules.heroicLevels()) {
                val c = build.levelClass(level) ?: continue
                counts[c] = (counts[c] ?: 0) + 1
            }
            var ok = false
            for (classLevel in classes) {
                val c = data.findClass(classLevel.className) ?: continue
                if ((counts[c] ?: 0) >= classLevel.level) {
                    ok = true
                }
            }
            if (!ok) {
                return false
            }
        }
        if (feat.canCastSpell) {
            for (c in build.buildClasses) {
                if (c?.name == "Arcane Trickster") {
                    return true
                }
            }
            if (build.canCastSpell[feat.canCastSpellLevel] == 0) {
                return false
            }
        }
        val stat = feat.stat
        if (stat != null && feat.statValue > stats.stat(stat, build.maxLevels)) {
            return false
        }
        val skill = feat.skill
        if (skill != null && feat.skillValue > calculateSkill(skill, build.maxLevels, feat.skillTome)) {
            return false
        }
        if (feat.pastLife && build.buildPoints < BuildPoints.HERO) {
            return false
        }
        if (feat.legend && build.buildPoints < BuildPoints.LEGEND) {
            return false
        }
        if (feat.level > build.maxLevels) {
            return false
        }
        return true
    }

    /**
     * Which of [feat]'s selectors can still be chosen at [level] (index 1..n). Ported from
     * frmFeats.ValidSelectors (frmFeats.frm:1123): not "All" selectors; not ones already taken (nor,
     * for a shared selector, ones its parent feat doesn't have, unless [alternate]); and, unless
     * [showAll], the feat can be taken any number of times or [alternate], only selectors whose race,
     * class, stat, skill and requirements are met.
     */
    fun validSelectors(feat: Feat, level: Int, alternate: Boolean, showAll: Boolean): BooleanArray {
        val taken = identifyTakenFeats(level)
        val count = feat.selectors.size
        val result = BooleanArray(count + 1)
        for (i in 1..count) {
            result[i] = !feat.selectors[i - 1].all
        }
        if (feat.times != 99) {
            for (i in 1..count) {
                if (taken.selectorTaken(feat, i)) {
                    result[i] = false
                }
            }
            if (feat.selectorStyle == SelectorStyle.SHARED && !alternate) {
                val parent = sharedParent(feat)
                for (i in 1..count) {
                    if (!taken.selectorTaken(parent, i)) {
                        result[i] = false
                    }
                }
            }
        }
        if (showAll || feat.times == 99 || alternate) {
            return result
        }
        for (i in 1..count) {
            if (!result[i]) {
                continue
            }
            result[i] = selectorMeetsRequirements(feat, i, level, taken)
        }
        return result
    }

    private fun selectorMeetsRequirements(feat: Feat, i: Int, level: Int, taken: TakenFeats): Boolean {
        val selector = feat.selectors[i - 1]
        if (raceRestricted(selector.race)) {
            return false
        }
        val classes = selector.classes
        if (classes != null && !checkClassLevels(level, classes)) {
            return false
        }
        val stat = selector.stat
        if (stat != null && selector.statValue > stats.stat(stat, level)) {
            return false
        }
        val skill = selector.skill
        if (skill != null && selector.skillValue > calculateSkill(skill, level, feat.skillTome)) {
            return false
        }
        for (group in 0..2) {
            if (checkFeatReq(requirementGroup(selector.requirements, group), group, taken) != null) {
                return false
            }
        }
        return true
    }

    /**
     * The feats offered in the Feats list. Ported from frmFeats.ShowAvailable (frmFeats.frm:1266)
     * and FilterFeatGroup (:1448): feats on [tab] (any on Selected) in every group in [groups]
     * (none = "Show All Feats"), not taken as often as they can be, with a selector left; then,
     * unless [showAll], those that can go in [active] (the chosen slot), or with no slot chosen,
     * those that pass [filterFeat]. [selectorLevel] is the level selectors are checked at (the
     * chosen slot's, else the max level); [alternate] is whether the slot VB treats as selected
     * is an alternate (see frmFeats.ValidSelectors).
     */
    fun availableFeats(tab: FeatTab, groups: Set<FeatGroup>, active: FeatListEntry?, showAll: Boolean, selectorLevel: Int, alternate: Boolean): List<Feat> {
        val result = ArrayList<Feat>()
        val taken = identifyTakenFeats(build.maxLevels)
        for (feat in data.featsInDisplayOrder) {
            if (tab != FeatTab.SELECTED && FeatTab.of(feat.channel) != tab) {
                continue
            }
            if (!feat.groups.containsAll(groups)) {
                continue
            }
            val times = taken.of(feat).times
            if (feat.selectorStyle == SelectorStyle.NONE) {
                if (times != 0 && (feat.times == 0 || times >= feat.times)) {
                    continue
                }
            } else {
                if (times != 0 && feat.times != 0 && times >= feat.times) {
                    continue
                }
                if (!anyTrue(validSelectors(feat, selectorLevel, alternate, showAll))) {
                    continue
                }
            }
            if (!showAll) {
                if (active != null) {
                    if (checkFeatSlot(feat, active.slot, selectorRequired = false).state == DropState.DEFAULT) {
                        continue
                    }
                } else if (!filterFeat(feat)) {
                    continue
                }
            }
            result.add(feat)
        }
        return result
    }

    private fun anyTrue(values: BooleanArray): Boolean {
        for (i in 1 until values.size) {
            if (values[i]) {
                return true
            }
        }
        return false
    }

    /**
     * The details box for [feat] with [selector] (0 for none). Ported from frmFeats.ShowDetails
     * (frmFeats.frm:1489), HasRequirements (:1613), ShowDetailsRace (:1635) and ShowDetailsReqs
     * (:1669). With no feat: the chosen slot's error ("Fred says:"), if [errorSlot] is given and in
     * error. [errorSlot] is the chosen slot when no feat is highlighted in the list; its error is
     * added at the end.
     *
     * VB quirk kept: a feat with a description but no wiki page still gets a "(Wiki)" link, to the
     * wiki's base address.
     */
    fun details(feat: Feat?, selector: Int, errorSlot: FeatListEntry?): FeatDetails {
        val out = DetailsBuilder()
        if (feat == null) {
            if (errorSlot != null && errorSlot.errorState) {
                out.addErrorText(errorSlot.errorText)
                return FeatDetails("Fred says:", out.build())
            }
            return FeatDetails("Details", emptyList())
        }
        val caption = featDisplay(feat, selector, abbreviate = true, featPrefix = false)
        var sel: ddolite.builder.model.Selector? = null
        if (selector != 0 && selector <= feat.selectors.size) {
            sel = feat.selectors[selector - 1]
        }
        var description = sel?.description ?: ""
        var wiki = sel?.wiki ?: ""
        if (description.isEmpty()) {
            description = feat.description
        }
        if (wiki.isEmpty()) {
            wiki = feat.wiki
        }
        if (description.isNotEmpty()) {
            out.addDescrip(description, makeWiki(wiki))
        }
        if (hasRequirements(feat, sel)) {
            out.addText("Requirements:")
        }
        if (feat.level != 0) {
            out.addText(" - " + feat.level + " character levels")
        }
        if (feat.canCastSpell) {
            if (feat.canCastSpellLevel == 0) {
                out.addText(" - can cast healing spells")
            } else {
                out.addText(" - can cast level " + feat.canCastSpellLevel + " spells")
            }
        }
        if (feat.bab != 0) {
            out.addText(" - BAB: " + feat.bab)
        }
        if (sel == null) {
            if (feat.statValue != 0) {
                out.addText(" - " + (feat.stat?.displayName ?: "") + " " + feat.statValue)
            }
            if (feat.skillValue != 0) {
                out.addText(" - " + (feat.skill?.displayName ?: "") + " " + feat.skillValue)
            }
        } else {
            if (sel.statValue != 0) {
                out.addText(" - " + (sel.stat?.displayName ?: "") + " " + sel.statValue)
            }
            if (sel.skillValue != 0) {
                out.addText(" - " + (sel.skill?.displayName ?: "") + " " + sel.skillValue)
            }
        }
        if (feat.legend) {
            out.addText(" - Legend build")
        }
        if (feat.pastLife) {
            out.addText(" - Hero or Legend build")
        }
        if (sel == null) {
            detailsRace(out, feat.race)
        } else {
            detailsRace(out, sel.race)
        }
        if (feat.raceOnly) {
            out.addText("Only as racial bonus feat")
        }
        val featClasses = feat.classes
        if (feat.classOnly) {
            out.addText("Only as bonus feat for:")
            for (c in classesInVbOrder(feat.classOnlyClasses)) {
                out.addText(" - " + c.name)
            }
            out.addText("Only on class level(s):")
            val levels = StringBuilder(" - ")
            for (level in 1..20) {
                if (feat.classOnlyLevels.contains(level)) {
                    levels.append(level).append(", ")
                }
            }
            out.addText(levels.substring(0, levels.length - 2))
        } else if (featClasses != null) {
            out.addText("Class required:")
            addClassLevels(out, featClasses)
        }
        val selectorClasses = sel?.classes
        if (selectorClasses != null) {
            out.addText("Class required:")
            addClassLevels(out, selectorClasses)
        }
        val alignments = sel?.alignments
        if (alignments != null) {
            out.addText("Alignment Required: ")
            for (alignment in ddolite.builder.model.Alignment.entries) {
                if (alignments.contains(alignment)) {
                    out.addText(" - " + alignment.displayName)
                }
            }
        }
        detailsReqs(out, sel?.requirements ?: feat.requirements)
        if (feat.selectorStyle == SelectorStyle.SHARED) {
            out.addText("Same selector as:")
            out.addText(" - " + featDisplay(sharedParent(feat), 0, abbreviate = true, featPrefix = false))
        }
        if (errorSlot != null && errorSlot.errorState) {
            out.addErrorText("Error: " + errorSlot.errorText)
        }
        return FeatDetails(caption, out.build())
    }

    /** Ported from frmFeats.HasRequirements (frmFeats.frm:1613). */
    private fun hasRequirements(feat: Feat, selector: ddolite.builder.model.Selector?): Boolean {
        if (feat.level != 0 || feat.canCastSpell || feat.bab != 0) {
            return true
        }
        if (selector == null) {
            if (feat.statValue != 0 || feat.skillValue != 0) {
                return true
            }
        } else if (selector.statValue != 0 || selector.skillValue != 0) {
            return true
        }
        return feat.pastLife || feat.legend
    }

    /** Ported from frmFeats.ShowDetailsRace (frmFeats.frm:1635). */
    private fun detailsRace(out: DetailsBuilder, requirement: RaceRequirement) {
        var heading = ""
        var prefix = ""
        when (requirement.mode) {
            RaceRequirementMode.ANY -> return
            RaceRequirementMode.REQUIRED -> heading = "Race required:"
            RaceRequirementMode.NOT_ALLOWED -> {
                heading = "Race restricted:"
                prefix = "Not "
            }
            RaceRequirementMode.STANDARD -> {
                out.addText("Race restricted:")
                heading = " - Not Iconic"
                prefix = "Not "
            }
            RaceRequirementMode.ICONIC -> {
                out.addText("Race restricted:")
                heading = " - Iconic"
                prefix = "Not "
            }
        }
        out.addText(heading)
        val races = ArrayList<Race>()
        for (name in requirement.races) {
            val race = data.findRace(name)
            if (race != null && !races.contains(race)) {
                races.add(race)
            }
        }
        races.sortBy { it.vbIndex }
        for (race in races) {
            out.addText(" - " + prefix + race.name)
        }
    }

    /** Ported from frmFeats.ShowDetailsReqs (frmFeats.frm:1669). */
    private fun detailsReqs(out: DetailsBuilder, requirements: ddolite.builder.model.Requirements) {
        val names = listOf("all", "one", "none")
        for (group in 0..2) {
            val reqs = requirementGroup(requirements, group)
            if (reqs.isEmpty()) {
                continue
            }
            out.addText("Requires " + names[group] + " of:")
            for (req in reqs) {
                out.addText(" - " + requirementDisplay(req, abbreviate = true))
            }
        }
    }

    private fun addClassLevels(out: DetailsBuilder, classes: List<ClassLevel>) {
        val sorted = ArrayList<Pair<CharacterClass, Int>>()
        for (classLevel in classes) {
            val c = data.findClass(classLevel.className) ?: continue
            sorted.add(Pair(c, classLevel.level))
        }
        sorted.sortBy { it.first.vbIndex }
        for (pair in sorted) {
            out.addText(" - " + pair.first.name + " " + pair.second)
        }
    }

    /** Classes by name, in VB's ClassEnum order (VB loops `For i = 1 To ceClasses - 1`). */
    private fun classesInVbOrder(names: List<String>): List<CharacterClass> {
        val result = ArrayList<CharacterClass>()
        for (name in names) {
            val c = data.findClass(name)
            if (c != null && !result.contains(c)) {
                result.add(c)
            }
        }
        result.sortBy { it.vbIndex }
        return result
    }
}
