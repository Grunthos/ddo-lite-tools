package ddolite.builder.data

import ddolite.builder.model.Ability
import ddolite.builder.model.PointerStyle
import ddolite.builder.model.RankDetail
import ddolite.builder.model.Requirement
import ddolite.builder.model.Requirements
import ddolite.builder.model.Selector
import ddolite.builder.model.SelectorStyle
import ddolite.builder.model.Stat
import ddolite.builder.model.Tree
import ddolite.builder.model.TreeColor
import ddolite.builder.model.TreeStyle
import ddolite.dataformat.DataSource
import ddolite.dataformat.RawRecord
import ddolite.dataformat.Vb
import ddolite.dataformat.readText
import ddolite.dataformat.splitRecords

/**
 * Loads Enhancements.txt. Ported from `basDataLoad.LoadEnhancements` (Build/basDataLoad.bas:1321).
 * Returns the trees sorted by name (`SortTrees`, :1948), as VB's `SeekTree` needs.
 */
fun loadEnhancements(source: DataSource, log: LoadLog): List<Tree> =
    loadTreeFile(source, BuilderRecordFile.ENHANCEMENTS, "Enhancements.txt", log)

/** Loads Destinies.txt: `basDataLoad.LoadDestinies` (:1365), the same as enhancements. */
fun loadDestinies(source: DataSource, log: LoadLog): List<Tree> =
    loadTreeFile(source, BuilderRecordFile.DESTINIES, "Destinies.txt", log)

private fun loadTreeFile(source: DataSource, file: BuilderRecordFile, fileName: String, log: LoadLog): List<Tree> {
    val decoded = source.readText(file.path)
    if (decoded == null) {
        log.fileNotFound(fileName)
        return emptyList()
    }
    val trees = ArrayList<Tree>()
    for (record in splitRecords(decoded.text, file.marker).records) {
        // VB only loads records that contain "Type: " somewhere.
        if (!record.body.contains("Type: ")) {
            log.invalidSection(fileName, record.line, "Type: ", firstLine(record))
            continue
        }
        val tree = TreeReader(fileName, log).read(record)
        if (tree != null) {
            trees.add(tree)
        }
    }
    // SortTrees: insertion sort on TreeName (ordinal, stable), then TreeID = position.
    trees.sortWith(compareBy { it.name })
    return trees
}

/** An ability being read; mutable until [build], as VB fills an `AbilityType` field by field. */
private class AbilityDraft(val name: String) {
    var abbreviation = name
    var description = ""
    var ranks = 1
    var cost = 1
    var selectorStyle = SelectorStyle.NONE
    var selectors = ArrayList<AbilitySelectorDraft>()
    var parent: Requirement? = null
    var siblings: List<Requirement> = emptyList()
    var selectorOnly = false
    var requirements = Requirements.EMPTY
    var rankReqs = false
    var rankDetails: ArrayList<RankDetail>? = null

    fun build(): Ability {
        val built = ArrayList<Selector>()
        for (selector in selectors) {
            built.add(selector.build())
        }
        return Ability(
            name, abbreviation, description, ranks, cost, selectorStyle, built, parent, siblings,
            selectorOnly, requirements, rankReqs, rankDetails,
        )
    }
}

/** An ability's selector being read. Abilities use only a few of the `SelectorType` fields. */
private class AbilitySelectorDraft(val name: String, var cost: Int, var requirements: Requirements) {
    var description = ""
    var rankReqs = false
    var rankDetails: ArrayList<RankDetail>? = null

    fun build(): Selector = Selector(
        name = name,
        wiki = "",
        description = description,
        all = false,
        classBonus = emptyList(),
        stat = null,
        statValue = 0,
        skill = null,
        skillValue = 0,
        classes = null,
        notClass = null,
        requirements = requirements,
        alignments = null,
        race = ddolite.builder.model.RaceRequirement.NONE,
        hide = false,
        notRaces = null,
        raceOnly = null,
        classOnly = null,
        cost = cost,
        rankReqs = rankReqs,
        rankDetails = rankDetails,
    )
}

