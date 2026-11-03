package ddolite.builder.build

import ddolite.builder.model.Feat

/**
 * The groups of the Messages screen's list. Ported from `DeprecateGroupEnum` (frmDeprecate.frm:195):
 * an item's ItemData is the group plus its 1-based place in that group's list.
 *
 * Not copied: the War Soul, binary tree, binary destiny and twist groups, which only binary
 * (.bld) builds produce; the prototype doesn't read those.
 */
enum class MessageGroup(val base: Int) {
    NAME_CHANGE(1000),
    FEAT(2000),
    ENHANCEMENT(3000),
    GUIDE(4000),
    DESTINY(5000),
}

/** One line of the Messages screen's list: its text, group, and 1-based place in that group. */
class DeprecationMessage(val text: String, val group: MessageGroup, val index: Int)

/**
 * The Messages screen's rules (frmDeprecate): the list of what a build file had that the current
 * data doesn't, details for each, the feats offered as replacements, and resolving a message.
 * [deprecations] is VB's `gtypDeprecate`; resolving a message removes it from there.
 */
class DeprecationMessages(private val rules: BuildRules, val deprecations: Deprecations) {
    private val data = rules.data
    private val build = rules.build

    /**
     * Issues (feats to replace) and notices (everything else). Ported from `CountMessages`
     * (frmDeprecate.frm:270), without the binary-only counts.
     *
     * VB quirk, kept: destiny abilities are counted, but ShowNotices doesn't list them (that code is
     * commented out), so they'd keep the count above 0. (VB stops loading at a missing destiny
     * ability anyway: VB-BUGS #1.)
     */
    fun countMessages(): Pair<Int, Int> {
        val issues = deprecations.feats.size
        var notices = deprecations.nameChanges.size
        notices = notices + deprecations.enhancements.size
        if (deprecations.levelingGuide != null) {
            notices = notices + 1
        }
        notices = notices + deprecations.destinies.size
        return Pair(issues, notices)
    }

    /** "2 Issues and 1 Message", or "No issues". Ported from `WriteLabel` (frmDeprecate.frm:251). */
    fun label(): String {
        val (issues, notices) = countMessages()
        var message = ""
        if (issues > 0) {
            message = if (issues == 1) "1 Issue" else "$issues Issues"
        }
        if (notices > 0) {
            if (message.isNotEmpty()) {
                message = message + " and "
            }
            message = message + if (notices == 1) "1 Message" else "$notices Messages"
        }
        if (message.isEmpty()) {
            message = "No issues"
        }
        return message
    }

    /** The list: issues first, then notices. Ported from `ShowIssues` (:294) and `ShowNotices` (:362). */
    fun messages(): List<DeprecationMessage> {
        val result = ArrayList<DeprecationMessage>()
        for (i in deprecations.feats.indices) {
            result.add(DeprecationMessage("Feats: " + featIssue(deprecations.feats[i]), MessageGroup.FEAT, i + 1))
        }
        for (i in deprecations.nameChanges.indices) {
            val change = deprecations.nameChanges[i]
            // NameChangeNotice (:391)
            result.add(DeprecationMessage("Feats: \"" + change.oldName + "\" renamed to \"" + change.newName + "\"", MessageGroup.NAME_CHANGE, i + 1))
        }
        for (i in deprecations.enhancements.indices) {
            result.add(DeprecationMessage("Enhancements: " + abilityNotice(deprecations.enhancements[i]), MessageGroup.ENHANCEMENT, i + 1))
        }
        val guide = deprecations.levelingGuide
        if (guide != null) {
            result.add(DeprecationMessage("Leveling Guide: " + abilityNotice(guide), MessageGroup.GUIDE, 1))
        }
        return result
    }

