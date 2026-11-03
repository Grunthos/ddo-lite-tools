# UI ideas for after VB parity

Status: **ideas, not planned work** (2026-11-04). Nothing here changes the Kotlin app until the
comparison with VB (PLAN.md §0, step 6) is signed off. The aim is to keep VB's behaviour and rules
while making the program easier to use than VB's many fixed-size windows.

## 1. The main choice: how the windows are arranged

### Option 1 – Tidied classic

The same windows as VB, but:
- one Edit window that keeps its size and place; "Next >", "< Back" and the Edit menu change what it
  shows instead of opening another window;
- every window resizable, with the lists and details boxes growing and the fixed parts staying put.

Cheap, and still feels like VB; still several windows to manage.

### Option 2 – Tabbed main window

Overview, Stats, Skills, … as tabs in the main window, with the output under or beside them.
Export, Options and Help stay dialogs. Easy to understand; the output and the screen being edited
compete for space.

### Option 3 – One window: navigation, editor, output (recommended)

```
┌─ Character Builder Lite ─ Kensei Warpriest* ──────────────────────────────────────────────┐
│ File  Edit  Tools  Window  Help                                                           │
├──────────────┬──────────────────────────────────────────────┬─────────────────────────────┤
│ ✓ Overview   │  Feats                     [filter: ______]  │ Feats (Errors)              │
│ ✓ Stats      │  ┌────────────────────────┐ ┌──────────────┐ │                             │
│ ⚠ Skills  4  │  │ 1 Heroic  Power Attack │ │ Acrobatic    │ │  1 Heroic : Power Attack    │
│ ✖ Feats   2  │  │ 1 Human   Least Drag…  │ │ Alertness    │ │  1 Human  : Least Dragon…   │
│ ✓ Spells     │  │ 3 Deity   (empty)    ✖ │ │ …            │ │  3 Deity  :                 │
│ ✓ Enhance…   │  │ …                      │ └──────────────┘ │  …                          │
│ ⚠ Guide      │  └────────────────────────┘  Details …       │                             │
│ ✓ Destinies  │                                              │                             │
├──────────────┴──────────────────────────────────────────────┴─────────────────────────────┤
│ Problems (6): ✖ Feats: level 3 Deity slot is empty · ⚠ Skills: 4 points unspent at 12 …   │
└───────────────────────────────────────────────────────────────────────────────────────────┘
        navigation        editor (fills the space)          output (drag the divider; hide)
```

- **Navigation** down the left in Edit-menu order, each with a ✓ / ⚠ / ✖ badge from the same checks
  that put "(Errors)" in the output (`ValidSkills`, `ValidFeats`, …).
- **Editor** in the middle: the screen that's chosen, using all the space it's given.
- **Output** on the right for that section, as VB's main window shows it; the divider drags, and the
  panel can be hidden. The `(Errors)` links already open the right screen.
- One window, which remembers its size and place; nothing to cascade or find.
- **Mobile:** the same pieces. A phone shows the navigation, then one screen at a time, with the
  output as another page; a tablet in landscape uses the desktop layout.

Export, Import, Options, the format editor, Help and Messages stay separate dialogs (they're used
now and then, and VB users expect them).

## 2. Improvements that suit any of the options

In rough order of value for the effort:

1. **Problems panel.** One list of everything wrong with the build: empty or invalid feat slots,
   unspent or overspent skill points, enhancements whose requirements fail, guide steps over their
   AP, stats not fully spent. Each line goes to the slot. The rules exist already (`CheckSlotErrors`,
   `CheckAbilityErrors`, `CheckGuideErrors`, the skills grid); this gathers them in one place.
2. **Undo / redo.** Once the build changes go through `builder-core` commands (the tidy-up step), a
   snapshot before each command gives undo and redo nearly for free.
3. **Type to filter** above the feat, ability, spell and tree lists.
4. **Details on hover** (a tooltip with the description), with the details box kept for pinning one.
5. **Keyboard:** Ctrl+1…8 for the screens; + / − for ranks and skill points beside drag-and-drop;
   Delete to clear a slot.
6. **Several builds open** as tabs, and comparing two builds side by side (a diff of their output).
7. **Leveling guide timeline:** the guide as a level-by-level list of what's taken, besides the grid.
8. **Appearance:** follow the system's light/dark setting; bundle Verdana so the layouts match VB
   exactly; clean scaling at 125–200%.
9. **Remembered layout:** divider positions, the last screen shown and the list filters, per user.

## 3. How to get there without losing VB parity

1. **Tidy-up step (planned):** every screen's content fills the space it's given, with no window
   sizes inside it, and its state and rules move out of the `*ScreenState` classes into `builder-core`.
2. **Option 3 shell** around the same screens. The classic multi-window layout stays available
   (Options › Layout: Classic / Single window) until parity is signed off, so the two can be
   compared side by side.
3. **Flexible layouts**, one screen at a time, replacing VB's fixed positions: Feats, Enhancements
   and Skills first (the busiest), then the rest. Android needs this anyway.
4. Then the improvements in §2, starting with the problems panel and undo.

## 4. Open questions

- Keep the classic layout for good, as an option, or drop it once the new one is accepted?
- Should the output panel follow the screen being edited (as VB's does) or always show the whole
  build, scrolled to the section?
- Any of these worth raising with Chris Lewis for the VB version too (e.g. the problems list)?
