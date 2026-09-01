# 2026-08-31: generate `ImageVector` icons from vendored SVGs at build time

Status: **done, 2026-09-01.** Heroicons is pinned at **v2.2.0** and all 78 committed icons
regenerated **byte-identically** from it, which answered step 1 (the version was never recorded)
and step 4 (the zero diff) at once. Kept below as the record of what was decided and why.

Four things went differently from the plan:

- **The generated file emits builder calls, not `packedImageVector`.** The packed form exists and
  round-trips every icon losslessly, but the generated file lands in `build/` where nobody reads it,
  so its size stops mattering. Byte-identical-to-what-shipped is worth more than a smaller
  build artifact, and `--packed` is one flag away if the artifact size ever matters.
- **`kotlin.srcDir` had to take the task, not the directory.** The directory form compiles fine and
  silently never runs the generator; on a clean checkout the module compiles zero sources.
- **No linter exclusions were needed.** detekt and spotless are both already scoped to `src/**`.
- **`defaultWidth` was normalized to the tier's native size.** `Solid20Mini` had drifted to 27 icons
  at 20dp and 11 at 16dp. Nothing reads the field -- `fitTo` scales from the viewport -- so the
  split was inert, and per-glyph overrides would have been manifest complexity preserving an
  accident.

Replace the 4,378 lines of checked-in `ImageVector` Kotlin with a Gradle task that generates them
from SVG sources vendored in the repo. Icons are fetched **at authoring time**, never during the
build.

## Why

`HeroIcons.kt` is 4,131 lines and `LucideIcons.kt` 247, all of it produced by
`skills/awake-ui-icons/scripts/svg_to_ui_image_vector.py` and pasted in by hand. Three problems
follow from the paste step:

- **Provenance is an assertion, not a fact.** Each glyph carries a KDoc naming its upstream source.
  Nothing checks it, and nothing on disk can be regenerated from.
- **No version is recorded for Heroicons at all.** `LucideIcons` notes `0.469.0`; `HeroIcons` notes
  the tier (`20/solid`) but no release. Once upstream moves, "which Heroicons produced this path
  data" is unanswerable. This is the only genuinely unrecoverable item here, and it gets worse with
  time.
- **The file has drifted despite the rule against hand-editing.** `HeroIcons.kt:35` carries a
  `chevron-down` KDoc documenting nothing — it sits directly above `x-mark`'s own KDoc and
  `val xMark`, while the real `chevronDown` is at line 67. Harmless in itself, but it is evidence
  that a "generated, do not hand-edit" file has been hand-edited.

The skill's own history is the argument for the whole thing: the original `HeroIcons.kt` was
hand-transcribed with every arc collapsed to straight lines, and the error was then replicated by
deriving icons from each other. Generation is already the rule; this closes the last manual step.

## Scope

**In:** Heroicons — 75 icons across `Solid20Mini`, `Solid24`, `Outline24`.

**Out:** Lucide. Those 8 icons went through `picosvg` stroke expansion (they are the only ones
using `quadTo`), so build-time generation for them needs a second external toolchain. 75 icons
versus 8 — take the value, leave the tail checked in.

## Shape

```
awake/heroicons/
  src/commonMain/svg/
    VERSIONS                              # heroicons=<tag>
    heroicons/20-solid/chevron-down.svg   # ~75 files, ~40 KB total
    heroicons/24-solid/…
    heroicons/24-outline/…
  src/commonMain/kotlin/…/HeroIcons.kt    # deleted
  build/generated/imagevector/…           # generated, gitignored

build-logic/src/main/kotlin/
  GenerateImageVectorsTask.kt
  FetchIconSvgTask.kt
  awake.icon-codegen-convention.gradle.kts
```

`src/commonMain/svg/`, **not** `src/commonMain/resources/`. Resources are packaged into every
consumer's jar, framework and wasm bundle; these are codegen inputs and nothing reads them at
runtime. Only ~40 KB, so this is about semantics rather than size — but it also avoids inheriting
the `*ProcessResources` ordering tangle described under Risks.

### Convention plugin

