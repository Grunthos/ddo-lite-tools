# Code review: clarity, readability and reliability (2026-11-05)

A review of the Kotlin code (about 37,500 lines, not counting tests) for readers who know Java, C# or
VB rather than Kotlin. The rule applied: use a Kotlin construct only where it makes the code clearer
than a plain loop, `if` or named variable. Everything below marked **Done** is committed on the
`kotlin` branch (not pushed); the full build and every test pass after each step.

## 1. Findings and what was done

### 1.1 Kotlin idioms

The code was already mostly plain: no `also`, `run`, `takeIf`, `fold` in the logic, `forEach`,
`filter` or `groupBy`. What was there:

| Construct | Found | Done |
|---|---|---|
| `x?.let { … }`, `.apply { … }`, `with(density) { … }` | 15 | **Done**: plain `if`s and locals; `(px / density.density).dp`; a named `greyMatrix()` helper. |
| `!!` (not-null assertion: throws if the value is null) | 17 | **Done**: a local after the null check (`val feat = e.feat; if (feat == null) …`), or an explicit error naming the impossible case. None left in the app. |
| `by lazy`, `lateinit` | 2 | `by lazy` **done** (plain initialisation). `lateinit` kept in `DesktopTemplateFiles.state`: set straight after construction, and documented. |
| `compareBy { … }`, `compareBy<T>({ … }, { … })` | 5 | **Done**: `sortWith { a, b -> … }` with the second key written out. |
| `coerceIn`, `coerceAtLeast` | 3 | One **done** (a clamp written as two `if`s); the guide's `scroll.coerceIn(0, max)` kept: it reads as "clamp". |
| `val (a, b) = f()` (destructuring) | 11 | Kept: the names are right there at the call (`val (levels, classLevels) = levelReqs(…)`). |
| Compose's `.use { }`, `remember { }`, `by mutableStateOf` | many | Kept: these are the framework's own API, with no plainer form. |
| `it` in one-line lambdas (`onClick = { screen.choose(it) }`) | many | Kept: short and conventional. |

### 1.2 `Pair` and `Triple`: the biggest readability problem

About 70 functions returned or stored `Pair`/`Triple`, read back as `.first`, `.second`, `.third`
(245 places), which tell the reader nothing. **Done**: each replaced by a small named class, e.g.

| Was | Now |
|---|---|
| `Pair<Int, Int>` (tier, ability) | `AbilityRef(tier, ability)` (model) |
| `Triple<Int, Int, Int>` (tier, ability, selector) | `AbilityChoice(tier, ability, selector)` |
| `Pair<Feat?, Int>`, `Pair<Feat, Int>` | `RequiredFeat(feat, selector)`, `FeatChoice(feat, selector)` |
| `Pair<String, Int>` (name, rank) | `NameAndRank(name, rank)` |
| `Pair<Int, Int>` from `levelReqs` | `LevelReqs(levels, classLevels)` |
| `Triple<String, Int, Int>` from `slotInfo` | `SlotInfo(caption, cost, maxRanks)` |
| `List<Pair<String, Boolean>>` (menus) | `MenuChoice(caption, enabled)` |
| `Pair<String, Boolean>` (AP line) | `Caption(text, error)` |
| `Triple<String, String, String>` | `TreeColumns(className, tier, ap)` |
| `Pair<Build, BuildRules>` (undo) | just `BuildRules` (it holds the build) |
| `Triple<List<Feat>, List<Tree>, List<Tree>>` | `ResolvedPointers(feats, trees, destinies)` |
| … and the .build reader/writer, output checks, past lives, Window menu, formats, drag and drop, grid cells | small private classes named for what they hold |

What remains: destructured results (named at the call, above), the test helper
`MapDataSource.ofText("a" to "b")`, and `BuilderSettings.fields` (key/value, read in one place).

### 1.3 Reliability

| Finding | Done |
|---|---|
| A build file or planner file that made the reader throw something unexpected crashed the app (only the file read was in a `try`). VB's LoadBuild has an error handler that shows the error. | **Done**: Open, Import (Ron's / DDO Builder) and restoring the autosave show "Can't open/import …" with the error. |
| A corrupt `Backup-Kotlin.build` could stop the program at start-up. | **Done** (as above); the backup is kept to look at or delete. |
| Settings that couldn't be saved were only printed to the console. | **Done**: a message box says so. |
| **Bug**: Stats › Clear Stats set the preferred column to Adventurer (a leftover TODO). VB uses the Default Build Points setting (frmStats.tmrFocus_Timer :1050). | **Done**. |
| The 19 `catch (e: Exception)` blocks | Checked: each either shows the error or copies VB's "ignore it" (opening a file with its program, a backup VB doesn't report either), with a comment saying so. Import from Forums catches everything on purpose: it's VB's `On Error` "Invalid or Incomplete build text". |
| Compiler warnings | One (a redundant `else`), **done**. The build now has none. |
| Dead code | None in the app. Three functions are used only by tests: `findBitmap`, `ofText`, `stripTrailingNewlines`. |

### 1.4 Comments

