package ddolite.builder.build

import ddolite.builder.data.LoadLog
import ddolite.builder.data.loadBuilderData
import ddolite.builder.model.Skill
import ddolite.builder.model.Stat
import ddolite.builder.model.CharacterClass
import ddolite.builder.rules.SkillGrid
import ddolite.builder.rules.SkillSpend
import ddolite.dataformat.DirectoryDataSource
import java.io.File
import java.nio.file.Path
import kotlin.test.Test

/**
 * Drafting aid for the golden builds (scratch; runs only while test-builds/golden/.draft exists):
 * fills empty skills from a priority list and empty spell slots from the class list, saves the
 * build back, and writes the feats each empty or red slot could take to build/golden-draft.txt.
 */
class GoldenBuildDrafter {
    private val NL = "\n"
    private val data = loadBuilderData(DirectoryDataSource(Path.of(System.getProperty("ddolite.dataDir"))), LoadLog())

    private val skillPriorities = mapOf(
        "A" to listOf(Skill.UMD, Skill.TUMBLE, Skill.SPOT, Skill.SEARCH, Skill.DISABLE_DEVICE, Skill.OPEN_LOCK, Skill.SPELLCRAFT, Skill.CONCENTRATION, Skill.BALANCE, Skill.JUMP, Skill.INTIMIDATE, Skill.HIDE, Skill.MOVE_SILENTLY, Skill.LISTEN, Skill.HAGGLE, Skill.REPAIR),
        "B" to listOf(Skill.CONCENTRATION, Skill.UMD, Skill.HEAL, Skill.SPELLCRAFT, Skill.DIPLOMACY, Skill.BALANCE, Skill.JUMP, Skill.TUMBLE, Skill.BLUFF, Skill.INTIMIDATE, Skill.PERFORM, Skill.HAGGLE),
        "C" to listOf(Skill.CONCENTRATION, Skill.UMD, Skill.SPELLCRAFT, Skill.REPAIR, Skill.DISABLE_DEVICE, Skill.SEARCH, Skill.SPOT, Skill.HEAL, Skill.DIPLOMACY, Skill.BALANCE, Skill.JUMP, Skill.SWIM),
        "D" to listOf(Skill.INTIMIDATE, Skill.JUMP, Skill.BALANCE, Skill.SWIM, Skill.UMD, Skill.SPOT),
    )

    private val extraTrees = mapOf(
        "A" to listOf("Archmage", "Thief-Acrobat", "Kensei", "Falconry", "Human"),
        "B" to listOf("War Soul", "Beacon of Hope", "Angel of Vengeance", "Tainted Scholar", "Aasimar", "Exalted Angel", "Divine Crusader"),
        "C" to listOf("Radiant Servant", "Divine Disciple", "Arcanotechnician", "Battle Engineer", "Purple Dragon Knight"),
        "D" to listOf("Kensei", "Stalwart Defender", "Warpriest", "Dwarf", "Legendary Dreadnought"),
    )

