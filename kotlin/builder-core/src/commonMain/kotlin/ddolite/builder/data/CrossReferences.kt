package ddolite.builder.data

import ddolite.builder.model.Ability
import ddolite.builder.model.AbilityTarget
import ddolite.builder.model.Feat
import ddolite.builder.model.FeatTarget
import ddolite.builder.model.PointerStyle
import ddolite.builder.model.RaceRequirement
import ddolite.builder.model.RankDetail
import ddolite.builder.model.Requirement
import ddolite.builder.model.Requirements
import ddolite.builder.model.Selector
import ddolite.builder.model.SelectorStyle
import ddolite.builder.model.Tree
import ddolite.builder.model.TreeStyle
import ddolite.dataformat.Vb

/**
 * The cross-reference pass that runs once every file is loaded: VB's `basProcess.ProcessData`
 * (Build/basProcess.bas:8), for the parts that link trees, abilities and feats:
 * `ValidateRacialTrees`, `ValidateClassTrees`, `ProcessAbilitySelectors`, `ProcessPointers` and
 * `ValidateTreeLockouts`. (ProcessFeatSelectors, ProcessSpells, ProcessTemplates and ProcessFeatMap
 * are in the loaders for those files.)
 *
 * VB resolves requirement text in two different ways, and both are kept, because they give
 * different answers for unusual text:
 * - [parseReqLine] (`ParseReqLine`) for abilities' and their selectors' own requirements. It never
 *   reports anything; what it can't find is left at -1.
 * - [processPointer] (`ProcessPointer`) for everything else (feat requirements, granted feats,
 *   parents, siblings, rank requirements), and for selector requirements `ParseReqLine` left
 *   untouched. It reports what it can't find.
 *
 * Where VB fails silently, or would stop with a run-time error, a warning marked as a VB
 * difference is reported instead, so the validator shows it.
 */
