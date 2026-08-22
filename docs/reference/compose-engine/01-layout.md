# 01 — Layout

Supersedes the layout half of `docs/reference/compose-modifier-layout-guidance.md`.

## The contract

Constraints go down, sizes come up, placement happens after. Each child is measured **exactly
once** per layout pass — that is the property that removes trial passes, and it is a contract, not
an optimization.

```kotlin
interface Measurable {
    fun measure(constraints: Constraints): Placeable
    fun minIntrinsicWidth(height: Int): Int
    fun maxIntrinsicWidth(height: Int): Int
    fun minIntrinsicHeight(width: Int): Int
    fun maxIntrinsicHeight(width: Int): Int
    val parentData: Any?          // weight(), align() -- see 02-modifier.md
}

abstract class Placeable {
    var width: Int = 0;  protected set
    var height: Int = 0; protected set
    abstract fun placeAt(x: Int, y: Int)
}

interface MeasureResult {
    val width: Int
    val height: Int
    fun placeChildren()
}

interface MeasureScope {
    val density: Float
    fun Dp.roundToPx(): Int
    fun layout(width: Int, height: Int, place: PlacementScope.() -> Unit): MeasureResult
}

fun interface MeasurePolicy {
    fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult
}
```

## Constraints

```kotlin
@JvmInline
value class Constraints internal constructor(private val packed: Long) {
    val minWidth: Int
    val maxWidth: Int
    val minHeight: Int
    val maxHeight: Int
    val hasBoundedWidth: Boolean  get() = maxWidth != Infinity
    val hasBoundedHeight: Boolean get() = maxHeight != Infinity

    fun offset(dx: Int = 0, dy: Int = 0): Constraints   // saturating -- see below
    fun constrain(width: Int, height: Int): IntSize

    companion object {
        const val Infinity = Int.MAX_VALUE
        fun fixed(width: Int, height: Int): Constraints
    }
}
```

