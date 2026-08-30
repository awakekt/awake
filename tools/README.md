# UI tooling

<!-- ui-tooling-map -->
> **Four docs cover UI tooling, with different jobs.** Land in the one that matches your question:
>
> | Question | Read |
> |---|---|
> | *Is anything wrong?* | `scripts/awake verify` — every gate, one run |
> | *Which tool answers my question, and may I re-record this baseline?* | [`skills/awake-ui-verification`](../skills/awake-ui-verification/SKILL.md) — judgment |
> | *What proof does this kind of UI change require?* | [`docs/reference/ui-validation.md`](../docs/reference/ui-validation.md) — policy |
> | *What commands do I run, in what order?* | [`docs/reference/ui-parity-tool.md`](../docs/reference/ui-parity-tool.md) — procedure |
> | *What is this script, and can it fail a build?* | [`tools/README.md`](README.md) — catalogue |
<!-- /ui-tooling-map -->


This folder contains the implementation details behind Awake's UI workflow. Start with the
single front door instead of choosing scripts yourself:

```bash
scripts/awake ui <reference|preview|validate|report|performance> ...
```

Use the lower-level tools only when you are maintaining the reference pipeline, adding a new
fixture, or investigating a renderer/font/icon problem.

## Layout

Grouped by **domain**, not by kind. A workflow is a sequence across kinds -- shadcn's is
`fetch -> extract -> vendor -> verify -> capture -> compare` -- so grouping by kind would scatter one
workflow across three folders. Kind is on each script's own header line instead, where you read it.

```
tools/
  verify_*.py            repo-wide GATEs. Top level because they belong to `awake verify`,
                         not to any one domain
  shadcn/                the whole shadcn pipeline: pin, extract, vendor, verify, capture,
                         compare, its JSON configs, and reference-app/ (the React app the
                         parity screenshots come from)
  fonts-tooling/         font instancing and reference capture, plus samples/
  icons/                 heroicons capture and its manifest
  jni-binding-generator/ unrelated to UI; own owners
```

The UI evidence scripts live in `skills/awake-ui-verification/scripts/` with the guidance that
explains when to run them, and the SVG converter in `skills/awake-ui-icons/scripts/`. Both moved
because they are standalone; everything above stayed because it is coupled to data or to a sibling
script in the same folder.

## Three kinds of tool

Every script here is exactly one of these, and each one says which in its own header. The
distinction is what tells you whether a red result means the tree is wrong or just that a picture
needs looking at.

| Kind | Contract | Can fail a build? |
|---|---|---|
| **GATE** | Exit non-zero means the tree is wrong | **yes** |
| **GENERATOR** | Writes a committed file. Re-running must be byte-identical | no — but a stale output is invisible, so each one wants a gate |
| **INVESTIGATION** | Produces evidence for a human. Proves nothing on its own | no |

```bash
scripts/awake verify          # every GATE, one run -- "is anything wrong?"
scripts/awake verify --only shadcn-reference
```

`awake verify` is the single answer to "is anything wrong". A tool not listed in its `GATES` table
cannot fail a build, by definition.

**Generators are the dangerous middle.** One looks like a gate — it runs, it succeeds — but it
*writes*, so nobody notices when the committed output stops matching. That already happened:
`extract_shadcn_tokens.py` pointed at a module path that no longer existed and `mkdir(parents=True)`
silently created the dead tree.

Every generator that can be gated now is:

| Generator | Staleness gate |
|---|---|
| `extract_shadcn_tokens.py` | `verify_shadcn_reference.sh` — also reports upstream drift |
| `vendor_reference_components.py` | `verify_generated.py` (`reference-components`), via the script's own `--check` |
| `:awake:tailwind-generator` | `verify_generated.py` (`tailwind-scale`) |
| `:awake:ui:font-atlas-generator` | `verify_generated.py` (`font-atlas`) |
| `instantiate_roboto.py` | **none, deliberately.** Its output is a TTF and re-running rewrites `head.modified` plus checksums — nine bytes, every glyph identical. A gate that always fails is worse than no gate; gating it needs a normalised compare that ignores the volatile tables. |
| `svg_to_ui_image_vector.py` | **not possible.** One-shot: one SVG in, one Kotlin val out, destination chosen by the caller, with no recorded mapping of which SVG produced which icon. There is nothing to re-run, so nothing to diff. A gate needs a manifest that does not exist. |

The four gates are declared in one table in `scripts/awake_ui.py`; adding one is a row. There is no
per-generator wrapper script, because all of them are "run a command, diff some paths" and four
bespoke copies would drift apart.

