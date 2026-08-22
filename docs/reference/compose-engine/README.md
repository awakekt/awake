# `awake:compose` — a retained UI engine for a game loop

**Not a Compose port.** A retained, single-pass UI engine with a Compose-shaped API, built for a
surface that redraws every frame behind a live 3D scene. The API is deliberately Compose's, because
familiarity is worth more than novelty; the engine underneath is smaller, differently tuned, and
answers to different constraints.

What that means concretely, and what it costs, is in [`15-compose-parity.md`](15-compose-parity.md) —
capability by capability, checked against the Compose 1.11.1 jars rather than recalled.

## What it is for

A UI layer inside a frame loop. Nothing in Compose's world redraws behind a live 3D scene, and that
single fact drives most of the differences:

- **A 60 fps loop rebuilds every frame anyway**, so automatic skipping buys much less than it does
  in an app that idles at 0 fps between taps. That is why there is no compiler plugin.
- **The frame is a hard 16.67 ms budget shared with rendering and physics**, so allocation is
  ratcheted in CI rather than left to a profiler someone runs later.
- **Input is shared with gameplay**, so the frame reports what the UI claimed — `isCaptured`,
  `isTextInputFocused`, `isScrollConsumed` — instead of assuming it owns the device.

## What it deliberately is not

Absent, not planned-and-inert. Each of these is a consequence of the choices above, not a gap
waiting to close:

| Not here | Why | Cost |
|---|---|---|
| Compiler plugin, automatic skipping | Kotlin context parameters give the calling convention with no version-locked artifact for consumers | The whole tree recomposes every frame |
| Snapshot state, read tracking | Nothing to invalidate when every frame rebuilds | No `mutableStateOf` |
| Subcomposition | Measure-time composition is a large machine | `LazyColumn` picks its window from the previous pass, so it settles a frame late |
| `LaunchedEffect` / `DisposableEffect` | Frame-driven, no coroutines — async belongs in the app layer | `remember { }` plus the caller's own scope |
| Real layers (`graphicsLayer`) | Needs render-to-texture, which `awake/render` does not have | `alpha` double-darkens overlapping children |

**There is no benchmark against Compose.** Every measured number here is against `ui-core`, the
engine this replaces. Any claim that this is *faster than Compose* would be unfalsifiable, so none
is made.

## How it is kept honest

The discipline is the differentiator, not the API. Four real defects were caught by these and not by
tests:

| Mechanism | Caught |
|---|---|
| **Cross-engine differ** — one scene through both engines, primitive for primitive | The 8 dp default gap, unprompted, on its first run |
| **Allocation ratchet** in the suite, currently 33,289 B/frame | A `Color` allocated per quad, +2.5 kB/frame, invisible in the frame total |
| **`LayoutStats.intrinsicQueries`** | Compose never says when you paid for an extra tree walk |
| **Absent beats inert** | `FocusDirection` ships 2 of 8 directions; the other 6 do not exist rather than silently doing nothing |
| **Every divergence classified with evidence**, or the diff fails | "The new engine draws something else" is the finding, not a nuisance |

Two the tests missed entirely: a click spanning two frames never fired while `clickable` sat at 100%
line coverage, and `Text` measured correctly while painting nothing at all.

## Status — Stage 1, near complete

Green on all five targets: desktop JVM, Android host, wasmJs under headless Chrome, iOS simulator,
iosArm64 compiles. **1,197 test runs**, 0 failures. Coverage (Kover, JVM-executed tests only, with
compiler-generated `$DefaultImpls` excluded): **98.3%**. Allocation: **33,289 B/frame**, stable to
the byte, against a 35,000 ratchet.

