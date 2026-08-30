# Compose next stages plan

Status: in progress — Stage A (bridge) and Stage B (retained scope closure) completed. This plan
continues the retained Compose engine after the completed modifier-node lifecycle milestone and the
active Stage 2 retained-scope work.

## Goal

Complete the highest-value Compose gaps that unblock app-root UI, scene-aware tools, and real
consumer layout/input behavior. This is **not** a project to replicate all AndroidX Compose.

The baseline, current changes, and resume details are in
[`2026-08-27-compose-stage-2-invalidation-handoff.md`](../handoffs/2026-08-27-compose-stage-2-invalidation-handoff.md).
The capability ledger remains
[`15-compose-parity.md`](../reference/compose-engine/15-compose-parity.md).

## Scope boundary

Included:

1. One app-level Compose host correctly bridged to one scene session.
2. Evidence and closure for explicit retained `recomposeScope` consumers.
3. Layout foundations required by real text and lazy-content consumers.
4. Input/platform behavior needed by supported Awake targets.
5. Rendering/modifier additions only when a real consumer is blocked.

Excluded by design:

- Kotlin compiler-plugin automatic recomposition and changed masks.
- AndroidX snapshot isolation or concurrent recomposition.
- `LaunchedEffect`, `DisposableEffect`, and coroutine `pointerInput`.
- Android window-insets APIs and other platform-window policy.

## Stage A — app-level Compose / Scene session bridge

### Problem

`sceneComposeAppModule` can stage one app-level Compose host before scene presentation and provide
`LocalWorld`/`LocalRenderer`, but its `FrameOutput` does not currently flow back into
`SceneAppLifecycleRuntime`. Scene systems can therefore read default UI ownership while the UI has
captured a gesture. Frame stats are also unavailable to the app Compose root.

### Design

Keep ownership separate:

```text
Application
├── Compose app host      composition, UI input ownership, semantics, UI draw staging
└── Scene session/runtime World, ECS systems, rendering, assets, lifecycle and disposal
```

The bridge carries the application host's `FrameOutput` into the active scene runtime before ECS
systems consume input. It must not add another `ComposeHost`, give Compose ownership of `World`, or
permit legacy scene `content {}` alongside the app-level host.

### Work

1. Define the smallest bridge contract for ownership, cursor, keyboard focus, semantics,
   primitives, and `SceneFrameStats`.
2. Wire the bridge in lifecycle order: Compose frame → bridge → scene systems → scene render.
3. Provide `LocalWorld`, `LocalRenderer`, and `LocalFrameStats` at the app Compose root.
4. Reject mutually exclusive legacy scene content and `sceneComposeAppModule` at installation.
5. Migrate one Studio shell test fixture only after the bridge proves equivalent behavior.

### Acceptance evidence

- A multi-frame app-level UI click blocks camera/gameplay input.
- Cursor and text-focus requests reach the platform adapter.
- Studio can read its three scene locals through app-root composition.
- Semantics/primitives are inspectable through the active scene runtime.
- Desktop and wasm tests cover the same interaction sequence.

## Stage B — close retained-scope Stage 2

### Work

1. Run a controlled alternating, repeated before/after desktop profile for the scoped Studio
   scene-picker chrome.
2. Add Studio-level idle and open-menu semantic/primitive equivalence evidence.
3. Keep the scope only if it delivers a repeatable composition-cost reduction without behavioral
   regression.
4. Update the parity ledger to **Subset**: explicit retained scopes, not compiler-generated
   automatic skipping.
5. Consider fixed-arity scope overloads and feature-local wrappers only after a second real
   consumer demonstrates repetition. Do not add varargs or opaque input bundles.

### Acceptance evidence

- Runtime nested invalidation, keyed reordering, and retained interaction regressions remain green.
- Desktop and wasm Studio probes report the scoped consumer correctly.
- The controlled profile records methodology and results, not one opportunistic timing.

## Stage C — layout and virtualisation foundations

### Order

1. Add `AlignmentLine`/baseline support if a real text or row/column consumer is blocked.
2. Design `SubcomposeLayout` and `BoxWithConstraints` as one measure-time composition feature;
   do not expose previous-frame constraints as a shortcut.
3. Move lazy-list window selection into measurement only after subcomposition has an accepted
   contract; remove its current one-to-two-frame jump settlement.

### Acceptance evidence

- Baseline-aligned text is correct at density 1 and 2.
- Constraint-aware content sees current-frame constraints, including first frame and resize.
- Lazy jumps select the correct window in the same frame without per-frame allocation regression.

## Stage D — input, focus, and directionality

### Work

1. Define RTL before adding `absoluteOffset`; then test logical versus absolute placement.
2. Add spatial focus directions and focus properties/restoration from a real keyboard/gamepad
   consumer, never enum values that silently do nothing.
