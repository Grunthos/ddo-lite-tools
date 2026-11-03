# Bugs found in the VB6 Character Builder Lite

Found while porting to Kotlin, by reading the VB source; none has been tried in the running VB
program yet (those marked **Confirm** most need trying first). The Kotlin port copies each one
so builds behave the same, and notes it in the code.

Line numbers are for the VB source on the `kotlin` branch. Paths are under `Source/`.

Severity: **Crash** = VB stops with a run-time error; **Data loss** = something the user chose
is lost; **Wrong** = a wrong result or rule; **Display** = cosmetic.

## Crashes

1. **Destiny ability that no longer exists stops the load.** `Modules/Build/basDeprecate.bas:444`
   `DeprecateAbility`: for a destiny ability it writes to an array it never sized ("Subscript out
   of range"). Hit when a `.build` names a destiny ability the current data has renamed or removed.
   *Fix:* `ReDim Preserve` the destiny array as the enhancement branch does.

2. **Stats › Schedule tab with a level-up chosen (Confirm).** `Forms/Build/frmStats.frm:1614`
   `DrawLevelupRow` loops `For i = 0 To MAX_LEVEL` (36) but the grid has columns 0-30 only
   (`grid.CellLeft(i)`). *Fix:* loop to the grid's last column, as the other rows do.

3. **Overview level grid with three classes and a level with no class.** `Forms/Build/frmOverview.frm:2074`
   `InitGrid`: the class column looks up the build class slot of each level's class; with all
   three slots filled and a level still empty, the lookup runs past the slots ("Subscript out of
   range"). *Fix:* show an empty name when the level has no class.

4. **Overview race list: Enter with no row highlighted.** `Controls/Build/userRaceCombo.ctl` `KeyDown`
   (`vbKeyReturn`, ~line 117) raises `Click` with row 0 of a 1-based array. Reached from
   `frmOverview.txtRace_KeyDown` (:1498). *Fix:* ignore Enter when nothing is highlighted.

5. **Leveling guide tree name shorter than 6 characters.** `Modules/Build/basFormatLite.bas:1326`, reading
   `[LevelingGuide]`: `Mid$(display, Len(display) - 5, 2)` fails for a display under 6 characters
   (an unknown short tree abbreviation in a file). *Fix:* check the length first.

6. **A class with only one initial in Classes.txt.** Code that reads `db.Class(c).Initial(3)` (leveling
   guide tree names for duplicate trees, `basBuild.AddGuideTree` :3198) fails if the class's
   `Initial:` line gave fewer than four. *Fix:* fall back to the first initial.

## Data loss and wrong results

7. **Deity feats saved by character level, read by class level.** Save: `Modules/Build/basFormatLite.bas:422`
   writes `"Deity " & .Level`; load: `:1016` `IdentifySlot` matches deity slots on `.ClassLevel`
   (the comment says "Deity feats are based on class level"). A deity feat whose character level
   differs from its class level (any multiclass, e.g. Fighter 1 / Cleric 2 → Deity at level 3,
   class level 1) is **lost when the file is reopened**. *Fix:* write `.ClassLevel`; to read old
   files, fall back to matching `.Level` when no class level matches.

8. **Warlock pact spells are empty after opening a build.** `Modules/Build/basMain.bas:254-268`
   `CascadeChanges` runs `InitBuildSpells` before `InitBuildFeats`; on load (`basDocuments.bas:294`)
   the feat list it reads is the one made before the `[Feats]` section was read, so
   `AddWarlockPactSpells` (`basBuild.bas:2099`) finds no Pact feat and clears the pact slots. They
   come back when the Spells screen opens or a feat changes. *Fix:* call `InitBuildFeats` before
   `InitBuildSpells` in `CascadeChanges` (cceAll, cceMaxLevel, cceClass).

9. **The level 36 level-up never counts.** `Modules/Build/basBuild.bas:298` `CalculateLevelup`:
   `iMaxLevelUp = 32 \ 4` ("NEED MAX LEVEL HERE"), so the stat chosen for level 36 never raises the
   score (Stats, Skills' Intelligence, feat stat requirements). *Fix:* `MAX_LEVELUPS`.

10. **Alternates and exchanges saved out of level order.** `Modules/Build/basBuild.bas:981`
    `AddSpecialFeat`: the insertion loop compares `.Feat(i)` with `.Feat(i - 1)` after shifting, so
    after one move it compares two old entries and stops; the new slot moves back at most one
    place. (Add alternates for levels 9, 3, 1 in that order: they're stored 9, 3, 1.) *Fix:* compare
    `typTemp.Level` with `.Feat(i - 1).Level`.

11. **Emptying any enhancement tree clears the tier 5 tree.** `Forms/Build/frmEnhancements.frm:1969`
    `ClearSlot`: `If .Abilities = 0 Then SetTier5 vbNullString` runs for any tree, so emptying a
    tree that isn't the tier 5 tree removes the tier 5 tree's tier 5 abilities (`ShaveTree`).
    *Fix:* only when `build.Tier5 = .TreeName`, as on line 1971.

12. **Deleting the tier 5 tree from the menu leaves it named as the tier 5 tree.**
    `Forms/Build/frmEnhancements.frm:1287` `mnuTrees_Click` (Delete tree) doesn't clear
    `build.Tier5`; double-clicking the tree (`usrTree_SlotDblClick` :1333) does. Resetting a tree
    (index 3) or all trees (4) also leaves `Tier5` set with no tier 5 abilities. *Fix:* clear it in
    those cases too.

13. **A tier 5 ability added by drag doesn't make its tree the tier 5 tree (Confirm).**
    `Forms/Build/frmEnhancements.frm:1832` `DropAbility` sets the ability but never calls `SetTier5`
    (double-click goes through `AddAbility`, which does). With no tier 5 tree yet, the build ends up
    with tier 5 abilities and an empty `Tier5`. *Fix:* call `SetTier5` when the tier is 5.

14. **"Delete All Trees" loses the race tree's abilities.** `Forms/Build/frmEnhancements.frm:1302`
    replaces the tree list with a fresh race tree. Possibly intended; worth a question ("Delete all
    trees and reset racial enhancements?").

15. **Feat search when opening old builds can miss feats.** `Modules/Build/basDataLoad.bas:1179`
    `SeekFeat(name, False)` binary-searches `db.FeatLookup`, which is sorted case-sensitively, while
    comparing lower-cased names; a name whose place differs between the two orders isn't found
    (e.g. names starting with a lower-case letter, or differing only in case). *Fix:* sort the lookup
    with `vbTextCompare`, or search linearly when not matching case.

16. **Arcane Trickster builds skip most feat list filters.** `Forms/Build/frmFeats.frm:1419`
    `FilterFeat`: for a build with Arcane Trickster, a spellcasting feat sets `FilterFeat = True`
    and carries on; every later `Exit Function` (stat, skill, past life, legend, level checks) then
    returns True. *Fix:* skip only the spellcasting check (don't set the result there).

17. **An unknown `[Section]` in a `.build` is read as `[Skills]`.** `Modules/Build/basFormatLite.bas:739`
    `Case Else: menSection = secSkills`. A typo or a newer section would be read as skill lines.
    *Fix:* an "unknown" section whose lines are ignored (or reported).

18. **After the first leveling guide step that doesn't resolve, the rest of the guide is dropped**
    (and that step's tree is still added, so the next save writes an empty `[LevelingGuide]`).
    `Modules/Build/basFormatLite.bas:1263` `LoadLevelingGuideText`. *Fix:* skip just that step, and add its tree
    only when it resolves.

## Display

19. **Feats window stays widened after a change elsewhere (Confirm).** `Forms/Build/frmFeats.frm:312`
    `Cascade` sets `menChannel = fceUnknown`, so `ChangeTab` (:364) doesn't restore the details box
    widened for the Granted tab: it comes back on Selected with the details wide, the filter list
    hidden, and no "<<" link. *Fix:* call `DetailsRestore` in `Cascade`.

20. **"(Wiki)" link to the wiki's front page.** `Forms/Build/frmFeats.frm:1489` `ShowDetails`: a feat
    with a description but no wiki name gets `MakeWiki("")`, the base address. *Fix:* pass "" when
    there's no wiki name (userDetails then shows no link).

21. **Universal AP help says "+?".** `Forms/Build/frmEnhancements.frm:317` `lblUniversalAPhelp(1)`
    ("- Universal AP Tomes can add +?") is never filled in by `LoadData` (:1148), unlike the racial
    one. *Fix:* `"- Universal AP Tomes can add +" & tomes.UniversalAPMax & " Universal AP"`.

22. **Racial AP note missing until the spinner moves.** `Forms/Build/frmEnhancements.frm:1148`
    `LoadData` doesn't set `lblRacialAPreq` ("Requires N racial past lives"); only
    `usrspnRacialAP_Change` (:1236) does. *Fix:* set it in `LoadData` too.

23. **"MaxLevels:" is written twice.** `Modules/Build/basFormatLite.bas:165-166`. Harmless (the
    second is read over the first). *Fix:* delete line 166.

## Latent (work by luck, or dead code)

24. `Modules/Build/basBuild.bas:3110` `GetAbilityMaxRanks` passes a `TreeStyleEnum` to `SeekTree`,
    which expects a `PointerEnum`; the numbers happen to pick the right list.
25. `Modules/Build/basBuild.bas:2883` `CheckAbilityErrors`: inside the "no selector" branch,
    `If .Selector <> 0 Then …` can never run.
26. `Modules/Build/basBuild.bas:631` `InitStandardFeats` uses one index across the Standard and
    Legend lists, so the Legend slot is entry 15 of its list (14 empty entries before it) and Standard
    entry 15 is never set. Works, but wastes entries and is easy to break.

## Data files: VB stops instead of reporting

The loaders stop with a run-time error on these mistakes in the data files; the Kotlin loader
reports them and carries on. Worth guarding in VB, since a data update can trigger them:

27. `Classes.txt`: `Trapper:` not true/false (Type mismatch); a `PactSpells:` line with no spells
    (Subscript out of range).
28. `AutoGrantedFeats.txt`: a level that isn't a number (`CInt` Type mismatch).
29. `Enhancements.txt` / `Destinies.txt`: a tree with no valid `Type:` before its abilities; a
    requirement with no ": ", no tier, an unknown tree, a tier out of range, or an ability not
    found; a selector-sharing ability with more selectors than its parent; a parent selector's tier
    out of range.
30. `Feats.txt`: a `SharedSelector:` chain that loops never ends (VB hangs).
31. A class or race not in `basEnum.bas` (ClassEnum/RaceEnum) is ignored by VB; new classes and races
    need the enums, `GetClassID`/`GetRaceID` and the names updated together.

Silently surprising, not errors: `Classes.txt` keeps only the last of several DisallowedClasses on one
line; a duplicate `PactSpells:` pact is loaded twice; `Spells.txt` keeps both copies of a duplicated
spell; `Tomes.txt` values go through `Val`, so "3 3" means 33.

## Data file errors (for the data maintainers)

- `Classes.txt:481` repeats Warlock's Fey `PactSpells` line.
- `Spells.txt` lists Prismatic Strike twice (lines 1371 and 1892).
- `Enhancements.txt:8058` Halfling tier 3 Halfling Guile requires "Tier 2: Halfling Guile" (there is none).
- `Enhancements.txt:14560` Vile Chemist Simple Thrown Mastery requires "Feat: Simple Thrown Weapon
  Expertise" (the feat is "Simple Thrown Expertise").