| Page | Module | State |
|---|---|---|
| `01-layout` | `:ui` | **Done.** `Constraints` (packed, zero-alloc), measure contract, `LayoutNode`, `Alignment`, Row/Column/Box, intrinsics both directions |
| `02-modifier` | `:ui` | **Done.** Chain, every `*ModifierNode` kind, chain-position-aware draw and hit-testing. `ModifierChainDiagnostics` rescoped — see the page |
| `03-composition-locals` | `:runtime`+`:ui` | **Done.** Plus `remember` with per-node slots |
| `04-styling-theme` | `:foundation` | **Done.** `background`/`border`/`clip`/`alpha`, `hoverable`/`focusable`/`clickable`, `InteractionSource`, and `Style`/`StyleState`/`styleable` with the six state rules |
| `05-animation` | `:ui`+`:foundation` | **Done for Stage 1.** `FrameClock` (clamped to a 10 fps floor), `rememberLoopingPhase`, `animateFloat`. No subscription and no `invalidateDraw` -- see the page for why the sketch's node-field phase cannot work |
| `06-focus-text-input` | `:ui`+`:foundation` | **Done for Stage 1.** `FocusOwner`, tab ring, modal trapping, `BasicTextField`, caret, `EditCommand`. No selection, no IME |
| `07-overlay-layering` | `:ui` | **Done for Stage 1.** Layer slots, paint and hit order, modal focus trapping, and modal *input* capture -- a modal blocks its own subtree only, so a toast declared outside one is blocked too |
| `08-lazy-lists` | `:foundation` | **Partial.** `verticalScroll`, `LazyColumn` with per-item measurement. Window settles a frame late |
| `09-testing-harness` | `:ui:testing` | **Done for Stage 1.** Cross-engine differ, capped at five scenes — see the gate below |
| `16-migration-deltas` | — | **Done.** The five visible changes Stage 3 will carry to every screen |
| `10-graphics-layer` | `:ui` | **Partial.** `alpha` composes; real layers need render-to-texture |
| `11-refinements` | — | Register written; the chain diagnostic was rescoped once ordering became meaningful |
| `12-gestures` | `:ui` | **Partial.** Three passes, capture, hover, click, drag, wheel. No long-press or multi-touch |
| `13-semantics` | `:ui` | **Done.** Typed keys, `mergeDescendants`, tree-derived order |
| `14-density-resize` | `:ui` | **Partial.** `density`/`fontScale`, viewport resize re-measures. RTL undecided |
| reconciler | `:runtime` | **Done.** Positional identity, `key(value){}`, layer slots, `Applier` seam |
| frame loop | `:ui` | **Done.** `ComposeHost.frame`, pointer edge detection, keyboard routing |

### The Stage 1 gate, narrowed

The original gate was the Checkout Form rendering on **both** engines and diffing
primitive-for-primitive. That is not fully achievable, and the reason is a property of `ui-core`
rather than a gap here: `hasResolvedVisuals()` resolves a style before claiming a slot, so it guesses
not-hovered. A button's hover and press appearance has no `ui-core` reference to diff against.

So the differ stops at five scenes — a box, a stack, a nested row, a styled panel, and a checkout
line with text and a weight. Across them it found five divergences, **every one of them `ui-core`
being the outlier**, all classified and listed in `16-migration-deltas.md`. It was not finding bugs
in this engine; it was cataloguing the old engine's accidental defaults.

The gate is now: **the Checkout Form renders on this engine, and pixel snapshots carry the fidelity
check.** Snapshots survive `ui-core`'s deletion; the differ does not. The differ stays as it is for
Stage 3's migration, where a field-level diagnosis on a screen that moved unexpectedly is worth more
than a pixel diff that only says "different".

Next: Stage 3.

## Why not `ui-core`

`ui-core` measures a container by **re-executing its own content lambda** as a throwaway trial pass.
On `samples:ui-showcase`'s Checkout Form that is 7,696 trial passes and ~44.7 ms per frame, and it
multiplies with nesting (`TrialMeasureScalingTest`: 3^depth → 2^depth). Every mitigation so far is a
constant factor inside the same model — see `skills/awake-ui-performance/SKILL.md`, which says so
outright, and `docs/tasks/archive/2026-08-02-trial-measure-double-execution.md`, which named the fix
and deferred it.

Compose's `Constraints` in → `Placeable` out is single-pass by construction. That property is the
whole reason for this engine.

## The frame