/**
 * Reads one tree. Ported from `basDataLoad.LoadTree` (Build/basDataLoad.bas:1405) and the routines
 * it calls. One reader per tree, because VB keeps the "current tier" (`log.Tier`) across the tree's
 * abilities, and uses it in error messages.
 */
private class TreeReader(private val fileName: String, private val log: LoadLog) {
    private var treeName = ""

    /** VB's `log.Tier`: -1 while reading the header, then the tier of the last `Tier:` line read. */
    private var currentTier = -1

    /** VB's `log.LoadItem` while reading abilities: the current ability's name. */
    private var currentAbility = ""

    // Header values (LoadTreeHeader).
    private var abbreviation = ""
    private var wiki = ""
    private var initials: List<String> = listOf("", "")
    private var color: TreeColor? = null
    private var style: TreeStyle? = null
    private val stats = LinkedHashSet<Stat>()
    private var lockout: String? = null
    private val tiers = ArrayList<ArrayList<AbilityDraft>>()

    fun read(record: RawRecord): Tree? {
        val split = splitRecords(record.body, BuilderSubMarkers.ABILITY)
        val header = RawRecord(split.preamble, record.line)
        if (!readHeader(header)) {
            log.loadError(fileName, record.line, "$treeName failed to load")
            return null
        }
        val treeStyle = style
        if (treeStyle == null) {
            // VB would stop with "Subscript out of range" adding the first ability: the tier array is
            // only created by a valid Type line, and an unknown header field stops the header early.
            log.vbDifference(fileName, record.line, "$treeName has no valid Type before its abilities (VB6 stops with an error)")
            return null
        }
        currentTier = 0
        for (section in split.records) {
            readAbility(section, record.line + section.line - 1, treeStyle)
        }
        addStats(treeStyle)
        addCoreRequirements(treeStyle)

        val builtTiers = ArrayList<List<Ability>>()
        for (tier in tiers) {
            val built = ArrayList<Ability>()
            for (ability in tier) {
                built.add(ability.build())
            }
            builtTiers.add(built)
        }
        return Tree(treeName, abbreviation, initials, color, treeStyle, stats, builtTiers, lockout, wiki)
    }

    /** VB's actLoadTree message: "<file>: <tree> header|Tier n: <ability> has invalid line:". */
    private fun invalidLine(lineNumber: Int, text: String) {
        var where = "header"
        if (currentTier >= 0) {
            where = "Tier $currentTier: $currentAbility"
        }
        log.error(fileName, lineNumber, "$fileName: $treeName $where has invalid line:", text)
    }

    /**
     * Reads the tree's header lines. Ported from `basDataLoad.LoadTreeHeader` (Build/basDataLoad.bas:1432).
     * Returns false if the tree must be dropped (an invalid `Type:`). An unknown field ends the header,
     * as in VB.
     */
    private fun readHeader(header: RawRecord): Boolean {
        val lines = header.numberedLines()
        // VB: .TreeName = strLine(0) – after CleanText, but not trimmed itself.
        treeName = ""
        if (lines.isNotEmpty()) {
            treeName = lines[0].text
        }
        abbreviation = treeName
        wiki = treeName
        for (i in 1 until lines.size) {
            val line = lines[i]
            val parsed = parseLineForLoad(line.text)
            if (parsed is ParsedLine.Blank) {
                continue
            }
            if (parsed !is ParsedLine.Field) {
                invalidLine(line.number, line.text)
                continue
            }
            when (parsed.field) {
                "abbreviation" -> abbreviation = parsed.item
                "wikiname" -> wiki = parsed.item
                "stats" -> {
                    for (name in parsed.list) {
                        val stat = Stat.find(name)
                        if (stat == null) {
                            log.error(fileName, line.number, "$fileName: $treeName header has invalid stat: $name", line.text)
                        } else {
                            stats.add(stat)
                        }
                    }
                }
                "lockout" -> lockout = parsed.item
                "initial" -> {
                    if (parsed.list.size == 1) {
                        initials = listOf(parsed.list[0], parsed.list[0])
                    } else if (parsed.list.size == 2) {
                        initials = listOf(parsed.list[0], parsed.list[1])
                    } else {
                        invalidLine(line.number, line.text)
                    }
                }
                "color" -> {
                    color = TreeColor.find(parsed.item)
                    if (color == null) {
                        invalidLine(line.number, line.text)
                    }
                }
                "type" -> {
                    val found = TreeStyle.find(parsed.item)
                    if (found == null) {
                        log.loadError(fileName, line.number, "$treeName header has invalid Type: ${parsed.item}")
                        return false
                    }
                    initTree(found)
                }
                else -> {
                    invalidLine(line.number, line.text)
                    break // VB: Exit For
                }
            }
        }
        return true
    }

