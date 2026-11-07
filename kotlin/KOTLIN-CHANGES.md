# Kotlin changes to VB behaviour and appearance

Every deliberate difference between the Kotlin version and VB's Character Builder Lite 4.15, so that
VB can be patched to match if wanted. Each change is discussed before it's made, and added here when
it's committed.

- **VB bugs** the Kotlin version fixes are in [VB-BUGS.md](VB-BUGS.md) (each with its *Fix:* for VB).
  They're only listed here by number.
- **VB changes to copy into Kotlin** go the other way, in [PORT-TOO.md](PORT-TOO.md).
- "VB" means the VB6 source on the `kotlin` branch. Paths are under `Source/`.

Kinds: **Rule** = what the program works out; **Feature** = something VB doesn't have; **Layout** =
where things are; **Look** = how they're drawn.

## 1. Rules and behaviour

| Change | Kind | VB to patch | Commit |
|---|---|---|---|
| Leveling Guide: ranks II and III of an ability are offered with the selector rank I took (VB offers every selector again). VB-BUGS 22k. | Rule | `Forms/Build/frmEnhancements.frm` `ShowGuideAvailable` (:2606) | 4ed3270 |
| Leveling Guide: after deleting, moving or inserting rows (and on Undo, Redo or returning to the tab) the tree stays selected and its ability list is rebuilt. VB-BUGS 22j. | Rule | `frmEnhancements.frm` `RefreshGuide` (:3430), `ShowTab` (:1134) | a55d6e5 |
| Leveling Guide: a step whose ability the data no longer has is kept as a red row (tier and tree shown, AP and Prog "?", costs nothing, saved back as read); a step whose tree the data no longer has is dropped; both are listed in Messages, and the rest of the guide loads. VB stops reading the guide at the first such step. | Rule | `Modules/Build/basFormatLite.bas` `LoadLevelingGuideText` (:1263) | 05ac7eb |
| An enhancement or destiny line whose rank the ability no longer has (e.g. "Haste Boost (Rank 3)", now one rank) keeps the ability at its highest rank, with a Messages notice (VB drops it). Messages says "Rank 3 not available (1 rank now)" rather than "not found" for such a rank. | Rule | `Modules/Build/basFormatLite.bas` `ResolveAbility`; frmDeprecate `AbilityNotice` | 78f06ba |
| Enhancements: an ability line the data no longer has (e.g. "Weapon Group Specialization: Axes", renamed) is kept as a red placeholder slot where the file had it among the tree's abilities: tier shown, AP "?", costs nothing, selecting it explains; double-click or Clear removes it; saved back in its place until then (VB drops it). Messages marks it "kept as a red slot". An ability dragged onto a placeholder replaces it, and the Leveling Guide's red rows for it become that ability (a rank it doesn't have stays red); clearing a placeholder removes its red guide rows. | Rule | `Modules/Build/basFormatLite.bas` `AddTreeAbility` (:1154) | d789e21, bfad56a, 17cd9f9 |
| Enhancements and Destinies: a right-click on a slot always opens the slot menu (VB clears an already selected slot on a right-click, without asking). | Rule | `frmEnhancements.frm` `usrList_SlotClick` (:1917), `frmDestiny2.frm` `usrList_SlotClick` (:1375) | bfad56a |
| Feats: a feat (or a feat's selector) the data no longer has is kept in its slot as a red placeholder showing the file's text: the slot counts as empty, selecting it explains; dropping a feat on it replaces it, double-click or Clear This Feat removes it, a slot swap moves it; saved back as read until then (VB drops it, leaving the slot empty). Messages marks it "kept as a red slot". | Rule | `Modules/Build/basFormatLite.bas` `LoadFeatsText` (:920) | ce28a72 |
| A red slot's error ("Error: Requires …") is the first line of the details box (VB puts it last, often out of sight). | Look | frmEnhancements, frmDestiny2 `ShowDetails` | 78f06ba |
| A file that can't be read or imported shows the error instead of closing the program. | Rule | `LoadBuild` and the import routines' error handlers | 8fc0792 |

## 2. Features VB doesn't have

| Change | Kind | VB to patch | Commit |
|---|---|---|---|
| Undo and Redo (Edit menu, Ctrl+Z / Ctrl+Y) for every change to the build. | Feature | new (no VB equivalent) | ac64b5b |
| "(Errors)" after a heading in the main window's output is a link that opens that window. | Feature | `Modules/Build/basOutput.bas` | d362644 |
| Leveling Guide: when "Available" lists nothing, the list says why ("Nothing in Warpriest at level 1: the earliest is level 3."). | Feature | `frmEnhancements.frm` `ShowGuideAvailable` | 86dc7a4 |
| Leveling Guide: the level caption gives the build's classes at that level: "Level: 20 (Ftr 12/Clr 8)" (VB: "Cur. Level: 20"). | Feature | `frmEnhancements.frm` `GuideTotals` (:2500), `lblGuideLevel` | e139669, 121fe14 |
| An ability's requirement reads "Req: 5 AP, Lvl 4" ("Req: unattainable" in red) on the Enhancements and Destinies status line and the Leveling Guide's info line (VB: "5 AP spent in tree"; in the guide "Spent in Tree: 5 AP" and "Min Level: 4" or "Untrainable"). | Feature | `frmEnhancements.frm` lblProg (:2385), lblGuideSpentInTree and lblGuideLevels (:3566); frmDestiny2 likewise | 0a315f2 |
| Leveling Guide: "Spent in Kensei: 13 AP" at the bottom left for the selected tree (the whole guide's spend in it). | Feature | `frmEnhancements.frm`, a new label | 5d20256 |

## 3. Layout

All windows share one frame (`WindowFrame.kt`): the header bar at the top; the content starting 16
pixels in from the left and 6 below the header (VB's forms start anywhere from 8 to 80 in); content
out to 16 pixels from the right; the footer bar across the bottom; and without a footer, a 16-pixel
margin at the bottom. Captions and column headers sit on the content's first line, with their boxes one
label line (17 pixels) below. Patching VB means moving each form's controls; the positions are in the
Kotlin windows' constants.

| Window | Change from VB | Commit |
|---|---|---|
| Main window | The output is inset 8 pixels all round (VB's Output margin option still adds its spaces on the left). | 02adab5 |
| Overview | Panels at the shared margins; the window is 890 wide (VB 866) to fit them; the footer spans the full width (VB's is inset). | 0ee0961 |
| Stats | The four frames start at the content corner; the right-hand frames end at the right margin; the content is 8 pixels higher. | 0ee0961 |
| Skills | The grid and the Skill Tomes frames start at the content corner (VB centres them). | 0ee0961 |
| Feats | Slots at the corner; the right-hand lists end at the right margin; the Filter list is the same height as the details box (VB's runs 8 pixels lower). | 02adab5 |
| Feats (2) | The slots, details box and filter list end where every window's boxes end, a status line above the footer (VB's end higher, leaving an uneven strip). | b024c2d |
| Spells | Slots at the corner with the details under them (VB centres the slots); the class drop-down and level spinner on the first line on the right; the spell list under them, ending level with the details. | 02adab5 |
| Enhancements | All three tabs start at the same corner and end at the right margin; both lists on the Trees tab line up (VB's are 8 pixels apart); the AP frames sit under the lists above them. | 789e3d2 |
| Enhancements, Leveling Guide | The lists are 24 pixels shorter; the details caption is bold, with Cost, Spent in Tree and Min Level between it and the box (VB has them under the box); the totals (Level, Available, Spent) are right-aligned together under the grid; the details box ends level with the grid. | 121fe14, 9eba41d, a96ccb0, 738d153 |
| Destinies | As the Enhancements window; "Perm Destiny Points" is a captioned frame like the Enhancements AP frames. | 02adab5 |
| Enhancements and Destinies, ability tabs | One shared tab: the slot list fills the window down to the totals line (VB's two forms differ in height, so their lists did: 443 and 403 pixels), and the totals sit in the same places in both (VB's Destinies ones were up to 16 pixels off). | 30f1913 |
| Enhancements and Destinies, Trees tabs | One shared tab. The first column is as wide as the widest text it can show (the longest class name; for Destinies the tier it shows, so its slots are wider), and the add list's Class column starts after its longest tree name, with its caption only when a listed tree has a class (VB sizes both from fixed sample names: "Arcane Trickster", "Divine Crusader", 176 pixels). | ad9ba8a |
| Slot lists (UserList) | Make room for the scroll bar when the rows don't fit, worked out from the height (VB uses a fixed 21 rows; the Spells slots could overlap the bar with 7 to 21 slots). | 30f1913 |
| Messages | The list spans the width; the three columns below share it evenly, all the same height, buttons at each column's right. | 0ee0961 |
| Options | The four frames line up two by two, frames in a row the same height, buttons at the frames' bottom right; the window is 437 tall (VB 428, whose Output frame runs past the bottom). | 0ee0961 |
| Output Format | Its links are in the standard header bar (Apply Changes greyed until there's a change); the BBCodes frame reaches the right margin. | 0ee0961 |
| Help, Export, Import | The text is inset 8 pixels (Help: 16 at the sides). | 0ee0961 |
| Lists and details boxes | Their scroll bars are always there, greyed when not needed, so text wraps beside them (VB's appear only when needed). | a591fce |

## 4. Appearance

| Change | Kind | VB to patch | Commit |
|---|---|---|---|
| Text is drawn with subpixel smoothing, full hinting and whole-pixel positions (Compose's own text drawing; VB uses Windows'). | Look | none (VB already uses ClearType) | 6a88281 |
| The menu bar, message boxes and file dialogs use the platform's look (Java's default "Metal" draws bold menus). | Look | none | 6a88281 |
| Disabled items in the Enhancements tree menu and the Spells slot menu are grey and can't be chosen, as VB's (the earlier Kotlin drew them black and closed the menu). | Look | none (VB already) | fe9521e |
| Right-click menus open upward or leftward when there's no room below or to the right, staying inside the window. | Look | none (Windows does this for VB) | 7b0f16e |
| At 125% or 150% Windows scaling, slot lists keep their size and 7 enhancement trees fit. | Look | none | ba0bb80 |

## 5. Under discussion

(Nothing at the moment.)
