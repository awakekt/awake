# Jetpack Compose Guidance: Animation
## Reference for `:awake:compose:foundation` animation callers and contributors

> **Companion to** [`mirror-map.md`](mirror-map.md) (the "Animation" status table) and
> [`compose-engine/05-animation.md`](compose-engine/05-animation.md) (the design: frame clock, no
> coroutines). Everything here is grounded in
> `awake/compose/foundation/src/commonMain/kotlin/com/awakekt/awake/compose/foundation/animation/`
> (`Animate.kt`, `TrailingValue.kt`, `ShimmerModifier.kt`) and
> `awake/compose/ui/src/commonMain/kotlin/com/awakekt/awake/compose/ui/platform/FrameClock.kt`.
> If a function is not listed on this page, it does not exist: there is no `Easing`, `tween`,
> `spring`, `Animatable`, `updateTransition` or `AnimatedVisibility` in `awake/compose` today.

This page replaces the guidance written for the retired immediate-mode animation module. Its two
biggest footguns, a string `id` that is the whole identity and a trial-measure pass that steps an
animation twice, no longer apply; see "What no longer applies" at the end.

---

## The model: a frame clock plus `remember`

Animation here has no subscription and no coroutine. The host provides one `FrameClock` per
composition as `LocalFrameClock`, advanced once per frame:

```kotlin
class FrameClock {
    val deltaSeconds: Float   // this frame, clamped to 0.1 s
    val totalSeconds: Float   // since the host's first frame, same for every reader in a pass
}
```

An animation reads the clock where it is declared and keeps its own state in `remember`, which
lives on the node. Content runs every frame, so that is all it takes to advance.

Two consequences:

- **Delta is clamped to `FrameClock.MAX_DELTA_SECONDS` (0.1 s).** A breakpoint, a backgrounded tab
  or a slow asset load does not teleport an animation; a dropped frame looks like a dropped frame.
- **State must live in `remember`, not on a modifier instance.** The modifier chain is rebuilt
  every pass, so the instance that started an animation is not the one that would advance it. The
  same mistake shipped once in `clickable`, where the link that saw a press was gone by the
  release. `GestureAcrossFramesTest` is the guard for that class.

All functions below are `context(_: Composer)`: calling one outside a composition is a compile
error, and each wraps its slots in `rememberGroup(...)` so a component's animation state does not
shift the slots of its caller.

---

## `animateFloat(target, durationSeconds = 0.2f)` -- ease toward a value

**Compose equivalent**: `animateFloatAsState(target, animationSpec = tween(...))`.

```kotlin
val open = animateFloat(if (expanded) 1f else 0f)           // 200 ms
val fill = animateFloat(progress, durationSeconds = 0.35f)
```

Behaviour, from `Animate.kt`:

- **Returns `target` on the first pass**, so a caller never animates in from zero unless it asked
  to (seed the first pass with the starting value and change the target afterwards).
- **Linear.** There is no easing parameter. A non-linear curve means shaping the returned value
  yourself (`t * t`), until an easing type lands.
- **Retarget restarts from the current value**, not from the old destination, and restarts the
  duration timer. Toggling a collapsible twice quickly reverses smoothly instead of snapping to the
  old target first. This matches Compose's retarget behaviour.
- **Reaches the target exactly** after `durationSeconds`; there is no asymptotic crawl. If
  `durationSeconds <= 0` it returns the target at once.
- `DEFAULT_DURATION_SECONDS` is 0.2 f, the shadcn transition default.

**Status**: Diverges (subset). One float, one linear tween. No spring, no `Animatable`, no
`AnimationSpec`, no `Color`/`Dp` variants: lerp a progress float into whatever you need.

```kotlin
val t = animateFloat(if (selected) 1f else 0f)
val background = lerp(unselected, selected, t)
```

Call it once per animated value per pass, in the same place every frame.

---

## `rememberLoopingPhase(durationSeconds)` -- anything that spins, sweeps or pulses

**Compose equivalent**: `rememberInfiniteTransition()` + `animateFloat(0f, 1f, infiniteRepeatable(
tween(...)))`.

Returns a value in `0f..1f` that wraps every `durationSeconds`, **starting at 0 when the caller
appears**. It requires `durationSeconds > 0`.

```kotlin
val phase = rememberLoopingPhase(durationSeconds = 1.2f)
val pulse = 0.9f + 0.2f * (1f - kotlin.math.abs(phase * 2f - 1f))   // ping-pong from a sawtooth
```