internal class CrossReferences(
    private val data: BuilderData,
    private val log: LoadLog,
) {
    private val featsByName = HashMap<String, Feat>()

    init {
        for (feat in data.feats) {
            if (!featsByName.containsKey(feat.name)) {
                featsByName[feat.name] = feat
            }
        }
    }

    // ---------------------------------------------------------------- tree checks

    /**
     * Each race needs a race tree of its own name, and its `Trees:` must be race-class trees.
     * Ported from `basProcess.ValidateRacialTrees` (Build/basProcess.bas:127).
     */
    fun validateRacialTrees() {
        for (race in data.races) {
            val own = data.findTree(race.name)
            if (own == null || own.style != TreeStyle.RACE) {
                log.error("Races.txt", null, "Race: ${race.name}", "Racial tree not found")
            }
            for (treeName in race.trees) {
                val tree = data.findTree(treeName)
                if (tree == null || tree.style != TreeStyle.RACE_CLASS) {
                    log.error("Races.txt", null, "Race: ${race.name}", "Invalid RaceClass tree: $treeName")
                }
            }
        }
    }

    /** Each class's `Trees:` must be class trees. Ported from `basProcess.ValidateClassTrees` (:153). */
    fun validateClassTrees() {
        for (c in data.classes) {
            for (treeName in c.trees) {
                val tree = data.findTree(treeName)
                if (tree == null || tree.style != TreeStyle.CLASS) {
                    log.error("Classes.txt", null, "Class: ${c.name}", "Invalid class tree: $treeName")
                }
            }
        }
    }

    /**
     * A tree's `Lockout:` must name a class or race-class tree. Ported from
     * `basProcess.ValidateTreeLockouts` (:170).
     */
    fun validateTreeLockouts() {
        for (tree in data.trees) {
            val lockout = tree.lockout
            if (lockout.isNullOrEmpty()) {
                continue
            }
            val target = data.findTree(lockout)
            if (target == null || target.style == TreeStyle.RACE || target.style == TreeStyle.GLOBAL || target.style == TreeStyle.DESTINY) {
                log.error("Enhancements.txt", null, "Tree: ${tree.name}", "Invalid lockout tree: $lockout")
            }
        }
    }

    // ---------------------------------------------------------------- shared selectors

    /**
     * Gives abilities with `SharedSelector:` or `Parent:` their parent's selectors. Ported from
     * `basProcess.ProcessAbilitySelectors` and `ProcessTreeSelectors` (:236, :256).
     *
     * - A "Feat: X" parent: the ability gets the feat's selectors (names, descriptions,
     *   requirements), at the ability's own cost, replacing any it had.
     * - "Tier n: Ability" in the same tree: if the ability has no selectors it gets as many as the
     *   parent; then, position by position, each takes the parent selector's name and requirements,
     *   and its description unless it has one. (Abilities list the same selectors as their parent to
     *   give each tier its own descriptions.)
     */
    fun processAbilitySelectors(trees: List<Tree>, isDestiny: Boolean): List<Tree> {
        val result = ArrayList<Tree>()
        for (tree in trees) {
            result.add(processTreeSelectors(tree, isDestiny))
        }
        return result
    }

    private fun processTreeSelectors(tree: Tree, isDestiny: Boolean): Tree {
        val tiers = ArrayList<ArrayList<Ability>>()
        for (tier in tree.tiers) {
            tiers.add(ArrayList(tier))
        }
        val prefix = if (isDestiny) "Destiny: " else "Enhancement: "
        for (tierIndex in tiers.indices) {
            for (i in tiers[tierIndex].indices) {
                val ability = tiers[tierIndex][i]
                if (ability.selectorStyle != SelectorStyle.SHARED && ability.selectorStyle != SelectorStyle.EXCLUSIVE) {
                    continue
                }
                val parent = ability.parent ?: Requirement("", null)
                val where = treeError(tree, tierIndex, ability)
                if (parent.raw.startsWith("Feat:")) {
                    // VB: SeekFeat(Mid$(raw, 7)), skipping "Feat: ".
                    val featName = vbMidFrom(parent.raw, 7)
                    val feat = featsByName[featName]
                    if (feat == null) {
                        log.error(fileOf(isDestiny), null, prefix + where, "Invalid parent selector: ${parent.raw}")
                        continue
                    }
                    val selectors = ArrayList<Selector>()
                    for (s in feat.selectors) {
                        selectors.add(abilitySelector(s.name, s.description, s.requirements, ability.cost))
                    }
                    tiers[tierIndex][i] = ability.copy(
                        selectors = selectors,
                        parent = parent.copy(style = PointerStyle.FEAT, target = FeatTarget(feat.name, null)),
                    )
                    continue
                }
                val style = if (isDestiny) PointerStyle.DESTINY else PointerStyle.ENHANCEMENT
                val found = findParentInTree(tree, tiers, parent.raw)
                if (found == null) {
                    log.error(fileOf(isDestiny), null, prefix + where, "Invalid parent selector: ${parent.raw}")
                    continue
                }
                val parentAbility = tiers[found.first][found.second]
                var selectors = ArrayList(ability.selectors)
                if (parentAbility.selectors.isNotEmpty() && selectors.isEmpty()) {
                    for (s in parentAbility.selectors) {
                        selectors.add(abilitySelector("", "", Requirements.EMPTY, ability.cost))
                    }
                }
                if (selectors.size > parentAbility.selectors.size) {
                    // VB would stop with "Subscript out of range".
                    log.vbDifference(fileOf(isDestiny), null, "$where has more selectors than its parent ${parent.raw} (VB6 stops with an error)")
                    selectors = ArrayList(selectors.subList(0, parentAbility.selectors.size))
                }
                for (j in selectors.indices) {
                    val own = selectors[j]
                    val from = parentAbility.selectors[j]
                    var description = own.description
                    if (description.isEmpty()) {
                        description = from.description
                    }
                    selectors[j] = own.copy(name = from.name, description = description, requirements = from.requirements, cost = ability.cost)
                }
                val target = AbilityTarget(style, tree.name, found.first, found.second, parentAbility.name, null, 0)
                tiers[tierIndex][i] = ability.copy(selectors = selectors, parent = parent.copy(style = style, target = target))
            }
        }
        return tree.copy(tiers = tiers)
    }

    /**
     * "Tier n: Ability" in the same tree. Ported from `basProcess.ProcessTreeSelectorParent` (:353).
     * Returns (tier, ability index) or null.
     */
    private fun findParentInTree(tree: Tree, tiers: List<List<Ability>>, raw: String): Pair<Int, Int>? {
        if (!raw.startsWith("Tier ")) {
            return null
        }
        val rest = raw.substring(5)
        val colon = rest.indexOf(':')
        if (colon < 0) {
            return null
        }
        val tier = Vb.cLng(Vb.valOf(rest.substring(0, colon)))
        if (tier < 0 || tier >= tiers.size) {
            log.vbDifference(fileName(tree), null, "${tree.name}: parent selector tier out of range: $raw (VB6 stops with an error)")
            return null
        }
        val name = vbMidFrom(rest, colon + 3) // VB: Mid$(strRaw, lngPos + 2), skipping ": "
        for (i in tiers[tier].indices) {
            if (tiers[tier][i].name == name) {
                return Pair(tier, i)
            }
        }
        return null
    }

    // ---------------------------------------------------------------- pointers

    /**
     * Resolves granted feats, feat requirements and every tree's requirements. Ported from
     * `basProcess.ProcessPointers` (:387). Returns the feats, trees and destinies with targets filled in.
     */
    fun processPointers(feats: List<Feat>, trees: List<Tree>, destinies: List<Tree>): Triple<List<Feat>, List<Tree>, List<Tree>> {
        checkGrantedFeats()
        val newFeats = ArrayList<Feat>()
        for (feat in feats) {
            newFeats.add(processFeat(feat))
        }
        val newTrees = ArrayList<Tree>()
        for (tree in trees) {
            newTrees.add(processTree(tree, trees, false))
        }
        val newDestinies = ArrayList<Tree>()
        for (tree in destinies) {
            newDestinies.add(processTree(tree, destinies, true))
        }
        return Triple(newFeats, newTrees, newDestinies)
    }

    /** Race and class `GrantedFeats<level>` must name feats (and selectors). VB stores them as "Feat: X". */
    private fun checkGrantedFeats() {
        for (race in data.races) {
            for (granted in race.grantedFeats) {
                val raw = "Feat: ${granted.featName}"
                processPointerFeat(Requirement(raw, null)) { _ ->
                    log.error("Races.txt", null, "Race: ${race.name}", "Invalid granted feat (level ${granted.level}): $raw")
                }
            }
        }
        for (c in data.classes) {
            for (granted in c.grantedFeats) {
                val raw = "Feat: ${granted.featName}"
                processPointerFeat(Requirement(raw, null)) { _ ->
                    log.error("Classes.txt", null, "Class: ${c.name}", "Invalid granted feat (level ${granted.level}): $raw")
                }
            }
        }
    }

    /** A feat's own and its selectors' requirements (`actProcessFeatReqs` messages). */
    private fun processFeat(feat: Feat): Feat {
        val requirements = processReqs(feat.requirements, null, null) { raw ->
            log.error("Feats.txt", null, "Feat: ${feat.name}", "Invalid requirement: $raw")
        }
        val selectors = ArrayList<Selector>()
        for (selector in feat.selectors) {
            val reqs = processReqs(selector.requirements, null, null) { raw ->
                log.error("Feats.txt", null, "Feat: ${feat.name}: ${selector.name}", "Invalid requirement: $raw")
            }
            selectors.add(selector.copy(requirements = reqs))
        }
        return feat.copy(requirements = requirements, selectors = selectors)
    }

    /**
     * One tree's links: parents, siblings, requirements, selector requirements and rank requirements.
     * Ported from `basProcess.ProcessTree` (:506), in its order.
     */
    private fun processTree(tree: Tree, allOfKind: List<Tree>, isDestiny: Boolean): Tree {
        val style = if (isDestiny) PointerStyle.DESTINY else PointerStyle.ENHANCEMENT
        val prefix = if (isDestiny) "Destiny: " else "Enhancement: "
        val tiers = ArrayList<List<Ability>>()
        for (tierIndex in tree.tiers.indices) {
            val abilities = ArrayList<Ability>()
            for (ability in tree.tiers[tierIndex]) {
                val where = prefix + treeError(tree, tierIndex, ability)
                val fail: (String) -> Unit = { raw -> log.error(fileOf(isDestiny), null, where, "Invalid requirement: $raw") }

                var parent = ability.parent
                if (parent != null) {
                    parent = processPointer(parent, tree, style, fail)
                }
                val siblings = ArrayList<Requirement>()
                for (sibling in ability.siblings) {
                    siblings.add(processPointer(sibling, tree, style, fail))
                }
                // The ability's own requirements: ParseReqLine only (VB reports nothing).
                val parsedOwn = parseReqs(ability.requirements, tree, isDestiny, allOfKind, where)
                val requirements = parsedOwn.requirements
                for (req in parsedOwn.untouched) {
                    warnNotFound(isDestiny, where, req.raw, "feat")
                }

                // Selector requirements: ParseReqLine first, then ProcessPointer for any it left untouched.
                val selectors = ArrayList<Selector>()
                for (selector in ability.selectors) {
                    val parsed = parseReqs(selector.requirements, tree, isDestiny, allOfKind, "$where${selector.name}")
                    var reqs = parsed.requirements
                    reqs = processReqsWhereUntouched(reqs, parsed.untouched, tree, style, fail)
                    var details = selector.rankDetails
                    if (selector.rankReqs && details != null) {
                        details = processRankDetails(details, tree, style, fail)
                    }
                    selectors.add(selector.copy(requirements = reqs, rankDetails = details))
                }
                var rankDetails = ability.rankDetails
                if (ability.rankReqs && rankDetails != null) {
                    rankDetails = processRankDetails(rankDetails, tree, style, fail)
                }
                abilities.add(
                    ability.copy(
                        parent = parent,
                        siblings = siblings,
                        requirements = requirements,
                        selectors = selectors,
                        rankDetails = rankDetails,
                    ),
                )
            }
            tiers.add(abilities)
        }
        return tree.copy(tiers = tiers)
    }

    private fun processRankDetails(details: List<RankDetail>, tree: Tree, style: PointerStyle, fail: (String) -> Unit): List<RankDetail> {
        val result = ArrayList<RankDetail>()
        for (detail in details) {
            result.add(detail.copy(requirements = processReqs(detail.requirements, tree, style, fail)))
        }
        return result
    }

    /** `ProcessReqs` (:584): [processPointer] on every requirement in the three groups. */
    private fun processReqs(reqs: Requirements, tree: Tree?, style: PointerStyle?, fail: (String) -> Unit): Requirements {
        return Requirements(
            processList(reqs.all, tree, style, fail),
            processList(reqs.one, tree, style, fail),
            processList(reqs.none, tree, style, fail),
        )
    }

    private fun processList(list: List<Requirement>, tree: Tree?, style: PointerStyle?, fail: (String) -> Unit): List<Requirement> {
        val result = ArrayList<Requirement>()
        for (req in list) {
            result.add(processPointer(req, tree, style, fail))
        }
        return result
    }

    /** [processReqs], but only for requirements [parseReqLine] left as VB would leave them untouched (0). */
    private fun processReqsWhereUntouched(
        reqs: Requirements,
        untouched: Set<Requirement>,
        tree: Tree,
        style: PointerStyle,
        fail: (String) -> Unit,
    ): Requirements {
        val groups = ArrayList<List<Requirement>>()
        for (list in listOf(reqs.all, reqs.one, reqs.none)) {
            val result = ArrayList<Requirement>()
            for (req in list) {
                if (untouched.contains(req)) {
                    result.add(processPointer(req, tree, style, fail))
                } else {
                    result.add(req)
                }
            }
            groups.add(result)
        }
        return Requirements(groups[0], groups[1], groups[2])
    }

    /**
     * Resolves one requirement or link the `ProcessPointer` way (Build/basProcess.bas:435).
     *
     * "Feat: X" or "Feat: X: Selector" → the feat (`ProcessPointerFeat`). "Tier n: Ability",
     * "Tier n: Ability: Selector", optionally ending " Rank n" → an ability in [tree] (`FindAbility`).
     * Anything else is left unresolved without a message, as in VB – including "<Tree> Tier n: …",
     * which only [parseReqLine] understands.
     */
    private fun processPointer(req: Requirement, tree: Tree?, style: PointerStyle?, fail: (String) -> Unit): Requirement {
        if (req.raw.isEmpty() || req.target != null) {
            return req
        }
        val colon = req.raw.indexOf(": ")
        if (colon < 0) {
            log.vbDifference(fileOf(style == PointerStyle.DESTINY), null, "Requirement has no \": \": ${req.raw} (VB6 stops with an error)")
            return req
        }
        val field = req.raw.substring(0, colon)
        val split = splitRank(req.raw.substring(colon + 2))
        if (field == "Feat") {
            return processPointerFeat(req, fail)
        }
        if (field.startsWith("Tier") && tree != null && style != null) {
            val tierPos = field.indexOf("Tier ")
            if (tierPos != 0) {
                return req // not reached by the shipped data; VB would look in the wrong list here
            }
            val tier = Vb.cLng(Vb.valOf(field.substring(5)))
            return findAbility(req.copy(style = style), tree, style, tier, split.first, split.second, fail)
        }
        log.vbDifference(fileOf(style == PointerStyle.DESTINY), null, "Requirement not resolved: ${req.raw} (VB6 ignores it)")
        return req
    }

    /**
     * "Feat: Name" or "Feat: Name: Selector". Ported from `basProcess.ProcessPointerFeat` (:595).
     * VB looks the feat up with the whole text after "Feat: ", so a " Rank n" ending isn't removed.
     * If the selector isn't found, VB keeps the feat (without a selector) and reports the error.
     */
    private fun processPointerFeat(req: Requirement, fail: (String) -> Unit): Requirement {
        var text = req.raw
        if (text.startsWith("Feat: ")) {
            text = text.substring(6)
        }
        val separator = text.indexOf(": ")
        if (separator < 0) {
            val feat = featsByName[text]
            if (feat == null) {
                fail(req.raw)
                return req.copy(style = PointerStyle.FEAT)
            }
            return req.copy(style = PointerStyle.FEAT, target = FeatTarget(feat.name, null))
        }
        val feat = featsByName[text.substring(0, separator)]
        if (feat == null) {
            fail(req.raw)
            return req.copy(style = PointerStyle.FEAT)
        }
        val selectorName = text.substring(separator + 2)
        if (feat.findSelector(selectorName) == null) {
            fail(req.raw)
            return req.copy(style = PointerStyle.FEAT, target = FeatTarget(feat.name, null))
        }
        return req.copy(style = PointerStyle.FEAT, target = FeatTarget(feat.name, selectorName))
    }

    /**
     * An ability (and selector) in tier [tier] of [tree]. Ported from `basProcess.FindAbility` (:632).
     * "Spell Critical Chance: …" and "Spell Critical: …" are ability names containing ": ", so they
     * aren't split. A missing selector is noted (VB writes it to the log without counting an error)
     * and the ability is kept without a selector.
     */
    private fun findAbility(
        req: Requirement,
        tree: Tree,
        style: PointerStyle,
        tier: Int,
        text: String,
        rank: Int,
        fail: (String) -> Unit,
    ): Requirement {
        var abilityName = text
        var selectorName = ""
        if (!text.startsWith("Spell Critical Chance: ") && !text.startsWith("Spell Critical: ")) {
            val separator = text.indexOf(": ")
            if (separator >= 0) {
                abilityName = text.substring(0, separator)
                selectorName = text.substring(separator + 2)
            }
        }
        if (tier < 0 || tier >= tree.tiers.size) {
            log.vbDifference(fileOf(style == PointerStyle.DESTINY), null, "${tree.name}: requirement tier out of range: ${req.raw} (VB6 stops with an error)")
            return req
        }
        val abilities = tree.tiers[tier]
        var index = -1
        for (i in abilities.indices) {
            if (abilities[i].name == abilityName) {
                index = i
                break
            }
        }
        if (index < 0) {
            fail(req.raw)
            return req
        }
        if (selectorName.isEmpty()) {
            return req.copy(target = AbilityTarget(style, tree.name, tier, index, abilityName, null, rank))
        }
        if (abilities[index].findSelector(selectorName) == null) {
            log.error(fileOf(style == PointerStyle.DESTINY), null, "${tree.name} Tier $tier selector not found: $text")
            return req.copy(target = AbilityTarget(style, tree.name, tier, index, abilityName, null, rank))
        }
        return req.copy(target = AbilityTarget(style, tree.name, tier, index, abilityName, selectorName, rank))
    }

    /** Requirements resolved by [parseReqLine], and those VB would leave untouched for ProcessPointer. */
    private class ParsedReqs(val requirements: Requirements, val untouched: Set<Requirement>)

    private fun parseReqs(reqs: Requirements, tree: Tree, isDestiny: Boolean, allOfKind: List<Tree>, where: String): ParsedReqs {
        val untouched = HashSet<Requirement>()
        val groups = ArrayList<List<Requirement>>()
        for (list in listOf(reqs.all, reqs.one, reqs.none)) {
            val result = ArrayList<Requirement>()
            for (req in list) {
                val outcome = parseReqLine(req, tree, isDestiny, allOfKind, where)
                result.add(outcome.first)
                if (outcome.second) {
                    untouched.add(outcome.first)
                }
            }
            groups.add(result)
        }
        return ParsedReqs(Requirements(groups[0], groups[1], groups[2]), untouched)
    }

    /**
     * Resolves a requirement the `ParseReqLine` way (Build/basProcess.bas:693). Returns the requirement
     * and whether VB leaves it "untouched" (its Feat and Ability still 0), so that ProcessPointer
     * tries it afterwards.
     *
     * - Text containing "feat:" (any case, anywhere) is a feat: split on ':' – "Feat: X" or
     *   "Feat: X: Selector" (exactly three parts; more and the selector is ignored).
     * - Otherwise "Tier n: Ability[ Rank r][: Selector[ Rank r]]" in this tree, or
     *   "<Tree> Tier n: …" in another enhancement tree (in a destiny, VB uses the destiny itself).
     *   A rank on the selector replaces the ability's (and a selector without one sets it back to 0).
     *
     * VB reports nothing here; things it can't find become warnings marked as VB differences.
     */
    private fun parseReqLine(req: Requirement, tree: Tree, isDestiny: Boolean, allOfKind: List<Tree>, where: String): Pair<Requirement, Boolean> {
        val raw = req.raw
        if (!raw.contains(':')) {
            return Pair(req, true)
        }
        val parts = Vb.split(raw, ":")
        if (raw.lowercase().contains("feat:")) {
            val featName = Vb.trim(parts[1])
            val feat = featsByName[featName]
            if (feat == null) {
                return Pair(req.copy(style = PointerStyle.FEAT), true) // Feat = 0: ProcessPointer reports it
            }
            if (parts.size == 3) {
                val selectorName = Vb.trim(parts[2])
                if (feat.findSelector(selectorName) == null) {
                    warnNotFound(isDestiny, where, raw, "selector")
                    return Pair(req.copy(style = PointerStyle.FEAT), false)
                }
                return Pair(req.copy(style = PointerStyle.FEAT, target = FeatTarget(feat.name, selectorName)), false)
            }
            return Pair(req.copy(style = PointerStyle.FEAT, target = FeatTarget(feat.name, null)), false)
        }

        val style = if (isDestiny) PointerStyle.DESTINY else PointerStyle.ENHANCEMENT
        val p0 = parts[0]
        var target: Tree? = tree
        val tierText: String
        if (Vb.trim(p0).take(4).lowercase() == "tier") {
            tierText = p0
        } else {
            val tierAt = p0.indexOf("Tier")
            if (tierAt < 0) {
                log.vbDifference(fileOf(isDestiny), null, "$where: requirement has no tier: $raw (VB6 stops with an error)")
                return Pair(req, false)
            }
            if (!isDestiny) {
                val treeName = Vb.trim(p0.substring(0, tierAt))
                target = findExact(allOfKind, treeName)
                if (target == null) {
                    log.vbDifference(fileOf(isDestiny), null, "$where: requirement names an unknown tree: $raw (VB6 stops with an error)")
                    return Pair(req, false)
                }
            }
            tierText = p0.substring(tierAt)
        }
        val words = Vb.split(tierText, " ")
        val tier = if (words.size > 1) Vb.cLng(Vb.valOf(words[1])) else -1
        if (target == null || tier < 0 || tier >= target.tiers.size) {
            log.vbDifference(fileOf(isDestiny), null, "$where: requirement tier out of range: $raw (VB6 stops with an error)")
            return Pair(req, false)
        }
        var split = splitRank(parts[1])
        var rank = split.second
        val abilityName = Vb.trim(split.first)
        var index = -1
        val abilities = target.tiers[tier]
        for (i in abilities.indices) {
            if (abilities[i].name == abilityName) {
                index = i
                break
            }
        }
        var selectorName: String? = null
        if (parts.size > 2) {
            split = splitRank(parts[2])
            rank = split.second
            selectorName = Vb.trim(split.first)
            if (index < 0) {
                log.vbDifference(fileOf(isDestiny), null, "$where: requirement not found: $raw (VB6 stops with an error)")
                return Pair(req.copy(style = style), false)
            }
            if (abilities[index].findSelector(selectorName) == null) {
                warnNotFound(isDestiny, where, raw, "selector")
                return Pair(req.copy(style = style), false)
            }
        }
        if (index < 0) {
            warnNotFound(isDestiny, where, raw, "ability")
            return Pair(req.copy(style = style), false)
        }
        // A reference to another enhancement tree is still an enhancement (style from this tree).
        return Pair(req.copy(style = style, target = AbilityTarget(style, target.name, tier, index, abilityName, selectorName, rank)), false)
    }

    private fun warnNotFound(isDestiny: Boolean, where: String, raw: String, what: String) {
        log.vbDifference(fileOf(isDestiny), null, "$where: requirement's $what not found: $raw (VB6 doesn't report this)")
    }

    private fun findExact(trees: List<Tree>, name: String): Tree? {
        for (t in trees) {
            if (t.name == name) {
                return t
            }
        }
        return null
    }
}

