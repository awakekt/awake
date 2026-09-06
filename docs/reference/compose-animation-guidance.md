# Jetpack Compose Guidance: Animation
## Complete reference for `awake:ui:animation` contributors and callers

> **Companion to** [`mirror-map.md`](mirror-map.md) (Faithful/Diverges status table, "Animation
> primitives" section) and [`compose-modifier-layout-guidance.md`](compose-modifier-layout-guidance.md)
> (Modifier/layout how-to). `mirror-map.md` records *what* diverges. This doc explains *how* to use
> each animation primitive safely, *why* it's shaped the way it is, and the footguns a
> Compose-literate reader will not expect. Every section is grounded in the actual source files
> under `awake/ui/animation/src/commonMain/kotlin/com/awakekt/awake/ui/` (`UiAnimation.kt`,
> `UiTransition.kt`, `UiAnimatedVisibility.kt`) and `awake/ui/graphics/src/commonMain/kotlin/com/awakekt/awake/ui/api/UiEasing.kt`,
> not memory.

A sibling doc rather than a section of `compose-modifier-layout-guidance.md` because animation
lives in its own Gradle module (`awake:ui:animation`, depending on `ui-core`) with its own
concerns -- it is not a `Modifier` field or a layout primitive. See that doc for `graphicsLayer`,
`alpha()`, and `scale()`, which animation's `withGraphicsLayerAlpha`/`withGraphicsLayerScale`
apply.

---

## Read this first: the two footguns that matter more than any one function's semantics

Both apply to *every* function on this page, not just one row in a table below.

### 1. `id` is the entire identity -- unlike Compose, there is no call-site fallback

Compose's `animateFloatAsState`/`Animatable`/`rememberInfiniteTransition` all get their identity
for free from **where the call sits in the composition's slot table**. Two different call sites
can never accidentally collide, and the same call site automatically keeps its animation across
recompositions without you naming anything.

Awake has no composition and no slot table. Every function below takes an explicit `id: String`,
and that string **is** the animation's entire identity -- looked up in `WidgetState` the same way
[`rememberStateValue`](compose-modifier-layout-guidance.md) is (see `mirror-map.md`'s new "State
hooks" section for the full identity model these two families share).

Two failure directions, both silent, neither caught at compile time:

```kotlin
// ❌ id built from a value that changes -- restarts the animation every time `page` changes,
// instead of animating smoothly from wherever it currently is
val x = context.animateFloatTween(id = "slide-$page", target = targetX)

// ✅ stable id; branch on `page` for the TARGET, not the id
val x = context.animateFloatTween(id = "slide", target = targetXFor(page))
```

```kotlin
// ❌ two unrelated widgets on the same screen both animate under the literal id "fade" --
// they silently share one WidgetState bucket; each one's target fights the other's
fun UiPrimitiveScope.cardA() { val a = animateFloat("fade", target = 1f) }
fun UiPrimitiveScope.cardB() { val a = animateFloat("fade", target = 0f) }

// ✅ derive a unique id from each widget's own id, the same convention required for any
// stateful widget (see skills/awake-ui-authoring/SKILL.md's "unique id" section)
fun UiPrimitiveScope.card(id: String, visible: Boolean) {
    val alpha = animateFloatTween("$id.fade", target = if (visible) 1f else 0f)
}
```

### 2. Trial-measurement passes: guard any *new* side-effecting `UiContext` read/write the same way

`row {}`/`column {}` with a `WrapContent` axis (or a weight-detection trial not covered by the
opt-in cache) re-executes its `content` lambda against a **trial** pass before the real one --
see `compose-modifier-layout-guidance.md`'s new "Layout DSL" section and `mirror-map.md`'s
Scope/DSL "two-pass trial-measure model" row for the full mechanism and when it fires. Every
`animateFloat*` function in this doc explicitly checks `isMeasuringInternal()` and returns the
already-stored value **without advancing state** when it's true:

```kotlin
// UiAnimation.kt -- animateFloat's actual guard
fun UiContext.animateFloat(id: String, target: Float, ...): Float {
    val state = widgetStateInternal("__animation__$id")
    val current = state.get("value", initial)
    if (isMeasuringInternal()) return current   // <-- do not step during a trial pass
    val next = animateFloatStep(current, target, frameDeltaSecondsInternal(), ...)
    state.set("value", next)
    return next
}
```

Without this guard, a `WrapContent` container re-executing its content once for trial sizing and
once for real placement would advance the same animation's elapsed time/spring state **twice per
real frame**, running it at roughly 2x speed (or worse, an unpredictable multiple, since some
paths trial-measure more than once). This already shipped once as a real bug for shimmer
(commit `85f14821`, cited directly in `UiAnimation.kt`'s own comments).

**This is not specific to the functions on this page.** `mirror-map.md` states the rule
explicitly: *"treat any new side-effecting `UiContext` state read/write as needing the same
guard, not just the three functions already covering it."* State hooks (`rememberStateValue` and
friends) already build this guard into the shared primitive, so you get it for free (see
`mirror-map.md`'s "State hooks" section) -- but a hand-rolled side effect (mutating a captured
`var`, calling an external callback, writing to some object outside `WidgetState` entirely) inside
animation-adjacent code has **no such protection** unless you add `if (context.isMeasuringInternal())
return` yourself before the effect.

---

## `animateFloat` -- continuous spring-style chase, never reaches an exact target

**Compose equivalent**: `animateFloatAsState(target, animationSpec = spring(...))` -- state-driven,
returns a `State<Float>` you read in composition; the framework keeps it animating toward `target`
on every recomposition it's read in.

**Awake**: `UiContext.animateFloat(id, target, initial = target, responsiveness = 12f, snapDistance = 0.001f): Float`
(also as `UiPrimitiveScope.animateFloat(...)`, forwarding to the same). An exponential-decay
spring: `t = 1f - exp(-responsiveness * deltaSeconds)`, `next = current + (target - current) * t`,
snapping to `target` exactly once the remaining distance is under `snapDistance`.

```kotlin
// A hover-scale that eases toward 1.05 while hovered, back to 1.0 otherwise
val scale = context.animateFloat(id = "$id.hoverScale", target = if (hovered) 1.05f else 1f)
button(id, modifier = Modifier.scale(scale)) { text("Hover me") }
```

**Status**: Diverges (ergonomics -- explicit `id` vs. automatic call-site identity, see above).

**Never use this for anything that must visibly finish.** Convergence time scales with
`ln(startValue / snapDistance)` -- fast early, then an imperceptibly slow crawl right before the
final snap. `skills/awake-shadcn-recipe-authoring/SKILL.md` documents the exact shipped symptom: a real
120px collapse *looked* fully collapsed within ~10 frames while the container kept an invisible
sub-pixel sliver of leftover height for dozens more frames, then hard-snapped once it finally
crossed `snapDistance` -- reported live as **"slowly hidden, then snap."** A wrap-sized parent
re-measuring every frame has no equivalent snap of its own, so it visibly desyncs from the child
at that final epsilon-crossing frame -- reported separately as **"the parent didn't have the same
animation."** Reach for `animateFloat` only for a continuous, never-terminating chase (a cursor
following a pointer, a camera easing toward a live target that keeps moving) with no real "done"
state. Anything that collapses, dismisses, or fades to zero wants `animateFloatTween` below.

**Frame-time-spike clamp, not a Compose concept**: a stalled frame (GC pause, window resize) feeds
a large `deltaSeconds` into the exponential-decay factor -- e.g. `responsiveness = 8`, a normal
60fps frame gives `t ≈ 0.13`, but a single 0.3s stall gives `t ≈ 0.91`, jumping the value most of
the way to `target` in one step, reading as a visible snap. `animateFloatStep` (the internal step
function) clamps `deltaSeconds` to `1f / 20f` before computing `t`, specifically to prevent this
-- Compose's real game-loop-driven `spring()` has no such clamp because Compose's frame callback
model doesn't hand it arbitrarily large deltas the same way; this is an Awake-specific mitigation,
not a divergence to imitate manually at call sites.

---

## `animateFloatTween` -- fixed-duration, `Easing`-shaped, reaches the exact target

**Compose equivalent**: `Animatable<Float>` + `animateTo(target, tween(durationMillis, easing))`,
or the `tween(...)` `AnimationSpec` passed to `animateFloatAsState`.

**Awake**: `UiContext.animateFloatTween(id, target, initial = target, durationMs = 300f, easing: Easing = LinearEasing): Float`
(also `UiPrimitiveScope.animateFloatTween(...)`). Runs for exactly `durationMs`, shaped by
`easing`, reaching `target` exactly at the end -- no asymptotic crawl.

```kotlin
// A collapse that must visibly finish -- see the animateFloat section above for why tween,
// not spring, is correct here
val animatedHeight = context.animateFloatTween(
    id = "$id.height",
    target = if (expanded) contentHeight else 0f,
    durationMs = 250f,
    easing = EaseOut,
)
```

**Status**: Diverges (ergonomics -- same explicit-`id` shape as `animateFloat`).

**Retarget semantics are Faithful, verified against the real source.** Changing `target` before a
previous tween finishes restarts the duration timer from the **current animated value**, not from
`initial` -- matching Compose's real `animateFloatAsState` retarget behavior, not a naive
"jump back to start" reset:

```kotlin
// UiAnimation.kt -- animateFloatTween's actual retarget logic
val retargeted = target != storedTarget
val effectiveStart = if (retargeted) currentValue else startValue   // <-- current value, not `initial`
val effectiveElapsed = if (retargeted) 0f else elapsedMs
```

So flipping `expanded` back to `true` mid-collapse smoothly reverses from wherever the height
currently is, exactly like toggling a Compose `AnimatedVisibility`/`animateFloatAsState` target
mid-flight -- this part needs no special caller-side handling.

**Calling this more than once per real frame with the same `id` double-steps it** (advances
`elapsedMs` twice) -- not a trial-measurement issue, a caller-error issue distinct from the guard
above. Call it exactly once per frame per `id` and reuse the returned value.

---

## `animateFloatRepeatable` -- infinite/finite repeat, closed-form (not frame-callback) progress

**Compose equivalent**: `rememberInfiniteTransition()` + `.animateFloat(initialValue, targetValue,
infiniteRepeatable(tween(...), repeatMode))`, or `repeatable(iterations, tween(...), repeatMode)`
for a finite repeat count.

**Awake**: `UiContext.animateFloatRepeatable(id, initialValue, targetValue, durationMs = 300f, easing: Easing = LinearEasing, repeatMode: RepeatMode = RepeatMode.Restart, iterations: Int = Int.MAX_VALUE): Float`.
`RepeatMode.Restart` jumps back to `initialValue` each cycle; `RepeatMode.Reverse` ping-pongs
(plays the previous cycle backward, no jump). `iterations = Int.MAX_VALUE` (the default) means
"forever," matching Compose's own `AnimationConstants.Infinite` sentinel convention.

```kotlin
// A spinner that rotates forever via scale ping-pong (rotation itself is not implemented --
// see compose-modifier-layout-guidance.md's GraphicsLayer section)
val pulse = context.animateFloatRepeatable(
    id = "$id.pulse",
    initialValue = 0.9f,
    targetValue = 1.1f,
    durationMs = 800f,
    repeatMode = RepeatMode.Reverse,
)

// A finite 3-blink attention animation, then holds at the end value
val blink = context.animateFloatRepeatable(
    id = "$id.blink",
    initialValue = 1f,
    targetValue = 0f,
    durationMs = 150f,
    repeatMode = RepeatMode.Reverse,
    iterations = 6,   // 6 half-cycles = 3 full blinks
)
```

**Status**: Faithful (ergonomics -- the `initialValue`/`targetValue`/`repeatMode`/`iterations`
parameter set genuinely matches Compose's `infiniteRepeatable`/`repeatable` split), Diverges
(mechanism). The mechanism divergence is deliberate, not an oversight: progress is computed in
**closed form** from an elapsed-time accumulator (`cycleIndex`/`cycleElapsedMs` derived from
`elapsedMs / durationMs`) rather than Compose's frame-callback-driven `Animatable`. `UiAnimation.kt`'s
own comment explains why -- watching the *output value* to decide when to turn around (the naive
port) is one frame late on the turnaround, causing a stutter; computing the cycle index from raw
elapsed time sidesteps that entirely. This is invisible at the call site; only matters if you're
modifying the implementation.

A finite `iterations` count holds at the **last permitted cycle's end value** once exhausted,
rather than continuing to wrap into cycles that were never allowed to happen -- confirmed in
`animateFloatRepeatableStep`'s `finished`/`cycleIndex` clamping.

---

## `Easing` -- fun-interface, CSS-named presets, real cubic-bezier solving

**Compose equivalent**: `Easing` fun-interface (`fun transform(fraction: Float): Float`) +
named presets (`LinearEasing`, `FastOutSlowInEasing`, `CubicBezierEasing(a, b, c, d)`).

**Awake**: Identical fun-interface shape (`com.awakekt.awake.ui.api.Easing`, `ui-graphics`
module) with its own preset library, named after CSS timing functions rather than Compose's Material
curve names:

| Awake preset | Control points | CSS equivalent |
|---|---|---|
| `LinearEasing` | `{ fraction -> fraction }` | `linear` |
| `EaseIn` | `CubicBezierEasing(0.42f, 0f, 1f, 1f)` | `ease-in` |
| `EaseOut` | `CubicBezierEasing(0f, 0f, 0.58f, 1f)` | `ease-out` |
| `EaseInOut` | `CubicBezierEasing(0.42f, 0f, 0.58f, 1f)` | `ease-in-out` |

**Status**: Faithful. `CubicBezierEasing` solves the real Bezier curve (Newton-Raphson with a
bisection fallback, the same approach Compose/WebKit use internally) rather than approximating --
confirmed by reading the full `solveCurveX`/`sampleCurveX`/`sampleCurveY` implementation in
`UiEasing.kt`. Write your own with the same constructor Compose uses:

```kotlin
val customBounce = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
val y = context.animateFloatTween(id, target, easing = customBounce)
```

---

## `rememberTransition` / `updateTransition` -- single-value coordinator, not Compose's multi-child API

**Compose equivalent**: `updateTransition(targetState)` returning a `Transition<S>`, from which
you register N independently-configured child animations: `transition.animateFloat(label = "x")
{ state -> ... }`, `transition.animateColor { ... }`, etc. -- each child owns its own
`AnimationSpec` and they all advance in lockstep under one coordinator.

**Awake**: `UiContext.rememberTransition(id, targetState: Any?, durationMs = 300f, easing: Easing = LinearEasing): Float`
(alias `updateTransition(...)`, identical body). Returns a single `0f..1f` progress value: `0f`
anchored to the **first** `targetState` this `id` ever saw, `1f` for any other `targetState`,
tweening between them via `animateFloatTween` under the hood.

```kotlin
// One progress value; derive as many properties as needed via plain lerp math
val progress = context.rememberTransition(id = "$id.state", targetState = selected)
val bg = lerpColor(unselectedColor, selectedColor, progress)
val borderWidth = lerp(1f, 2f, progress)
```

**Status**: Diverges (ergonomics, scope). This is a real, deliberately scoped-down implementation,
not a stub -- confirmed by reading `UiTransition.kt` in full: it genuinely anchors the first-seen
state and tweens on divergence. What's missing is Compose's per-property child registration
(`transition.animateFloat { state -> ... }`) letting several independently-configured properties
share one coordinator object; the doc comment states plainly this needs real multi-child
bookkeeping the current id-per-value `WidgetState` store doesn't cleanly support, so it was
deferred rather than half-built. **Practical consequence**: if two properties need genuinely
different durations/easings for the same state transition, you cannot express that with one
`rememberTransition` call -- call `animateFloatTween` directly a second time with its own spec
instead of reaching for a second `rememberTransition` (which would just duplicate the same 0..1
progress under a different id).

Calling this more than once per frame with the same `id` double-steps the underlying tween --
same caveat as `animateFloatTween`, inherited, not new. It needs no trial-measurement guard of
its own: it delegates all stepping to `animateFloatTween`, which already carries the guard, and
its own first-seen-state anchor is read-only during a trial pass (`WidgetState.getOrPut` never
mutates on a read of an existing key).

---

## `animatedVisibility` -- real `AnimatedVisibility` equivalent, alpha-only

**Compose equivalent**: `AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) { content() }`
-- keeps composing `content` through the exit animation instead of unmounting the instant `visible`
flips false.

**Awake**: `UiPrimitiveScope.animatedVisibility(id, visible: Boolean, durationMs = 200f, easing: Easing = LinearEasing, content: UiPrimitiveScope.() -> Unit)`.
Built on `animateFloatTween` driving `withGraphicsLayerAlpha` (see `compose-modifier-layout-guidance.md`'s
GraphicsLayer section for how the alpha stack applies to every primitive emitted inside `content`,
including nested composite widgets).

```kotlin
animatedVisibility(id = "$id.toast", visible = toastShown, durationMs = 200f) {
    shadcnSurface("toast-body") { text("Saved") }
}
```

**Status**: Faithful (alpha-only). Real, not a marker: `content` keeps being called -- wrapped in
a fading alpha layer -- until the exit tween actually settles at (or below) `0.001f`, so a
fade-out is a real fade rather than an instant unmount followed by an invisible-but-still-drawn
frame. No enter/exit **slide** or **scale** variant exists (this engine's `graphicsLayer` scope
doesn't carry those yet, per `compose-modifier-layout-guidance.md`'s Graphics Layer section) --
only the alpha channel. Retargeting `visible` mid-fade retargets smoothly from the current alpha
(inherits `animateFloatTween`'s real retarget semantics documented above), not a jump.

---

## The missing piece: no reusable `AnimationSpec` value type

**Compose**: `tween(durationMillis, easing)`/`spring(...)`/`repeatable(iterations, tween(...))` are
all `AnimationSpec` **values** -- build one once, pass it to multiple `animateXAsState` calls, or
wrap one spec in another (`repeatable(spring(...))`).

**Awake**: No equivalent value type exists. Each function on this page takes its spec inline as
loose parameters (`durationMs`, `easing`, `responsiveness`) -- there is nothing to construct once
and reuse. `animateFloatRepeatable` is its own free-standing function rather than a `repeatable()`
wrapper *around* `animateFloatTween`'s spec, even though its own doc comment explicitly likens it
to that Compose wrapping pattern. **Status**: Diverges (a real gap, not a subtle behavioral
difference). Practical consequence: a shared "this is our standard collapse timing" constant has
to be duplicated as loose `durationMs`/`easing` values at every call site rather than one
`val CollapseSpec = tween(250, EaseOut)`-style object:

```kotlin
// No AnimationSpec value type -- the closest available pattern today is a pair of named
// constants callers reference explicitly, not a single passable spec object
private const val COLLAPSE_DURATION_MS = 250f
private val COLLAPSE_EASING = EaseOut

val height = context.animateFloatTween(id, target, durationMs = COLLAPSE_DURATION_MS, easing = COLLAPSE_EASING)
```

Do not invent a local `AnimationSpec`-shaped wrapper type to paper over this in one component --
that is exactly the kind of new, non-Compose-shaped surface `skills/awake-ui-authoring/SKILL.md`'s
"New `Modifier`/layout extensions must match Compose's real API" rule (same rule applies to any
Compose-mimicking surface, not just `Modifier`) says to check against `mirror-map.md` before
adding. If a real reusable spec type is needed, it is a `ui-core`/`ui-animation` API design task,
not a per-component workaround.

---

## Quick Reference

| Function | Compose equivalent | Status | Reaches exact target? |
|---|---|---|---|
| `animateFloat(id, target, ...)` | `animateFloatAsState` + `spring()` | Diverges (ergonomics) | No -- asymptotic, never use for something that must finish |
| `animateFloatTween(id, target, ...)` | `Animatable.animateTo` + `tween()` | Diverges (ergonomics) | Yes, exactly, at `durationMs` |
| `animateFloatRepeatable(id, ...)` | `rememberInfiniteTransition` + `infiniteRepeatable`/`repeatable` | Faithful (ergonomics), Diverges (mechanism) | Holds at last cycle's end value once `iterations` exhausted |
| `Easing` / `LinearEasing`/`EaseIn`/`EaseOut`/`EaseInOut`/`CubicBezierEasing` | Same fun-interface shape, different preset names | Faithful | n/a |
| `rememberTransition` / `updateTransition` | `updateTransition` (multi-child) | Diverges (scoped to single progress value) | Yes, via `animateFloatTween` |
| `animatedVisibility` | `AnimatedVisibility` | Faithful (alpha-only, no slide/scale) | Yes |
| Reusable `AnimationSpec` value | `tween()`/`spring()`/`repeatable()` as passable values | Not implemented | n/a |

## Links

- [`UiAnimation.kt`](../../awake/ui/animation/src/commonMain/kotlin/com/awakekt/awake/ui/UiAnimation.kt) -- `animateFloat`, `animateFloatTween`, `animateFloatRepeatable`, `RepeatMode`
- [`UiTransition.kt`](../../awake/ui/animation/src/commonMain/kotlin/com/awakekt/awake/ui/UiTransition.kt) -- `rememberTransition`/`updateTransition`
- [`UiAnimatedVisibility.kt`](../../awake/ui/animation/src/commonMain/kotlin/com/awakekt/awake/ui/UiAnimatedVisibility.kt) -- `withGraphicsLayerAlpha`, `withGraphicsLayerScale`, `animatedVisibility`
- [`UiEasing.kt`](../../awake/ui/graphics/src/commonMain/kotlin/com/awakekt/awake/ui/api/UiEasing.kt) -- `Easing`, presets, `CubicBezierEasing`
- [`mirror-map.md`](mirror-map.md) -- status table (Animation primitives section, State hooks section for the shared identity model)
- [`compose-modifier-layout-guidance.md`](compose-modifier-layout-guidance.md) -- `Modifier`/layout how-to, GraphicsLayer section (`alpha()`/`scale()`/`graphicsLayer()`), Layout DSL section (trial-measurement model this doc's guard section depends on)
- [`skills/awake-shadcn-recipe-authoring/SKILL.md`](../../skills/awake-shadcn-recipe-authoring/SKILL.md) -- the `animateFloat` vs `animateFloatTween` decision from a real `shadcnCollapsibleCard` build