    /**
     * "Name: Selector not found (taken as a Heroic feat at level 3)". Ported from `FeatIssue`
     * (frmDeprecate.frm:307).
     *
     * VB quirk, kept: with no slot (an index past the list), the type is blank, and InStr finds ""
     * in "aeiou", so it reads "taken as an  feat at level 0".
     */
    fun featIssue(feat: DeprecatedFeat): String {
        var type = ""
        var level = 0
        val slots = build.feats(feat.type)
        if (feat.index < slots.size) {
            level = slots[feat.index].level
            type = featTypeName(feat.type, level)
        }
        var result = feat.featName
        if (feat.selectorName.isNotEmpty()) {
            result = result + ": " + feat.selectorName
        }
        result = result + " not found (taken as a"
        if (type.isEmpty() || "aeiou".contains(type.substring(0, 1).lowercase())) {
            result = result + "n"
        }
        return result + " " + type + " feat at level " + level + ")"
    }

    /**
     * The kind of slot, for messages. Ported from `GetFeatTypeName` (frmDeprecate.frm:326).
     *
     * VB quirk, kept: levels 26, 28 and 29 are named "Destiny", but standard slots there don't
     * exist; and Granted gives "" (VB's Select has no case for it).
     */
    fun featTypeName(type: BuildFeatType, level: Int): String {
        when (type) {
            BuildFeatType.STANDARD -> {
                if (level == 1 || level == 3 || level == 6 || level == 9 || level == 12 || level == 15 || level == 18) {
                    return "Heroic"
                }
                if (level == 21 || level == 24 || level == 27 || level == 30) {
                    return "Epic"
                }
                if (level == 26 || level == 28 || level == 29) {
                    return "Destiny"
                }
                return "Standard"
            }
            BuildFeatType.LEGEND -> return "Legend"
            BuildFeatType.RACE -> return "Racial"
            BuildFeatType.CLASS1, BuildFeatType.CLASS2, BuildFeatType.CLASS3 -> {
                if (level > 0 && level < 21) {
                    val characterClass = build.levelClasses[level - 1]
                    return characterClass?.name ?: "" // GetClassName(ceAny) is blank
                }
                return "Class"
            }
            BuildFeatType.DEITY -> return "Deity"
            BuildFeatType.ALTERNATE -> return "Alternate"
            BuildFeatType.EXCHANGE -> return "Exchange"
            BuildFeatType.GRANTED -> return ""
        }
    }

    /** "Tree Tier n: Ability: Selector not found". Ported from `AbilityNotice` (frmDeprecate.frm:400). */
    fun abilityNotice(ability: DeprecatedAbility): String {
        var result = ability.treeName + " Tier " + ability.tier + ": " + ability.abilityName
        if (ability.selectorName.isNotEmpty()) {
            result = result + ": " + ability.selectorName
        }
        return result + " not found"
    }

    /** The details box for a message: the details part of `lstMessage_Click` (frmDeprecate.frm:420). */
    fun details(message: DeprecationMessage): List<DetailLine> {
        val details = DetailsBuilder()
        when (message.group) {
            MessageGroup.FEAT -> featDetails(details, message.index)
            MessageGroup.NAME_CHANGE -> {
                details.addText("Name changes are automatic.")
                details.addText("Nothing to resolve.")
            }
            MessageGroup.ENHANCEMENT -> abilityDetails(details, deprecations.enhancements[message.index - 1], "Enhancement not found or invalid:")
            MessageGroup.GUIDE -> {
                val guide = deprecations.levelingGuide
                if (guide != null) {
                    // A text build's guide is cut short at the line; only binary builds reset it.
                    abilityDetails(details, guide, "Leveling Guide truncated at invalid ability:")
                }
            }
            MessageGroup.DESTINY -> abilityDetails(details, deprecations.destinies[message.index - 1], "Destiny ability not found or invalid:")
        }
        return details.build()
    }

