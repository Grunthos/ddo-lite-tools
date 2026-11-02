# DDO Lite Tools – Kotlin Conversion Plan

Status: **Draft v2** (adds mobile constraints) · Last updated: 2026-11-02 · Owner: Philip Warner

This is the working plan for converting the VB6 DDO Lite Tools (Character Builder,
Cannith Crafting, Compendium) to Kotlin. It is meant to be kept up to date as work
progresses: tick off tasks, record decisions in §3, and add risks to §12.

---

## 1. Goals and non-goals

**Goals**
- **First goal: a portable desktop app for Windows, Linux and macOS** that replaces the VB6 apps and
  behaves the same for existing users. "Portable" = unzip and run, no installer: an app folder with its
  own bundled Java runtime, one zip per OS (Compose `createDistributable`, built by a CI job on each OS).
  User files stay next to the app, as today.
- Keep every data and user file format **unchanged**, so data maintainers and users notice nothing.
- Pull game rules out of the UI into tested, UI-free code.
- Make data updates safer by adding automated validation (CI).
- **Close second: Android** (iOS possible later). The desktop release comes first, but nothing
  built for it should block Android, and the prototypes run on both from the start. See §2a for the constraints this
  places on the work from day one.

**Non-goals (for the first release)**
- Redesigning the UI or workflow (parity first, improvements later).
- Changing the data file formats.
- Supporting the binary `.bld` format inside the app (a one-off converter only, §7.6).
- Porting the standalone utilities (ADQ, Colors, LightsOut, Stopwatch) – decided separately (§3, D-9).

---

## 2. Baseline (as surveyed 2026-11-02, master @ `0c57d69`)

| Area | Size | Notes |
|---|---|---|
| Character Builder | 17.0k lines modules + 22.9k forms/controls | 16 forms (1 dead), 7 Builder-specific + shared controls |
| Cannith Crafting | 6.4k + 14.9k | `frmGearset` 4.1k |
| Compendium | 3.9k + 19.9k | `frmCharacter` 5.7k |
| Shared | 13.6k | `clsConfig` 2.8k, `clsWindowsXP`, shared user controls |
| Data | 44 files, ~45k lines | Block `Key: Value` format, CRLF, mostly ASCII; `Spells.txt` has a UTF-8 BOM |
| Graphics | 338 BMP, 280 ICO | Compiled to `.RES` via `Source/Build-RESFiles.ps1` |
| Tests | none | |

**Change history.** 249 non-merge commits: 132 data-only, 52 data + code, 20 code-only,
45 neither. Since `v4.14.0`, 6 of 13 commits changed code. **Content updates often need
code changes too**, so the period where both versions are maintained must be short (§10).

**Key structural facts**
- Game data is mostly external, but `RaceEnum`/`ClassEnum` (`basEnum.bas`) hard-code races and classes, and some rules are code (e.g. Favored Soul deity slot levels in `basBuild.bas` `InitDeityFeats`).
- Over half the code is in forms/controls. Forms contain rules and write the global `build` directly.
- `CascadeChanges` (`basMain.bas:250`) is an explicit map: kind of change → derived data to recalculate → windows to refresh or close.
- The Builder main window draws `GenerateOutput` (`basOutput.bas:154`), the same text as the forum export.
- Text `.build` files store names, not indexes. Renames are handled by `NameChange.txt` + `basDeprecate`.
- Win32 dependencies: mouse-wheel subclassing, comdlg32 dialogs, wininet (wiki scraping), messages between apps via `EnumWindows`/`WM_SETTEXT` (`basAutomation`, used only for `"Colors"` and `"Notes"`), registry, clipboard, `ExitWindowsEx` (utilities).

---

## 2a. Designing for mobile later

Mobile is not in scope for the first release, but these rules apply from the start, because
fixing them afterwards would mean rewriting the core:

1. **Core and UI-state modules are Kotlin Multiplatform (KMP).** `data-format`, `common`,
   `builder-core` (and later `cannith-core`, `compendium-core`) plus the ViewModels are
   written in `commonMain`, with `jvm`, `android` and (optionally) `ios` targets. CI compiles every
   target from day one, even before a mobile UI exists. This is cheap and catches JVM-only APIs early.