/**
 * A " Rank n" ending: returns the text without it and the rank (0 if none). Ported from
 * `basProcess.ProcessRank` / `GetAbilityReqRank` (:489, :497): the text must be at least 8
 * characters, with " Rank " just before the last character, which is the rank.
 */
internal fun splitRank(text: String): Pair<String, Int> {
    if (text.length < 8) {
        return Pair(text, 0)
    }
    if (text.substring(text.length - 7, text.length - 1) != " Rank ") {
        return Pair(text, 0)
    }
    val rank = Vb.cLng(Vb.valOf(text.substring(text.length - 1)))
    return Pair(text.substring(0, text.length - 7), rank)
}

/**
 * VB's `TreeError` (Build/basErrorLog.bas): "<tree> Tier <n>: <ability> " – with the trailing space.
 */
private fun treeError(tree: Tree, tier: Int, ability: Ability): String = "${tree.name} Tier $tier: ${ability.name} "

private fun fileOf(isDestiny: Boolean): String = if (isDestiny) "Destinies.txt" else "Enhancements.txt"

private fun fileName(tree: Tree): String = fileOf(tree.style == TreeStyle.DESTINY)

/** VB's `Mid$(text, start)` with a 1-based start; past the end gives "". */
private fun vbMidFrom(text: String, start: Int): String {
    val from = start - 1
    if (from >= text.length) {
        return ""
    }
    return text.substring(from)
}

/** A selector of an ability, with only the fields abilities use (see TreesLoader). */
internal fun abilitySelector(name: String, description: String, requirements: Requirements, cost: Int): Selector = Selector(
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
    race = RaceRequirement.NONE,
    hide = false,
    notRaces = null,
    raceOnly = null,
    classOnly = null,
    cost = cost,
)