    /**
     * Sets the tree's style and creates its tiers. Ported from `basDataLoad.InitTree`
     * (Build/basDataLoad.bas:1510). Quirk kept: for enhancement trees " enhancements" is added to the
     * wiki name as it stands *at the Type line*, so a WikiName line after Type replaces it.
     */
    private fun initTree(found: TreeStyle) {
        style = found
        if (found != TreeStyle.DESTINY) {
            wiki = "$wiki enhancements"
        } else {
            wiki = treeName
        }
        tiers.clear()
        for (t in 0..found.tierCount) {
            tiers.add(ArrayList())
        }
    }

    /**
     * Reads one ability. Ported from `basDataLoad.LoadAbility` (Build/basDataLoad.bas:1521).
     *
     * Quirks kept:
     * - An ability with no `Tier:` line goes in tier 0 (VB's local `lngTier` starts at 0), but
     *   error messages before its Tier line show the previous ability's tier (`log.Tier`).
     * - An invalid tier is reported and replaced by the previous ability's tier.
     * - Ranks and Cost outside their ranges are reported but kept.
     * - The first `SelectorName:` line ends the ability's own lines.
     */
    private fun readAbility(section: RawRecord, firstLineNumber: Int, treeStyle: TreeStyle) {
        val lines = section.numberedLines()
        if (lines.isEmpty()) {
            return
        }
        val name = Vb.trim(lines[0].text)
        if (name.isEmpty()) {
            return
        }
        val ability = AbilityDraft(name)
        currentAbility = name
        var tier = 0
        for (i in 1 until lines.size) {
            val line = lines[i]
            val lineNumber = firstLineNumber + line.number - 1
            val parsed = parseLineForLoad(line.text)
            if (parsed is ParsedLine.Blank) {
                continue
            }
            if (parsed !is ParsedLine.Field) {
                invalidLine(lineNumber, line.text)
                continue
            }
            val value = parsed.value
            val field = parsed.field
            if (field == "selectorname") {
                readSelectors(ability, section, firstLineNumber)
                break
            }
            when (field) {
                "abbreviation" -> ability.abbreviation = parsed.item
                "descrip" -> ability.description = parsed.item
                "tier" -> {
                    tier = value
                    if (tier < 0 || tier > treeStyle.maxValidTier) {
                        invalidLine(lineNumber, line.text) // ValidTier's LogError
                        tier = currentTier
                    }
                    if (currentTier > tier) {
                        log.loadError(fileName, lineNumber, "$treeName Tier $tier($currentTier?): $name not in Tier order")
                    }
                    currentTier = tier
                }
                "ranks" -> {
                    ability.ranks = value
                    if (value < 1 || value > 3) {
                        invalidLine(lineNumber, line.text)
                    }
                }
                "cost" -> {
                    ability.cost = value
                    if (value < 1 || value > 4) {
                        invalidLine(lineNumber, line.text)
                    }
                }
                "flags" -> {
                    for (flag in parsed.list) {
                        if (flag.lowercase() == "selectoronly") {
                            ability.selectorOnly = true
                        } else {
                            invalidLine(lineNumber, line.text)
                        }
                    }
                }
                "selector" -> {
                    if (ability.selectorStyle == SelectorStyle.NONE) {
                        ability.selectorStyle = SelectorStyle.ROOT
                    }
                    // Each selector copies the ability's cost and requirements as they are now.
                    val list = ArrayList<AbilitySelectorDraft>()
                    for (selectorName in parsed.list) {
                        list.add(AbilitySelectorDraft(selectorName, ability.cost, ability.requirements))
                    }
                    ability.selectors = list
                }
                "sharedselector" -> {
                    ability.selectorStyle = SelectorStyle.SHARED
                    ability.parent = Requirement(parsed.item, null)
                }
                "parent" -> {
                    ability.selectorStyle = SelectorStyle.EXCLUSIVE
                    ability.parent = Requirement(parsed.item, null)
                }
                "siblings" -> ability.siblings = pathRequirements(parsed.list)
                "all", "one", "none" -> ability.requirements = ability.requirements.withGroup(field, pathRequirements(parsed.list))
                "rank2all", "rank3all", "rank3none" -> {
                    val rank = rankOf(field)
                    val group = field.substring(5)
                    if (ability.selectorOnly) {
                        // VB can't tell which selector a rank requirement is for, so it adds it to all.
                        for (selector in ability.selectors) {
                            selector.rankReqs = true
                            selector.rankDetails = withRankRequirements(selector.rankDetails, rank, group, parsed.list)
                        }
                    } else {
                        ability.rankReqs = true
                        ability.rankDetails = withRankRequirements(ability.rankDetails, rank, group, parsed.list)
                    }
                }
                "rank2cost", "rank3cost" -> {
                    // VB: .Rank(n).Cost = strList(i), with i left over from an earlier loop in the
                    // same ability (0 in the shipped data, where this is used once). VB notes the
                    // feature "isn't complete". The first list entry is used here.
                    val details = initRanks(ability.rankDetails)
                    val index = rankOf(field) - 2
                    details[index] = RankDetail(Vb.cLng(Vb.valOf(parsed.list[0])), details[index].requirements)
                    ability.rankDetails = details
                }
                else -> invalidLine(lineNumber, line.text)
            }
        }
        tiers[tier].add(ability)
    }

