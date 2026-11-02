# DDO Lite Tools – Kotlin conversion

Work in progress: converting the VB6 Character Builder, Cannith Crafting and Compendium
to Kotlin. See [PLAN.md](PLAN.md) for the plan and its current status.

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
| `common` | Shared by all three apps: colour themes (.screen files) so far |
| `builder-core` | Character Builder logic: `ParseLine`, Races/Classes loaders, skills grid rules |
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

## Prototypes (plan step F1)

Throwaway code to test Compose before building the real screens; see PLAN.md §8.2.

```
./gradlew :prototypes:skills-desktop:run           # open the prototype window
./gradlew :prototypes:skills-desktop:screenshots   # render it off-screen with simulated clicks → build/screenshots
./gradlew :prototypes:skills-android:assembleDebug # Android APK (same screens, data bundled)
```

| Module | What it is |
|---|---|
| `prototypes/skills-ui` | Shared screens (desktop + Android): themed spinner, skills grid, tree slots with drag-and-drop |
| `prototypes/skills-desktop` | Desktop window and the off-screen screenshot tool |
| `prototypes/skills-android` | Android app wrapping the same screens |
