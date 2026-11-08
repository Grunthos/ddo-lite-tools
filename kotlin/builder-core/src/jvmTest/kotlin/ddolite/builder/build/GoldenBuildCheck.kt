package ddolite.builder.build

import ddolite.builder.data.LoadLog
import ddolite.builder.data.loadBuilderData
import ddolite.builder.model.Skill
import ddolite.builder.model.Stat
import ddolite.builder.model.CharacterClass
import ddolite.builder.rules.RankStatus
import ddolite.builder.rules.SkillGrid
import ddolite.dataformat.DirectoryDataSource
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The golden test builds (kotlin/test-builds/golden): builds written to cover every feature, to be
 * opened and saved in VB 4.15 and captured as goldens (PLAN.md §6). Each must open with no
 * Messages and nothing the screens would show in red, except a build whose name contains "errors",
 * which is meant to have them. Writes the full report to build/golden-check.txt.
 */
class GoldenBuildCheck {
    private val data = loadBuilderData(DirectoryDataSource(Path.of(System.getProperty("ddolite.dataDir"))), LoadLog())
    private val info = ArrayList<String>()

    @Test
    fun goldenBuildsAreClean() {
        val folder = File(System.getProperty("ddolite.testBuildsDir"), "golden")
        val files = folder.listFiles() ?: return
        files.sortBy { it.name }
        val report = StringBuilder()
        val unexpected = ArrayList<String>()
        for (file in files) {
            if (!file.name.endsWith(".build")) {
                continue
            }
            info.clear()
            val issues = check(file)
            report.append("== ").append(file.name).append(" (").append(issues.size).append(" issues)\n")
            for (line in info) {
                report.append("  info: ").append(line).append('\n')
            }
            for (issue in issues) {
                report.append("  ").append(issue).append('\n')
            }
            if (issues.isNotEmpty() && !file.name.contains("errors", ignoreCase = true)) {
                unexpected.add(file.name)
            }
        }
        File("build").mkdirs()
        File("build/golden-check.txt").writeText(report.toString())
        println(report)
        assertEquals(emptyList(), unexpected, "builds with issues (see build/golden-check.txt)")
    }