```kotlin
val host = ComposeHost()

fun frame(input: FrameInput): FrameOutput = host.frame(input) {
    CheckoutSummary(state)   // runs EXACTLY ONCE
}
```

Inside, in this order — and every way of getting it wrong is silent:

```
dispatchInput   // hit-test LAST frame's placed tree, so there is no one-frame lag
reconcile       // the content lambda runs once
measure/place   // constraints down, sizes up
paint           // -> FrameOutput
```

`primitives` is the same `UiDrawPrimitive` list `ui-core` emits, so a render backend consumes either
engine unchanged — and one scene runs through both and is diffed field by field.

## Calling convention — verified, not assumed

A composable is a function with a `Composer` **context parameter**. No compiler plugin.

```kotlin
context(_: Composer)
fun Column(modifier: Modifier = Modifier, content: () -> Unit)
```

Measured against Kotlin 2.4.10 on 2026-08-21, all five targets
(`ContextParameterCallingConventionTest`):

| Question | Answer |
|---|---|
| Does `-Xcontext-parameters` need enabling? | **No.** The compiler reports the flag as *"redundant for the current language version 2.4"*. No experimental opt-in for a published library. |
| Does a plain `() -> Unit` content lambda see the enclosing context? | **Yes** — which is what makes `Column { Text("hi") }` nest. |
| Is calling one outside a composition an error? | **Compile error**: `no context argument for 'composer: Composer' found`. |
| Does it work off the JVM? | **Yes** — verified running on desktop JVM, android host, wasmJs under real headless Chrome, and iOS Native simulator; iosArm64 compiles. |

That last row is why the test runs on all five targets rather than one: wasm and Native lower IR
differently, and a regression should name itself instead of surfacing as an unexplained build
failure.

**Do not add a decorative `@Composable` annotation.** Without a plugin it enforces nothing, and an
annotation that looks like Compose's while checking nothing is the *silent when unarmed* failure this
repo keeps hitting. The context parameter enforces for real.

The one thing a compiler plugin would still buy is automatic skipping (`$changed` masks). That is a
Stage 2 decision, gated on a measurement Stage 1 produces — see the stage map below.

## Effects and coroutines — a non-goal for the *engine*, not the app

There is no `LaunchedEffect` or `DisposableEffect` equivalent, and the engine has no coroutines.

This is not a claim that the codebase avoids coroutines. **Network calls, asset loads and anything
else asynchronous belong in the app layer** — the MVI store, an ECS system, a repository — which
already uses coroutines and already runs off the frame thread. The result lands in state, and the
next frame reads it. The engine never awaits anything.

Compose needs `LaunchedEffect` because its recomposition is event-driven and a Compose app idles at
0 fps between taps: something has to wake it. This engine reconciles every frame behind a live 3D
scene, so "start work when this enters composition, cancel when it leaves" is `remember { }` plus
the caller's own scope, and disposal is the node's `onAttach`/`onDetach`.

**Revisit trigger:** if Stage 2 lands subtree-level recomposition, the engine stops running every
frame for every subtree, and an effect API becomes worth its cost again. Re-open the decision then,
not per PR.

## Where this is stricter than Compose

Not "better" — stricter, on specific axes, each because this repo or `ui-core` shipped the bug it
prevents. `15-compose-parity.md` is the full capability-by-capability answer, including everywhere
this engine has *less*; `11-refinements.md` is the review gate that keeps the list evidenced.

### Built

