package ddolite.builder.build

/** The Builder's Edit-menu screens, in menu order (frmMain.frm:311). */
enum class BuildScreen(val title: String) {
    OVERVIEW("Overview"),
    STATS("Stats"),
    SKILLS("Skills"),
    FEATS("Feats"),
    SPELLS("Spells"),
    ENHANCEMENTS("Enhancements"),
    DESTINY("Destiny"),
}

/**
 * Why [screen] can't be opened yet, or null if it can. Ported from the checks at the top of
 * `basMain.OpenForm` (Build/basMain.bas:131). [active] is the screen the request comes from: VB's
 * message is shorter when that screen is the one that fixes the problem ("Select a Race first."
 * from Overview, "...in the Overview screen first." from anywhere else).
 *
 * The screen showing the message also does what VB does next: on "Select a Race first." the
 * Overview screen drops down its race list.
 */
fun openScreenNotice(rules: BuildRules, screen: BuildScreen, active: BuildScreen?): String? {
    val build = rules.build
    if (screen == BuildScreen.STATS || screen == BuildScreen.SKILLS || screen == BuildScreen.FEATS || screen == BuildScreen.ENHANCEMENTS) {
        if (build.race == null) {
            if (active == BuildScreen.OVERVIEW) {
                return "Select a Race first."
            }
            return "Select a Race in the Overview screen first."
        }
    }
    if (screen == BuildScreen.STATS || screen == BuildScreen.SKILLS || screen == BuildScreen.FEATS ||
        screen == BuildScreen.SPELLS || screen == BuildScreen.ENHANCEMENTS
    ) {
        if (build.levelClass(1) == null) {
            if (active == BuildScreen.OVERVIEW) {
                return "Select a class split first."
            }
            return "Select a class split in the Overview screen first."
        }
    }
    if (screen == BuildScreen.SKILLS || screen == BuildScreen.FEATS) {
        if (build.statTotals[build.buildPoints.ordinal] != rules.buildPointsFor(build.buildPoints)) {
            if (active == BuildScreen.STATS) {
                return "Allocate base stats first."
            }
            return "Allocate base stats in the Stats screen first."
        }
    }
    if (screen == BuildScreen.SPELLS) {
        if (build.canCastSpell[1] == 0) {
            return "This build cannot cast spells."
        }
    }
    if (screen == BuildScreen.DESTINY) {
        if (build.maxLevels < 20) {
            return "This build never reaches level 20."
        }
    }
    return null
}

/**
 * Whether [screen] should be closed after a change because it no longer makes sense. Ported from
 * the window part of `basMain.CascadeChanges` (basMain.bas:282); refreshing the screens that stay
 * open needs no call here, as they redraw from the build.
 */
fun closesAfterChange(rules: BuildRules, screen: BuildScreen, change: CascadeChange): Boolean {
    val build = rules.build
    val noRace = build.race == null
    val noClass = build.levelClass(1) == null
    return when (screen) {
        BuildScreen.OVERVIEW -> false
        BuildScreen.STATS -> (change == CascadeChange.ALL || change == CascadeChange.CLASS || change == CascadeChange.RACE ||
            change == CascadeChange.MAX_LEVEL) && noRace
        BuildScreen.SKILLS -> (change == CascadeChange.ALL || change == CascadeChange.RACE || change == CascadeChange.CLASS ||
            change == CascadeChange.STATS || change == CascadeChange.MAX_LEVEL) && (noRace || noClass)
        BuildScreen.FEATS -> change != CascadeChange.FEAT && noClass
        BuildScreen.SPELLS -> build.canCastSpell[1] == 0
        BuildScreen.ENHANCEMENTS -> (change == CascadeChange.ALL || change == CascadeChange.RACE || change == CascadeChange.CLASS ||
            change == CascadeChange.MAX_LEVEL || change == CascadeChange.FEAT || change == CascadeChange.ENHANCEMENTS) && (noRace || noClass)
        BuildScreen.DESTINY -> build.maxLevels < 20
    }
}