`hasBoundedHeight` replaces `ui-core`'s `UNBOUNDED_MAIN_AXIS = 100_000f`. That sentinel's own doc
admits the problem it was working around — *"a real unbounded value would poison the `origin +
extent` arithmetic every measure pass does"* — and it leaked into resolved sizes anyway, which is
what `measuredMaxBottomExcludingFill` exists to patch.

**`offset` and `constrain` saturate rather than wrap.** `Infinity` is `Int.MAX_VALUE`, so
`maxWidth - padding` on an unbounded axis silently overflows in Compose. Saturating arithmetic plus
a debug assert is refinement row 1 in `11-refinements.md`.

## Pixels — settled: Int

Layout arithmetic is **Int device pixels**. `Dp → Int px` at the modifier boundary, `Int → Float
Rectangle` at paint.

**Measured** (`ConstraintsAllocationProbe`, desktop, 100,000 `offset`+`copy` operations — the two a
measure pass actually performs):

| | Bytes / 100k ops | Per op |
|---|---|---|
| Int, packed into one `Long` as a `value class` | **0** | **0 B** |
| Float, four fields in an object | 3,200,032 | **32.0 B** |

Four Ints fit in 64 bits, so `Constraints` is a `value class` and a measure pass allocates nothing.
Four Floats need 128 bits, cannot pack, and must be a real object.

The ship gate is **16 KB/frame** total. At 32 B/op that is exhausted by **512 constraint operations
per frame** — roughly 170 nodes with three layout modifiers each. Any real screen is past that, so
Float `Constraints` alone would consume the entire frame budget before a single primitive is drawn.

### The escape hatch, and why it is not taken

Float *can* be made allocation-free: two Floats fit in a `Long`, so splitting the API into
`measure(width: WidthConstraints, height: HeightConstraints)` gives two `value class` parameters and
zero allocation. That costs the single-`Constraints` shape every `MeasurePolicy`, every
`LayoutModifierNode`, and every line of Compose documentation assumes — the parity that is the point
of this engine. Not worth it.

### What Int costs, and what Stage 1 owes because of it

- **Weight remainder.** `slack * weight / total` in Int loses up to `n - 1` px across `n` children.
  Compose accumulates the remainder rather than dropping it; `ColumnMeasurePolicy` must do the same.
  This is a real behaviour to implement, not a rounding detail to wave through.
- **Fractional density.** Re-verify the 2× dpr cases from the 2026-08 shadcn parity audit against
  the new engine. Int px at device resolution is exactly what Compose does, so this is expected to
  help rather than hurt — but "expected" is not "verified", and those fixtures already exist.

Int also removes a class of subpixel seams the repo already fights (`pixelPerfectPixel`,
`TextBaselineQuantizationTest`, `GlyphAbsoluteSizeTest`).

### Packing

16 bits per field, `0xFFFF` reserved for `Infinity`, so a real dimension caps at 65,534 px. No
viewport is that large. If one ever is, widen to Compose's focus-bucket scheme (18/13, 13/18, 15/16,
16/15) rather than dropping the packing. `ConstraintsTest` covers the sentinel boundary — the case
that breaks if a real size and `Infinity` ever collide.

## Column, in full

The payoff, and the readable version of everything `smartColumn` currently does:

```kotlin
internal class ColumnMeasurePolicy(
    private val arrangement: Arrangement.Vertical,
    private val horizontalAlignment: Alignment.Horizontal,
) : MeasurePolicy {
    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val placeables = arrayOfNulls<Placeable>(measurables.size)
        val gap = arrangement.spacing.roundToPx()
        var totalWeight = 0f
        var fixedHeight = 0
        var maxWidth = 0

        // Pass 1 -- unweighted children, main axis loosened so each reports its own size.
        for (i in measurables.indices) {
            val weight = measurables[i].columnParentData?.weight ?: 0f
            if (weight > 0f) { totalWeight += weight; continue }
            val p = measurables[i].measure(
                constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity),
            )
            placeables[i] = p
            fixedHeight += p.height
            maxWidth = maxOf(maxWidth, p.width)
        }

        // Pass 2 -- weighted children split the remaining slack. Still measured once each.
        val gaps = gap * (measurables.size - 1).coerceAtLeast(0)
        val slack = (constraints.maxHeight - fixedHeight - gaps).coerceAtLeast(0)
        for (i in measurables.indices) {
            val weight = measurables[i].columnParentData?.weight ?: continue
            val share = (slack * weight / totalWeight).roundToInt()
            val p = measurables[i].measure(constraints.copy(minHeight = share, maxHeight = share))
            placeables[i] = p
            maxWidth = maxOf(maxWidth, p.width)
        }

        val height = (fixedHeight + slack + gaps).coerceIn(constraints.minHeight, constraints.maxHeight)
        val width = maxWidth.coerceIn(constraints.minWidth, constraints.maxWidth)
        return layout(width, height) {
            var y = 0
            for (i in placeables.indices) {
                val p = placeables[i]!!
                p.placeAt(horizontalAlignment.align(p.width, width), y)
                y += p.height + gap
            }
        }
    }
}
```

Two loops is not two passes over the tree: each child's `measure` is still called once. Weighted
children are measured second because their constraint is not known until the unweighted ones have
reported.

### What this replaces

`smartColumn`'s three-strategy dispatch · `resolveMeasuredColumn` · `UiPrimitiveScope.column` ·
`planWeightedColumnSlots` · `resolveHasWeightedChild` · `precomputedMeasured` · `plannedSlots` ·
`wrapContentPass` · `UiWeightCache` / `LocalCacheKey` / `UiWeightCacheConsistencyCheck` ·
`recordingSuppressionDepth` · `wrapContributionSuppressionDepth` · `tolerateUnplannedWeight`.

Those exist to *obtain* sizes that a measure policy is simply handed. `resolveWeightedMainAxis`'s
slack math and `Arrangement.plan()` port across; the machinery around them does not.

## Ports

Read, do not depend — signatures assume "return a final rect now", which is the contract being
removed.

| From | To |
|---|---|
| `layouts/Arrangement.kt` — `resolveWeightedMainAxis()`, `Arrangement.plan()` | Row/Column measure policies |
| `layout/UiAlignment.kt`, `layout/place()` | `Alignment.align()`, `PlacementScope` |
| `layout/UiInsets.kt` | `PaddingNode` (02-modifier.md) |
| `scope/UiScopeMetrics.kt` `claimModifiedSlot()` | Splits across `SizeNode` / `PaddingNode` / `AlignNode` |
| `awake:ui:text` measurement | `TextMeasurePolicy` + intrinsics |

## Intrinsics

`minIntrinsicWidth` and friends answer "how wide would you like to be" without committing to a
measure. They cost an extra tree walk, and Compose does not tell you when you have paid for one.
Count them — `UiLayoutStats.intrinsicQueries`, same zero-cost-when-disabled contract
`UiMeasureTrialStats` already has. Refinement row 5.

`TextMeasurePolicy` must answer intrinsics **without re-shaping**; reuse the cache from
`docs/tasks/archive/2026-08-03-text-layout-measure-cache.md`.

## Defaults

Match Compose, not `ui-core`'s accidents. `mirror-map.md:148` lists both of these as `Diverges`:

- default vertical arrangement: packed, zero gap (not `ui-core`'s unrequested 8 dp `spacedBy`)
- `Box`: shrink-wraps to content (not `FillMax`)

A rewrite is the moment to stop carrying them.