| Upgrade | Compose today | Evidence |
|---|---|---|
| **`Constraints.offset` saturates** | `Infinity == Int.MAX_VALUE`, so `maxWidth - padding` silently wraps and an unbounded axis quietly becomes a huge finite one | `offsetLeavesAnUnboundedAxisUnbounded`, `paddingKeepsAnUnboundedAxisUnbounded`. `ui-core` hit the same class with its own `UNBOUNDED_MAIN_AXIS` sentinel |
| **`Infinity` is a reserved bit pattern, not a magic max** | `Int.MAX_VALUE` doubles as a real value and a sentinel | `theLargestRealDimensionIsNotMistakenForInfinity` pins the boundary where a real size and the sentinel would collide |
| **`@Composable` with no compiler plugin** | Requires a Kotlin-version-locked plugin, imposed on every consumer of a published artifact | Kotlin context parameters. Verified on 2.4.10 across all five targets: no flag, nesting works, outside-composition is a compile error |
| **Read-only `children`** | `LayoutNode` children are mutable to their holder | A caller could restructure the tree between measure and place, leaving placement running against sizes that no longer exist. Surfaced by `kmp-audit` |
| **`LayoutStats.intrinsicQueries`** | Compose never says when an intrinsic cost you an extra subtree walk | `awake-ui-performance` Rule 4: a path that is silent when unarmed is the one that ships wrong. Off by default, zero cost disarmed |
| **Allocation ratcheted in the suite** | No equivalent gate | 33,289 B/frame, stable to the byte. It caught a `Color` allocated per quad that the frame total read as noise |
| **Allocation-free measure** | `layout()` allocates a `MeasureResult` per call | Each node and chain link owns a reused result and scope. Driven by the 16 KB/frame ship gate — `ConstraintsAllocationProbe` measures the `Constraints` half at 0 B against Float's 32 B/op. Tradeoff: the result object is mutable, so it must not be retained past a pass |

### Designed, not yet built

| Upgrade | Why |
|---|---|
| `ModifierChainDiagnostics` | **Rescoped, not built.** It was specced to flag clickable-after-padding and background-after-padding as mistakes; both are correct orderings now, so it would be a false alarm. What it should flag is undecided |
| Missing-`key()` detection | Reordering a list without keys silently loses state, with *"no exception, no warning"* — `mirror-map.md` documents the identical class for `animateFloat` ids |
| Node lifecycle `onAttach`/`onDetach` | Compose bolts disposal on via `DisposableEffect`; `mirror-map.md` lists the gap as `Diverges` |
| Layout errors naming the node path | House style already — see `requireScrollableContainer`'s message |

Not upgrades, recorded so they are not claimed as such: weight-remainder spreading, sharing one
Row/Column implementation, and packed `Int` constraints are all what Compose already does. Reverting
`ui-core`'s accidental defaults (8 dp arrangement gap, `Box` filling) is a return *to* Compose, not
past it.

## Modules

```
:awake:compose:runtime      reconciler, node identity, remember
:awake:compose:ui           LayoutNode, Constraints, Modifier, draw/pointer/semantics phases
:awake:compose:foundation   Row/Column/Box/Text/Canvas/scroll/lazy, background/border, padding/size
```

Packages mirror Compose's own, because an import line is part of the API shape:

```
compose.ui                    Modifier, Alignment
compose.ui.layout             Measurable, Placeable, MeasurePolicy, Layout, onPlaced
compose.ui.node               LayoutNode, every *ModifierNode interface
compose.ui.draw               drawBehind, clip
compose.ui.graphics(.drawscope)  Painter, DrawScope
compose.ui.input.pointer      PointerEvent, PointerInputDispatcher
compose.ui.semantics          SemanticsProperties, SemanticsTree
compose.ui.platform           LocalDensity, LocalTextStyle
compose.ui.unit               Constraints, Density, Dp, Sp
compose.foundation            background, border, Canvas, clickable
compose.foundation.layout     Row, Column, Box, Spacer, Arrangement, padding, size, IntrinsicSize
compose.foundation.gestures   draggable
compose.foundation.text       Text
```

`:ui`'s tests take a **test-only** dependency on `:foundation`: the chain, paint and pointer tests
need something concrete to build a tree from, and moving them would hand this module's coverage to
another one. Same shape `:awake:ui:graphics` already uses with `ui-core`.

Top-level peer of `:awake:ui`, not nested under it: `:runtime` is not UI-specific (a composer and
reconciler diff any tree, as `androidx.compose.runtime` is UI-agnostic), and this engine *replaces*
`ui-core` rather than extending it.