3. Add platform touch adaptation and a transform gesture contract before `transformable`.
4. Design nested scroll, overscroll, bring-into-view, and anchored drag only when a scroll
   consumer demonstrates the needed relationship.

### Acceptance evidence

- Desktop keyboard/gamepad and wasm/browser paths use the same focus behavior.
- Touch contacts preserve identity across frames and do not leak capture.
- RTL tests cover both density 1 and 2 and logical/absolute modifier ordering.

## Stage E — consumer-blocked rendering and modifier work

Take only the first real blocker, in this order when applicable:

1. Partial border sides for joined controls.
2. `zIndex` with an allocation-safe invalidation/sort policy.
3. Generic shadow gradient/spread, inner shadow, or remaining blend modes through the existing
   render-target compositor.
4. `drawWithCache` only with an explicit invalidation contract.

Every item needs a core-level test plus a real consumer proof. Do not compensate with a recipe
offset, margin, or custom drawing fork.

## Stop conditions

- Stop a stage when a consumer can be expressed safely with existing APIs; do not promote a
  hypothetical convenience feature.
- Stop before broad migration if profiling does not show material composition cost.
- Defer an item when its semantics require platform policy or AndroidX coroutine/snapshot runtime.

## Parity coverage and classification register

Every capability and ledger item from `15-compose-parity.md` and `17-modifier-parity.md` is strictly classified into one of three buckets:

### 1. Active Implementation Stages (A–E)

| Ledger Item | Stage | Scope & Implementation Contract |
|---|---|---|
| Scene Session & App Compose Bridge | **Stage A (Done)** | Flow `FrameOutput` to `SceneAppLifecycleRuntime`, inject `LocalFrameStats`, enforce mutual exclusivity. |
| Retained Scope Closure & Verification | **Stage B (Done)** | Controlled before/after profiling, steady-state skip verification, parity ledger updated to **Subset**. |
| `AlignmentLine` (`FirstBaseline`, `LastBaseline`) | **Stage C (Done)** | Required for text baseline alignment in `Row`/`Column`. Extends `MeasureScope.layout` without per-frame map allocation. |
| `SubcomposeLayout` / `BoxWithConstraints` | **Stage C (Done)** | Measure-time composition feature that exposes current-frame constraints (first frame and resize) without 1-frame lag. |
| `LazyColumn`/`LazyRow` Measure-Time Windowing | **Stage C (Done)** | Migrate lazy list item index resolution from pre-measure windowing to measure-time subcomposition to eliminate 1–2 frame jump settlement. |
| RTL & `Modifier.absoluteOffset` | **Stage D (Done)** | Define RTL layout direction semantics first; implement `absoluteOffset` alongside logical `offset` with density 1 and 2 tests. |
| Spatial Focus Traversal (`Left`, `Right`, `Up`, `Down`) | **Stage D (Done)** | Add 2D directional navigation in `FocusOwner` for gamepad/keyboard controllers. |
| Multi-touch Contact & Transform Contract | **Stage D (Done)** | Touch contact identity preservation across frames and transform gesture contract before `Modifier.transformable`. |
| Nested Scroll & Anchored Drag Infrastructure | **Stage D (Done)** | `nestedScroll`, `bringIntoView`, `overscroll`, and `anchoredDraggable` when hierarchical scroll relationships are designed. |
| Partial Border Sides | **Stage E (Done)** | Independent per-side border stroke support for joined controls (`ButtonGroup`, segmented tabs). |
| `Modifier.zIndex` | **Stage E (Done)** | Allocation-safe per-parent draw order and pointer hit-test priority with stable tie-breaking. |
| Generic Shape Shadow & Blend Modes | **Stage E (Done)** | Supported compositor shadow shapes and destination blend modes verified across render backends. |
| `Modifier.drawWithCache` | **Stage E (Done)** | Cache draw operations across frames with explicit size and state invalidation contract. |

### 2. Conditional / Deferred with Explicit Trigger