## Every tool: what it is, when to run it, and what not to do with it

Read the **Don't** column first — each one is a mistake that has actually been made here.

### Repo-wide gates (`tools/`)

| Tool | Kind | Run it when | Don't |
|---|---|---|---|
| `verify_generated.py` | GATE | Before a commit that touches a generator or its output; automatically under `awake verify` | Don't add a generator whose output is not byte-deterministic — `instantiate_roboto.py` is excluded for exactly this, and a gate that always fails gets ignored, then removed |
| `verify_detekt_baselines.py` | GATE | Automatically; it needs no arguments | Don't "fix" it with `./gradlew detektBaseline` — that regenerates, absorbing *new* findings as accepted debt. Delete the named entries instead |
| `verify_agent_skills_sync.py` | GATE | After editing a vendored skill | Don't fix a diff by editing the vendored copy; edit the source and re-sync, or the next sync silently reverts you |
| `test_repo_root_depth.py` | GATE | Automatically; it needs no arguments | Don't "fix" a failure by changing the expected depth. It is telling you a script moved and its path resolution did not follow |
| `test_awake_ui_cli.py`, `shadcn/test_vendor_reference_components.py`, `skills/…/test_compare_component_crops.py` | GATE | Automatically, under `awake verify`'s `tool-tests` | Don't add a tool without a test. These ran for weeks with no CI invoking pytest at all — 17 tests passing into a void |

### shadcn pipeline (`tools/shadcn/`)

Ordered as the pipeline runs: `fetch → extract → vendor → verify → capture → compare`.

| Tool | Kind | Run it when | Don't |
|---|---|---|---|
| `fetch_shadcn_reference.sh` | GENERATOR | First, and after bumping `PINNED_SHA` | Don't bump the pin as part of another change. It moves every parity number and is a visual decision with its own review |
| `extract_shadcn_tokens.py` | GENERATOR | After a fetch | Don't read `apps/v4/app/globals.css` — that is the docs site's own theme and it has genuinely diverged from the registry. `apps/v4/registry/themes.ts` is what ships. Checking the wrong file produces a false drift alarm |
| `vendor_reference_components.py` | GENERATOR | After a fetch, before capturing | Don't hand-edit anything under `reference-app/src/ui/`. That folder was hand-copied once and 11 of 26 files had drifted from upstream, so every parity number taken against them was measured against the wrong component |
| `verify_shadcn_reference.sh` | GATE | Before trusting any token number | Don't treat a pass as "we match shadcn". It proves the *table* matches the pin, not that Awake matches the table |
| `capture_shadcn_local.py` | INVESTIGATION | After vendoring, or when adding a case | Don't recapture to make a failing test pass. A recapture that moves numbers must be reviewed as "what was the reference wrong about" — see the kbd/toggle entry in `docs/tasks/2026-08-23-vendor-the-reference-app-components.md` |
| `compare_parity.py` | INVESTIGATION | Diagnosing a specific visual difference | Don't cite its mismatch % as a fidelity score. It is only as good as the crop alignment; a `poor` crop row means the framing differs too much to conclude anything |
| `port_progress.py` | INVESTIGATION | Checking how far Stage 3's port has got | Don't make it a gate. Mid-port it is red on purpose, and a gate that is red for weeks is one people stop reading — then it is still unread on the day it matters |

### UI evidence (`skills/awake-ui-verification/scripts/`)

| Tool | Kind | Run it when | Don't |
|---|---|---|---|
| `compare_component_crops.py` | INVESTIGATION | Comparing one component against its reference | Don't pass `--fail-on-mismatch` before reviewing the crop and heatmap and choosing an explicit `maxMismatchPct`. Without one, a case reports `REVIEW`, which is the honest answer, not a failure |
| `generate_ui_parity_report.py` | INVESTIGATION | Summarising registered slices | Don't read it as a gate. It reports what the manifest covers; a component absent from the manifest is invisible, not passing |
| `generate_ui_status.py` | INVESTIGATION | After landing UI work, to refresh the matrix | Don't hand-edit `ui-fidelity-status.md`. Every row is a probe against source, which is what stops it claiming done for unwired work |
| `ui_preview_server.py`, `ui_preview_watch.sh` | INVESTIGATION | Eyeballing during iteration | Don't cite it as proof of anything. It is a viewer |

### Assets (`tools/fonts-tooling/`, `tools/icons/`, `skills/awake-ui-icons/scripts/`)

