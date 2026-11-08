# Capturing goldens from VB 4.15

The builds in this folder were written to cover every feature (PLAN.md §6.2) and checked with the
Kotlin port (`GoldenBuildCheck`): A, B and C open with no Messages and nothing red; D is meant to
have errors. VB is the authority, so each one is opened and saved in the official VB 4.15 exe and
what VB writes becomes the golden.

| Build | Covers |
|---|---|
| A Three Class | Human Rogue 6 / Wizard 10 / Fighter 4, level 20. All four point-buy columns (Champion preferred), stat tomes, three class trees + race tree + universal tree (Falconry, universal AP), racial AP, tier 5, selectors, multi-rank abilities at partial ranks, Toughness taken twice, two alternates, an exchange, wizard spells, leveling guide with Bank AP, Reset Tree and Reset All Trees |
| B Epic Caster | Aasimar Favored Soul 18 / Warlock 2, level 34. Legend points only (other columns excluded), deity feats (with a shared selector), warlock pact, race bonus feat (Bond) and race tree abilities that need it, epic, destiny and legend feats, Great Ability twice, Epic Spell Power with two selectors, three destinies with a tier 5 and permanent destiny points, Favored Soul and pact spells |
| C Iconic Divine Artificer | Purple Dragon Knight (iconic) Cleric 13 / Artificer 7, level 20. Hero points only, skill tomes, Notes (two lines), domain, deity feats, cleric cures, artificer spells (rare scrolls), duplicate ability names in a tree, guide steps through ranks 1–3 |
| D Errors | Dwarf Fighter 12 / Cleric 8, level 30. Red feats (BAB, class-only), a renamed feat (Messages), overspent and over-maximum skills, red enhancements, overspent AP, red guide steps, destiny AP at exactly level 30 (VB gives no base destiny AP there), and deity feats on a cleric whose class levels don't match character levels (VB saves them under a slot it can't read back) |

## Once per snapshot

Everything goes in one flat folder beside the builds, `vb-4.15/` (git keeps it byte for byte).
Files are named after the build's letter: `A …`, `B …`, `C …`, `D …` (`Eresh …` for the sample).

1. In `vb-4.15/manifest.txt`, note the VB version (4.15.0) and the commit of `Data/` used.
2. Leave Tools > Options at their defaults (the exports depend on feat order, skill order …), or
   screenshot it if not: `Options.png`.
3. Start VB with "Show Error Log on startup" on; if it shows a log, copy `Error.log` to `error.log`.

## For each build

Do this for A–D, and for `Save/Eresh WFWizRog-v4.15.build` (the one sample that is clean in 4.15).

1. File > Open the build. If Messages appear (Tools > Messages), screenshot the list: `D Messages.png`.
2. File > Save As `Golden <build name>.build` (for example `Golden A Three Class.build`). This is
   the golden .build.
3. File > Export > to Forums. Section: All Sections. For each Format (Code Tags, DDO Forums,
   Plain Text, Reddit, Something Awful, The Vault), Save to File as `A forum-<format>.txt`
   (for example `A forum-Plain Text.txt`).
4. File > Export > to DDO Builder: `A ddobuilder.ddocp`. (Skip to Ron's Planner: it fails in VB,
   VB-BUGS 6a.)
5. File > Export > Leveling Guide > csv: `A guide.csv`; > txt: `A guide.txt` (builds with a guide).
6. Screenshots, for the colours the text exports don't carry (one window at a time, default size):
   the main window (`A Main.png`), Feats (`A Feats.png`), Enhancements on a tree with red slots, or
   the tier 5 tree if none (`A Enhancements.png`), and Destinies for B and D (`B Destinies.png`).
7. File > Close, then File > Open the Golden build again. If anything differs (Messages, empty
   slots), note it in `A reopen.txt`. Build D is expected to lose its deity feats here
   (VB-BUGS #7), and to show "Max: 0" in red on the Destinies window (VB-BUGS 18d).

Skip a step that doesn't apply. The tests will compare Kotlin with everything here except the
screenshots, which are for comparing by eye.
