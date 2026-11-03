# DDO Lite Tools – Kotlin conversion

Work in progress: converting the VB6 Character Builder, Cannith Crafting and Compendium
to Kotlin. See [PLAN.md](PLAN.md) for the plan and its current status (§0), [TESTING.md](TESTING.md)
for the checks against VB, and [VB-BUGS.md](VB-BUGS.md) for the VB bugs found while porting.

The Character Builder runs on Windows (desktop) with every window except Convert Builds and Colors;
Cannith Crafting and the Compendium haven't been started.

The VB6 apps are unaffected: this folder isn't part of their release zip, and both
versions read the same `../Data` folder.

## Building

Needs JDK 21. The Android target also needs the Android SDK: set `ANDROID_HOME`, or create
`local.properties` here with `sdk.dir=C:/Users/<you>/AppData/Local/Android/Sdk`
(forward slashes).

```
./gradlew build                  # compile all targets, run all tests
./gradlew :tools:validate:run    # check ../Data for problems
```

## Modules

| Module | What it is |
|---|---|
| `data-format` | Reading data files: Windows-1252/BOM decoding, VB6 string functions (`Vb`), basUtils helpers, record splitting, `DataSource` |
| `common` | Shared by all three apps: colour themes (.screen files), the RES file reader (class icons), Help.txt |
| `builder-core` | Character Builder logic, no UI: data loaders (`data`), the build and its rules (`build`), output and exports (`output`), imports (`imports`), settings (`settings`) |
| `tools/validate` | Command-line data validator, run by CI |
| `tools/form-layout` | Reads VB6 `.frm` files: layout report (rows/columns from absolute positions) and wireframe PNGs, for the F0 inventory |

`data-format`, `common` and `builder-core` are Kotlin Multiplatform (JVM + Android), so they can
later be used by a mobile app. Code in them must not use JVM-only APIs (`java.io`,
`java.nio`, AWT, `String.format`); the tools are JVM-only and may.

## Code conventions

- **Say where code came from.** Every function ported from VB6 has a comment naming the
  VB module, routine and line, e.g. `Ported from basDataLoad.ParseLine (Build/basDataLoad.bas:2126)`,
  and for code taken out of a form, the form and event handler. Note any VB quirk being kept on
  purpose, and anything new that has no VB equivalent.
- **Plain Kotlin.** Write code that readers from Java, C# or VB can follow: loops, `if`
  statements, explicit null checks and named variables. Use Kotlin idioms (lambdas,
  scope functions like `let`/`apply`, operator chains) only where they're genuinely clearer or faster.
- **Copy VB behaviour exactly first.** Use `Vb.trim`, `Vb.split`, `Vb.isNumeric`, `Vb.valOf`,
  `Vb.cLng` rather than the Kotlin equivalents wherever the difference could change a result.
  Improve on VB only once tests prove the behaviour is unchanged.

## The Character Builder app

Started as the Compose test drive (PLAN.md §8.2) and grew into the Builder. The rules are all in
`builder-core`: the screens' state classes in `builder-ui` hold only what a window shows and call
`builder-core` to change the build, and every change goes into the undo history (`BuildHistory`).

It reads `Data/` and `Settings/` from the repository (or next to the .exe), and writes only its own files
there: `Settings-Kotlin.txt` (started from VB's Settings.txt the first time), `Error-Kotlin.log` and
`Backup-Kotlin.build` (the autosave). VB's files are never changed. A `.build` file on the command line is
opened at start.

```
./gradlew :builder-desktop:run           # open the Character Builder
./gradlew :builder-desktop:portableZip   # portable Windows app: build/portable/ (folder + zip, .exe with its own Java runtime)
./gradlew :builder-desktop:screenshots   # render it off-screen with simulated clicks → build/screenshots
./gradlew :builder-android:assembleDebug # Android APK (same screens, data bundled)
```

| Module | What it is |
|---|---|
| `builder-ui` | The screens and their state (desktop + Android): every Builder window |
| `builder-desktop` | The desktop app (windows, menus, file dialogs, settings file) and the off-screen screenshot tool |
| `builder-android` | Android app wrapping the same screens |