| Tool | Kind | Run it when | Don't |
|---|---|---|---|
| `svg_to_ui_image_vector.py` | GENERATOR | Adding or regenerating an icon | Don't hand-transcribe path data, and don't derive one glyph by rotating another's coordinates. That is how the icon set ended up with every corner arc flattened to line segments. Run `--self-test` after editing the converter |
| `capture_heroicons_reference.py` | INVESTIGATION | After adding an icon | Don't forget the `heroicons_manifest.json` row — the script and `IconFidelityTest` both read it, keyed by (name, tier) because Heroicons ships different path data per tier for the same name |
| `instantiate_roboto.py` | GENERATOR | Changing the shipped font instance | Don't expect a byte-identical re-run; see the gate table below |
| `capture_font_reference.py` | INVESTIGATION | Investigating glyph baseline drift | Don't compare Awake against Awake. The whole point is Chromium as an external control — it is what proved real Roboto puts every glyph on one baseline while our atlas splits round and flat glyphs by a pixel |

## Choose the right lane

| Need | Use | A pass means |
|---|---|---|
| Check a component while iterating | `awake ui reference`, `preview`, or `validate` | You produced the relevant reference, Awake preview, or semantic crop. Review the generated image. |
| Report a registered parity slice | `awake ui report` | Geometry, paint, and missing-oracle evidence are written separately. |
| Measure comparison-tool cost | `awake ui performance` | Crop/diff orchestration time, not UI frame performance. |
| Prove a layout matches shadcn | `ShadcnGeometryParityTest` | The measured bounds match the pinned reference. |
| Detect an unintended visual change | snapshot tests or `ShadcnReferenceComparisonTest` | Awake did not drift from its accepted output. This is not proof of fidelity. |
| Maintain upstream shadcn inputs | fetch, extract, and capture tools below | The pinned source, tokens, and captures are refreshed. |
| Diagnose rendering assets | font or icon fidelity tools below | Awake is compared with the original asset rendered externally. |

Live preview serving is optional convenience tooling. It is never a verification gate.

Read `docs/reference/ui-validation.md` for the required proof for a UI change, and
`docs/reference/glossary/ui-testing.md` for plain-English test terms.

The staged cleanup and retirement criteria live in
[`docs/tasks/2026-08-16-ui-tooling-simplification.md`](../docs/tasks/2026-08-16-ui-tooling-simplification.md).

## Normal component workflow

For a component that already has registered fixtures:

```bash
# Render the official pinned reference in Chromium.
scripts/awake ui reference --component button --state rest --theme light

# Render Awake's matching fixture. Add --debug-layout when bounds need inspection.
scripts/awake ui preview --component button --state rest --theme light

# Crop Awake by semantic node and compare it with the reference. This never records a baseline.
scripts/awake ui validate --component button --theme light

# Summarize manifest-backed evidence and measure comparison-tool cost.
scripts/awake ui report
scripts/awake ui performance --component button --theme light
```

The command fails for an unregistered state or pairing rather than guessing. Add the reference
case, Awake fixture, and manifest entry together when expanding support.

## Maintainer workflow

Run this only when changing the pinned shadcn source, its token extraction, or the full
reference-report pipeline:

```bash
tools/shadcn/fetch_shadcn_reference.sh
python3 tools/shadcn/extract_shadcn_tokens.py
./gradlew :samples:ui-showcase:desktopTest --tests "*ShadcnReferenceComparisonTest*"
scripts/awake ui report
python3 skills/awake-ui-verification/scripts/generate_ui_status.py
```

The generated reports are status aids, not independent sources of truth. If they disagree with
the code or the tests, repair the generator before relying on the report.

## Asset generation

Both of these generate committed Kotlin source. Never hand-edit their output, and never
hand-author the data they produce — that is how the icon set ended up with every corner arc
flattened to line segments, and how the font atlas ended up with mismatched glyph metrics.

| Script | Generates | Notes |
|---|---|---|
| `svg_to_ui_image_vector.py` | `UiImageVector` glyph data (e.g. `HeroIcons.kt`) | Preserves curves as real cubic Beziers, converts SVG arcs exactly, keeps nested `evenodd` subpaths as holes. Rejects what the engine cannot render (strokes, transforms, crossing subpaths). Run `--self-test` after editing. See `skills/awake-ui-icons/SKILL.md`. |
| `:awake:ui:font-atlas-generator` (`generateFontAtlas` task, Kotlin/JVM, not a `tools/*.py` script) | `RobotoRegularUiFontData.kt` (packed glyph atlas + metrics) | Reads glyph metrics from the TTF's own outline geometry (`Font.createGlyphVector`) and rasterizes a separate antialiased atlas bitmap via `Graphics2D`. Glyph offsets and advances must stay in the same coordinate space — mixing cell-relative offsets with pen-relative advances produces uneven letter spacing. Replaced the former `generate_ui_font_atlas.py`, which derived metrics from the antialiased raster ink bbox and quantized them to 1/64 em. |