    /** VB's `Val(Mid$(field, 5, 1))`: the rank digit in "rank2all" etc. */
    private fun rankOf(field: String): Int = field[4].digitToInt()

    /** Reads the selector sections after the first `SelectorName:` line (VB splits the ability again). */
    private fun readSelectors(ability: AbilityDraft, section: RawRecord, firstLineNumber: Int) {
        for (part in splitRecords(section.body, BuilderSubMarkers.SELECTOR).records) {
            readSelector(ability, part, firstLineNumber + part.line - 1)
        }
    }

    /**
     * One selector section of an ability. Ported from `basDataLoad.LoadSelector` (Build/basDataLoad.bas:1753).
     *
     * Quirks kept: a section for a selector the ability doesn't list is ignored without an error;
     * the section clears the selector's rank details (VB: `ReDim .Rank(0)`), even ones set by the
     * ability's own Rank lines, but not its rankReqs flag; an unknown field ends the section.
     */
    private fun readSelector(ability: AbilityDraft, part: RawRecord, firstLineNumber: Int) {
        val lines = part.numberedLines()
        if (lines.isEmpty()) {
            return
        }
        val name = Vb.trim(lines[0].text)
        if (name.isEmpty()) {
            return
        }
        var selector: AbilitySelectorDraft? = null
        for (candidate in ability.selectors) {
            if (candidate.name == name) {
                selector = candidate
                break
            }
        }
        if (selector == null) {
            return
        }
        selector.rankDetails = null
        for (i in 1 until lines.size) {
            val line = lines[i]
            val lineNumber = firstLineNumber + line.number - 1
            val parsed = parseLineForLoad(line.text)
            if (parsed is ParsedLine.Blank) {
                continue
            }
            if (parsed !is ParsedLine.Field) {
                invalidLine(lineNumber, line.text)
                continue
            }
            val field = parsed.field
            when (field) {
                "descrip" -> selector.description = parsed.item
                "cost" -> {
                    // VB notes that none of the cost calculations use this.
                    selector.cost = parsed.value
                    if (parsed.value < 1 || parsed.value > 2) {
                        invalidLine(lineNumber, line.text)
                    }
                }
                "all", "one", "none" -> selector.requirements = selector.requirements.withGroup(field, pathRequirements(parsed.list))
                "rank2all", "rank3all", "rank3none" -> {
                    selector.rankReqs = true
                    selector.rankDetails = withRankRequirements(selector.rankDetails, rankOf(field), field.substring(5), parsed.list)
                }
                else -> {
                    invalidLine(lineNumber, line.text)
                    break
                }
            }
        }
    }