    /** Ported from `ShowAbilityDetails` (frmDeprecate.frm:483). */
    private fun abilityDetails(details: DetailsBuilder, ability: DeprecatedAbility, header: String) {
        if (header.isNotEmpty()) {
            details.addText(header)
            details.addText("")
        }
        details.addText("Tree: " + ability.treeName)
        if (ability.guideTreeDisplay.isNotEmpty()) {
            details.addText("TreeID: " + ability.guideTreeDisplay)
        }
        details.addText("Tier: " + ability.tier)
        details.addText("Ability: " + ability.abilityName)
        if (ability.selectorName.isNotEmpty()) {
            details.addText("Selector: " + ability.selectorName)
        } else {
            details.addText("Selector: [None]")
        }
        details.addText("Rank: " + ability.rank)
    }

    /**
     * Ported from `ShowFeatDetails` (frmDeprecate.frm:600). A text build never sets VB's numeric
     * `.Selector`, so a feat that's found always reads "Selector: Unknown" or the selector's name.
     */
    private fun featDetails(details: DetailsBuilder, index: Int) {
        val deprecated = deprecations.feats[index - 1]
        val slots = build.feats(deprecated.type)
        if (slots.size <= deprecated.index) {
            return
        }
        val slot = slots[deprecated.index]
        val found = data.findFeatIgnoringCase(deprecated.featName)
        if (found != null) {
            details.addText("Selector not found")
        } else {
            details.addText("Feat not found")
        }
        details.addText("")
        details.addText("Feat: " + deprecated.featName)
        if (found != null && found.selectors.isNotEmpty()) {
            if (deprecated.selectorName.isNotEmpty()) {
                details.addText("Selector: " + deprecated.selectorName)
            } else {
                details.addText("Selector: Unknown")
            }
            details.addText("")
        }
        var type = featTypeName(deprecated.type, slot.level)
        details.addText("Type: $type")
        details.addText("Level: " + slot.level)
        if (deprecated.type.classIndex >= 0) {
            details.addText(type + " Level: " + slot.classLevel)
        } else if (deprecated.type == BuildFeatType.ALTERNATE || deprecated.type == BuildFeatType.EXCHANGE) {
            val childType = deprecated.childType ?: return
            val parents = build.feats(childType)
            if (parents.size > deprecated.childIndex) {
                val parent = parents[deprecated.childIndex]
                details.addText("")
                details.addText("Parent Slot")
                type = featTypeName(parent.type, parent.level)
                details.addText("Type: $type")
                details.addText("Level: " + parent.level)
                if (parent.type.classIndex >= 0) {
                    details.addText(type + " Level: " + parent.classLevel)
                }
                details.addText("Feat: " + parent.featName)
                val parentFeat = data.findFeat(parent.featName)
                if (parentFeat != null && parent.selector > 0) {
                    details.addText("Selector: " + parentFeat.selectors[parent.selector - 1].name)
                }
            }
        }
    }

    /** Every selectable feat, in data order. The other half of `ListFeats` (frmDeprecate.frm:561). */
    fun selectableFeats(): List<Feat> {
        val result = ArrayList<Feat>()
        for (feat in data.feats) {
            if (feat.selectable) {
                result.add(feat)
            }
        }
        return result
    }

    /**
     * Best Guess: up to 10 feats nearest the missing feat's name, stopping at the first more than
     * four times as far as the nearest. Ported from `FindSimilarFeats` (frmDeprecate.frm:580).
     *
     * VB quirks, kept: unselectable feats are offered too, and feats the same distance away come in
     * the order VB's QuickSort2 leaves them (it isn't a stable sort).
     */
    fun similarFeats(featName: String): List<Feat> {
        var name = featName
        val colon = name.indexOf(':')
        if (colon >= 0) {
            name = name.substring(0, colon)
        }
        val feats = data.feats
        // VB's lngDistance(1 To 2, 1 To db.Feats): row 1 the feat, row 2 the distance; 1-based here too.
        val ids = IntArray(feats.size + 1)
        val distances = IntArray(feats.size + 1)
        for (i in 1..feats.size) {
            ids[i] = i
            distances[i] = levenshteinDistance(name, feats[i - 1].name)
        }
        quickSort2(ids, distances, 1, feats.size)
        val result = ArrayList<Feat>()
        for (i in 1..10) {
            if (distances[i] > distances[1] * 4) {
                break
            }
            result.add(feats[ids[i] - 1])
        }
        return result
    }

