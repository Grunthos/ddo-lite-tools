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

1. Make a folder `kotlin/golden/snapshot-<yyyy-mm-dd>/`.
2. Note the VB version (4.15.0) and the commit of `Data/` used, in `manifest.txt`.
3. Screenshot Tools > Options so the output settings (feat order, skill order …) are recorded;
   the exports depend on them. Leave them at their defaults if possible.

## For each build

Put everything in `builds/<build name>/` in the snapshot folder.

1. File > Open the build. If Messages appear (Tools > Messages), screenshot the list: `messages.png`.
2. File > Save As `saved.build`. This is the golden .build.
3. File > Export > to Forums. Section: the whole build. For each Format in the list, Save to
   File as `forum-<format>.txt` (for example `forum-Plain Text.txt`, `forum-DDO Forums.txt`).
4. File > Export > to Ron's Planner: `ronsplanner.txt`. File > Export > to DDO Builder: `ddobuilder.txt`.
5. File > Export > Leveling Guide > csv: `guide.csv`; > txt: `guide.txt`.
6. Screenshots (one window at a time, at its default size): the main window's output, then
   Edit > Overview, Stats, Skills, Feats, Spells, Enhancements (Trees tab and Leveling Guide tab)
   and Destiny where the build has them. Name them after the window: `stats.png`, `enhancements-guide.png` ….
7. Tools > Error Log: if anything was logged, save it as `error.log`.
8. File > Close, then File > Open `saved.build` again. If anything differs (Messages, empty slots),
   note it in `reopen.txt`. Build D is expected to lose its deity feats here.

Skip a step that doesn't apply (A and C have no destinies; nothing to screenshot if a window can't open).
