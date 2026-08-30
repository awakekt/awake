# 2026-08-22: `PathFillTessellation.kt` — findings and fixes

Status: **planned, not started.** Found by reading the largest file the
[`UiPath.kt` split](2026-08-22-split-uipath-plan.md) produced. 664 lines, three times the next
biggest file in the module.

The split was right; it just did not go far enough, because the original file's clusters were mapped
by *declaration order* and one of them turned out to be two concerns wearing one name.

## Findings

Nine, ordered by what they cost. Every number measured, not estimated.

### 1. The file is two concerns — 53% of it is not fill tessellation

| Lines | Concern |
|---|---|
| 1–315 | Fill tessellation: `FillGroup`, `resolveFillGroups`, `scanlineFillTriangulation`, `tessellateFill`, `tessellateFillAa`, `offsetPolygon`, `appendBoundaryFringe` |
| 316–664 | **Mesh clipping and geometry predicates**: `clipToConvexPath*`, `clipToConvexContour*`, `clipPolygonToConvexContour*`, `containsPoint*`, `containsContour`, `windingContribution`, `isLeft`, `isConvex` |

Clipping a mesh against a convex contour has nothing to do with tessellating a fill. The second half
is the larger half.

**Fix:** split again into `PathFillTessellation.kt` (~315) and `MeshClipping.kt` (~250), with the
geometry predicates (`isLeft`, `isConvex`, `containsPoint`, `containsContour`,
`windingContribution`) going to a third, `PathPredicates.kt` (~100). Three files of comparable size,
each named after what it does.

### 2. Eleven functions implement three algorithms

The clipping half is triplicated across mesh vertex types:

| Algorithm | Written | For |
|---|---|---|
| `clipToConvexContour` | **3×** | `TriangleMesh`, `TexturedTriangleMesh`, `ColoredTriangleMesh` |
| `clipPolygonToConvexContour` | **3×** | plain, textured, coloured — three Sutherland–Hodgman copies |
| `clipToConvexPaths` | **3×** | bodies are identical apart from the receiver type |
| `clipToConvexPath` | **2×** | see finding 3 |

Roughly 200 of the file's 664 lines are the same three algorithms written out repeatedly.

**Fix:** the abstraction is already almost present. `TexturedVertex` and `ColoredVertex` both declare
`val position: DrawPoint`; `DrawPoint` *is* a position.

```kotlin
interface Vertex {
    val position: DrawPoint
    fun lerpTo(other: Vertex, t: Float): Vertex   // interpolates the extra channels
}
```

`DrawPoint` implements it with `position get() = this`. One generic
`clipMeshToConvexContour<V : Vertex>` replaces three, and one `clipPolygon<V : Vertex>` replaces the
three Sutherland–Hodgman copies. `clipToConvexPaths` becomes one function.

**Risk to check first:** the three copies may have drifted. Diff them line by line before unifying —
if one has a fix the others lack, that is a bug the unification would silently propagate or lose.
This is the single most important precondition in this plan.

### 3. The clipping API is asymmetric

`clipToConvexPath` (singular) exists for `TriangleMesh` and `TexturedTriangleMesh`. It does **not**
exist for `ColoredTriangleMesh`, which does have `clipToConvexPaths` (plural). Nothing explains the
gap; it reads as an oversight nobody has needed yet.

**Fix:** finding 2 removes it by construction — one generic function serves all three.

### 4. Ten public helpers, zero external consumers

Measured across `awake/` and `samples/`:

| Declaration | Files using it outside `PathFillTessellation.kt` |
|---|---|
| `resolveFillGroups`, `scanlineFillTriangulation`, `offsetPolygon`, `appendBoundaryFringe`, `isLeft`, `isConvex`, `FillGroup`, `UiFillGroup`, `AA_FRINGE_PX`, `MAX_MITER_SCALE` | **0 each** |

All public, none used. Meanwhile `appendCentroidFan` and `appendGroupFill` — the same kind of
helper — are `internal`. There is no rule, just history.

**Fix:** everything with zero external consumers becomes `internal`. The module's public surface
should be `tessellateFill`, `tessellateFillAa`, the `clipToConvexPath*` family, `containsPoint` and
`DrawPath.convexClipContour`. That is what a caller needs; the rest is how it is done.

This directly answers "should it be a member or a util": neither — it should be `internal`.

### 5. The centroid fan is written twice

`appendCentroidFan` (line 173) computes a centroid and fans a polygon into `points`/`indices`.
`tessellateFillAa`'s `onFanned` lambda (line 210) does the identical thing inline, building
`ColoredVertex` instead of `DrawPoint` — about 18 duplicated lines including the centroid loop.

