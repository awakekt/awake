# 2026-08-22: retiring the global `UiDensity`

Status: **planned, not started.** Independent of the
[`Ui`-prefix rename](2026-08-22-ui-prefix-rename-plan.md), which is where it was found — but it is
worth doing on its own merits and should land **before** that pass.

## The hazard

`compose.ui.unit.Dp` is a **typealias** for `core.math2d.Dp`. There is exactly one `Dp` in the tree,
which is deliberate. It means both of these apply to the same receiver:

```kotlin
// core.math2d — top-level extension, reads a process-wide mutable singleton
fun Dp.toPx(): Float = value * UiDensity.scale

// compose.ui.unit.Density — member extension, reads the node's own density
interface Density { fun Dp.toPx(): Float = value * density }
```

Inside a `Density` scope the member wins and the answer is per-node. **Outside one, the top-level
extension silently wins and returns a process-wide value** — whatever the last platform host set. No
error, no warning, no ambiguity the compiler will report. A layout computed against the wrong scale,
with nothing pointing at the cause.

`:awake:compose:*` imports the global extensions **zero** times today, so this has not been hit.
Nothing prevents it, and the first person to hit it will not find it quickly.

## Why it exists at all

A global was the only option `ui-core` had. It measures by re-executing content lambdas and has no
scope to read a density from, so a process-wide value is the only way to convert `Dp` to pixels
anywhere in the tree.

That constraint is gone. `compose.ui.unit.Density` is per-node *precisely* so a subtree can be
measured in isolation — which is also why a measure policy never reads a `CompositionLocal`. And
`ComposeHost(density, fontScale)` already takes the value per host.

**Nothing in the surviving graph needs a process-wide display scale.**

## Blockers, measured

| # | Blocker | Files | Kind | When |
|---|---|---|---|---|
| 1 | `core/graphics2d/UiPath.kt` reads `Dp.toPx()` to resolve `DrawShape` corner radii | 1 | **Rewire** | Now |
| 2 | `WebGpuCanvasHost`, `VulkanMetalView`, `VulkanDesktopHost` write `UiDensity.scale` | 3 | **Rewire** | Now |
| 3 | `ui/testing`'s `UiComponentFrame` saves and restores it around a render | 1 | Deletion | With `ui-core` |
| 4 | `ui-core`, `ui/headless`, `ui/designsystem`, `ui/animation` read it throughout | 80 | Free | Stage 3 deletes them |

94 files import the global extensions. **80 of them (85%) are in modules Stage 3 deletes or
rewrites**, so the reads go with the modules and cost nothing.

## The work

### Phase 1 — rewire `UiPath` (1 file, behavioural)

`UiPath.kt` resolves `DrawShape` corner radii through `Dp.toPx()`. A geometry type reading a display
global is the smell that makes everything else hard.

Thread density in as a parameter — the shape-resolving function already takes `bounds`, so this is
one more argument, not a new concept.

**Risk is real.** Corner radii resolve against whatever a caller passes. A caller passing the wrong
value produces subtly wrong shapes and no error. Every call site must be checked, not assumed.

**Verification:** shape geometry tests at more than one density. If none exist at 2× today, write
them *before* the change so they pin current behaviour rather than the new behaviour.

### Phase 2 — hosts stop writing a global (3 files, behavioural)

`WebGpuCanvasHost` (wasm), `VulkanMetalView` (iOS) and `VulkanDesktopHost` (desktop) all set
`UiDensity.scale` from the real display. They are **producers only** — nothing in `backend/` reads it
back, verified.

They pass the value to `ComposeHost(density, fontScale)` instead, which they must do for the compose
path regardless.

**Risk:** a host that forgets leaves the UI at scale 1 on a HiDPI display. Visible, but only on
hardware that has one — so it needs checking per platform rather than on desktop alone.

Keep writing the global too during this phase. `ui-core` still reads it and still ships.

### Phase 3 — move what is left (structural)

With no reader outside `ui-core` and its dependents, `UiDensity`, `Dp.toPx()`, `Sp.toPx()` and
`Float.px` move out of `core:math2d` into `ui-core`.

`core:math2d` then keeps `Dp` and `Sp` as pure value classes with **no display knowledge**. A unit
type should not depend on a screen.

The `Dp.toPx()` ambiguity stops existing at this point: there is one `toPx()` in scope for compose
code, and it is the per-node one.

### Phase 4 — delete (free)

`ui-core` is deleted at the end of Stage 3. The global goes with it, along with
`UiComponentFrame`'s save/restore, which only exists because the render it wraps reads a global.

## What this unblocks

- The `Dp.toPx()` ambiguity, which is the actual hazard.
- The `UiDensity` → `Density` name collision stops existing without renaming anything — see the
  rename plan's decision 3.
- `core:math2d` becomes a pure unit module, which is what its name claims.

## Not in scope

- Renaming `UiDensity`. It is being deleted; naming a corpse is wasted work.
- Per-node density in `ui-core`. That engine cannot have it — the absence is why the global exists.
- `LocalDensity` behaviour in compose. Already correct; this changes nothing there.