```bash
python3 skills/awake-ui-icons/scripts/svg_to_ui_image_vector.py icon.svg --name chevronDown --dp 16 --source "Heroicons chevron-down (20/solid)"
python3 skills/awake-ui-icons/scripts/svg_to_ui_image_vector.py --self-test
```

## Font fidelity

`capture_font_reference.py` renders each sample in `font_samples_manifest.json` with the exact
TTF baked into `RobotoRegularUiFontData`, using Chromium as the control, and saves it to
`docs/reference/font-previews/`. `FontReferenceFidelityTest` renders the same sample through
Awake's atlas pipeline and compares the resulting ink bounds at the pinned sizes.

It exists because atlas metrics alone cannot say whether a glyph sitting a pixel low is faithful
to the typeface or introduced by us. The control supplies the pinned geometry, while the
headless raster gate separately checks weighted-face baselines and antialiased edge coverage.

```bash
python3 tools/fonts-tooling/capture_font_reference.py
./gradlew :awake:compose:ui-testing:desktopTest --tests "*FontReferenceFidelityTest*"
./gradlew :awake:backend:vulkan:desktopTest --tests "*headlessMtsdfGlyphCoverageGrowsAcrossUiSizes*"
```

## Shadcn reference implementation details

The chain that answers "does this actually look like shadcn?" rather than "did this change?".
Read `docs/reference/shadcn-reference-pipeline.md` first.

| Script | Purpose |
|---|---|
| `fetch_shadcn_reference.sh` | Clones `shadcn-ui/ui` at a pinned SHA into `third_party/` (gitignored). Everything below depends on it. |
| `extract_shadcn_tokens.py` | Parses the pinned registry's new-york/neutral theme into `ShadcnReferenceTokens.kt`, the numeric ground truth for token tests. |
| `vendor_reference_components.py` | Copies `src/ui/*.tsx` from the pinned checkout into `reference-app/`, rewriting shadcn's `@/registry/...` aliases to relative paths. `--check` reports drift without writing, which is how `verify_generated.py` gates it. An unmapped alias is a hard error, never a silent passthrough. |
| `capture_shadcn_local.py` | Builds and serves `reference-app/`, then screenshots each case from `shadcn_reference_cases.json` into `docs/reference/shadcn-previews-local/`. Components come verbatim from the pinned checkout, so the reference is shadcn's own source. Captures states a docs page cannot show (focus, disabled, hover, open overlays) and any theme or radius. A case may name its own `selector` when Radix portals its content outside `#case`. |
| `compare_parity.py` | Diffs an Awake render against a reference capture: aligned crop, heatmap, mismatch metrics. Pairing lives in `shadcn_parity_pairs.json`. |

Three more live in [`skills/awake-ui-verification/scripts/`](../skills/awake-ui-verification/scripts/),
next to the guidance that says when to run them:

| Script | Purpose |
|---|---|
| `compare_component_crops.py` | Resolves an Awake semantic node ID to a raster crop, compares that crop with a component-hugging shadcn reference PNG, and writes the crop, heatmap, and JSON metrics. Supports a batch manifest. |
| `generate_ui_parity_report.py` | Generates the manifest-backed JSON/Markdown evidence report under `build/reports/ui-parity/`. |
| `generate_ui_status.py` | Regenerates `docs/reference/ui-fidelity-status.md`, the per-area status matrix. Each row's status comes from a probe against the source, so it cannot claim done for unwired work. |
| `shadcn_parity_baseline.json` | Committed regression baseline consumed by `ShadcnReferenceComparisonTest.kt` (not a script) -- each pair's last-accepted mismatch%, an absolute-percentage-point tolerance, and an `excluded` map for pairs whose crop alignment can't be trusted yet. See `docs/reference/ui-validation.md`'s "Shadcn Parity Regression Gate" section. |

```bash
tools/shadcn/fetch_shadcn_reference.sh
./gradlew :samples:ui-showcase:desktopTest --tests "*ShadcnReferenceComparisonTest*"
scripts/awake ui report
python3 skills/awake-ui-verification/scripts/generate_ui_status.py
```

