# Manual checks: Kotlin against VB

Things to compare by hand. Run the Kotlin version with `./gradlew :builder-desktop:run` (from
`kotlin/`), or the portable app (`./gradlew :builder-desktop:portableZip`, then
`builder-desktop/build/portable/CharacterBuilderLiteNG/CharacterBuilderLiteNG.exe`), and VB's Character
Builder Lite 4.15 next to it on the same build. Tick items off, or note what differs under the item.

The most useful first pass: open each file in `Save/` in both, then go through the windows in order and
look for anything different.

Some differences are deliberate: layout (every window shares one frame and margins), fixed VB bugs and a
few new features. They're listed in [KOTLIN-CHANGES.md](KOTLIN-CHANGES.md), so there's no need to note
them here; anything else that differs is worth a note. Write results under an item as `TEST RESULT:`,
and I'll answer questions there as `Reply:`.

## 0. Capturing VB's output (golden files, PLAN.md §6)

With the 4.15 exe, VB's exports can be saved once and compared by the tests every time, which is
far more thorough than comparing by eye. For each build in `Save/` (and any others worth keeping):

- [ ] Open it in VB 4.15 (or start `CharacterBuilderLite.exe "<file>"`).
- [ ] File › Export › to Forums: for each format (DDO Forums, Code Tags, Plain Text, Reddit, and any
      other `.bbcodes` in Settings), Section "All Sections", Save to File as
      `kotlin/golden/snapshot-4.15/builds/<build name>/forum-<format>.txt`.
- [ ] File › Export › to DDO Builder → `ddobuilder.ddocp` in the same folder (to Ron's Planner fails
      in VB, VB-BUGS 6a). File › Export › Leveling Guide › csv and txt → `guide.csv`, `guide.txt`
      (builds with a guide only).
- [ ] Once: start VB with "Show Error Log on startup" on and copy its `Error.log` to
      `kotlin/golden/snapshot-4.15/error.log`.

Put a copy of the `.build` beside its exports as `input.build`. I'll then write the tests that compare
the Kotlin output with these files and list every difference.

## 1. Questions only VB can answer

- [X] **Stats › Schedule tab with a level-up chosen.** In VB, choose a stat for "All Levels" (or any
      level), then click Schedule. Does it crash ("Subscript out of range")? `DrawLevelupRow` runs to
      level 36 but the grid stops at 30. Kotlin stops at 30.
  - TEST RESULT: VB Does indeed crash
  - Reply: thanks; confirmed in VB-BUGS #2. Kotlin's Schedule stops at level 30, so it doesn't crash.
- [ ] **Level 36 level-up.** VB's code counts level-ups only to level 32 (VB-BUGS #9), so the one at
      level 36 should never raise a stat. To check, in VB:
      1. Open a build with Max Levels 36, or set Overview › Max Levels to 36.
      2. In Stats › Levelups, note the Strength total in the main window's Stats section.
      3. Set the "Level 36" box to Strength.
      Does the Strength total go up by 1? I expect it doesn't (Kotlin copies VB: it doesn't either).
  - TEST RESULT: Please give further info. This request is unclear.
  - Reply: sorry; reworded above with the steps.
- [X] **Skills right-click menu**: after choosing "Max Ranks (Full Ranks)" from the menu, a left-click on
      the same skill name should start again at Half Ranks (that's how I read the VB code, and what Kotlin
      does), rather than moving on to Even Levels.
  - TEST RESULT: That is a correct behaviour.

## 2. Files

- [ ] Open each `.build` in `Save/` in Kotlin: no errors, the main window's summary matches what VB shows.
  - TEST RESULT: No. The files have errors. 
  - Reply: expected for most of them: they were saved with older data, and 4.15 renamed or removed some of
    their enhancements and feats, so VB drops them too (the Messages window lists them). Only
    `Eresh WFWizRog-v4.15.build` is clean. Worth checking: do VB and Kotlin report the same errors for the
    same file? Re-saving each sample in VB 4.15 (after re-adding what was dropped) gives clean test files.