    @Test
    fun draft() {
        val folder = File(System.getProperty("ddolite.testBuildsDir"), "golden")
        if (!File(folder, ".draft").exists()) {
            return
        }
        val report = StringBuilder()
        val files = folder.listFiles() ?: return
        for (file in files) {
            if (!file.name.endsWith(".build")) {
                continue
            }
            val loaded = readBuildFile(file.readBytes(), data, BuildPoints.ADVENTURER)
            val rules = loaded.rules
            val build = loaded.build
            report.append("== ").append(file.name).append('\n')
            val priorities = skillPriorities[file.name.substring(0, 1)]
            var changed = false
            if (priorities != null) {
                changed = fillSkills(rules, priorities)
            }
            if (fillSpells(rules)) {
                changed = true
            }
            if (changed) {
                file.writeBytes(writeBuildFile(rules))
            }

            val again = readBuildFile(file.readBytes(), data, BuildPoints.ADVENTURER).rules
            val featRules = FeatRules(again)
            featRules.initFeatList(null, -1)
            featRules.checkSlotErrors()
            for (entry in again.featList) {
                if (entry.actualType == BuildFeatType.GRANTED || (entry.feat != null && !entry.errorState)) {
                    continue
                }
                val names = ArrayList<String>()
                for (feat in data.feats) {
                    if (featRules.checkFeatSlot(feat, entry.slot, selectorRequired = false).state == DropState.CAN_DROP) {
                        names.add(feat.name)
                    }
                }
                report.append("  ").append(entry.actualType).append(' ').append(entry.level).append(' ')
                    .append(entry.display).append(" [").append(entry.errorText).append("]: ")
                    .append(names.joinToString(", ")).append('\n')
            }
            val listed = ArrayList<String>()
            for (bt in build.trees) {
                listed.add(bt.treeName)
            }
            for (name in (extraTrees[file.name.substring(0, 1)] ?: emptyList())) {
                if (!listed.contains(name)) {
                    val tree = data.findTree(name) ?: data.findDestiny(name)
                    if (tree == null) {
                        report.append("  no tree ").append(name).append(NL)
                        continue
                    }
                    report.append("  all ").append(name).append(": ")
                    for (t in tree.tiers.indices) {
                        for (ab in tree.tiers[t]) {
                            report.append("T").append(t).append(" ").append(ab.name).append(if (ab.ranks > 1) "(x" + ab.ranks + ")" else "").append(if (ab.selectors.isNotEmpty()) "{" + ab.selectors.joinToString("|") { it.name } + "}" else "").append(" c").append(ab.cost).append("; ")
                        }
                    }
                    report.append(NL)
                }
            }
            for (bt in build.trees) {
                val tree = data.findTree(bt.treeName) ?: continue
                report.append("  tree ").append(bt.treeName).append(": ")
                val enh = EnhancementRules(again)
                val againTree = again.build.trees[build.trees.indexOf(bt)]
                for (ref in enh.availableAbilities(tree, againTree, false)) {
                    val ab = tree.tiers[ref.tier][ref.ability]
                    report.append("T").append(ref.tier).append(" ").append(ab.name).append(if (ab.ranks > 1) "(x" + ab.ranks + ")" else "").append(if (ab.selectors.isNotEmpty()) "{" + ab.selectors.joinToString("|") { it.name } + "}" else "").append("; ")
                }
                report.append('\n')
            }
        }
        File("build").mkdirs()
        File("build/golden-draft.txt").writeText(report.toString())
    }

    /** If the build has no skill points yet, spends each level's points down [priorities]. */
    private fun fillSkills(rules: BuildRules, priorities: List<Skill>): Boolean {
        val build = rules.build
        for (skill in Skill.entries) {
            for (level in 1..HEROIC_LEVELS) {
                if (build.skills[skill.ordinal][level - 1] != 0) {
                    return false
                }
            }
        }
        val heroic = rules.heroicLevels()
        val classes = ArrayList<CharacterClass>()
        val modifiers = ArrayList<Int>()
        val statRules = StatRules(rules)
        for (level in 1..heroic) {
            classes.add(build.levelClass(level)!!)
            modifiers.add(statRules.stat(Stat.INT, level, modifier = true))
        }
        val grid = SkillGrid(classes, build.race?.skillPoints ?: 0, modifiers)
        val spend = SkillSpend()
        for (level in 1..heroic) {
            for (skill in priorities) {
                while (grid.increment(spend, skill, level)) {
                    // until the cap or the level's points
                }
            }
        }
        for (skill in Skill.entries) {
            for (level in 1..heroic) {
                build.skills[skill.ordinal][level - 1] = spend.get(skill, level)
            }
        }
        return true
    }

    /** Empty standard and free spell slots get the first spells on the class list not already taken. */
    private fun fillSpells(rules: BuildRules): Boolean {
        var changed = false
        val spells = SpellRules(rules)
        for (cs in rules.build.classSpells) {
            for (spellLevel in 1..cs.maxSpellLevel) {
                for (slot in cs.level(spellLevel).slots) {
                    if (slot.spell.isNotEmpty() || (slot.type != SpellSlotType.STANDARD && slot.type != SpellSlotType.FREE)) {
                        continue
                    }
                    val available = spells.availableSpells(cs.characterClass, spellLevel)
                    if (available.isNotEmpty()) {
                        slot.spell = available[0].name
                        changed = true
                    }
                }
            }
        }
        return changed
    }
}
