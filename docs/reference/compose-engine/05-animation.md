# 05 — Animation

Frame-clock driven, no coroutines. A node subscribes on attach and unsubscribes on detach.

```kotlin
interface FrameSubscription { fun cancel() }

fun Modifier.Node.requestFrames(onFrame: (deltaSeconds: Float) -> Unit): FrameSubscription
fun DrawModifierNode.invalidateDraw()
```

## Every `isMeasuringInternal()` guard becomes unnecessary

`ui-core` re-executes content to measure it, so any side-effecting animation steps twice per frame.
Each `animateFloat*` carries an explicit guard, and `mirror-map.md` records this as a *forced*
divergence with no Compose analog — citing shimmer, commit `85f14821`, as a real shipped bug.

Content runs once here. There is no second execution to double-step, so the guards, and the rule
that every new side-effecting hook must remember to add one, both go away.

## Worked example — shimmer as a modifier

Today: `emitShimmerOverlay(id, slot, shapeSpec, radiusPx, highlightColor, durationMs)` plus a
`shimmer` marker field on `UiModifier`.

```kotlin
private class ShimmerNode(
    private val highlight: Color,
    private val durationMs: Float,
) : DrawModifierNode {

    private var phase = 0f
    private var frames: FrameSubscription? = null

    override fun onAttach() {
        frames = requestFrames { deltaSeconds ->
            phase = (phase + deltaSeconds * 1000f / durationMs) % 1f
            invalidateDraw()
        }
    }

    override fun onDetach() {
        frames?.cancel()
        frames = null
    }

    override fun DrawScope.draw(drawContent: () -> Unit) {
        drawContent()
        val bandWidth = (size.width * 1.5f).coerceAtLeast(MIN_BAND_WIDTH)
        val x = -bandWidth + (size.width + bandWidth) * phase
        drawGradientRect(
            bounds = Rectangle(x, 0f, bandWidth, size.height),
            gradient = UiLinearGradient(
                topLeft = Color.Transparent, topRight = highlight,
                bottomRight = highlight, bottomLeft = Color.Transparent,
            ),
        )
    }
}

fun Modifier.shimmer(
    highlight: Color = Color.White.withAlpha(0.6f),
    durationMs: Float = 1200f,
): Modifier = this then ShimmerNode(highlight, durationMs)
```

The shape it shimmers inside comes from the chain, not a parameter:

```kotlin
Column(
    Modifier.fillMaxWidth()
        .clip(RoundedCorner(8.dp))
        .background(theme.muted)
        .shimmer()
        .padding(16.dp),
) { /* … */ }
```

### What disappeared

| Gone | Why |
|---|---|
| `id: String` | Identity is positional; phase lives on the node. Kills the unstable-id footgun `mirror-map.md` documents for the whole `animateFloat` family |
| `slot: Rectangle` | `DrawScope.size` is the node's own measured size |
| `shapeSpec` / `radiusPx` | The clip composes from the chain instead of being re-declared per call |
| The `isMeasuringInternal()` guard | Content runs once |
| `shimmer` as a `UiModifier` field | A `DrawModifierNode` *is* the effect |

### What does not change

Arbitrary-content shimmer — over an icon glyph — still needs offscreen compositing and
`BlendMode.SrcAtop`, exactly as `ShimmerPrimitives.kt`'s own comment already says. See
`10-graphics-layer.md`. This is the same clip-based approach; it just composes.

## Open

`animateFloat`, `animateFloatTween`, `animateFloatRepeatable`, `rememberTransition` and
`animatedVisibility` port in Stage 2, when node-local `remember` exists. Until then they keep their
string ids.