```kotlin
plugins { base }

val svgRoot = layout.projectDirectory.dir("src/commonMain/svg")
val generatedRoot = layout.buildDirectory.dir("generated/imagevector")

val generateImageVectors = tasks.register<GenerateImageVectorsTask>("generateImageVectors") {
    group = "build setup"
    description = "Generate ImageVector sources from vendored SVGs."
    sourceDirectory.set(svgRoot)
    outputDirectory.set(generatedRoot)
    generatorScript.set(rootProject.layout.projectDirectory.file(
        "skills/awake-ui-icons/scripts/svg_to_ui_image_vector.py"))
    pythonExecutable.convention(
        providers.gradleProperty("awake.icons.python")
            .orElse(providers.environmentVariable("AWAKE_PYTHON"))
            .orElse("python3")
    )
}

// Authoring-time only. Deliberately never wired into `build` -- see "On-demand fetch" below.
tasks.register<FetchIconSvgTask>("addIcon") {
    group = "build setup"
    description = "Download one Heroicons SVG into src/commonMain/svg and pin its version."
    destinationDirectory.set(svgRoot)
}

kotlin.sourceSets.named("commonMain") { kotlin.srcDir(generatedRoot) }
```

Declared inputs/outputs so Gradle skips the task when no SVG changed — the same reason
`awake:asset:shader-compiler` declares them around its cargo invocations instead of running an
unconditional `Exec`.

## On-demand fetch: authoring time, not build time

`HeroIcons.kt` already describes itself as growing "one glyph at a time, on demand". Keep that;
automate only the paste:

```bash
./gradlew addIcon --name=chevron-up --tier=20/solid
```

writes `src/commonMain/svg/heroicons/20-solid/chevron-up.svg` and is committed.

Fetching during the build was considered and rejected: it breaks offline and sandboxed CI builds,
pulls unverified content from the network on every machine, and — the decisive one — makes the
missing-version problem permanent rather than fixing it.

A third option exists if the vendored tree becomes annoying: consume Heroicons as a pinned npm
dependency through the existing `kotlin-js-store/` yarn setup. Reproducible and nothing vendored,
but it couples JVM-side codegen to the JS toolchain. Not worth it for 40 KB unless the tree proves
a maintenance burden.

## Steps

1. **Identify the Heroicons release** matching the committed path data. Everything else depends on
   this and it only gets harder to answer.
2. Vendor the 75 SVGs for the three tiers; write `VERSIONS`.
3. **Teach the generator to emit a whole file**, not a snippet: license header, imports, the three
   nested tier objects, and the existing 25-line class KDoc about tiers and draw-time stroke
   conversion. This is the bulk of the work and it is in the Python template, not in Gradle.
4. Generate and **diff against the committed `HeroIcons.kt`.** Do not delete anything until this
   diff is understood. A zero diff proves the pipeline reproduces what ships; a non-zero diff is
   expected given the orphaned KDoc, and each hunk needs an explanation before it is accepted.
5. Add the task, task class and convention plugin; wire `kotlin.srcDir`.
6. Exclude the generated directory from detekt and spotless.
7. Delete `HeroIcons.kt`; confirm `heroicons.api` is unchanged.

## Risks

- **Generated sources and the linters.** detekt and spotless scan Kotlin source sets and will pick
  up generated files. Exclude the generated directory rather than ordering around it — generated
  code should not be linted, and spotless would fight the generator over formatting.
- **`build/generated` vs `src/`.** `awake.shader-pipeline-convention` needs ~20 lines of
  `dependsOn`/`mustRunAfter` because `syncAwakeShaders` writes into `src/`, tripping Gradle's
  implicit-dependency validation for every consumer of that tree. Writing to `build/generated` and
  registering it with `kotlin.srcDir` lets Gradle infer the compile dependency and avoids nearly all
  of it.
- **Python in the build.** External toolchains are already normal here (cargo/naga for shaders, C++
  for jolt), and the script ships a `--self-test`. Still a new requirement for anyone building the
  icon module; the `awake.icons.python` property and `AWAKE_PYTHON` env var exist for hosts where
  `python3` is not on PATH.
- **`Outline24` is stroke data.** That tier keeps strokes and converts them at draw time
  (`strokeToFillPath`), unlike Lucide's pre-expanded fills. The generator must preserve stroke
  information for it rather than flattening.
- **IDE first-run.** Autocomplete for `HeroIcons` needs one build or Gradle sync before it resolves.

## Not doing

- Runtime SVG parsing. It would ship a parser in every binary (the wasm bundle is already 16.8 MiB),
  pay a parse cost per icon at startup, and turn a compile-time reference into a lookup that fails at
  runtime on a typo.
- Migrating Lucide, per Scope.
