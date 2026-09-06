# 2026-08-22: dropping the `Ui` prefix from the 2D draw vocabulary

Status: **planned, not started.** Blast radius measured 2026-08-22 — every number below is a count,
not an estimate. The requested 29-item list is mostly mechanical; four items are not, and two of
those turn out not to be renames at all.

## Decision ledger

Settled during planning, from measurement rather than preference:

| # | Question | Decision |
|---|---|---|
| 1 | When does the rename run? | **Opportunistic, not big-bang.** Rename what a module owns as you touch it; never introduce a new `Ui`-prefixed name. `UiLayoutStats` → `LayoutStats` already landed; all new `:awake:compose:*` API avoids the prefix |
| 2 | Rename everything at once? | **No, twice over.** Split by *kind of work* — rename, move, rewire never share a commit ([details](#three-kinds-of-work-and-they-must-not-share-a-commit)) — and split the rename itself by whether the consumer survives Stage 3 ([details](#the-decision-that-halves-the-work)) |
| 3 | `UiDensity` → `Density`? | **No — it is not a rename.** Two `Dp.toPx()` apply to one receiver and one silently reads a global. [Details](#uidensity--density--the-name-is-not-the-problem) |
| 4 | Can `UiDensity` be retired outright? | **Yes, and it is now its own task** — [retire-global-density-plan](2026-08-22-retire-global-density-plan.md). Lands before this pass |
| 5 | `UiLinearGradient` → `LinearGradient`? | **Yes, and it is half done** — the typealias already exists; promote it |
| 6 | `UiVertexLayout` — name and home? | **`VertexFormats2D` in `core:geometry`.** Its only 2D dependency is a dead import. [Details](#uivertexlayout--neutralize-the-name-move-it-to-coregeometry) |

| 7 | `2D` suffix on all of `passes2d`? | **No — `Draw*` across the module, `2D` only for `RenderFeature`,** which really collides with the 3D one. Decided 2026-08-22. `TexturedPrimitiveRun` is the evidence: already unprefixed in that module, absorbed cleanly as `TexturedDrawRun`, where a blanket suffix would make it `TexturedPrimitiveRun2D` — worse than the status quo. [Details](#the-2d-suffix-pick-draw-suffix-only-the-real-collision) |
| 8 | `DrawCommand` or the existing dead `DrawPrimitive` alias? | **`DrawCommand`.** Three of eleven variants are clip-stack state changes, not drawable geometry, and the consumer is a command buffer |

Still needing your call:

| # | Question | Recommendation |
|---|---|---|
| 9 | Bundle with the `io.github.awakelab.*` namespace move? | **Yes if that decision is final** — one edit per import instead of two, across ~2,200 sites. Otherwise run Phase A alone rather than wait |
| 10 | Do the vertex/mesh types follow out of `core:graphics2d`? | Deferred, and [splitting `UiPath.kt`](2026-08-22-split-uipath-plan.md) makes it a one-line decision rather than an extraction |

## Why

The `Ui` prefix dates from when these types lived in the UI stack. They do not any more:
`6c84a4fe4` moved the 2D draw vocabulary into `core:graphics2d` and `core:math2d`, where the prefix
now says nothing — a `DrawCommand` in a graphics module does not need to announce that some UI
somewhere might use it. `awake:compose:*` already avoids the prefix in everything it declares, so
the gap widens with each commit that lands there.

This document covers the one bulk pass the shared 2D types still need. Everything else follows
decision 1 above: rename opportunistically, as each module is touched.

## Three kinds of work, and they must not share a commit

The requested list is a rename. Answering it honestly turned up two other kinds of change sitting
inside the same scope, with very different risk. Separating them is the difference between a pass
that can be reviewed by reading a diff and one that cannot.

| Kind | What it is | Risk | How it is verified |
|---|---|---|---|
| **Rename** | A symbol's name changes. Nothing moves, nothing behaves differently | Near zero | It compiles, or it does not |
| **Move** | A declaration changes module or file. The dependency graph shifts | Real — a wrong home is invisible until something needs to depend the wrong way | Compiles, plus the module graph still makes sense |
| **Rewire** | Code stops reading a global and takes a parameter | Genuine — behaviour can change | Tests, and only tests |

### Renames — mechanical, ~29 types

Every type in [The expected rename](#the-expected-rename) except one, plus 11 files, 1 misnamed test
class and 4 dead aliases. No declaration changes module. Reviewable as a diff of names.

### Moves — structural, 2 now and 2 deferred

| Move | Status | Why it is a move, not a rename |
|---|---|---|
| `UiVertexLayout` → `VertexFormats2D` in `core:geometry` | **In scope** | Changes module. Safe because its only 2D dependency is a dead import — verified |
| `UiDensity` + `toPx`/`px` out of `core:math2d` | **Split out** — [own task](2026-08-22-retire-global-density-plan.md) | `core:math2d` stops depending on a display concept |
| `UiPath.kt` split — 16 types in 69 KB | Deferred | Own change, own review |
| Vertex/mesh types out of `core:graphics2d` | Deferred | Module-boundary call |

### Rewires — behavioural, 2 files

Both fall out of retiring `UiDensity`, and **both can change what renders**:

| Rewire | Files | Risk |
|---|---|---|
| `UiPath` takes density as a parameter instead of reading the global | 1 | Corner radii resolve against a passed value. If a caller passes the wrong one, shapes are subtly wrong and nothing errors |
| Platform hosts pass density to `ComposeHost` instead of writing a global | 3 | A host that forgets leaves the UI at scale 1 on a HiDPI display |

These are the only part of this plan that needs more than a compiler to believe. They want tests
first, and they should land **before** the rename — see [Ordering](#ordering).

## Ordering

1. **[Retire the global density](2026-08-22-retire-global-density-plan.md) first.** Four files, two
   of them behavioural. Easiest to review in isolation and hardest to review buried in 200 renamed
   ones — and it deletes a type this plan would otherwise have to name.
2. **Moves next** — `UiVertexLayout` to `core:geometry` — while the diff is still small enough that a
   wrong module choice is obvious.
3. **Renames last**, in bulk, once nothing structural is left to think about. This is the pass that
   touches ~200 files and should be the one where nothing needs judgement.

Reversing this — renaming first — means every later move and rewire is reviewed against a diff that
has already touched everything.

## Blast radius, measured

~2,200 references across ~200 files. One type is 44% of the job. Counts are type references only —
file renames and test-class renames are additional, and enumerated per module below.

| Type | Files | Refs |
|---|---|---|
| `UiDrawPrimitive` | 127 | 978 |
| `UiShapeSpec` | 30 | 172 |
| `UiPoint` | 5 | 152 |
| `UiDensity` | 16 | 105 |
| `UiPathCommand` | 5 | 97 |
| `UiVertexLayout` | 11 | 95 |
| `UiPath` | 15 | 80 |
| `UiRun` | 14 | 75 |
| `UiStagedRun` | 4 | 63 |
| `UiFillRule` | 5 | 60 |
| `UiPipelineKind` | 9 | 55 |
| `UiPrimitiveTransform` | 14 | 47 |
| `UiLinearGradient` | 14 | 40 |
| remaining 16 types | ≤11 each | 350 total |

## The decision that halves the work

**60% of the churn is in modules scheduled for deletion or rewrite.**

| Module | Files touched | Fate |
|---|---|---|
| `ui/headless` | 33 | Stage 3 port target |
| `ui/ui-core` | 29 | **Deleted** at the end of Stage 3 |
| `ui/designsystem` | 21 | Stage 3 rewrite |
| `ui/animation` | 3 | Rewrite, not port — see `05-animation.md` |

86 of ~200 files would be renamed and then thrown away.

So the pass splits by **whether the consumer survives Stage 3**:

**Phase A — the surviving graph (~60 files).** Rename the declarations in `core:graphics2d` and
`core:math2d`, and update only consumers that outlive `ui-core`:

| Module | Files |
|---|---|
| `backend/vulkan` | 18 |
| `ui/testing` | 14 |
| `compose/*` | 13 |
| `samples/*` | 8 |
| `backend/webgpu` | 5 |
| `engine/render/passes2d` | 3 |
| `engine/bootstrap` | 3 |

The dying modules keep compiling on `typealias UiDrawPrimitive = DrawCommand` and friends, placed
beside the new declarations.

**Phase B — delete each alias as its module goes.** No separate pass: an alias dies with the module
that needed it, so the cleanup cannot be forgotten or drift.

Phase A is roughly half the files and none of the throwaway work.

## Corrections to the list

Two entries as requested are wrong, and one naming rule is inconsistent. None of these are style
preferences — each is a measured conflict.

### `UiDensity` → `Density` — the name is not the problem

Renaming it collides with `compose.ui.unit.Density`, but that collision is a symptom. The real issue
is one level down and worth fixing on its own terms.

`compose.ui.unit.Dp` is a **typealias** for `core.math2d.Dp`. There is exactly one `Dp` in the tree,
which is deliberate — but it means both of these apply to the same receiver:

```kotlin
// core.math2d — top-level extension, reads a process-wide mutable singleton
fun Dp.toPx(): Float = value * UiDensity.scale

// compose.ui.unit.Density — member extension, reads the node's own density
interface Density { fun Dp.toPx(): Float = value * density }
```

Inside a `Density` scope the member wins and the answer is per-node. **Outside one, the top-level
extension silently wins and returns a process-wide value.** No error, no warning — a layout computed
against whatever the last platform host happened to set.

Compose imports the global extensions **zero** times today, so the trap is unsprung. Nothing prevents
it, and the failure would be a wrong size somewhere with nothing pointing at the cause.

**Fix: move density out of `core:math2d`. Do not rename anything.**

1. `core:math2d` keeps `Dp` and `Sp` as pure value classes and loses all density knowledge. A unit
   type should not depend on a display.
2. `UiDensity`, `Dp.toPx()`, `Sp.toPx()` and `Float.px` move to `ui-core`, which owns the
   immediate-mode model that wants a global.
3. `ui-core` is deleted at the end of Stage 3, so **the global dies with it** and `Density` becomes
   unambiguous with no rename at all.
4. `WebGpuCanvasHost` and `VulkanMetalView` stop writing a global and pass density to
   `ComposeHost(density, fontScale)`, which they have to do for the compose path regardless.

Cost, measured: 94 files import the global extensions, and **80 of them (85%) are in modules already
scheduled for deletion or rewrite** — `ui-core` (36), `ui/headless` (36), `ui/designsystem` (4),
`ui/animation` (4). The types move *with* those modules, so those files need nothing. Only 14
surviving files need a real edit: `ui/testing` (5), samples (4), `backend/vulkan` (2),
`engine/render/passes2d` (1), `engine/bootstrap` (1), `core/graphics2d` (1).

This is smaller than the rename it replaces, and it deletes a hazard rather than renaming around one.

**If the move cannot happen in this pass**, the interim is to rename the *extensions* rather than the
object: `Dp.toPxGlobal()`, `Float.pxGlobal`. Ugly on purpose — it makes the wrong call impossible to
make by accident, which a same-named pair does not.

### `UiLinearGradient` → `LinearGradient` — already done, mostly

`UiGradient.kt` already declares `typealias LinearGradient = UiLinearGradient` (and
`typealias Gradient = UiLinearGradient`). The rename is: promote the alias to the real declaration,
delete both aliases, update the 40 references. No conflict to resolve.

### The `2D` suffix: pick `Draw*`, suffix only the real collision

The requested list mixes two conventions inside one module. `engine/render/passes2d` would end up
with `DrawRun`, `StagedDrawRun`, `DrawRunRecorder`, `DrawRunCoalescer`, `DrawMeshUploader` beside
`Pass2D`, `PipelineKind2D`, `RenderFeature2D`. Same module, two rules.

Only **one** of the four suffixed names has a real collision: `RenderFeature` exists at
`awake/engine/render/passes/.../RenderFeature.kt` — the 3D one. `Pass`, `PipelineKind` and
`VertexLayout` have no declaration anywhere in `awake/`.

**Recommendation: `Draw*` across the module, `2D` only where a 3D counterpart exists.**

| Old | Recommended | Instead of |
|---|---|---|
| `UiRun` | `DrawRun` | — |
| `UiStagedRun` | `StagedDrawRun` | — |
| `UiPass` | `DrawPass` | ~~`Pass2D`~~ |
| `UiPipelineKind` | `DrawPipelineKind` | ~~`PipelineKind2D`~~ |
| `UiRunRecorder` | `DrawRunRecorder` | — |
| `UiRunCoalescer` | `DrawRunCoalescer` | — |
| `UiMeshUploader` | `DrawMeshUploader` | — |
| `UiRenderFeature` | `RenderFeature2D` | keeps the suffix — real collision |
| `SharedUiRenderFeature` | `SharedRenderFeature2D` | follows it |

Why not suffix everything:

- **The package already says it.** `render.passes2d.DrawRun` repeats "2D" twice if the type carries
  it too.
- **`Draw*` is a family, `*2D` is a tag.** `DrawCommand`, `DrawPath`, `DrawRun`, `DrawPass`,
  `DrawStroke` read as one vocabulary. `Pass2D` and `PipelineKind2D` read as leftovers from a split.
- **A suffix with no counterpart ages badly.** `Pass2D` implies a `Pass3D` a reader will go looking
  for and not find.

The counter-argument, which is real: a bare `DrawRun` in a call site does not announce its dimension.
It is weakened by `Draw*` already being the 2D draw vocabulary's prefix — `DrawCommand` is 2D, so
`DrawRun` is too.

**Decided 2026-08-22: (a).** `Draw*` across the module, `2D` reserved for `RenderFeature`, which
really does collide with `engine/render/passes`'s 3D one.

The alternative — `2D` on all nine — is self-describing at every call site and was genuinely
defensible. It loses on `TexturedPrimitiveRun`: that type is already in the module with no prefix,
`Draw*` absorbs it as `TexturedDrawRun`, and a blanket suffix turns it into
`TexturedPrimitiveRun2D`, which is worse than what is there today.

Either rule beat what the requested list has: five `Draw*` beside four `*2D` inside one module, with
no rule a reader can infer.

**New evidence for (a):** `TexturedPrimitiveRun` already lives in `UiStagedRun.kt` with no prefix and
no suffix. `Draw*` absorbs it cleanly — `DrawRun`, `StagedDrawRun`, `TexturedDrawRun` read as one
family. A blanket `2D` suffix turns it into `TexturedPrimitiveRun2D`, which is worse than the status
quo. The module is not a clean slate; it is already mixed, and (a) is the rule that finishes it.

### `UiVertexLayout` — neutralize the name, move it to `core:geometry`

It is not a type and not a layout. It is an `object` holding named `VertexFormat` constants
(`Quad`, `Glyph`, `RoundedQuad`, …), so `VertexLayout2D` misnames it twice.

**It has no 2D dependency.** Every type it uses — `VertexFormat`, `VertexAttribute`,
`VertexSemantic`, `GpuDataShape` — lives in `core:geometry`, which is dimension-neutral. The file
imports `core.math2d.Vec2`, and that import is **dead**: the `Vec2` occurrences are
`GpuDataShape.Vec2`, a different type. Drop the import and nothing 2D remains.

**Move it to `core:geometry`.** No new module dependency — `core:geometry` already depends only on
`core:math`, and after the dead import goes the file needs nothing more. It sits beside the
vocabulary its constants are built from.

`engine/render/passes2d` would also have worked, since both backends already
`api(project(":awake:engine:render:passes2d"))`. `core:geometry` is better: it is lower in the graph,
shared with 3D, and owns the types rather than merely consuming them.

**Name: `VertexFormats2D`.** Plural, because it holds several. `2D` rather than `Draw*` because this
is the one place the suffix genuinely earns itself — in `core:geometry` the neighbours are
dimension-neutral, so the dimension *is* the disambiguator, and a future `VertexFormats3D` reads
correctly beside it. `Draw*` is the 2D *draw vocabulary*'s prefix and would be out of place here.

Consumers, measured: `backend/webgpu` (3), `backend/vulkan` (3), `engine/render/passes2d` (2),
`core/graphics2d` (2 — its own declaration and one test). Zero production consumers in its current
module.

Side effect worth having: `core:graphics2d` drops to four files, all genuinely draw vocabulary
(`DrawCommand`, `DrawPath`, `UiGradient`, `RendererVertexWriters`).

## The expected rename

All 29 requested, plus **two the list missed** (`UiFillGroup`, `TexturedPrimitiveRun`), **four dead
aliases** to delete, **eleven files** to rename and **one misnamed test class**. With the owning
module and measured reference count. Names reflect the corrections above:
`Draw*` across the 2D draw and render vocabulary, `2D` suffix only for `RenderFeature`, which really
does collide with the 3D one.

### `core:graphics2d` — 20 types across 4 files, one leaving

The requested list names 19 of these. Scanning the module turned up a 20th, two **dead aliases** to
delete, and the fact that one file declares sixteen types.

| File | Declares | Becomes | Refs |
|---|---|---|---|
| `UiDrawPrimitive.kt` | `UiDrawPrimitive` | `DrawCommand` | 978 |
| | `UiPrimitiveTransform` | `DrawTransform` | 47 |
| | `typealias DrawPrimitive` | **delete** | dead — used only in its own file |
| | `typealias PrimitiveTransform` | **delete** | dead — same |
| `UiGradient.kt` | `UiLinearGradient` | `LinearGradient` | 40 |
| | `typealias LinearGradient` | **promote, then delete the alias** | |
| | `typealias Gradient` | **delete** | 1 consumer; fold it into `LinearGradient` |
| `UiPath.kt` | `UiPath` | `DrawPath` | 80 |
| | `UiShapeSpec` | `DrawShape` | 172 |
| | `UiPoint` | `DrawPoint` | 152 |
| | `UiPathCommand` | `PathCommand` | 97 |
| | `UiFillRule` | `FillRule` | 60 |
| | `UiStroke` | `DrawStroke` | 34 |
| | `UiTriangleMesh` | `TriangleMesh` | 36 |
| | `UiColoredVertex` | `ColoredVertex` | 33 |
| | `UiColoredTriangleMesh` | `ColoredTriangleMesh` | 29 |
| | `UiTexturedVertex` | `TexturedVertex` | 21 |
| | `UiTexturedTriangleMesh` | `TexturedTriangleMesh` | 20 |
| | `UiStrokeCap` | `StrokeCap` | 15 |
| | `UiStrokeJoin` | `StrokeJoin` | 14 |
| | `UiPathContour` | `PathContour` | 10 |
| | `UiPathBuilder` | `PathBuilder` | 6 |
| | `UiFillGroup` | `FillGroup` | **missing from the requested list** — `private`, 1 file, zero risk |
| `UiVertexLayout.kt` | `UiVertexLayout` | `VertexFormats2D`, **moves to `core:geometry`** | 95 |

**Four files need renaming:** `DrawCommand.kt`, `Gradient.kt`, `DrawPath.kt`, and `UiVertexLayout.kt`
disappears from the module entirely.

**`DrawCommand` over the dead `DrawPrimitive` alias — decided, on what the type contains.**
`UiDrawPrimitive.kt` already declares `typealias DrawPrimitive`, so a neutral name was reached for
once before. It loses:

- **Three of eleven variants are not primitives.** `ClipPathPush`, `ClipPush` and `ClipPop` are state
  changes on a clip stack. A primitive is a drawable geometric element — the OpenGL/Vulkan sense of
  points, lines and triangles. `ClipPop` is not one, and calling it one teaches a reader to go
  looking for it in a vertex buffer.
- **The consumer is a command buffer.** 340 `commandBuffer` references across the backends, and clip
  push/pop lands on `vkCmdSetScissor`. That is a command by the domain's own word.
- **The siblings already chose.** `DrawRun`, `DrawRunRecorder`, `DrawRunCoalescer`. "Record a run of
  commands" is coherent; "record a run of primitives" is not.

Delete both dead aliases with the rename.

**`UiPath.kt` declares sixteen types in 69 KB.** Renaming it to `DrawPath.kt` leaves a file whose
name describes one of the sixteen. Splitting it is the right fix and is deliberately **not** part of
this pass — see [Open](#open). Renaming first is still correct: a split is easier to review when the
names in it are already final.

### `engine/render/passes2d` — 9 types across 7 files, plus a test

The requested list names 9 types. The module has more to do than that: **files carry the prefix
too**, two files declare more than one type, one type is *already* unprefixed, and a test class is
misnamed. Full map, so nothing is missed:

| File | Declares | Becomes | Refs |
|---|---|---|---|
| `UiStagedRun.kt` | `UiStagedRun` | `StagedDrawRun` | 63 |
| | `UiRun` | `DrawRun` | 75 |
| | `TexturedPrimitiveRun` | `TexturedDrawRun` | — **already unprefixed**; see below |
| `UiRenderFeature.kt` | `UiRenderFeature` | `RenderFeature2D` | 36 |
| | `UiPass` | `DrawPass` | 13 |
| `UiPipelineKind.kt` | `UiPipelineKind` | `DrawPipelineKind` | 55 |
| `UiRunRecorder.kt` | `UiRunRecorder` | `DrawRunRecorder` | 22 |
| `UiRunCoalescer.kt` | `UiRunCoalescer` | `DrawRunCoalescer` | 19 |
| `UiMeshUploader.kt` | `UiMeshUploader` | `DrawMeshUploader` | 11 |
| `SharedUiRenderFeature.kt` | `SharedUiRenderFeature` | `SharedRenderFeature2D` | 9 |
| `UiRunCoalescerTest.kt` | `UiBatchCoalescerTest` | `DrawRunCoalescerTest` | — see below |

**Seven files need renaming**, not just the types. `StagedDrawRun.kt`, `RenderFeature2D.kt`,
`DrawPipelineKind.kt`, `DrawRunRecorder.kt`, `DrawRunCoalescer.kt`, `DrawMeshUploader.kt`,
`SharedRenderFeature2D.kt`. Detekt's `MatchingDeclarationName` is satisfied in both multi-type files
because one declaration still matches the filename.

**`TexturedPrimitiveRun` settles the naming argument.** It sits in `UiStagedRun.kt` beside `UiRun`
and `UiStagedRun` with **no prefix at all**. The module is *already* internally inconsistent — this
is not a convention being broken, it is one being finished. Under the `Draw*` family it becomes
`TexturedDrawRun` and the three run types read as a set: `DrawRun`, `StagedDrawRun`,
`TexturedDrawRun`. Under a blanket `2D` suffix it becomes `TexturedPrimitiveRun2D`, which is worse
than what is there now.

**`UiBatchCoalescerTest` is doubly wrong** and predates the rename: it lives in
`UiRunCoalescerTest.kt`, so the class does not match its file, and it says "Batch" while testing
`UiRunCoalescer`. Rename the class to `DrawRunCoalescerTest` and the file with it. Not scope creep —
a rename pass that leaves a misnamed test behind has not finished the module.

### `core:math2d` — 1 type, and it is the one to change

| Old | Requested | Refs | Verdict |
|---|---|---|---|
| `UiDensity` | ~~`Density`~~ | 105 | **Not a rename, and not in this plan.** It is deleted by [retire-global-density-plan](2026-08-22-retire-global-density-plan.md), which lands first. Nothing here needs to name it |

### Phase split

Every type above is declared in `core:graphics2d`, `core:math2d` or `engine/render/passes2d` — all
three survive Stage 3, so **every declaration moves in Phase A**. The split is purely on the
*consumer* side: `ui-core`, `ui/headless`, `ui/designsystem` and `ui/animation` stay on typealiases
until they are deleted or rewritten.

## Bundle with the namespace rename, if that decision is final

`decision_maven_namespace` queues a separate move to `io.github.awakelab.*`. Both renames edit the
same lines:

```kotlin
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import io.github.awakelab.core.graphics2d.DrawCommand
```

Doing them together is one edit per import instead of two, across ~2,200 sites. If the namespace is
settled, bundle. If it is not, **run Phase A anyway** rather than wait — the prefix gap widens with
every compose commit, and Phase A's aliases make a later namespace pass no harder.

## Sequencing and safety

**Requires a quiet tree.** This is a ~200-file mechanical pass. Two commits earlier in this
project's history swallowed a parallel worker's files because a bulk edit raced hand edits in the
same working tree (`feedback_concurrent_agent_git_race`). Do not start while the compose engine is
being worked on in the same checkout. Either pause that work or run this in a git worktree.

**Verification is compile-and-test, not review.** A rename either compiles or does not; the risk is
not correctness but scope creep. Rules:

1. One type per commit for the four largest (`UiDrawPrimitive`, `UiShapeSpec`, `UiPoint`,
   `UiPathCommand`); the rest may batch.
2. No behaviour change in a rename commit. If a rename reveals a bug, land the rename and file the
   bug separately.
3. `./gradlew detekt` plus the full five-target test sweep after each commit — note that
   `:awake:ui:testing:detekt` is **already red at HEAD** for unrelated reasons, so compare against
   that baseline rather than expecting green.
4. Stage by explicit path. Never `git add` a directory.

**Do not run `spotlessApply` on a module as part of this.** `awake/ui/testing` has never been
formatted; one `spotlessApply` there rewrites ~15 unrelated files and buries the rename in noise.

## Retiring `UiDensity` — its own task

Moved out to
[2026-08-22-retire-global-density-plan](2026-08-22-retire-global-density-plan.md), because it passes
the standalone test: it removes a real hazard — two `Dp.toPx()` on one receiver, one of them silently
global — and is worth doing whether or not this rename ever happens. It is also behavioural where
this pass is mechanical, so it wants its own verification story.

**It must land first.** Four files, two of them rewires. Doing it before the rename means this plan
never has to decide what `UiDensity` should be called: the type is gone.

## Open

One, and it does not block Phase A.

**Whether the vertex and mesh types leave `core:graphics2d`.** `UiColoredVertex`,
`UiTexturedVertex`, `UiColoredTriangleMesh` and `UiTexturedTriangleMesh` are GPU-feed types, and they
are declared **inside `UiPath.kt`** — a 1,651-line file whose name says nothing about them.
`VertexFormats2D` leaves for `core:geometry` on exactly the argument that would move these too.

Still not decided, but no longer blocked on archaeology:
[splitting `UiPath.kt`](2026-08-22-split-uipath-plan.md) puts them in a `TriangleMesh.kt` of their
own, which turns this from an extraction into a one-line decision. Do the split first, then decide.

**The variants stay nested**, and keep their names: `DrawCommand.Quad`, `DrawCommand.Glyph`. The
hierarchy is sealed and closed, the qualifier says which family a variant belongs to at the use site,
and a caller wanting brevity can import the variant. Flattening eleven types to top level in a module
that already has a 69 KB file buys nothing.

**But the rename makes a missing level obvious.** Three of the eleven are state changes, and nothing
in the type says so — the cross-engine differ had to hand-roll the distinction:

```kotlin
private fun List<UiDrawPrimitive>.paintOnly() = filter {
    it !is ClipPush && it !is ClipPop && it !is ClipPathPush
}
```

Encoding it would give a backend that only rasterises an exhaustive `when` over eight variants
instead of eleven, and turn that filter into `filterIsInstance<DrawCommand.Paint>()`:

```kotlin
sealed interface DrawCommand {
    sealed interface Paint : DrawCommand   // Quad, RoundedQuad, Glyph, FilledPath, …
    sealed interface State : DrawCommand   // ClipPush, ClipPop, ClipPathPush
}
```

Not part of this pass — it changes the type's shape and wants its own review. Recorded because the
rename is what surfaces it, and `paintOnly()` is the evidence that the distinction is already needed.