### Semantic component crops

`capture_shadcn_local.py` already crops the reference side: each case is rendered in
`tools/shadcn/reference-app` inside a `w-fit` `#case` wrapper (or an explicit portal selector)
and Playwright screenshots that target. `compare_component_crops.py` supplies the missing
Awake-side crop. It reads the semantic JSON emitted beside an Awake preview PNG, converts the
node's logical `Rectangle` to the preview's raster scale, and compares the resulting crop without
manual image editing.

Use the same state/content on both sides; for a grouped case, repeat `--node-id` to union the
semantic bounds before diffing:

```bash
python3 skills/awake-ui-verification/scripts/compare_component_crops.py \
  --awake-png samples/ui-showcase/build/ui-previews/<preview-id>.png \
  --semantic-json samples/ui-showcase/build/ui-previews/<preview-id>.json \
  --node-id <component-node-id> \
  --reference-png docs/reference/shadcn-previews-local/<matching-case>_light.png \
  --name <case-name> \
  --padding 4 \
  --out-dir build/reports/ui-component-parity
```

For reviewed pairings, use `tools/shadcn/shadcn_parity_manifest.json`; copy an existing case for
the shape when adding a new one. Run it with `--manifest`. The tool never updates a baseline. A
case without `maxMismatchPct` is reported as
`REVIEW`, not pass/fail. Use
`--fail-on-mismatch` only after reviewing the generated crop and heatmap and choosing an
explicit per-case `maxMismatchPct`; a before/after crop diff proves drift, while the pinned
shadcn reference is the correctness comparison.

Comparison metrics are only as good as the alignment between the two captures. The generated
report carries a `crop` column for exactly this reason — a `poor` row means the framing
differs too much to conclude anything, not that the component is wrong.