`:ui` depends on `:runtime` plus `:awake:ui:graphics` and `:awake:ui:text` — primitive layers that
carry no `ui-core` dependency in `commonMain`. **Nothing depends on `:awake:ui:ui-core`, in either
direction.**

`Dp` and `Sp` are reused from `:awake:ui:graphics`'s `ui.api` package rather than redefined here —
one `Dp` in the tree, not two. They belong in a dedicated `unit` package, as Compose has it; that
move is deferred to the planned namespace rename because 242 files import them today. See
`14-density-resize.md`.

### Planned: split `:awake:ui:graphics`, then move only its UI half

`:awake:ui:graphics` is two modules wearing one name, and the split runs along a single rule:
**if a render backend needs the type, it belongs in `core`.**

`UiDrawPrimitive` is the contract between "produces 2D draw commands" and "rasterizes them". Of its
16 consumers, 8 are on the render side — `backend/vulkan`, `backend/webgpu`,
`engine/render/contract`, `engine/render/passes`, `engine/bootstrap`, `scene/rendering`,
`scene/authoring`. Neither UI engine owns it, so it cannot move under `:awake:compose`: that would
make Vulkan depend on a UI engine.

| | Goes to | Why |
|---|---|---|
| `UiDrawPrimitive`, `UiPath`, `UiGradient`, `Rectangle`, `UiPrimitiveTransform` | `:awake:core:graphics` | The render contract. Backends consume them; `UiPath`/`UiGradient`/`Rectangle` are referenced by `FilledPath`/`GradientQuad`/`ClipPush` |
| `Dp`, `Sp`, `UiDensity`, `UiImageVector`, `UiIcon`, `UiEasing`, `PopupContracts` | `:awake:compose:*` | Authoring surface. No backend needs them |
| `:awake:ui:text` | `:awake:compose:text` | Font and glyph metrics are consumed by the UI engine only |

`Color` is already correct at `:awake:core:color` — 7 non-UI modules use it (backends, render,
scene), so it was never a UI type. Keep it a sibling of `:awake:core:graphics` rather than folding
it in: scene and material colours are not 2D-draw concepts.

End state:

```
:awake:core:color
:awake:core:graphics        <- the render-contract half of :awake:ui:graphics
:awake:compose:runtime
:awake:compose:text         <- :awake:ui:text
:awake:compose:ui           <- absorbs the authoring half
:awake:compose:foundation
```

**Ordering.** The `:awake:core:graphics` extraction is independent of this engine and can land
whenever — it only makes an existing contract's home honest, and every consumer above it keeps
working.

The authoring half must wait. Six modules still depend on `:awake:ui:graphics` today:
`:awake:ui:ui-core`, `:awake:ui:headless`, `:awake:ui:heroicons`, `:awake:ui:tailwind`,
`:awake:ui:text`, and `:awake:engine:render:passes`. Moving it early would make `ui-core` depend on
the `:awake:compose` tree while it is still the shipping engine, which inverts the direction this
split exists to keep clean.

The move belongs in **Stage 3**, after `ui-headless` and `ui-designsystem` are ported and `ui-core`
is deleted, bundled with the `io.github.awakelab.*` namespace rename so the ~242 `Dp`/`Sp` imports
and these package moves are one pass rather than three.

Nothing is blocked in the meantime: `:awake:compose:ui` already declares
`api(project(":awake:ui:graphics"))`, so `UiDrawPrimitive` and friends are reachable now.

## Pages