    /** Everything about [file] that VB would show as a Message or in red, plus anything left unfilled. */
    private fun check(file: File): List<String> {
        val issues = ArrayList<String>()
        val loaded = readBuildFile(file.readBytes(), data, BuildPoints.ADVENTURER)
        for (problem in loaded.problems) {
            issues.add("load: $problem")
        }
        val rules = loaded.rules
        val build = loaded.build
        val original = ddolite.dataformat.Windows1252.decode(file.readBytes()).split("\r\n")
        val saved = writeBuildText(rules).split("\r\n")
        for (i in 0 until maxOf(original.size, saved.size)) {
            val a = original.getOrNull(i)
            val b = saved.getOrNull(i)
            if (a != b) {
                issues.add("round trip: line ${i + 1} \"$a\" saved as \"$b\"")
                break
            }
        }
        for (message in DeprecationMessages(rules, loaded.deprecations).messages()) {
            issues.add("message: " + message.text)
        }

        // Stats
        StatRules(rules).recountPoints()
        val budget = rules.buildPointsFor(build.buildPoints)
        if (build.statTotals[build.buildPoints.ordinal] != budget) {
            issues.add("stats: ${build.statTotals[build.buildPoints.ordinal]} of $budget points spent")
        }
        for (i in 0..build.maxLevels / 4) {
            if (i < build.levelups.size && build.levelups[i] == null) {
                issues.add("stats: no level-up stat at level ${i * 4}")
            }
        }

        // Skills
        val heroic = rules.heroicLevels()
        val classes = ArrayList<CharacterClass>()
        for (level in 1..heroic) {
            classes.add(build.levelClass(level)!!)
        }
        val statRules = StatRules(rules)
        val modifiers = ArrayList<Int>()
        for (level in 1..heroic) {
            modifiers.add(statRules.stat(Stat.INT, level, modifier = true))
        }
        val grid = SkillGrid(classes, build.race?.skillPoints ?: 0, modifiers)
        val spend = ddolite.builder.rules.SkillSpend()
        for (skill in Skill.entries) {
            for (level in 1..heroic) {
                spend.set(skill, level, build.skills[skill.ordinal][level - 1])
            }
        }
        for (level in 1..heroic) {
            when (grid.pointsStatus(spend, level)) {
                RankStatus.OVER -> issues.add("skills: level $level overspent")
                RankStatus.UNDER -> issues.add("skills: level $level has points left")
                else -> {}
            }
            for (skill in Skill.entries) {
                if (grid.cellStatus(spend, skill, level) == RankStatus.OVER) {
                    issues.add("skills: $skill over its maximum at level $level")
                }
            }
        }

        // Feats
        val featRules = FeatRules(rules)
        featRules.initFeatList(null, -1)
        featRules.checkSlotErrors()
        for (entry in rules.featList) {
            if (entry.errorState) {
                issues.add("feat: level ${entry.level} ${entry.display}: ${entry.errorText}")
            } else if (entry.feat == null && entry.actualType != BuildFeatType.GRANTED) {
                issues.add("feat: level ${entry.level} ${entry.actualType} slot empty")
            }
        }

        // Spells
        for (cs in build.classSpells) {
            for (spellLevel in 1..cs.maxSpellLevel) {
                var empty = 0
                for (slot in cs.level(spellLevel).slots) {
                    if (slot.spell.isEmpty() && (slot.type == SpellSlotType.STANDARD || slot.type == SpellSlotType.FREE)) {
                        empty++
                    }
                }
                if (empty > 0) {
                    issues.add("spells: ${cs.characterClass.name} level $spellLevel has $empty empty slots")
                }
            }
        }

        // Enhancements and destinies
        val enhancements = EnhancementRules(rules)
        val ap = enhancements.apTotals()
        info.add("enhancements: " + ap.caption())
        if (ap.overspent) {
            issues.add("enhancements: " + ap.caption())
        }
        checkTrees(enhancements, build.trees, false, issues)
        if (build.maxLevels >= 20) {
            val destiny = enhancements.destinyTotals()
            info.add("destinies: " + destiny.caption())
            if (destiny.overspent) {
                issues.add("destinies: " + destiny.caption())
            }
            checkTrees(enhancements, build.destinies, true, issues)
        }

        // Leveling guide
        val guideRules = GuideRules(rules)
        guideRules.initGuideEnhancements()
        info.add("guide: " + rules.guideEnhancements.size + " steps, " + guideRules.totals().levelCaption() + ", " + guideRules.totals().spentCaption())
        for (step in rules.guideEnhancements) {
            if (step.errorState) {
                issues.add("guide: ${step.display} ${step.rankText}: ${step.errorText}")
            }
        }
        return issues
    }

    private fun checkTrees(enhancements: EnhancementRules, trees: List<BuildTree>, destinies: Boolean, issues: MutableList<String>) {
        val kind = if (destinies) "destiny" else "enhancement"
        for (bt in trees) {
            val tree = if (destinies) data.findDestiny(bt.treeName) else data.findTree(bt.treeName)
            if (tree == null) {
                issues.add("$kind: no tree ${bt.treeName}")
                continue
            }
            for (placeholder in bt.missing) {
                issues.add("$kind: ${bt.treeName} placeholder ${placeholder.text}")
            }
            val spend = enhancements.spentInTree(tree, bt)
            for (a in bt.abilities) {
                if (a.ability == BLANK_ABILITY) {
                    continue
                }
                val error = enhancements.abilityError(tree, bt, a, spend)
                if (error != null) {
                    issues.add("$kind: ${bt.treeName} " + enhancements.slotInfo(tree, a).caption + ": " + error)
                }
            }
        }
    }
}
