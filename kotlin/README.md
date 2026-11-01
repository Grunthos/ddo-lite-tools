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
| `builder-core` | Character Builder logic. So far: the Builder's `ParseLine` and its list of data files |
| `tools/validate` | Command-line data validator, run by CI |

`data-format` and `builder-core` are Kotlin Multiplatform (JVM + Android), so they can
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