`ShadcnReferenceComparisonTest` fails the build on regression (a pair's mismatch% drifting worse
than its `tools/shadcn/shadcn_parity_baseline.json` entry by more than the baseline's tolerance), not on
absolute distance from shadcn/ui -- that distance is real and stays untargeted. Re-record the
baseline the same way as any other golden here, `-DAWAKE_RECORD_SNAPSHOTS=true`, only after
reading the diff PNG under `build/reports/shadcn-parity/` and confirming the drift is intended
(see `skills/awake-ui-verification/SKILL.md`).

### `awake ui` command line

`scripts/awake` is the discoverable command-line front door for this pipeline. It does not own
a second renderer or invent reference images: it resolves a component fixture from
`tools/shadcn/shadcn_reference_cases.json` and `tools/shadcn/shadcn_parity_manifest.json`, then dispatches
to the existing official capture, Awake preview, and semantic-crop comparison tools.

Use it directly from the repository, or add `scripts/` to `PATH` to use `awake` without a path:

```bash
export PATH="$PWD/scripts:$PATH"

# Pinned upstream source rendered in Chromium.
awake ui reference --component button --state rest --theme light

# Existing Awake fixture. This writes the preview PNG and design-report JSON.
awake ui preview --component button --state rest --theme light

# Same Awake preview plus the semantic layout overlay:
# blue = node bounds, green = content bounds, red = clip bounds.
awake ui preview --component button --state rest --theme light --debug-layout

# Writes an Awake crop, heatmap, and JSON metric; it never updates a baseline.
awake ui validate --component button --theme light
```

The current Kotlin preview registry contains fixed, committed fixtures. The CLI rejects a state,
variant, style, base color, or accent that does not have a matching fixture rather than silently
rendering a different state. Add the reference-app case, Awake preview entry, and parity-manifest
row together before expanding the command's supported combinations.

## Icon fidelity

Proves each shipped `HeroIcons` `UiImageVector` renders the same shape as the official
Heroicons SVG it was generated from, automatically -- see `skills/awake-ui-icons/SKILL.md`.

| Script | Purpose |
|---|---|
| `capture_heroicons_reference.py` | Downloads each icon's official SVG and rasterizes it to a fixed 128x128 reference PNG (white fill on black, playwright/chromium) under `docs/reference/icon-previews/`. |
| `heroicons_manifest.json` | The (name, tier, Kotlin symbol) list both this script and `IconFidelityTest` read -- one source of truth, keyed by (name, tier) since Heroicons ships different path data per tier for the same name (e.g. `square-3-stack-3d` in both 20/solid and 24/solid). |

```bash
python3 tools/icons/capture_heroicons_reference.py                    # re-capture every reference
python3 tools/icons/capture_heroicons_reference.py --only chevron-down,camera
./gradlew :awake:ui:headless:desktopTest --tests "*IconFidelityTest*"
```

`IconFidelityTest` (`awake/ui/headless/src/desktopTest/`) renders each icon through
the real CPU rasterizer at the same 128x128 size and compares coverage-mask IoU against the
reference (threshold and measured correct-vs-corrupted separation are documented on
`passThreshold` in the test). It writes a reference/ours/diff PNG per icon to
`build/reports/icon-fidelity/` and a `metrics.tsv` — always regenerate references after adding
or regenerating an icon in `HeroIcons.kt`, and add the new entry to `heroicons_manifest.json`.

Catches gross shape errors (wrong path data, a rotated/mirrored derivation, a flattened curve).
Does not catch sub-pixel drift -- both pipelines' antialiasing differs enough that IoU alone
can't distinguish a 1px edge nudge from noise; that is a known ceiling, not a gap this guard
silently papers over.

## Optional live preview

`ui_preview_server.py` and `ui_preview_watch.sh` serve rendered preview PNGs for manual
review. They are a convenience for eyeballing, not a verification gate — see
`docs/reference/ui-validation.md` for what actually counts as proof.

## Does any of this need replacing for `awake:compose`?

**No. One adapter, roughly one file.** The split is clean because of where the engine boundary
falls, and it is worth stating precisely so nobody plans a second pipeline.

**Engine-agnostic — every one of these works unchanged.** The whole reference half never touches
Awake: the pinned checkout, the token table, the vendored `.tsx`, the reference app, the captured
PNGs and geometry JSON, and the font/icon controls are all produced by Chromium from upstream
sources. `ui-core` and `awake:compose` are compared *against* them; neither participates in
producing them.

**Engine-coupled — exactly one contract.** The Awake side of every comparison is two artifacts
written by `AwakeUiPreviewWriter` (`awake:ui:testing`): a preview PNG, and a sibling JSON whose
shape is all the tools actually depend on:

```json
{ "width": 800, "height": 600,
  "semantics": [ { "id": "parity-button", "bounds": { "x": 0, "y": 0, "w": 40, "h": 40 } } ] }
```

A flat list, `id`, and `bounds{x,y,w,h}`. Both engines can produce it, but not identically:

| | `ui-core` | `awake:compose` |
|---|---|---|
| Type | `UiSemanticNode(bounds: Rectangle, id: String?)` | `SemanticsNode(config, x, y, width, height, children)` |
| Identity | `id: String?` | `config[TestTag]` |
| Bounds | one `Rectangle` | four `Int`s |
| Shape | flat, **emit-ordered** | **tree**, spatially ordered from the placed tree |

So the adapter flattens the tree, maps `testTag → id` and `x/y/width/height → bounds`, and every
tool above keeps working with no change. That is already the plan's
`09-testing-harness.md` deliverable — "the `ui-testing` adapter" — not new work discovered here.

**One behavioural difference to expect, and it is an improvement.** `ui-core` orders semantics by
emit order, so its JSON reshuffles when an unrelated widget moves; compose derives order from the
placed tree. Any tool that reads nodes *by id* — which is all of them — is unaffected. A tool that
ever reads by index would break, and none should be written that way.

**What genuinely is engine-coupled and will need editing:** `generate_ui_status.py`, whose probes
hard-code `awake/ui/ui-core/src`, `awake/ui/headless/src` and `awake/ui/shadcn/src`. It reports
on the module layout, so it follows the modules. That is a path list, not a redesign.

## Other

`jni-binding-generator/` generates JNI bindings for the native backends and has its own
documentation.

## Detekt baselines

Every module's `detekt-baseline.xml` was regenerated on 2026-08-09 after the parallel
component wave (Resizable, Sheet, ScrollArea, Empty, Combobox, Table, studio camera mode)
and the accordion/slider/checkbox fixes. Everything absorbed is structural — MagicNumber on
graphics/geometry constants, LongMethod, LongParameterList, CyclomaticComplexMethod — and was
reviewed before absorbing: findings that pointed at real defects (dead code, unused
parameters, an unapplied modifier) were fixed in source, not baselined. detekt's baseline
parser rejects XML comments, so this record lives here rather than in the files themselves.

A new entry appearing in a baseline diff means new structural debt: prefer fixing it or a
commented `@Suppress` at the site; `./gradlew detektBaseline` is the deliberate second choice.