    /**
     * Puts the chosen feat and selector (0 for none) in the message's slot. The build part of
     * `ChooseFeat` (frmDeprecate.frm:654); the caller cascades and resolves the message.
     */
    fun chooseFeat(index: Int, featName: String, selector: Int) {
        val deprecated = deprecations.feats[index - 1]
        val slots = build.feats(deprecated.type)
        if (slots.size > deprecated.index) {
            val slot = slots[deprecated.index]
            slot.featName = featName
            slot.selector = selector
        }
    }

    /**
     * Removes the message from its list. Ported from `ResolveMessage` (frmDeprecate.frm:819);
     * returns true when none are left (VB then clears `Deprecated`, hides Tools › Messages and
     * closes the window).
     */
    fun resolve(message: DeprecationMessage): Boolean {
        when (message.group) {
            MessageGroup.FEAT -> deprecations.feats.removeAt(message.index - 1)
            MessageGroup.NAME_CHANGE -> deprecations.nameChanges.removeAt(message.index - 1)
            MessageGroup.ENHANCEMENT -> deprecations.enhancements.removeAt(message.index - 1)
            MessageGroup.GUIDE -> deprecations.levelingGuide = null
            MessageGroup.DESTINY -> deprecations.destinies.removeAt(message.index - 1)
        }
        val (issues, notices) = countMessages()
        return issues + notices == 0
    }
}

/**
 * The number of insertions, deletions and substitutions to turn [s1] into [s2], ignoring case.
 * Ported from `LevenshteinDistance` (frmDeprecate.frm:894). VB compares letters with StrComp's
 * vbTextCompare, which follows the Windows locale; here letters are compared ignoring case.
 */
fun levenshteinDistance(s1: String, s2: String): Int {
    val l1 = s1.length
    val l2 = s2.length
    val d = Array(l1 + 1) { IntArray(l2 + 1) }
    for (i in 0..l1) {
        d[i][0] = i
    }
    for (j in 0..l2) {
        d[0][j] = j
    }
    for (j in 1..l2) {
        for (i in 1..l1) {
            val cost = if (s1[i - 1].equals(s2[j - 1], ignoreCase = true)) 0 else 1
            val insertion = d[i - 1][j] + 1
            val deletion = d[i][j - 1] + 1
            val substitution = d[i - 1][j - 1] + cost
            if (insertion <= deletion) {
                d[i][j] = if (insertion <= substitution) insertion else substitution
            } else {
                d[i][j] = if (deletion <= substitution) deletion else substitution
            }
        }
    }
    return d[l1][l2]
}

/**
 * Sorts [ids] and [distances] together by distance, from [left] to [right] (1-based). Ported from
 * `QuickSort2` (frmDeprecate.frm:930) as `FindSimilarFeats` calls it (dimension 1, column 2), so
 * equal distances end up in the same order as in VB.
 */
internal fun quickSort2(ids: IntArray, distances: IntArray, left: Int, right: Int) {
    var first = left
    var last = right
    val mid = distances[(left + right) / 2]
    do {
        while (distances[first] < mid && first < right) {
            first++
        }
        while (mid < distances[last] && last > left) {
            last--
        }
        if (first <= last) {
            var swap = ids[first]
            ids[first] = ids[last]
            ids[last] = swap
            swap = distances[first]
            distances[first] = distances[last]
            distances[last] = swap
            first++
            last--
        }
    } while (first <= last)
    if (left < last) {
        quickSort2(ids, distances, left, last)
    }
    if (first < right) {
        quickSort2(ids, distances, first, right)
    }
}