| Ledger Item | Status | Explicit Trigger / Condition to Unblock |
|---|---|---|
| `staticCompositionLocalOf` | **Deferred** | Only useful as a recomposition optimization when deep subtrees need to bypass invalidation; triggered only if a hot `CompositionLocal` consumer profiles significant recomposition churn. |
| 65,534 px Packed-Constraint Widen | **Deferred** | Fixed 16-bit packing per dimension is optimal for memory and cache; widen to variable bit-widths only if a viewport or infinite scrolling canvas exceeds 65,534 px. |
| `Rulers` in `MeasureScope.layout` | **Deferred** | Compose 1.7+ feature for custom layout guide references; triggered only when a multi-component layout cannot be expressed via `AlignmentLine` or layout modifiers. |
| Standalone `Modifier.rotate` | **Deferred** | Requires widening 2D vertex layout and modifying shader pipelines across Vulkan & WebGPU backends. `graphicsLayer(rotationZ = ...)` covers offscreen subtree rotation in the interim. Triggered if inline per-quad CPU rotation is proven necessary without offscreen buffer overhead. |
| `Modifier.paint` | **Deferred** | Vector/raster painter wrapper modifier; triggered when an external vector/image asset needs generic `Painter` drawing without wrapping in an explicit widget (`Image`/`Icon`). |
| `innerShadow` | **Deferred** | Dependent on inverted shape-mask rendering pipeline in compositor pass; triggered when a design system component specifically requires inset elevation. |
| `pointerHoverIcon` | **Deferred** | Blocked on platform-level cursor API integration in `:core:platform` (window mouse cursor shape customization on Desktop JVM & Web). |
| `onFocusEvent` & `focusProperties` | **Deferred** | `onFocusChanged` and `focusTarget` cover current usage; explicit focus redirect rules (`focusProperties`) triggered when complex composite widgets require custom Tab traversal interception. |
| `Modifier.scrollable` | **Deferred** | Low-level gesture-only scroll without clipping/layout; triggered only if a custom container needs raw scroll physics without standard `verticalScroll`/`horizontalScroll`. |
| Visibility / Layout Observers (`approachLayout`, `onFirstVisible`, `onVisibilityChanged`, `onLayoutRectChanged`) | **Deferred** | Triggered if analytics/virtualization require viewport intersection reporting without full `LazyList` structure. |
| Typed `StyleState` Keys | **Deferred** | Current 6 core states (`hovered`, `pressed`, `focused`, `disabled`, `selected`, `checked`) cover all shadcn state rules. Extension keys triggered only if a custom consumer introduces domain-specific pseudo-classes (e.g. `error`, `readOnly`). |
| Platform Font Loading / Fallback | **Deferred** | `UiFonts.family` combined atlas covers multi-weight font sets. Dynamic platform font loading is outside engine core; triggered if runtime custom TTF loading from disk/network is required. |
| Rescoped `ModifierChainDiagnostics` | **Deferred** | Requires formalizing the exact set of anti-patterns (e.g. redundant identity modifiers, conflicting size constraints) after chain order semantics were proved valid. |

### 3. Intentional Non-Goals & Architectural Divergences

| Capability | Verdict | Architectural Rationale & Constraint |
|---|---|---|
| Kotlin Compiler Plugin / Automatic `$changed` Masks | **Intentional Non-Goal** | Engine uses Kotlin context parameters (`context(_: Composer)`). Eliminates compiler plugin dependency and Kotlin-version lockstep. Skipping is explicitly opted into via `recomposeScope`. |
| AndroidX Snapshot Isolation / Concurrent Recomposition | **Intentional Non-Goal** | Engine runs synchronous, single-threaded per-frame UI pumps. State writes dirty scopes synchronously without multiversion concurrency control. |
| Coroutine Effects (`LaunchedEffect`, `DisposableEffect`, `pointerInput` suspend DSL) | **Intentional Non-Goal** | Engine is frame-clock driven without asynchronous coroutines inside composition. Node `onAttach`/`onDetach` handles lifecycle/disposal, and `PointerInputNode` handles multi-pass input. |
| Android Window Insets (`imePadding`, `statusBarsPadding`, `safeDrawingPadding`, etc.) | **Intentional Non-Goal** | Android OS-specific window concepts not applicable to a cross-platform game engine surface. Canvas/viewport sizing is driven cleanly by `LocalViewportSize`. |
| True Layer Semantics for Un-layered `alpha` | **Intentional Divergence** | `Modifier.alpha` is applied per-primitive at emission for zero-allocation performance. Overlapping sibling blending requires wrapping in `Modifier.graphicsLayer(alpha = ...)` which provides true offscreen layer isolation. |
| `Modifier.onGloballyPositioned` / `LayoutCoordinates` Object | **Intentional Divergence** | Avoids allocating heavy `LayoutCoordinates` hierarchies per frame. `onSizeChanged` and `onPlaced` report resolved bounds directly. |
| `Modifier.composed { }` | **Intentional Non-Goal** | Engine does not have composition-local modifier instances; chains are rebuilt/reconciled with `ModifierNodeElement` and `remember` at call sites. |
| `Modifier.indication` (Ripple abstraction) | **Intentional Non-Goal** | Replaced by `Style` state rules and `InteractionSource` sets tailored to game engine rendering and shadcn design system. |
| Vararg `remember(vararg keys)` | **Intentional Non-Goal** | Vararg parameter causes heap allocation on every frame in the hot path. Fixed-arity overloads (`remember(k1)`, `remember(k1, k2)`) are provided. |

## Documentation on completion

Update the applicable detail page, `15-compose-parity.md`, `17-modifier-parity.md` where relevant,
and the current handoff. Commit each completed stage separately from unrelated generated artifacts.
