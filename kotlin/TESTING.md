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
      make good automatic tests: drop them in `Save/` and the round-trip tests use them.
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

## 6. Enhancements (Trees tab only so far)

- [ ] Tree list and the slots for an opened build; dragging trees into and out of slots.

## 7. Display

- [ ] Windows at 150% scaling (Windows display settings): text and layout still fit.
- [ ] Each theme in `Settings/` looks like VB's (Kotlin: Tools › Colors lists them).