- [ ] Save from Kotlin, open the saved file in VB: same build.
  - TEST RESULT: Done this with the one file the works. Seems to be correct. There should be a more thorough
    way to test this. Open/SaveAs then diff? See next question: do we need both?
  - Reply: Open, Save As, then compare is what the round-trip test does, automatically, for every `.build`
    in `Save/` and `kotlin/test-builds/`: it opens the file, saves it, compares the two line by line, and
    fails on any difference it can't explain (explained: something the data dropped, listed in Messages;
    a renamed feat; VB's DestinyTier5 line; the level 36 Levelup line older versions didn't write). A
    clean 4.15 file must come back unchanged. So this item only needs an occasional spot check in VB;
    the next one is the way to add coverage.
- [ ] More round-trip files: save builds in VB 4.15 (casters, epic, Drow, multiclass, Iconic races, one with
      a leveling guide) and drop them in `Save/`, or `kotlin/test-builds/` to commit them. Each one becomes an
      automatic test (Kotlin must save it byte for byte the same; it does for `Eresh WFWizRog-v4.15.build`).
- [X] File › New, Close, Save As, and quitting with unsaved changes (Yes / No / Cancel) behave as in VB.
  - TEST RESULT: Yes.
- [X] The title bar shows the build name, the file name if different, and `*` for unsaved changes.
  - TEST RESULT: Yes.

## 3. Overview