It keeps a phase and adds `delta / duration` each frame rather than deriving one from
`FrameClock.totalSeconds`. A phase derived from total time needs no state, but a spinner that
appears mid-session would start at an arbitrary rotation, which is wrong for anything whose zero
means "not started yet".

**Status**: Diverges (subset). A sawtooth only: no `RepeatMode.Reverse` and no finite iteration
count. Build a ping-pong as above, and stop a finite repeat by not reading the phase.

---

## `Modifier.shimmer(highlight, durationSeconds = 1.2f)`

A `DrawModifierNode` that draws a soft highlight band over the node's content, sweeping left to
right on `rememberLoopingPhase`. The shape it shimmers inside comes from the chain, so put it after
the `clip`/`background` it should follow:

```kotlin
Box(
    Modifier.fillMaxWidth().height(16.dp)
        .clip(RoundedCornerShape(4.dp))
        .background(theme.muted)
        .shimmer(),
)
```

It paints over the content, not through it, so it will not mask an icon glyph. Arbitrary-content
shimmer needs offscreen compositing and `BlendMode.SrcAtop`; see
[`compose-engine/10-graphics-layer.md`](compose-engine/10-graphics-layer.md).

---

## `animateTrailing(target, holdSeconds, drainPerSecond)` and `TrailingValue`

Not a Compose API. A value that trails behind the one it follows, as a health bar's second fill
shows damage just taken: it rises with the target at once, and when the target drops it holds
(`DEFAULT_TRAIL_HOLD_SECONDS` = 0.5 s) and then drains down at `DEFAULT_TRAIL_DRAIN_PER_SECOND`
(1.5 bar-units a second). A drop while it drains holds it again. It starts at `target` the first
time it is read.

```kotlin
val trail = animateTrailing(health)           // composable, uses LocalFrameClock
// draw a fill of width * trail under the main fill, which draws `health`
```

Outside a composition, hold a `TrailingValue(initial)` yourself and call
`advance(target, deltaSeconds)` once a frame; it allocates nothing. `snapTo(target)` puts the trail
on the target at once, for a bar refilled or shown for the first time. A `NaN` target is ignored.

---

## Writing a new animation

1. Read `LocalFrameClock.current` where the animation is declared. Never cache the clock's delta
   across frames.
2. Keep state in `remember` inside a `rememberGroup(PrivateObject) { ... }`, as the functions above
   do. State on a modifier instance or in a captured `var` outside `remember` does not survive a
   rebuilt chain.
3. If the effect is visual only, drive it from a `DrawModifierNode` reading a value passed through
   its `ModifierNodeElement.update`, as `shimmer` does. There is no `invalidateDraw()` to call: the
   host repaints the whole tree every frame.
4. Cover it with a test that advances the clock over several frames (`AnimateTest`, in
   `awake/compose/foundation/src/commonTest`).

## What no longer applies

- **"`id` is the entire identity."** There is no `id`. Identity is the node's position, so two
  animations cannot collide on a string and a changing value cannot silently restart one. The
  remaining hazard is positional: an unkeyed call that comes or goes shifts the slots after it, so
  wrap list items in `key(value) { }`. See `mirror-map.md` "State and `remember`".
- **Trial-measure double stepping and the `isMeasuringInternal()` guard.** Content runs once per
  frame and each child is measured once, so there is no second execution to double-step.
- **A bare `animateFloat(id, ...)` that never finished** (the former spring-style chase). The
  current `animateFloat` is a fixed-duration tween that reaches its target.

## Links

- [`Animate.kt`](../../awake/compose/foundation/src/commonMain/kotlin/com/awakekt/awake/compose/foundation/animation/Animate.kt) -- `animateFloat`, `rememberLoopingPhase`
- [`TrailingValue.kt`](../../awake/compose/foundation/src/commonMain/kotlin/com/awakekt/awake/compose/foundation/animation/TrailingValue.kt) -- `TrailingValue`, `animateTrailing`
- [`ShimmerModifier.kt`](../../awake/compose/foundation/src/commonMain/kotlin/com/awakekt/awake/compose/foundation/animation/ShimmerModifier.kt) -- `Modifier.shimmer`
- [`FrameClock.kt`](../../awake/compose/ui/src/commonMain/kotlin/com/awakekt/awake/compose/ui/platform/FrameClock.kt) -- `FrameClock`, `LocalFrameClock`
- [`compose-engine/05-animation.md`](compose-engine/05-animation.md) -- design
- [`mirror-map.md`](mirror-map.md) -- status table
- [`compose-modifier-layout-guidance.md`](compose-modifier-layout-guidance.md) -- modifier and layout pointers
