# Manual checks: Kotlin against VB

Things to compare by hand when there's time. Run the Kotlin version with
`./gradlew :prototypes:skills-desktop:run` (from `kotlin/`), and the VB Character Builder Lite next to it
on the same build. Tick items off, or note what differs under the item.

The most useful first pass: open each file in `Save/` in both, then go through the windows in order and
look for anything different.

## 1. Questions only VB can answer

- [ ] **Stats › Schedule tab with a level-up chosen.** In VB, choose a stat for "All Levels" (or any
      level), then click Schedule. Does it crash ("Subscript out of range")? `DrawLevelupRow` runs to
      level 36 but the grid stops at 30. Kotlin stops at 30.
- [ ] **Level 36 level-up.** Set "Level 36" to a stat on a level-30-or-more build: VB's code counts
      level-ups only to level 32, so it shouldn't raise the score. Kotlin copies that.
- [ ] **Skills right-click menu**: after choosing "Max Ranks (Full Ranks)" from the menu, a left-click on
      the same skill name should start again at Half Ranks (that's how I read the VB code, and what Kotlin
      does), rather than moving on to Even Levels.

## 2. Files

- [ ] Open each `.build` in `Save/` in Kotlin: no errors, the main window's summary matches what VB shows.
- [ ] Save from Kotlin, open the saved file in VB: same build.
- [ ] Save a build in a current VB, then open and save it in Kotlin: the file should be byte for byte the
      same (it is for `Eresh WFWizRog.build`). More such files (casters, epic, Drow, multiclass, Iconic races)
      make good automatic tests: drop them in `Save/` (or `kotlin/test-builds/` to commit them) and the
      round-trip tests use them.
- [ ] File › New, Close, Save As, and quitting with unsaved changes (Yes / No / Cancel) behave as in VB.
- [ ] The title bar shows the build name, the file name if different, and `*` for unsaved changes.

## 3. Overview

- [ ] Layout, fonts and colours against VB at 100% scaling (fonts will differ: Verdana isn't bundled yet).
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
- [ ] Clear Stats asks "Keep tomes?" (or "Clear stats and levelups?" with no tomes) and does the same.
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
- [ ] Destinies tab: slots, ranks, red slots, abilities offered, selectors, details (no level
      requirements, as VB), adding by double-click and drag, clearing, the tier 5 question.

## 8. Display

- [ ] Windows at 150% scaling (Windows display settings): text and layout still fit.
- [ ] Each theme in `Settings/` looks like VB's (Kotlin: Tools › Colors lists them).