Every ported function names its VB source, as the conventions ask. **Done**: comments that still
called the app "the prototype", or listed finished work as "not yet" (BuildFiles, the desktop
`main`, DeprecationMessages), and the main window's title ("… (Kotlin prototype)" is now
"Character Builder Lite NG"). The Android demo's files (`SkillsScreen`, `TreeSlots*`) are still a
prototype and say so.

## 2. Test coverage

Measured with JaCoCo: `./gradlew jvmCoverage` writes `build/reports/jacoco/jvmCoverage/index.html` in
each shared module.

| Module | Lines | Branches |
|---|---|---|
| data-format | 92% | 83% |
| common | 91% | 73% |
| builder-core (the rules) | 82% | 64% |
| builder-ui (screens and their state) | 15% | 7% |

- **builder-core** is reasonably covered: the loaders, settings and model are at 90%+. The weakest
  are `TreeEdits` (60%), `GuideRules` (66%), `FeatRules` (67%), `BuildOutput` (71%) and
  `EnhancementRules` (72%), mostly VB edge cases (error messages, quirks) that no sample build reaches.
- **builder-ui** was the gap (11%). **Done**: `ScreenStateTest` drives the Enhancements, Feats and
  Spells screens as the windows do (add by double-click, rank arrows, clear, fill an empty slot,
  undo), raising those three to 40–46%. The window drawing (the `…Window` composables) isn't unit
  tested: the screenshot tool renders it but checks nothing.

## 3. Recommendations (not done)

1. **More screen-state tests**: Overview (race, classes, level grid), Stats, Skills, Destinies,
   the Leveling Guide tab and Messages, in the style of `ScreenStateTest`. These are where a user's
   clicks become build changes, and where the tidy-up into `builder-core` should continue.
2. **Golden tests against VB** (PLAN §6): the output and exports compared with VB's for the sample
   builds, once 4.15.0's exes are available. Today the output tests check lines checked by eye.
3. **Split `BuilderWindows`** (`Main.kt`, about 390 lines): the menu bar and each dialog's window
   into their own functions. Best done with the layout work (UI-IDEAS.md), which will reshape it.
4. **Long ported routines** (`FeatRules.checkFeatSlot` 215 lines, `FeatsLoader.readFeatField`,
   `GuideRules.checkGuideErrors` and `initGuideEnhancements`, `PlannerExport.identifyChannels`) keep
   VB's shape on purpose so they can be compared with VB line by line. Leave them until the golden
   tests exist; then they can be split safely.
5. **Compose UI tests** for a few windows (click a link, see the result) would cover the last 0%
   files; worth it once the layout settles.

## 4. Repetition in the windows (2026-11-06)

Why there was repetition: the port follows VB form by form, and VB's forms were copied from one another
(frmDestiny2 from frmEnhancements); VB's built-in controls had to be re-created in Compose, often per
window; and the Feats window was ported before the shared list controls existed.

| # | Repetition | Status |
|---|---|---|
| 1 | The same text styles written out 28 times | **Done**: `labelStyle`, `controlStyle`, `captionStyle` (Styles.kt) |
| 2 | Pointer tracking for menus, in 5 windows | **Done**: `Modifier.trackPointer` (WindowFrame.kt) |
| 3 | The dragged item's box, in 4 windows | **Done**: `DragGhost` (WindowFrame.kt) |
| 4 | 4 push-button helpers | **Done**: `PushButton` (OverviewScreen.kt's simple controls) |
| 5 | Feats' private list box, details box and double-click timer | **Done**: the shared ones (149 lines) |
| 6 | Captions built from `MenuChoice` lists before each pop-up menu | **Done**: `PopupMenu` takes the list |
| 7 | Enhancements and Destinies: ~420 of the Destinies state's 534 code lines, and 262 of its window's 345, also in Enhancements | **Done**: `TreeAbilitiesScreen` and `TreeListScreen` (TreeTabs.kt), one tab each, each window keeping its own rules; small layout differences unified, the Trees tab sized from its text (KOTLIN-CHANGES.md). A base class for the rules could follow once golden tests exist |
| 8 | The Feats slot list re-implements `UserList` | **Done**: on `UserList`, which now works out from its height when the scroll bar needs room; screens unchanged |

Every change above was checked with the screenshots, pixel for pixel (no differences).

**Tests first** for 7 and 8: `TreeAndSlotScreensTest` drives the Destinies window (both tabs, the tree
menu, PDPs, undo), the Enhancements Trees tab (add, swap, remove, the race tree kept, menu reset) and
the Feats slots (drag from the list, swap, drag back, the menu, the read-only Granted tab). It found a
porting bug: the Enhancements window's LoadData left out VB's `ShowTrees False, False` (:1184), so
until something redrew the Trees tab, its rows couldn't be selected and the menu's Delete and Reset
did nothing. Fixed.

Coverage (lines / branches): DestinyScreenState 70% / 39%, FeatsScreenState 74% / 50%,
EnhancementsScreenState 59% / 34%, GuideScreenState 66% / 35%, SpellsScreenState 46% / 24%;
builder-ui overall 25% / 14% (was 15% / 7%; the window drawing itself isn't unit tested).