2. **No JVM-only APIs in common code.** No `java.io.File`, `java.nio`, `java.net.http`, AWT,
   `String.format`, or `java.text`. Use:
   - files and paths → `kotlinx-io` (or Okio), behind a small `Storage` interface;
   - HTTP → Ktor client (only where needed in apps; the wiki scraper stays a JVM tool);
   - Windows-1252 decoding → our own lookup table (it's a 256-entry table; KMP has no built-in for it);
   - number formatting → our own helpers (the output must match VB6 exactly anyway).
3. **Where game data comes from is abstracted.** Desktop reads the `Data/` folder next to the app (as VB6
   does). Mobile ships the data in the app bundle, with optional downloadable updates later.
   Loaders take a `DataSource`, never a path.
4. **Screens don't depend on windows.** Each form is a composable screen + ViewModel that knows nothing
   about the window around it. The desktop shell puts screens in separate `Window`s (D-6); a
   mobile shell would put the same screens in navigation (tabs/stack).
5. **Touch alternatives for mouse-only interactions** are noted in the F0 inventory now, even if
   built later: right-click → long-press menu; hover tooltips → tap for details;
   mouse wheel → scroll/fling; drag-and-drop → long-press then drag, *and* a tap-to-select,
   tap-to-place fallback; click and hold to repeat on spinners → still works with touch.
6. **Expect a different layout on phones.** Dense surfaces (the 21×20 Skills grid, the enhancement
   trees, the leveling guide) will need a phone-specific layout. Parity is the desktop target; mobile
   reuses the core and ViewModels but may change the layout. Tablets should be able to use the desktop layout.
7. **Platform services go behind interfaces** in `common`: file picker/share, clipboard,
   open URL, settings storage, single instance. Desktop and mobile each provide their own.
   FileKit (multiplatform file picker) covers files and dialogs.

---

## 3. Decisions log

| ID | Decision | Status |
|---|---|---|
| D-1 | Target: Kotlin Multiplatform, Gradle multi-module. Desktop (JVM) first; Android/iOS targets compiled from day one (§2a) | Agreed |
| D-2 | UI toolkit: Compose Multiplatform (desktop now, mobile later). Swing is **no longer a sensible fallback** because it can't run on mobile. If F1 finds problems, the fallback is custom pointer/drawing code inside Compose | **Provisional – F1 found no blockers (§8.2); confirm after the Android run** |
| D-3 | Same repo, top-level `kotlin/` folder, on a long-lived `kotlin` branch; merge `master` in often; propose merging to master once B3 works | Agreed (pending Chris Lewis's buy-in, see D-10) |
| D-4 | Character Builder first; Cannith second; Compendium third | Agreed |
| D-5 | Data/user file formats are frozen; Kotlin must read and write them byte-compatibly | Agreed |
| D-6 | Separate top-level windows (as VB6) for the first release | Recommended – confirm |
| D-7 | Races/classes loaded from data, not enums | Agreed |
| D-8 | Drop `frmDestiny.frm` (not in the `.vbp`); move `frmConvert` (`.bld`) and `frmRefreshData` (wiki scrape) to command-line tools | Recommended – confirm |
| D-9 | Fate of the ADQ/Colors/LightsOut/Stopwatch utilities | **Open** |
| D-10 | Upstream owner (Chris Lewis) agreement for the `kotlin/` folder, a LICENSE, and supplying VB6 builds | **Open** |
| D-11 | Accept a bundled JRE (download size 55 MB zipped / 122 MB unpacked for the prototype vs today's small exes); delivered as portable per-OS zips (§1) | Open – portable-zip form agreed 2026-11-02; size still to accept |
| D-12 | Mobile: Android first; iOS only if there's demand and a Mac + Apple developer account available | Proposed – revisit at end of F4 |

---

## 4. Repository layout and conventions

```
ddo-lite-tools/
  Data/ Save/ Settings/ Source/ Utils/ ...   (unchanged VB6 tree)
  kotlin/
    PLAN.md                     this file
    PORT-TOO.md                 VB6 changes that must be mirrored in Kotlin (§10)
    settings.gradle.kts, build.gradle.kts, gradle/libs.versions.toml
    data-format/                KMP  block parser + writer
    common/                     KMP  config (Settings.txt), themes (.screen), BBCode formats (.bbcodes), tomes,
                                     wiki links, platform-service interfaces (§2a.7)
    builder-core/               KMP  Builder models, loaders, BuildState, rules, output/import/export
    builder-ui/                 KMP  Builder screens (composables) + ViewModels – no windows
    cannith-core/ cannith-ui/   KMP  (phase C)
    compendium-core/ compendium-ui/ KMP (phase D)
    ui-kit/                     KMP  themed widgets + widget gallery
    app-desktop/                JVM  launcher; puts screens in windows; desktop platform services
    app-android/                     (phase G) navigation shell; Android platform services
    tools/validate/             data validator CLI (CI)
    tools/bld-convert/          binary .bld → .build converter
    tools/refresh-data/         replacement for frmRefreshData (wiki spell scrape)
    golden/                     golden files + manifests (§6)
```

- Tests read `../Data` directly. Golden tests use a **pinned copy** of the data (§6.3).
- `.gitattributes`: add `kotlin/golden/** -text` so git doesn't change golden line endings.
- `.gitignore`: add `kotlin/**/build/`, `kotlin/.gradle/`, `kotlin/.idea/`, `kotlin/local.properties`.
- `Make-Release.ps1` names folders explicitly, so `kotlin/` never ships in the VB6 zip.
- JDK 21 (LTS), current stable Kotlin 2.x and Compose Multiplatform at project start; pin versions in `libs.versions.toml`.
- CI (GitHub Actions, Windows + Linux runners): build, unit tests, golden tests, validator on `Data/`.
  Compile the KMP modules for the `android` target too (and `ios` on a macOS runner if D-12 says yes).
- Tests in KMP modules use `kotlin.test` in `commonTest` so they run on every target. Golden-file tests
  may live in `jvmTest` (they need file access).
- The `tools/*` are JVM-only and may use JVM libraries (jsoup, `java.nio`).

---

## 5. Phase overview

```
          ┌── 1A  Golden capture (VB6 reference outputs) ───────────────┐
Start ────┤                                                             ├── B5+ golden diffs pass
          └── 1B  Builder core: B1 → B2 → B3 → B4 → B5 → B6 ────────────┘
                                   │
                    F0 routine inventory ─ F1 test drive ─ F2 widget kit ─ F3 form waves ─ F4 Builder parity
                                                                                │
                                         C  Cannith  →  D  Compendium  →  E  Packaging & release
```

1A and 1B run in parallel. F0 can start straight away. F1 should finish before F2 starts.

---

## 6. Phase 1A – Golden capture

**Purpose.** Record what VB6 does today, as the test data the Kotlin port must match.

### 6.1 Getting a VB6 build
- No VB6 build environment is available locally. The latest tag is `v4.14.0`, but `master` has **code changes since then** (deity slots, granted-feat source label, feat list limits). A 4.14 exe would record the *old* behaviour for those.
- **Agreed:** Philip builds the first golden snapshot from the official 4.15.0 release exes once it's published (built from `0c57d69` or later). Until then, 1B work that needs golden files (B3 error-log comparison, B5) waits, and everything else carries on.
- **Fallback (only if 4.15.0 is delayed a long time):** use the 4.14.0 release exes with the current `Data/`, and mark the affected cases (Favored Soul deity slots, the Source column of auto-granted feats) as "known difference – check by hand".
- **Optional:** twinBASIC, or the VB6 IDE in a VM, if VB6 fixes are needed during the overlap (§10), or to add a `/export in.build out.txt` command-line switch that makes capture scriptable.

### 6.2 The build set (20–30 builds)
Cover, at least:
- [ ] Single class, two-class and three-class splits
- [ ] Iconic races (e.g. Purple Dragon Knight, Mindcleaver) and newer races (Duergar, Dhampir)
- [ ] Favored Soul (deity slots), Cleric, Warlock (pact spells), Alchemist, Artificer
- [ ] Builds that reach epic/legendary levels (MaxLevels > 20), with destinies and twists
- [ ] Exchanged and alternate feats; selectors; multi-rank feats
- [ ] Tomes and every stat-point budget (28/32/34/36)
- [ ] Builds with deprecated names (trigger `frmDeprecate`)
- [ ] The existing `Save/*.build` files
- [ ] User builds collected from the forum or Discord (with permission)
- [ ] Ron's Planner and DDO Builder import samples

### 6.3 What to capture for each build
- Forum export text for each output format in `Settings/*.bbcodes` (plain text, DDO Forums, Reddit, …).
- Ron's Planner and DDO Builder export text.
- Leveling guide export (csv, txt).
- Screenshots of Overview, Stats, Skills, Feats, Spells, Enhancements and Destiny (for UI parity in F4; PNG).
- Once per snapshot: the data-load error log (`error.log`) from a clean start.

### 6.4 Storage and the manifest
```
kotlin/golden/
  snapshot-<date>/
    manifest.json        { dataCommit, exeName, exeVersion, capturedBy, capturedOn, notes }
    Data/                pinned copy of Data/ used for capture
    error.log
    builds/<name>/
      input.build
      forum-<format>.txt
      ron.txt, ddobuilder.txt, guide.csv, guide.txt
      screens/<form>.png
```
- Capture procedure: start the exe with the build path as its argument (`CharacterBuilderLite.exe "<file>"`; the Builder accepts a file on the command line, `basMain.bas:30`). Then File ▸ Export ▸ to Forums, pick the format, Copy, and paste into the file. Optionally automate with pywinauto/AutoHotkey.
- **Rule:** recapture after any Data or VB6 code change that affects captured builds. Never edit golden files by hand.

**Exit criteria 1A:** one complete snapshot committed, with a manifest, covering every checklist item in 6.2.

---

## 7. Phase 1B – Builder core (no UI)

Each step lists its deliverables and the test that marks it done.

### B1 – `data-format`
- [x] Record splitting exactly as VB's `Split(strRaw, "FeatName: ")` (`splitRecords`, `RawRecord.lines`), with line numbers for error messages.
- [x] VB6 runtime string semantics (`Vb.trim/split/isNumeric/valOf/cLng`) and basUtils helpers (`trimAll`, `cleanText`).
- [x] **Finding:** there is no single "line parser". Each VB app has its own `ParseLine`
      (Build, Cannith ×2, Compendium ×2) with different comment, list-separator and number rules.
      So `data-format` holds only the shared building blocks, and each `*-core` module ports its own
      `ParseLine`. The Builder's is done (`builder-core`: `parseLine`).
- [x] Encoding: strip BOM, accept CRLF and LF, read as Windows-1252 unless a UTF-8 BOM is present.
      Works on bytes/strings in `commonMain`. Windows-1252 table is our own code (§2a.2).
- [x] Reads through a `DataSource` interface (folder on desktop, bundled resources on mobile, files in memory in tests).
- [ ] Writer that preserves order, formatting and CRLF line endings. **Moved to B4**: the apps never write
      game data files, only `.build`/settings files, so the writer is designed with those.
- **Done when:** every file in `Data/` decodes and splits (done: `DataFolderTest`); round-trip byte tests move to B4 with the writer.

### B2 – Builder data models and loaders
- [ ] Races, Classes (incl. spell slots and lists, granted and bonus feats), Feats (+ selectors, requirements, groups), FeatMap, AutoGrantedFeats, EpicGrantedFeats, Enhancements (trees/tiers/abilities/selectors/ranks), Destinies, Spells, Templates, NameChange, Exceptions, Tomes.
- [ ] Look races and classes up by name (D-7). Keep enums only for fixed concepts (stats, skills, alignment).
- [ ] Immutable models (`data class`/`List`), built by loaders that collect errors.
- **Done when:** every loader has unit tests, including malformed-input cases.

### B3 – Validator CLI (`tools/validate`)
- [ ] Reports the same problems as the VB6 load log. Exit code ≠ 0 on errors.
- [ ] CI job runs on every push or PR that touches `Data/`.
- **Done when:** output matches `golden/snapshot-*/error.log` (same set of errors, wording may differ, differences explained). **First comparison against VB6, and the point to propose merging to `master` (D-3).**

### B4 – Build model and `.build` reading and writing
- [ ] `Build` model (replaces `BuildType6`), plus `BuildState`, which holds the build and its derived data.
- [ ] Read and write `.build` byte-compatibly. Port the deprecation flow (`basDeprecate`) as a pure function: `(build, nameChanges) → (build', issues)`.
- [ ] `tools/bld-convert` for the binary `.bld` formats v1–6 (`basDocuments.bas` ~L540).
- **Done when:** every `.build` in `Save/` and the golden set reads and re-writes identically; deprecation cases give the expected issues.

### B5 – Output, import and export
- [ ] Port `basOutput` (`GenerateOutput`, `AlignText`, validation display), `basFormat`, `basFormatLite`, the BBCode formats from `common`.
- [ ] Port `basFormatRon`, `basFormatBuilder` (import + export), leveling guide csv/txt.
- **Done when:** golden output tests pass for all builds, formats and exports. From here on, porting more rules means making more golden diffs pass.

### B6 – Rules (taken out of `basBuild`, `basProcess` and the forms, using the F0 inventory)
Order: BAB/stats → skills → granted/deity/selected feats and exchanges → spells → enhancement trees → destinies and twists → leveling guide.
- [ ] Commands on `BuildState` (e.g. `setRace`, `setClassAtLevel`, `setStatPoints`, `setSkillRanks`, `selectFeat`, `exchangeFeat`, `placeAbility`, `swapTrees`, `moveGuideRows`).
- [ ] Derived data recalculated on change, following `CascadeChanges` exactly. Write the change → recalculation table from §2 as a test.
- [ ] Move rules that are written as code into data where possible (e.g. Favored Soul deity slot levels → `Classes.txt`). Each move needs a matching data change and a test.
- **Done when:** golden output tests still pass with outputs computed from scratch (not just read from files); each rule from the F0 inventory has a unit test.

---

## 8. Forms plan (Builder)

### 8.0 How the Builder is organised (the order to follow)

The Builder is several separate windows around a main window, and a build is made by working
through them in the order of frmMain's **Edit** menu (`frmMain.frm:311`, handler `mnuEdit_Click` :738):

| # | Edit menu | Form | Key (`Form_KeyDown` :751) | What it sets |
|---|---|---|---|---|
| 1 | Overview… | frmOverview | O | name, race, alignment, classes by level |
| 2 | Stats… | frmStats | T | base stats (build points), level-up stats, tomes |
| 3 | Skills… | frmSkills | K | skill points per level, skill tomes |
| 4 | Feats… | frmFeats | F | feats per slot |
| 5 | Spells… | frmSpells | P | spells per level (casters only) |
| 6 | Enhancements… | frmEnhancements | E | trees, abilities, leveling guide |
| 7 | Destiny… | frmDestiny2 | D | epic destinies and twists (level 20+) |
| – | Gear… | (frmGear) | – | never built: commented out in `OpenForm`; drop |

- **frmMain** holds the menus and the **output view** (the build summary, regenerated as the build changes).
- **Every Edit window has the same frame** (the `userHeader` control, Navigation colours): a **header** whose left
  links are the window's tabs, current one underlined (Overview: Overview;Notes · Stats: Stats;Schedule ·
  Skills: Skill Ranks;Skill Tomes · Feats: Selected;Granted · Enhancements: Trees;Enhancements;Leveling Guide ·
  Destiny: Destiny Trees;Destinies), right links such as Help (and Contrast on Skills); and a **footer** with
  "< previous" / "next >" links in Edit-menu order, which open the neighbouring window and close this one
  (`usrFooter_Click`), so the windows also work as a step-by-step wizard. The F2 widget kit needs this as
  one shared `HeaderBar` (prototype: `prototypes/skills-ui/HeaderBar.kt`).
- **Gating** (`basMain.OpenForm`, Build/basMain.bas:131): a window only opens once the earlier steps make
  sense – Stats/Skills/Feats/Enhancements need a race; Stats/Skills/Feats/Spells/Enhancements need a class
  at level 1; Skills/Feats need all build points spent in Stats; Spells needs a caster; Destiny needs
  20+ levels. The messages differ when the user is already in the window that fixes it ("Select a Race
  first." vs "…in the Overview screen first."). The Edit menu items are enabled the same way
  (`basDocuments.bas:270`: Spells only for casters, Destiny only for 20+ levels). These are rules → `builder-core`
  (a `canOpen(window)` query with the reason), with tests.
- **Windows:** each is a separate top-level window, owned by frmMain when the `ChildWindows` option is on
  (`cfg.ChildWindows`, default True); positions saved per window (`cfg.SavePosition`/`MoveForm`); a Window
  menu lists the open ones; closing the last edit window brings the output back up (`UnloadForm`).
  Changes in one window refresh the others through `CascadeChanges`.

**Consequence for the order of work.** The F1 prototypes jumped to Skills and Enhancements because those
were the riskiest *interactions* (their presets stand in for Overview). Real screens are built in Edit-menu
order: the shell (frmMain + output) and Overview first, since every later window depends on the race and
classes Overview sets – then Stats, Skills, Feats, Spells, Enhancements, Destiny. That also sets the
core order: the build model (B4) and the Overview/Stats rules come before more loaders that only later
windows need.

### 8.1 F0 – Routine inventory (can start now)
For each form and control, list every routine, menu item, timer, drag-and-drop source/target and keyboard handler, and classify each routine:

| Class | Goes to | Example |
|---|---|---|
| **Rule** | `builder-core` command or query | `frmSkills.MaxRanks`, `frmEnhancements.CheckErrors` |
| **Screen state** | the form's ViewModel | selection, filters, active tab or cell, dragging state |
| **Drawing** | Compose `Canvas` / layout | `DrawGrid`, `DrawCell`, `FitDisplay` |
| **Platform** | `common` / app shell | dialogs, clipboard, wheel, window positions |
| **Dead** | dropped | commented code, debug-only routines |

For each interaction, also record a **touch alternative** (§2a.5), e.g. "right-click menu on a tree
slot → long-press"; "drag ability to slot → long-press then drag, or tap ability then tap slot".

**Layout (rows and columns from absolute positions).** `tools/form-layout` (`./gradlew :tools:form-layout:run`
→ `tools/form-layout/build/form-layout/`) reads each `.frm`, adds up the container offsets to get every
control's absolute position (pixels at 100% = twips ÷ 15), and groups each container's children into
rows and columns by their top and left edges (edges within 3 px count as one; near misses are listed,
as they were probably meant to line up). For each form it writes:
- `<form>.md`: per container, its row/column lines and a table of children with row, column and spans;
  menus; timers; and every code line that moves or resizes a control at run time (those positions are
  only a starting point).
- `<form>.png` wireframes at 100% scale, to lay beside a screenshot of the real form: container borders
  coloured by nesting depth with name tags, each container's own rows/columns as dashed numbered lines,
  hidden controls dotted. Containers that sit on top of each other (tab pages) get a picture each.

Philip compares the wireframes with the running VB6 app and notes anything the `.frm` doesn't show
(run-time layout, owner-drawn content). The Compose screen then follows the same rows and columns.

Deliverable: `kotlin/inventory/<form>.md` per form (generated from the `.frm` files, then reviewed). Each becomes **that form's parity checklist**. Do them in Edit-menu order (§8.0): `frmMain`, `frmOverview`, `frmStats`, `frmSkills`, …

| Form | Lines | Wave | Main risks |
|---|---|---|---|
| frmMain | 961 | 1 | 79 menu items, recent files, Window menu, output canvas, 4 timers |
| frmOverview | 2378 | 2 | 35 direct `build` writes, drag-and-drop on classes/levels |
| frmStats | 1663 | 2 | spinners, point-budget rules |
| frmSkills | 2043 | 2 | custom grid, click and hold to repeat, rules in the form |
| frmFeats | 1747 | 3 | channels/slots, `userList` scrolling, exchanges |
| frmSpells | 789 | 3 | slot types (free, cleric cure, warlock pact) |
| frmEnhancements | 3889 | 4 | 156 routines, drag-and-drop list→slot, guide grid (multi-select, move, insert, auto-scroll) |
| frmDestiny2 | 1983 | 4 | as Enhancements, plus twists |
| frmImport / frmExport / frmFormat | 1257 / 380 / 754 | 5 | parsing (core), simple UI |
| frmOptions / frmDeprecate / frmLevel / frmHelp / frmAbout / frmMessages | small | 5 | simple dialogs |
| frmConvert, frmRefreshData | — | CLI | D-8 |
| frmDestiny | — | drop | not in the `.vbp` |

### 8.2 F1 – Compose test drive (~1 week, before F2)
Build throwaway prototypes of the three riskiest interactions:
1. Themed `userSpinner` with click and hold to repeat and the mouse wheel.
2. The Skills grid: drawing, cell hit-testing, keyboard, wheel, row/column highlights, a live theme switch.
3. Dragging an ability from a list onto a tree slot (Enhancements), including the "can drop here" highlight. Try Compose's drag-and-drop API first; if it falls short, build a custom pointer-based version.

4. Put prototype 2 (the Skills grid) in a minimal Android app on an emulator or phone, using touch input only.
   This checks that the shared screen and ViewModel structure really is shared (§2a.4) and shows early
   how much a phone layout needs to change.

**Pass criteria:** prototypes 1–3 work smoothly at 100% and 150% display scaling, with no blocking
toolkit problems; prototype 4 builds and runs from the same `ui-kit`/screen code. **Fail → write custom
pointer/drawing code inside Compose for the failing piece (D-2). Revisit the toolkit itself only if
the problem is fundamental.**

**F1 status (2026-11-02): mostly done; no blocking problems found.** Code in `prototypes/` (see its modules):
- [x] 1. Spinner: themed, click and hold to repeat (VB's 300/50 ms timers), right button = large step,
      middle = min/max, wheel. Not yet: keyboard, typing digits.
- [x] 2. Skills grid: drawing, hit-testing, row/column highlight, live theme switch, all three mouse buttons,
      hold to repeat, name-column fills. Rules come from `builder-core` (`SkillGrid`), not the UI.
      Not yet: keyboard (VB's grid has none either), the right-click menu on skill names.
- [x] 3. Drag-and-drop: tree slots (add, swap, remove) with "can drop" / "drop here" highlights and a
      ghost following the pointer. **Finding:** Compose's own drag-and-drop API carries platform data
      (AWT on desktop, ClipData on Android), so shared code would need per-platform versions. A small
      shared implementation (`DragAndDrop.kt`, ~150 lines) works the same for mouse and touch; use that.
      The ability-onto-slot drag uses the same mechanism and is left for F3.
- [x] 4. Android: the same screens build into an APK (`:prototypes:skills-android:assembleDebug`) with the
      data bundled as assets. **Run on the Pixel 3a API 34 emulator (2026-11-02):** data loads, theme
      draws, tapping cells adds points, touch-dragging a tree into a slot works (same shared code as desktop).
      As expected the desktop layout doesn't fit a phone (only 8 level columns show, buttons wrap, the
      tree-slot column is squeezed): phone layouts are phase G work, not a toolkit problem.
      Other mouse buttons still need §2a.5 alternatives.
      Emulator from Git Bash: set `ANDROID_AVD_HOME=C:\Users\pjw\.android\avd` (Cygwin's HOME hides the AVDs).
- [x] Portable Windows app: `:prototypes:skills-desktop:portableZip` builds `CharacterBuilderPrototype.exe` with its own
      Java runtime and Data/ + Settings/ beside it (zip 55 MB, 122 MB unpacked). Unzipped outside the repo and
      started from another folder, it finds its data next to the .exe and runs (2026-11-02). Figures for D-11.
- [x] Multi-window shell like the VB Builder (§8.0): main window with File / Edit / Window menus and an output
      stand-in; Edit opens Skills and Enhancements in their own windows (other items disabled), plain-letter
      shortcuts K / E as in `frmMain.Form_KeyDown`; the Window menu lists open screens and brings one to the front.
      Changes in one window show in the others straight away (shared state, no `CascadeChanges` calls needed).
      Not yet: windows owned by the main window (`ChildWindows`), saved positions, the OpenForm gating.
- [x] Enhancements window: header with its three tabs (Trees working; Enhancements and Leveling Guide placeholders)
      and footer; Skills window likewise. Pressing a tree in the list or a slot selects it at once, before any
      drag (VB: ListBox selects on mouse down; `usrTree_SlotClick` toggles a slot's selection, drawn in drop-slot
      colours). **Lesson:** items used as gesture keys must compare by value (data classes), or a redraw during
      the press restarts the drag handler and the drag is lost.
- [ ] 100%/150% display scaling check, by eye on a real desktop (the off-screen renderer runs at 100%).
- **Findings for later:** AndroidX Compose needs `compileSdk` 37 (UI modules only); the default font is used
  (Verdana not yet bundled, F2); an off-screen renderer (`:prototypes:skills-desktop:screenshots`) can
  drive the UI with simulated clicks and save PNGs, which is useful for UI regression checks in F3/F4.
- **Recommendation:** confirm D-2 (Compose Multiplatform) once the Android run and the scaling check pass.

### 8.3 F2 – Widget kit and gallery (`ui-kit`)
- [ ] Theme model loaded from `.screen` files (replaces `cfg.GetColor(group, value)`), switchable at runtime.
- [ ] Fonts: Verdana 9 equivalent (`basFonts`), with a fallback where Verdana is missing.
- [ ] Widgets: Spinner, CheckBox, Header (tabs + links), Tab, Dropdown, Button, Details, Slot, List (slot list with drag-and-drop), Stats, RaceCombo, plus a generic `GridCanvas` for custom-drawn grids.
- [ ] Gallery app showing every widget in every `.screen` theme.
- **Done when:** the gallery matches the 1A screenshots by eye, in at least 3 themes.

### 8.4 F3 – Form waves
Patterns used in every wave:
- **One ViewModel per form**, holding screen state only. All changes go through `BuildState` commands, and the form watches derived state. Forms never change the build directly.
- **Refresh and close rules** from `CascadeChanges` (e.g. close Skills when race/class are unset, close Destiny when MaxLevels < 20) are written as derived "window visibility" state with tests.
- **Custom-drawn surfaces** are ported fairly directly onto `GridCanvas` (`Line`/`Print`/`TextWidth` → `drawRect`/`drawText`/`TextMeasurer`). Ordinary dialogs get real layouts, not copies of VB twip coordinates.
- **Windows** (D-6): one Compose `Window` per form, created by `app-desktop`. Positions and sizes saved in `Settings.txt` as today, plus the Window menu and cascade. Screens in `builder-ui` must not reference `Window`, AWT or desktop-only APIs (§2a.4); a check in CI enforces this by compiling `builder-ui` for Android.

Waves, in Edit-menu order (§8.0; each needs the previous one's patterns settled):
1. **Shell:** frmMain (output view from B5, menus, recent files, open/save/new/close, dirty flag, Window menu), message window.
2. **Inputs:** Overview, then Stats, then Skills (with the `canOpen` gating rules).
3. **Feats, then Spells.**
4. **Enhancements, then Destiny2.**
5. **Dialogs:** Import, Export, Format, Options, Deprecate, Level, Help, About.

**A form is done when:**
- [ ] every inventory item is implemented, or explicitly dropped and recorded in its inventory file;
- [ ] ViewModel tests cover each routine classed as screen state;
- [ ] the golden builds look the same as the 1A screenshots;
- [ ] keyboard, mouse-wheel and context-menu behaviour checked by hand;
- [ ] any open VB6 changes on `PORT-TOO.md` for that form are applied.

### 8.5 F4 – Builder parity and beta
- [ ] All golden tests green on the latest snapshot.
- [ ] Beta testers (data maintainers + a few users) run both versions on their own builds.
- [ ] Problems sorted into bug / intentional difference / VB6 bug kept for compatibility.

---

## 9. Translating VB6 to Kotlin

| VB6 | Kotlin | Watch out for |
|---|---|---|
| `Type` (UDT) assigned by value | `data class` + `copy()`, preferably immutable | **Assigning a UDT in VB copies it; in Kotlin it shares the reference** – the most likely source of subtle bugs |
| `ReDim Preserve arr` + separate count variable | `MutableList` / `List` | Remove the separate counts |
| Arrays starting at 1, `Mid`/`InStr` counting from 1 | Counting from 0 | Port with VB names at first; add helpers `mid1()`/`instr1()` only during the first pass |
| `\` integer division, `CLng` (banker's rounding) | `/` on `Int`, `roundHalfEven` | |
| `Byte` fields (overflow at 255) | `Int` with validation | |
| `Option Compare Text` (only `frmMaterial`) | `equals(ignoreCase = true)` | |
| `On Error Resume Next` (32 places) | Explicit checks, `Result`, or `runCatching` | Log what VB silently swallowed |
| `Split(x, vbNewLine)` | `lines()` | Mixed line endings |
| Global `build`, `db`, `Feat`, `Skill`, `Guide`, `cfg` | `BuildState`, `GameData`, `Config` passed in explicitly | |
| `Format$`, `CStr` on numbers, `Str$` | Our own formatting helpers in `common` | `String.format` is JVM-only; VB formatting must be copied exactly for golden tests |
| `xp.File.*`, `Open … For Input` | `Storage` / `DataSource` interfaces (§2a) | No `java.io` in common code |
| `RaiseEvent` from controls | Callback lambdas or `Flow` | |
| `Timer` controls | Coroutines (`LaunchedEffect`, `delay`) | |
| Win32 calls | JVM/Compose APIs (§11) | |

**Approach:** first pass is a faithful port, routine by routine, keeping VB names so it can be compared side by side. Refactor only after the golden or unit tests pass.

**Code conventions** (also in [README.md](README.md)):
- **Say where code came from.** Every ported function has a doc comment naming the VB module, routine
  and line (e.g. `Ported from basDataLoad.ParseLine (Build/basDataLoad.bas:2126)`). For code taken out of a
  form, name the form and event handler (e.g. `From frmSkills.picGrid_MouseDown`). Note VB quirks kept on
  purpose, and mark anything new that has no VB equivalent.
- **Plain Kotlin.** The code must be readable by people who know Java, C# or VB rather than Kotlin: loops,
  `if` statements, explicit null checks and named variables. Use Kotlin idioms (lambdas, `let`/`apply`,
  operator chains, function references) only where they are genuinely clearer or faster.

---

## 10. Running both versions in parallel

- `kotlin` branch: merge `master` in **at least weekly** and after every release.
- Every VB6 **code** change on `master` gets an entry in `kotlin/PORT-TOO.md` (commit, summary, affected Kotlin area, status). An entry is closed by a Kotlin change *plus* a test.
- Every **data** change: CI runs the validator; recapture golden files when captured builds are affected (§6.4).
- After F4 starts: VB6 gets only data updates and critical fixes, no new features.
- Cutover: the Kotlin release ships alongside VB6 for one game update cycle; then VB6 becomes read-only.

---

## 11. Replacing Win32 functionality

All of these sit behind interfaces in `common` (§2a.7), with one implementation per platform.

| VB6 / Win32 | Desktop | Mobile (later) |
|---|---|---|
| Mouse-wheel subclassing (`basMouseWheel`) | Built-in pointer scroll events | Scroll/fling |
| comdlg32 open/save/colour/print | FileKit; Compose colour picker; printing deferred | FileKit / share sheet |
| `ShellExecute` / open URL (`basWiki`) | `LocalUriHandler` / `Desktop.browse` | `LocalUriHandler` |
| wininet (`frmRefreshData`) | `tools/refresh-data` (JVM: `java.net.http` + jsoup) | n/a (maintainer tool) |
| Clipboard (export) | Compose `ClipboardManager` | Clipboard + share sheet |
| `EnumWindows` / `WM_SETTEXT` messages between apps (`Broadcast "Colors"`, `"Notes"`) | In-process events (single launcher) | In-process events |
| Registry / `GetSetting` | `Settings.txt` (already the main store); document any registry keys still used | App-private settings storage, same format |
| `App.PrevInstance` | Single-instance lock file / socket | n/a |
| `.RES` icons and bitmaps | Compose Multiplatform resources; convert BMP/ICO → PNG in the build | Same resources |
| `Data/` folder next to the exe | `DataSource` reading the folder | `DataSource` reading bundled resources (+ downloadable updates later) |
| `ExitWindowsEx`, process enumeration | Only in the utilities (D-9) | n/a |

---

## 12. Risk register

| # | Risk | L | I | Mitigation |
|---|---|---|---|---|
| R1 | Hand-drawn UI takes far more effort than expected (~57k lines in forms/controls) | H | H | F0 inventory, F1 test drive, port drawing code fairly directly onto `GridCanvas` |
| R2 | Compose drag-and-drop or custom drawing isn't good enough | M | H | F1 pass/fail gate; custom pointer/drawing code inside Compose (Swing would rule out mobile) |
| R3 | Rule differences in feats/enhancements/destinies | H | H | Golden tests (B5), rule unit tests (B6), beta (F4) |
| R4 | No VB6 build → golden files reflect stale code | M | M | Exes from Chris (D-10); "known difference" list; optional twinBASIC |
| R5 | Every VB6 fix has to be made twice for too long | H | M | `PORT-TOO.md`, weekly merges, VB6 feature freeze from F4 |
| R6 | Copy-versus-share bugs from UDTs | M | M | Immutable models; faithful-port reviews |
| R7 | Upstream owner doesn't want `kotlin/` in the repo | L | M | Keep it on a branch/fork; data shared via merges from `master` |
| R8 | Bundled JRE size / install friction | M | L | jpackage, check D-11 early |
| R9 | Font/DPI differences change layouts | M | L | Test at 100/150% in F1; fallback fonts |
| R10 | JVM-only code creeps into shared modules, blocking mobile later | M | M | KMP modules from day one; CI compiles the Android target (§2a.1) |
| R11 | Dense desktop screens don't work on phones | H | M | Separate phone layouts on the same ViewModels (§2a.6); tablets first |
| R12 | User build files on mobile (getting them in and out, syncing with desktop) | M | L | Import/export through share sheet and files; cloud sync is a separate later decision |

---

## 13. Later phases (outline, to be detailed at the end of F4)

- **C – Cannith Crafting:** `cannith-core` (shards, scaling, augments, gearsets, analysis, quick match, value), golden capture of gearset analysis and output, forms (`frmGearset` 4.1k lines first in the inventory). Reuses `ui-kit`.
- **D – Compendium:** `compendium-core` (quests, patrons, sagas, challenges, wilderness, `.compendium` reading and writing, import/export), forms (`frmCharacter` 5.7k lines, `userQuests`). Notes and Colors messages become in-process events.
- **E – Packaging and release:** a single launcher app with the three tools; portable per-OS zips (Compose `createDistributable`: app folder + bundled runtime + native launcher) for Windows, Linux and macOS, built on a CI matrix — an installer (MSI etc.) only if asked for; try an early portable zip of the F1 prototype to check size and start-up (D-11); app icons; release script equivalent to `Make-Release.ps1`; the user data location stays next to the app (`Save/`, `Settings/`) for compatibility.
- **G – Mobile (Android first, D-12):** `app-android` navigation shell around the existing
  `*-ui` screens; bundled data with an update check; phone layouts for the dense screens
  (Skills, Enhancements, Destiny, leveling guide), with tablets using the desktop layouts; touch
  alternatives from the F0 inventories; getting builds in and out via files and the share sheet. Likely order:
  Builder read-only viewer (open a `.build`, show the output and screens) → full editing → Cannith → Compendium.
  iOS only if D-12 says yes (same code, plus an Xcode shell).

---

## 14. Immediate next actions

Priority: the Windows desktop app. All the layouts come from Windows forms, so screens are built and tuned on
desktop first; Android only has to keep compiling (CI) until phase G.

1. [ ] Ask Chris Lewis: support for the `kotlin` branch/folder and a LICENSE (D-10).
2. [x] Create the `kotlin` branch; set up Gradle (`data-format`, `builder-core` as KMP with `jvm` + `android` targets, `tools/validate`), `.gitattributes`, `.gitignore`, CI workflow.
3. [x] B1: shared reading building blocks + Builder `ParseLine` + tests over all of `Data/` (writer moved to B4).
4. [x] B2: Races and Classes loaders (first typed models, races/classes by name, D-7).
5. [x] B6 (first rules): skills grid (`SkillGrid`: InitBuildSkills + the frmSkills editing rules).
6. [x] F1: prototypes 1–3 on desktop, Android build (see §8.2 status).
7. [ ] F1: ~~run the Android prototype~~ (done, emulator); portable Windows exe done; check 150% scaling on desktop; then confirm D-2.
8. [ ] **All data and file support before more screens** (agreed 2026-11-02): with the data model settled
   against the real data, the screens are mostly presentation. In VB's `InitData` order
   (Build/basDataLoad.bas, later files refer to earlier ones):
   1. [ ] B2: ~~Templates, Spells~~ (done, with the ProcessTemplates and ProcessSpells checks), Feats (+ selectors), FeatMap, AutoGrantedFeats/EpicGrantedFeats,
      Enhancements + Destinies (trees, abilities, selectors), tome data, NameChange — each with real-data
      tests and added to the validator.
   2. [ ] B4: build model + reading `.build` (`basFormatLite.LoadFileLite`) with renamed/removed-item
      handling (`basDeprecate`).
   3. [ ] B4: writing `.build` (`SaveFileLite`) + round-trip test (load → save → byte-identical) on the
      builds in `Save/` (Philip: add more varied builds – races, casters, epic, old ones with renamed items).
   4. [ ] Then the Overview window on real files, then Stats, Skills, … in Edit-menu order.
9. [ ] F0: inventory in Edit-menu order: `frmMain`, `frmOverview`, `frmStats`, then `frmSkills`. Layout tool done (`tools/form-layout`, §8.1); Philip to compare its wireframes with the real forms.
10. [ ] 1A: assemble the build set (§6.2) now; capture the first snapshot when 4.15.0 is released.
11. [ ] Confirm D-6, D-8, D-9, D-11, D-12.
12. [ ] Tell the data maintainers: `Classes.txt:481` repeats Warlock's Fey `PactSpells` line (VB6 loads it twice); `Spells.txt` lists Prismatic Strike twice (lines 1371 and 1892).
