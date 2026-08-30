# 2026-08-23: shadcn tokens — keep what exists, take one idea from the proposal

**Question asked:** is the existing shadcn skill set and parity tooling stale enough to recreate, or
should it be reused? And how does it compare to a `ShadcnColors` + `staticCompositionLocalOf` +
`Modifier.shadcnFocusRing` design?

**Answer: reuse. It is not stale — it is the most mechanically verified part of this repo.** One idea
from the proposal is worth taking, and it is not the one it looks like.

## Why not recreate

| Measured | |
|---|---|
| `Shadcn*Test` files in `ui:designsystem` | **54** |
| `@Test` methods there | **130** |
| Status | **all green** |
| Skills last touched | 2026-08-21 (two days) |

The values are not hand-typed. `ShadcnReferenceTokenTest` diffs the resolved theme against
`ronjunevaldoz/shadcn-compose`'s own `ShadcnColors.kt` (fetched 2026-07-18), whose values carry
doc-comment citations back to `ui.shadcn.com/docs/theming`'s published OKLCH variables.
`ShadcnReferenceTokenExpandedTest` extends that to **every base color in both light and dark**, plus
the radius spec.

That chain — our resolver → shadcn-compose's values → shadcn's published OKLCH — is the ground truth
the proposal is trying to reach. It already exists and it is armed.

The radius scale is the sharpest example of what re-typing would cost. It was once additive, with a
comment asserting shadcn's scale "is additive, not multiplicative". That was wrong, and it produced
*identical numbers* at the default preset's 10 dp base — so it was correct by coincidence there and
drifted on every other preset. It is now multiplicative, verified against the pinned reference app's
own `index.css`. A fresh hand-typed table has no way to catch that class of error.

## The proposal, field by field

Every one of the nineteen fields in the proposed `ShadcnColors` already exists:

`background` `foreground` `card` `cardForeground` `popover` `popoverForeground` `primary`
`primaryForeground` `secondary` `secondaryForeground` `muted` `mutedForeground` `accent`
`accentForeground` `destructive` `destructiveForeground` `border` `input` `ring`

So the data class is not new surface. Adopting it verbatim would replace machine-checked values with
hand-typed ones.

### Three things not to carry over

**`@Immutable` — do not add it.** There is no compiler plugin here, so the annotation enforces
nothing. An annotation that looks exactly like Compose's and checks nothing is the *silent when
unarmed* failure class this repo keeps hitting — `cacheKey`, unstable `animateFloat` ids, and
`ui-ownership-convention`'s own vacuous pass. The engine README already rules this out for
`@Composable` and the same reasoning applies here.

**`staticCompositionLocalOf` — an alias at best.** In Compose it skips invalidation tracking, so
changing it recomposes a whole subtree instead of only readers. This engine rebuilds the entire tree
every frame and has no invalidation to skip, so the two would behave identically. Worth adding for
familiarity, but only with a doc line saying why it is the same — otherwise it implies a performance
property that does not exist here.

**The proposed `shadcnFocusRing` has a bug and would reintroduce the artifact it is meant to avoid.**

```kotlin
fun Modifier.shadcnFocusRing(...): Modifier = this.then(
    if (isFocused) {
        this.drawBehind { … }   // `this` again — the chain is appended to itself
    } else this
)
```

The outer `then` receives a modifier that already contains the receiver, so the whole chain is
duplicated. It should be `if (isFocused) this.drawBehind { … } else this`, with no outer `then`.

Beyond the bug, `drawBehind` paints *behind* the node, so any opaque `background` in the chain
occludes the ring entirely — and where it is not occluded, growing the rect by 4 dp with a −2 dp
offset puts it outside the node's own bounds, where an ancestor `clipToBounds` cuts it. That is the
artifact, not a fix for it.

## The one idea worth taking

**We are missing the outer ring, and the proposal is right that something has to draw it.**

Today the entire focus ring is:

```kotlin
internal fun UiThemeValues.shadcnFocusRing(): Style = Style {
    focused { borderColor(colors.ring) }
}
```

A border **recolor**, and nothing else — verified: no outer-ring geometry exists anywhere in
`ui-shadcn`. The `ring` *token* is correct and tested; the ring *rendering* is half-built.

Real shadcn's focus treatment is two things at once — the border takes the ring colour **and** a
separate translucent ring is drawn outside it. We do the first and not the second, which is why
focus reads flatter here than in the reference.

**Where it should live, once the engine migration is done:** a `DrawModifierNode` that draws *after*
content rather than behind it, sized from `DrawScope.size` rather than from a passed rect, and
composed from the chain rather than re-declared per call site. `05-animation.md`'s shimmer worked
example is the same shape and the same reasoning.

## The actual blocker, and it is cheap

None of this is a token problem. All 130 tests and the whole theme live in `ui-shadcn`, which
depends on `ui-core` and `ui-headless` — both deleted in Stage 3.

The coupling turns out to be thin:

| File | Engine-side imports |
|---|---|
| `ShadcnTokens.kt` | `UiShapeTokens` — one interface |
| `ShadcnTheme.kt` | `UiColorTokens`, `UiShapeTokens`, `UiThemeValues`, `UiTypography` — four interfaces |
| `ShadcnReferenceTokenTest.kt` / `…ExpandedTest.kt` | **none** — `Color` and `kotlin.test` only |

The reference tests import nothing from the engine at all, so they survive the move untouched. The
token layer needs four interfaces rehomed and nothing else.

## Recommendation

1. **Keep the tokens, the resolver and all 130 tests.** They are the asset.
2. **Rehome the four `ui.api.theme` interfaces** so the token layer stops depending on `ui-core`.
   This is Stage 3's cheapest first step and it de-risks everything after it.
3. **Adopt the proposal's *shape* on the compose side** — a theme value type provided through a
   local, read by recipes — but keep the existing verified *values*.
4. **Build the outer focus ring** as a `DrawModifierNode` that draws after content, once
   `:compose:foundation` is the base. Add a reference test for it, the way every other token has one.
5. **Do not add `@Immutable`.** Add `staticCompositionLocalOf` only with a note that it is an alias
   here.