| | |
|---|---|
| `01-layout.md` | `Constraints`, `Measurable`/`Placeable`/`MeasurePolicy`, `LayoutNode`, intrinsics, Row/Column/Box policies, the Int-vs-Float pixel decision |
| `02-modifier.md` | `Modifier` chain, `LayoutModifierNode`/`DrawModifierNode`/`PointerInputNode`, ordering, port map from `UiModifier` |
| `03-composition-locals.md` | `CompositionLocal` on a retained tree; migration of the 7 existing `UiLocal`s |
| `04-styling-theme.md` | Why `Style`/`styleable` stays in `ui-designsystem`; the primitive draw modifiers `:ui` ships instead |
| `05-animation.md` | Frame-clock animation; why every `isMeasuringInternal()` guard becomes unnecessary |
| `06-focus-text-input.md` | Focus tree, caret/selection, IME routing |
| `07-overlay-layering.md` | Popup/dialog/tooltip/toast as tree layers; z-order, occlusion, modal capture |
| `08-lazy-lists.md` | Virtualization with real per-item measurement |
| `09-testing-harness.md` | `ui-testing` adapter, cross-engine primitive differ, probe ports |
| `10-graphics-layer.md` | What the tree fixes for free vs what needs render-to-texture |
| `11-refinements.md` | The refinement register — every deliberate divergence from Compose, with its evidence |
| `12-gestures.md` | Drag, wheel, hover, long-press, capture |
| `13-semantics.md` | Semantics tree and traversal order |
| `14-density-resize.md` | Density, DPI change, resize invalidation, RTL decision |
| `15-compose-parity.md` | Verified parity matrix against Compose 1.11.1 -- what is mimicked, what is left out, what is genuinely better, and what was never checked |

## Stages

- **Stage 0** — doc set, module wiring, calling-convention spike. **Done.**
- **Stage 1** — the engine. **Near complete**; see the status table. The exit gate is the Checkout
  Form rendering on this engine with pixel snapshots as the fidelity check — narrowed from
  "diffing clean on both engines", for the reason given above the table.
- **Stage 2** — skipping (composer invalidation, read-tracking state), **gated on a measurement that
  has not been taken**: F2 attribution on a real scene once trial cost is structurally zero. If UI
  build is not a material share of the 16.67 ms budget, skipping buys nothing and the
  Kotlin-version-locked plugin it needs is a liability bought for free.
- **Stage 3** — `ui-designsystem` depends on `:foundation` instead of `ui-headless`; `ui-core` and
  `ui-headless` are deleted together. **No shadcn code moves into `:awake:compose:*`** — the recipes
  stay in `ui-designsystem` and change only what they import; `:foundation` stays unstyled and knows
  nothing about tokens or a theme. `ui-headless` is not ported: measured, its controls hold no
  state — `Checkbox` is 225 lines and 0 state markers — so it is rendering-without-theming, not
  Radix's behaviour-without-rendering, and `:foundation` already ships what it was standing in for.
  Six real behaviours move; twelve re-export shims are pure deletion. Full plan:
  [`2026-08-23-stage-3-plan.md`](../../tasks/2026-08-23-stage-3-plan.md). Read
  `16-migration-deltas.md` before touching a screen.
- **Stage 4** — prove the four numbers, then update skills and guidance. Not before.

## Provenance

**No file is copied from `androidx.compose`.** Names, signatures and documented semantics are
mirrored; implementations are written against Awake's own types. This is the house stance already
(`skills/kmp-api-mimicry`; `docs/reference/mirror-map.md` is a shape audit, not a port log).

Both projects are Apache-2.0, so derivative work is permitted **with** attribution — and this repo
has `LICENSE.md` but no `NOTICE`. If any derivative material ever lands, adding `NOTICE` is part of
that change.

Claims about what Compose does are checked against the jars in `~/.gradle/caches`, using `javap`,
which prints **declarations only** — never method bodies. Reimplementing declaring code is the
category *Google v. Oracle* (2021) settled as fair use. One claim in this doc set was written from
memory and turned out to be wrong (`Modifier.styleable` does exist, in `foundation.style`), which is
why the rule is now check-then-write.

## Glossary

| Term | Meaning |
|---|---|
| **Build / reconcile** | Running the DSL once to create or update the node tree. Replaces "the frame's UI code". |
| **Trial pass** | `ui-core`'s throwaway re-execution of a content lambda to learn a size. Does not exist here. |
| **Measure** | `Constraints` down, size up. Each node measured once. |
| **Place** | Assigning final positions, after sizes are known. |
| **Positional identity** | A node's key: `(childIndex, nodeType)`, plus an explicit `key(value){}` for lists and conditionals. |
