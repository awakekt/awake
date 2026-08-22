# 03 — CompositionLocal

**Stage 1 blocker.** Two of these reach *measurement*, so text cannot be measured without them.

```kotlin
class CompositionLocal<T> internal constructor(internal val default: () -> T)

fun <T> compositionLocalOf(default: () -> T): CompositionLocal<T>

context(composer: Composer)
fun <T> CompositionLocal<T>.current(): T

context(composer: Composer)
fun <T> CompositionLocalProvider(local: CompositionLocal<T>, value: T, content: () -> Unit)
```

On a retained tree a provider is a node. Reads resolve by walking to the nearest providing ancestor,
so there is no push/pop pair to leak on a throw — the failure mode `ui-core`'s
`pushLocal`/`popLocal` has today.

## Migrating the seven `UiLocal`s

| `ui-core` | Fate | Reaches measurement? |
|---|---|---|
| `LocalTextStyle` | Port | **Yes** — font size and scale size the text |
| `LocalFont` | Port | **Yes** — glyph metrics |
| `LocalTheme` | Port | No |
| `LocalShapeSpec` | Port | No |
| `LocalAlpha` | Becomes `GraphicsLayerNode` state | No — it is a draw concern, and a stack only because draw was interleaved with layout |
| `LocalTransform` | Becomes `GraphicsLayerNode` state | No — same, and see `10-graphics-layer.md` for why nesting starts composing correctly |
| `LocalCacheKey` | **Deleted** | It exists only to arm the trial-measure cache |

`LocalAlpha` and `LocalTransform` moving out of the local stack is not cosmetic: they are per-node
paint state, and modelling them as ambient values is what made nested scale fail to compose.

## Value semantics

`LocalAlpha` composes multiplicatively today via a custom merge lambda in `uiLocalOf`. A
`CompositionLocal` has no merge hook — nesting composes because the *nodes* nest. Keep it that way;
a merge lambda on an ambient value is the thing that let two different composition rules exist.