    /**
     * Adds the automatic "+1 stat" abilities to the end of tiers 3 and 4 of class, race-class and
     * global trees that have a `Stats:` line. Ported from `basDataLoad.AddStats` (Build/basDataLoad.bas:1846).
     * With one stat the ability is named after it; with several it's "Stat", with a selector per stat.
     * The tier 4 one requires the tier 3 one.
     */
    private fun addStats(treeStyle: TreeStyle) {
        if (treeStyle == TreeStyle.RACE || treeStyle == TreeStyle.DESTINY) {
            return
        }
        if (stats.isEmpty()) {
            return
        }
        // VB counts stats in StatEnum order.
        val ordered = ArrayList<Stat>()
        for (stat in Stat.entries) {
            if (stats.contains(stat)) {
                ordered.add(stat)
            }
        }
        var abilityName = "Stat"
        if (ordered.size == 1) {
            abilityName = ordered[0].displayName
        }
        for (tier in 3..4) {
            val ability = AbilityDraft(abilityName)
            ability.description = "+1 to the selected ability score"
            ability.cost = 2
            ability.ranks = 1
            if (tier > 3) {
                // Style unset (peError), as VB leaves it.
                ability.requirements = Requirements(listOf(Requirement("Tier ${tier - 1}: $abilityName", null)), emptyList(), emptyList())
            }
            if (ordered.size > 1) {
                ability.selectorStyle = SelectorStyle.ROOT
                ability.selectorOnly = true
                for (stat in ordered) {
                    ability.selectors.add(AbilitySelectorDraft(stat.displayName, 2, ability.requirements))
                }
            }
            tiers[tier].add(ability)
        }
    }

    /**
     * Makes each core ability after the first require the one before it (they're taken in order).
     * Ported from `basDataLoad.AddCoreReqs` (Build/basDataLoad.bas:1922), which also fills in the
     * resolved tree and ability numbers; here only the raw path and style are kept, like the rest.
     */
    private fun addCoreRequirements(treeStyle: TreeStyle) {
        var style = PointerStyle.ENHANCEMENT
        if (treeStyle == TreeStyle.DESTINY) {
            style = PointerStyle.DESTINY
        }
        val cores = tiers[0]
        for (i in 1 until cores.size) {
            val ability = cores[i]
            val all = ArrayList(ability.requirements.all)
            all.add(Requirement("Tier 0: ${cores[i - 1].name}", style))
            ability.requirements = ability.requirements.withGroup("all", all)
        }
    }
}

/** Requirements in Enhancements.txt and Destinies.txt are paths, stored raw with no style yet. */
private fun pathRequirements(list: List<String>): List<Requirement> {
    val result = ArrayList<Requirement>()
    for (raw in list) {
        result.add(Requirement(raw, null))
    }
    return result
}

/**
 * Ranks 2 and 3, created if needed. Ported from `basDataLoad.InitRanks` (Build/basDataLoad.bas:1828),
 * which keeps any existing details.
 */
private fun initRanks(details: ArrayList<RankDetail>?): ArrayList<RankDetail> {
    if (details != null) {
        return details
    }
    val result = ArrayList<RankDetail>()
    result.add(RankDetail.EMPTY)
    result.add(RankDetail.EMPTY)
    return result
}

/** Sets one requirement group ("all", "none") of rank [rank] (2 or 3), creating the rank details if needed. */
private fun withRankRequirements(details: ArrayList<RankDetail>?, rank: Int, group: String, list: List<String>): ArrayList<RankDetail> {
    val result = initRanks(details)
    val index = rank - 2
    val old = result[index]
    result[index] = RankDetail(old.cost, old.requirements.withGroup(group, pathRequirements(list)))
    return result
}