**Fix:** falls out of finding 2. With a `Vertex` abstraction there is one `appendCentroidFan<V>`.

### 6. `appendGroupFill`'s two-lambda shape is a sealed type in disguise

```kotlin
internal inline fun appendGroupFill(
    group: FillGroup,
    onTriangulated: (List<DrawPoint>, IntArray) -> Unit,
    onFanned: (List<DrawPoint>) -> Unit,
)
```

Two callbacks because the two callers append to different collections. The function is not appending
anything — it is *deciding* between two outcomes and letting the caller act.

**Fix:**

```kotlin
internal sealed interface FillResult {
    data class Triangulated(val points: List<DrawPoint>, val indices: IntArray) : FillResult
    data class Fanned(val polygon: List<DrawPoint>) : FillResult
}

internal fun FillGroup.resolveFill(): FillResult
```

Callers `when` on it. The decision becomes testable on its own — today it can only be observed
through whichever lambda fires. This is the "no proper sealed?" case in this file.

### 7. `typealias UiFillGroup = FillGroup` is a rename leftover

Zero consumers anywhere. It was added as a Phase-A compatibility alias for modules that never used
it.

**Fix:** delete. Not Phase B — Phase B is for aliases something depends on.

### 8. `scanlineFillTriangulation` is O(edges × scanlines)

```kotlin
val active = edges.filter { it.y0 <= yTop && it.y1 >= yBottom }.sortedBy { xAt(it, midY) }
```

Inside the scanline loop. Every scanline filters and sorts **every** edge, so a path with E edges and
S scanlines costs O(E·S log E) and allocates two lists per scanline. `ys` also builds a list of 2E
boxed floats before deduplicating.

The classic fix is an active-edge table: sort edges once by `y0`, maintain the active set
incrementally as the scanline advances.

**Fix: measure before touching.** No benchmark exists, and complex glyph paths are the only realistic
input where E is large. Write the benchmark first; if a real path is under a millisecond, this stays
as a recorded observation and nothing changes. **Do not rewrite a working tessellator on
complexity-class reasoning alone** — `awake-ui-performance` Rule 5.

### 9. `Edge` is a local class inside the function

Declared inside `scanlineFillTriangulation`, which makes the scanline stepping impossible to test
without going through the whole triangulator. `openEdge` is also a captured `var` reassigned inside a
`forEach` with `return@forEach` for control flow — correct, but the hardest thirty lines in the file
to read.

**Fix:** lift `Edge` to a private top-level class and extract the per-scanline span emission into a
private function. Testable, and the outer loop becomes readable. No behaviour change.

## Order

1. **Diff the three clip copies** (finding 2's precondition). Nothing else starts until it is known
   whether they have drifted. If they have, land each fix separately first.
2. **Split the file** (finding 1) — pure moves, no edits.
3. **Delete `UiFillGroup`, tighten visibility** (findings 7, 4) — mechanical, compiler-verified.
4. **Unify the clip family behind `Vertex`** (findings 2, 3, 5) — the large one.
5. **`FillResult` sealed type** (finding 6) — small, and easier once the file is split.
6. **Lift `Edge`, extract span emission** (finding 9) — readability, no behaviour change.
7. **Benchmark the scanline** (finding 8) — and only then decide whether to touch it.

Steps 2, 3, 6 are behaviour-preserving by construction. Steps 4 and 5 change structure and want the
existing tests green at each step, not just at the end.

## Verification

31 tests already cover this code directly — `UiPathTest` (16), `UiPathFillTessellationTest` (15) —
plus `UiRasterizerTest`/`UiRasterizerBlendTest` (8) and three headless render tests in
`backend/vulkan` that exercise AA filled paths, stroked paths and rounded quads end to end.

**The rule for the whole plan: no behaviour change.** Every step is a structural edit. If a step
reveals a bug — and finding 2's diff may — land the structural change and file the bug separately.

**Gap worth noting:** there is no test for the clipping half at all. `clipToConvexPath` and friends
are exercised only indirectly, through the rasteriser. Finding 2 unifies three implementations into
one, which is exactly the change that wants direct tests first. Write them against the current
behaviour before unifying, so they pin what is there rather than what is arrived at.

## Not in scope

- Moving anything out of `core:graphics2d`. The vertex/mesh module question is still open in the
  [rename plan](2026-08-22-ui-prefix-rename-plan.md).
- The `points` vs `vertices` property-name inconsistency on the three mesh types. Real, small, and
  its own change — `TriangleMesh.points` where the other two say `vertices`.
- Any change to the tessellation *algorithms*. This plan reorganises and deduplicates; it does not
  improve output quality.
