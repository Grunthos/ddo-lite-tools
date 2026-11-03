package ddolite.builder.build

import ddolite.builder.model.Skill

/** The Skills window's Skill Tomes tab: frmSkills.frm's edits to `build.SkillTome`. */
object SkillTomes {
    /** usrspnSkill_Change (frmSkills.frm:1390). */
    fun set(build: Build, skill: Skill, value: Int) {
        build.skillTomes[skill.ordinal] = value
    }

    /** "Clear Tomes", after the user agreed: Erase build.SkillTome (tmrFocus_Timer, frmSkills.frm:1229). */
    fun clear(build: Build) {
        for (i in build.skillTomes.indices) {
            build.skillTomes[i] = 0
        }
    }
}