- [ ] Colours and text against VB at 100% scaling (fonts will differ: Verdana isn't bundled yet).
  - TEST RESULT: Is this still relevant with recent layout changes.
  - Reply: only partly: positions now differ on purpose (KOTLIN-CHANGES.md §3), so compare colours, text
    and what's shown, not where. Reworded.
- [ ] Race list: mouse, Up/Down/PgUp/PgDn/Home/End/Enter/F4/Escape; the type headings; the current
      race highlighted.
- [ ] Iconic races: on a new build, picking one sets the main class; Drow drops the Champion points.
- [ ] Alignment list follows the classes; with only one possible alignment it's chosen for you.
- [ ] Class icons greyed by alignment, and the race's disallowed classes.
- [ ] Dragging classes: onto a slot, between slots (swap), off a slot (clears it and later ones).
- [ ] Level grid: click a class column to give that level the class; drag in the class column to swap
      two levels; the grid's colours and BAB.
- [ ] "This will erase the Leveling Guide" appears on a build with a guide, and No cancels the change.
- [ ] Max levels spinner; the Sub-Race notice link; the Notes tab.
- [ ] Windows that should close after a change (e.g. Skills after removing the race) do.

## 4. Stats

- [ ] The four columns: costs, "next point" figures, the Include boxes, points spent.
- [ ] Changing the preferred column carries to the other columns only when they can afford it.
- [ ] Drow: no Champion column, 4 fewer points; Adventurer unaffected.
- [ ] Level-ups: "All Levels", and changing one level afterwards; only the build's levels shown.
- [ ] Tomes: Supreme raises every stat to at least its value; the cap by max level and its note.
- [ ] Templates for the main class; the class link switches classes; warnings show for 5 seconds.
- [ ] Applied templates give the same stats and level-ups as VB (e.g. Fighter THF).
- [ ] Clear Stats asks "Keep tomes?" (or "Clear stats and levelups?" with no tomes) and does the same;
      afterwards the preferred column is the Default Build Points from Options (fixed 2026-11-05).
- [ ] Schedule tab: every number against VB for a few builds.

## 5. Skills

- [ ] Grid size and position in the window; row heights; skill names abbreviated when they don't fit.
- [ ] Class icons above the levels on a multiclass build (none on a single-class build).
- [ ] Each cell's colours (dim / normal / red) and the class-skill shading at each Contrast setting.
- [ ] Left / right / middle click, holding to repeat, double-click.
- [ ] Clicking a skill name repeatedly: half ranks → full ranks → even levels → clear.
- [ ] Right-click on a skill name: the menu, and each item.
- [ ] Right-click on a level number clears that level.
- [ ] Disable Device / Open Lock need a trapper level first.
- [ ] Points per level use the Intelligence at that level (change Int or an Int tome in Stats and watch).
- [ ] Skill Tomes tab: the spinners' maximum, and the values saved in the file.
- [ ] Clear Skills; "< Stats" and the Stats window's "Skills >".

## 6. Feats

- [ ] Each sample build: the same slots, sources, levels, BAB and red (error) slots as VB, on Selected
      and Granted; with "Channels" ticked, the same tabs.
- [ ] Click a slot: the list title ("Level 3", "Fighter 2", …) and the feats offered match VB; "Show All"
      and the filter groups (click, Ctrl-click) change the list the same way.
- [ ] A feat with selectors: the selectors offered (and their order) match VB.
- [ ] The details box for a few feats and selectors, including "Fred says:" for a slot in error, and
      the >> / << links.
- [ ] Drag a feat onto a slot (yellow = fits, pink = fits with an error); slot onto slot swaps; slot
      onto the feat list clears it; double-click a slot clears it; double-click a feat fills the chosen
      (or first fitting) slot.
- [ ] Right-click a slot: the menu items match VB; Add Alternate; Exchange Feat (the level dialog, and
      "Fred says:" when the slot is locked); Delete This Slot; Clear All Feats.
- [ ] Changing race, class, stats or skills elsewhere updates the slot errors here.
- [ ] **VB quirk to confirm:** add three alternates for levels 9, 3 and 1 (in that order) and save: VB's
      file should list them 9, 3, 1 (out of order), as Kotlin does.
- [ ] **VB quirk to confirm:** on the Granted tab, change something in another window (e.g. the build
      name doesn't count; change a skill point): the Feats window should come back on Selected with the
      details box still wide and no "<<" link.

## 6a. Spells

- [ ] Each caster build: the same classes, levels, slots and "Level N" / "Pact @ N" as VB, and the
      same spells offered (rare Wizard/Artificer scrolls indented with "*").
- [ ] Double-click or Enter on a spell fills the first empty slot; a class's free spell gets a new
      free slot at the top.
- [ ] Drag a spell onto a slot; drag a slot onto another (swap); drag a slot onto the list (clear);
      double-click a slot (clear); the slot menu (this spell / this class / all).
- [ ] Opening a saved warlock build: VB's main window (before opening Spells) should show the pact
      spells missing (VB-BUGS #8); after opening the Spells window they're back.
- [ ] **VB bug to confirm:** drag a rare scroll (e.g. Wizard level 6 Acid Fog) into a slot and save:
      VB should write "  Acid Fog*" (VB-BUGS 18a).

## 7. Enhancements (Trees and Enhancements tabs)

- [ ] Each sample build: the same trees, Class/Tier/AP columns, available trees and the
      "Spent: … / Max: … AP" line as VB; the racial and universal AP spinners' limits.
- [ ] Trees: double-click a listed tree to add it; drag one onto a row (replace) or the empty row
      (add); drag a row onto another (swap); double-click a row to remove it; the right-click menu.
- [ ] Trees tab, straight after opening the window: click a tree (it's selected) and right-click › Reset
      (it resets). Neither worked until the tab had been redrawn (fixed 2026-11-06).
- [ ] The Class column is as wide as the longest class name, and the add list's Class column starts after
      its longest tree name (its caption only when a listed tree has a class).
- [ ] Enhancements tab, for each tree: the same slots, AP, ranks and red (error) slots as VB.
      Note: the repository's sample builds were saved with older data, so several of their trees are
      short of AP now (renamed abilities are dropped on load); VB should show the same red slots.
- [ ] The abilities offered (and with Show All), the selectors, and the details text, ranks, cost and
      "N AP spent in tree".
- [ ] Add an ability by double-click and by drag (onto the yellow/pink blank slot); drag a slot onto
      the ability list to clear it; double-click a slot; the rank arrows; the slot menu.
- [ ] Taking a tier 5 ability in a second tree asks to move the tier 5 tree.
- [ ] **VB quirks to confirm:** emptying a tree that isn't the tier 5 tree still clears the tier 5
      tree; a tier 5 ability dragged into a tree (rather than double-clicked) doesn't make it the
      tier 5 tree.

## 7a. Destinies

- [ ] Each build at level 20+: the same destinies, AP column and "Spent: … pdp / Max: … pdp AP" line;
      the Perm Destiny Points spinner's limits.
- [ ] **VB bug to confirm:** a level 30 build (e.g. Kensei Warpriest) shows "Max: 0" in red (VB-BUGS 18d).
- [ ] **VB bug to confirm:** the Destiny Trees list shows "5" under "Destiny" and nothing under "Tier" (22a).
- [ ] Add (double-click / drag, at most 3), swap, double-click to remove, the menu.
- [ ] Both tabs are now the Enhancements window's tabs (shared code): everything in §7 should work the
      same here; the Destiny column is narrow (it shows the tier) and the slots wider.
- [ ] Destinies tab: slots, ranks, red slots, abilities offered, selectors, details (no level
      requirements, as VB), adding by double-click and drag, clearing, the tier 5 question.

## 7b. Messages (builds with feats or enhancements the data no longer has)

- [ ] Open `Save/Eresh WFWizRog-old-feats-and-ehhancements.build` in both: the Messages window opens
      by itself with the same label ("2 Issues and 6 Messages"), the same list, and the same details.
- [ ] Best Guess: the same feats in the same order; unticked, every selectable feat (none highlighted
      until a message is clicked again); a feat with selectors lists them on the right.
- [ ] Choose (with and without a selector chosen: "Choose a selector."), Ignore, and OK on a notice;
      the label counts down; after the last one the window closes and Tools › Messages goes.
- [ ] Tools › Messages reopens it while there are messages left.
- [ ] **VB bug to confirm:** with the Feats window open, Choose a feat in Messages: the Feats window
      still shows the slot empty (VB-BUGS 22b).
- [ ] **VB bug to confirm:** after File › New, Tools › Messages is still there (22c).
- [ ] **VB bug to confirm:** set a tier 5 destiny, save, reopen: it's gone (18f).

## 7c. Leveling Guide (Enhancements window, third tab)

- [ ] Build a short guide in both on the same build (e.g. Kensei Warpriest: Kensei core with a
      selector, a few more, a Bank, a Reset): the same Level, Tier, Tree, AP and Prog columns, the
      same red rows and error texts in the details, and the same level, Available and Spent figures
      (Kotlin writes "Level: 20 (Ftr 12/Clr 8)" for VB's "Cur. Level: 20").
- [ ] The tree list (and Show All), "(Commands)", each filter's list, the selector list and
      "Back to Trees"; double-click to add; choosing rank 3 adds ranks 1-3.
- [ ] Rows: click, Ctrl-click, Shift-click, drag down the Ability column; the right-click menu (Move
      Rows → Insert Rows Here, Delete Rows, Delete to End, Delete All with its question).
- [ ] Save and reopen: the same guide.
- [ ] **VB bug to confirm:** "Selected and Available" lists the same as "Show All" (VB-BUGS 18g).
- [ ] Delete, move or insert rows with a tree selected: its ability list follows (a deleted ability is
      offered again). **VB bug to confirm:** VB leaves the old list, with no tree selected (22j).
- [ ] Take rank I of a multi-rank ability with a selector (e.g. Efficient Metamagic as Maximize): ranks II
      and III are offered with the same selector. **VB bug to confirm:** VB offers every selector (22k).
- [ ] With a tree selected, "Spent in <tree>: N AP" at the bottom left (new).

## 7d. Main window output

- [ ] Each sample build: the main window's text against VB's, section by section (level order,
      stats, skills grid, feats, spells, enhancements, leveling guide, destinies), including colours.
- [ ] With each Edit window active, the main window shows that window's sections (and goes back to
      everything when the last Edit window closes).
- [ ] **VB bug to confirm:** with the Destinies window active, only the name, classes and race show
      (VB-BUGS 22g).
- [ ] **VB bug to confirm:** a tree without its first core makes "Enhancements … Errors" (18i).

## 7e. Export

- [ ] File › Export › to Forums: the same text as VB's Export window for each format (DDO Forums,
      Code Tags, Plain Text, Reddit, Something Awful, The Vault) and each section; Copy to Clipboard
      ("Copied!" for a second) and Save to File (Windows-1252 text).
- [ ] The text box can be edited, and changes to the build replace it (as VB's Cascade).
- [ ] File › Export › Leveling Guide › csv and txt, and the guide tab's Export link: the same files
      as VB's, asking before overwriting, then opening the file.
- [ ] File › Export › to Ron's Planner and to DDO Builder: open the files in those planners (or compare with
      VB's DDO Builder file; VB's Ron's export fails, VB-BUGS 6a).
- [ ] **VB bug to confirm:** a txt guide export where every tree name has 5 letters or fewer (e.g.
      only Human) stops with an error in VB (the centred "Tree" heading).

## 7f. Settings and Options

- [ ] First run with VB's Settings.txt and no Settings-Kotlin.txt: the theme (with any colour changes), output
      format, default build points, contrast, recent files and window places are VB's. Afterwards only
      Settings-Kotlin.txt changes; VB's Settings.txt is untouched.
- [ ] File menu recent files: open one; one that's gone asks to be removed from the list.
- [ ] Tools › Options: each setting as VB; Show All Feats only shows in the output after the next change (as VB).

- [ ] Tools › Options › Skill Order "Native First": the Skills grid lists the main class's native skills, then
      the other classes' native skills, then the rest. Feat Order "Group Related": the Feats list sorts by
      SortName and indents related feats two spaces.
- [ ] Options › BBCodes, the Export window's "Format:" and the help links open the format editor ("Output
      Format"): Load, Save As (a .bbcodes file in Settings), Apply Changes (lit only after a change),
      "Apply changes?" on closing; Reddit and BBCodes enable the tags as VB.

## 7g. Import

- [ ] File › Import › from Ron's Planner and from DDO Builder: files exported by either version come back
      the same (past lives aren't imported, as VB); the build is unsaved, with no file name.
- [ ] **VB bug to confirm:** a DDO Builder file starting with `<?xml …?>` imports nothing:
      VB's tag reader stops at the declaration (18r).
- [ ] **VB bug to confirm:** a Drow Champion exported to DDO Builder comes back as Adventurer (18q).
- [ ] File › Import › from Forums: paste a build copied from a forum post, Translate Build.
- [ ] **VB bugs to confirm** (18j-p): only levels 1-5 of the Level Order come back; any build with a
      Destinies section fails ("Invalid or Incomplete build text…"); spells come back as "Name (3)";
      "Half-Elf" reads as Elf; the 32 and 36 level-ups are ignored.

## 7h. Help, About, Error Log

- [ ] Every window's Help link opens the same topic as VB; links inside topics (other topics, web pages,
      files, windows) and "< Back"; Escape closes the Help window.
- [ ] **VB bug to confirm:** the Destinies window's Help says "Help Code 'Destinies' not found." (22h).
- [ ] Help menu: What's New?, FAQ, Save File Specifications, DDO Forums, ddowiki Dashboard, About.
- [ ] With a data error (e.g. a misspelt class in a copy of Templates.txt): Error-Kotlin.log is written,
      opened at start-up when "Show Error Log on startup" is on, and Tools › Error Log appears.

## 7i. Start-up, autosave and templates

- [ ] Autosave: change a build, switch a Feats/Enhancements/Destinies tab or close a build window, then
      end the program without saving (Task Manager). At the next start: "A backup build was autosaved.
      Open it now?"; Yes opens it unsaved with no file name; No deletes it. (Kotlin's file is
      Backup-Kotlin.build; VB's Backup.bld is left alone.)
- [ ] Command line: `CharacterBuilderLiteNG.exe "Kensei Warpriest.build"` opens it (a bare name is
      looked for in the program's folder, then Save).
- [ ] Stats › Customize: Edit Custom Templates (copies the shipped file to Save\Templates.txt and opens
      it), Reload Templates, Delete Custom Templates (only with a custom file), Edit System Templates.
      A custom file older than the shipped one is ignored, and "Stat Template Change" help shows at start.
- [ ] Main window: drag the output with the mouse when it has scroll bars (hand cursor).
- [ ] Window menu: Cascade Windows (from the screen's top-left, as VB), the open windows in VB's order
      with the active one ticked. **VB bug to confirm:** the Destinies window is never listed (22i).

## 7j. Undo and Redo (new: VB has none)

- [ ] Edit › Undo / Redo, and Ctrl+Z / Ctrl+Y in the build windows (not in a text box, which undoes its
      own typing): each change goes back one step at a time, in every window, and Redo brings it back.
- [ ] Typing a build name or notes undoes as one step; each spinner click or rank arrow is a step.
- [ ] Undoing back to the saved or opened state clears the `*`; a new change after an undo drops Redo.
- [ ] Opening, New and Import start the history again.

## 7k. Re-check: fixed after the first comparison (2026-11-04)

- [ ] Lists, details boxes and the main window have scroll bars (the lists and details boxes always,
      greyed when not needed; the main window and slot lists only when needed).
- [ ] The main window stays the size you leave it when other windows open (it re-maximized before).
- [ ] "Next >" / "< Back" open the next window where the last one was.
- [ ] The Edit windows can't be resized (Fixed Dialogs, as VB's).
- [ ] At 125% or 150% Windows scaling, the Feats, Trees and Destinies slot lists keep their size, and 7
      enhancement trees fit without scrolling.
- [ ] Rank arrows work on an ability just added (they didn't until the list was redrawn).
- [ ] Details boxes (e.g. Destinies, bottom right): the text stays inside the box and wraps beside the
      scroll bar.
- [ ] "(Errors)" after a heading in the main window is a link to that window (new; not in VB).
- [ ] Leveling Guide grid: past 20 rows its scroll bar comes on (it stayed greyed before); the arrows
      and the track move a row, and the thumb drags.
- [ ] Right-click menus near the bottom or right edge open upward or leftward, inside the window
      (e.g. the bottom row of the Leveling Guide grid); they were cut off.
- [ ] Every window (Overview, Stats, Skills, Feats, Spells, Enhancements, Destinies, Messages, Options,
      Output Format): content starts at the same corner under the header bar, captions and column
      headers on the first line, boxes a line below, right edges at the same margin; drag and drop,
      right-click menus and drop-downs still land in the right place.
- [ ] Enhancements and Destinies, ability tabs: the slot list fills the window down to the totals line
      (no scroll bar for the sample builds), and the totals are in the same places in both windows.
- [ ] Disabled right-click menu items are grey and do nothing (Enhancements tree menu, Spells slot menu,
      Leveling Guide row menu), as VB's.
- [ ] Feats slots (now drawn by the same list as the other windows): hover, click, right-click menu,
      double-click, and dragging still behave as before.
- [ ] Feats: the Filter list is the same height as the details box. Main window: the output is inset
      a little all round.
- [ ] Overview is 890 wide (VB 866) and Options 437 tall (VB 428), to fit inside the margins; Help,
      Export and Import text is inset like the main window's.
- [ ] Text is crisp (ClearType-style smoothing and hinting), and the menu bar, message boxes and
      file dialogs look like Windows' own (they were Swing's bold "Metal" style).
- [ ] Leveling Guide, "Available" with nothing to list says why in the list, e.g. Kensei Warpriest,
      Warpriest at level 1: "Nothing in Warpriest at level 1: the earliest is level 3." (new; VB
      leaves the list blank).
- [ ] Leveling Guide, bottom right: "Level: 3 (Ftr 2/Clr 1)", the build's classes at the guide's level
      (new; VB shows "Cur. Level: 3" under the grid). Cost, Spent in Tree and Min Level are between the
      details caption and the box, lined up with its edges; the caption is bold, and the lists are a little shorter
      to make room (VB has them under the box). Level, Available and Spent are right-aligned under the grid.
- [ ] A file that can't be read or imported shows the error instead of closing the program.

## 8. Display

- [ ] Windows at 150% scaling (Windows display settings): text and layout still fit.
- [ ] Each theme in `Settings/` looks like VB's (Kotlin: Tools › Colors lists them).
