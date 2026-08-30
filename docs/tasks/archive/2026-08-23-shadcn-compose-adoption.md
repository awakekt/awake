# 2026-08-23: what to take from `shadcn-compose`

Status: **analysis, feeding Stage 3 step 3.** Read before rewriting the 94 recipes.

Source: [`ronjunevaldoz/shadcn-compose`](https://github.com/ronjunevaldoz/shadcn-compose) — same
author, same target (shadcn on Compose Multiplatform), a working library rather than a proposal.

## First, it settles the `ui-headless` question

> "No Material dependency, no icon-library dependency" — 70+ components across primitives, forms,
> overlays, data display and AI chat, from one `commonMain`, on Android/iOS/Desktop/Web.

That is a **live existence proof that shadcn's full component set builds on Foundation alone.**
There is no headless tier in it. The Stage 3 plan argued `ui-headless` should be deleted rather than
ported by reasoning from the layer's own contents — 0 state markers across six controls, twelve
re-export shims. This is the same conclusion reached independently by someone shipping it.

So the answer to "can `:compose:foundation` carry all shadcn components alone" is yes, and it is not
a bet.

## Second, the styling mechanism is convergent, not a choice either of us made

| | `shadcn-compose` | Awake `:compose:foundation` |
|---|---|---|
| Type | `androidx.compose.foundation.style.Style` (experimental, `@OptIn(ExperimentalFoundationStyleApi::class)`) | `fun interface Style { fun applyStyle(scope: StyleScope) }` |
| State rules | `hovered { }`, `pressed`, `disabled` | `StyleScope.hovered(style)`, `pressed`, `disabled` |
| Composition | nested blocks inside one `Style { }` | `Style.then`, six state rules |

`Style.kt`'s own comment already records the convergence: *"`androidx.compose.foundation.style.Style`
has the same `applyStyle(scope)` shape. An earlier version of `04-styling-theme.md` claimed Compose
had no equivalent; it does."* Two independent arrivals at one shape is the strongest signal available
that the shape is right.

**Nothing to adopt here — Awake already has it.** Recorded so the mechanism is not re-litigated
during the port.

## What to actually adopt

### 1. A style is an extension on its variant, not a separately-named helper

```kotlin
// shadcn-compose
sealed interface ButtonVariant {
    data object Default : ButtonVariant
    data object Outline : ButtonVariant
    // …
}

@Composable
fun ButtonVariant.rememberStyle(): Style = rememberShadcnStyle(this) { … }
```

`ui-designsystem` publishes **67 `*Style` helpers** (`shadcnButtonStyle`, `shadcnCardStyle`,
`shadcnSidebarMenuItemStyle`…) alongside 94 recipes. The Stage 3 plan's answer was "fold them into
the recipes as the port goes, and decide per-helper whether any survive as public."

This is a better third option, and it dissolves the question rather than answering it:

- **No naming channel.** `ButtonVariant.rememberStyle()` needs no name invented for it, so there are
  not 67 names to keep consistent.
- **No public-API decision.** The helper is reachable exactly where the variant is, which is exactly
  where a caller styling a custom widget already is.
- **Exhaustiveness.** A new variant that forgets its style is a `when` compile error, not a runtime
  fallback.

Take this for step 3 instead of the fold-or-keep decision the plan currently defers.

#### Why not a `ShadcnStyles.button(variant, enabled)` facade

A namespace object with one function per component is the obvious third design, and neither library
chose it. Worth recording why, because it looks like the tidier option:

- It **keeps the naming channel** the variant extension removes. `ShadcnStyles.button`,
  `.accordionContent`, `.breadcrumbItem` — that is still 62 names to invent and keep consistent, just
  behind a dot.
- It **loses exhaustiveness.** A new `ButtonVariant` that nobody styled is a `when` compile error
  with the extension; against a facade it is whatever the `else` branch does.
- Discoverability is the one real gain, and the variant type already provides it: you have a
  `ButtonVariant` in hand at the call site, so `variant.rememberStyle()` completes from there.

**Do not take `enabled` as a parameter.** Disabled is a *state*, and both libraries already model it
as one — `StyleScope.disabled(style)` here, `disabledDim()` there. Threading it through the factory
signature duplicates what `StyleState` carries, and re-keys the memoised style every time it flips.
The signature stays `variant.rememberStyle()`; enabled/hovered/pressed/focused all arrive through
the scope.

#### What this does and does not remove by hand

It removes hand-authoring **at the recipe**: 94 recipes stop assembling styles and just apply one.

It does not make styles generated. `ButtonStyles.kt` is hand-written — a `when (variant)` mapping
each variant onto token reads (`colors.primary`, `colors.onPrimary`). That mapping is a design
decision, not a derivation: nothing in the token table says Outline is the variant that gets a
border. What *is* generated is the token layer underneath it, which Awake already has
(`ShadcnReferenceTokens.kt`, extracted from the pinned registry and gated).

So: tokens generated, variant→style written once per variant, recipes author nothing.

### 2. Cross-cutting style fragments get their own files

`styles/` in `shadcn-compose` is 19 files, and six of them are not per-component at all:

```
FocusRing.kt   PressedMoveDown.kt   DisabledDim.kt   Shimmer.kt   ScrollFade.kt   Border.kt
```

They compose into any variant as one line each:

```kotlin
Style {
    background(colors.primary)
    contentColor(colors.onPrimary)
    hovered { background(colors.primary.copy(alpha = 0.9f)) }
    focusRing(RoundedCornerShape(shapes.lg))
    pressedMoveDown()
    disabledDim()
}
```

This is how "every variant gets the same focus ring" stays true without being written six times —
and the button file's own comment says that is deliberate, because real shadcn applies
`focus-visible:ring-*` from a shared class regardless of variant.

Awake has `shadcnFocusRing` already. The pattern worth taking is the **directory of one-concern
fragments**, so a shared interaction affordance has one home instead of being inlined per recipe.
This is the same "one canonical mechanism per concern" rule `awake-ui-authoring` states, applied to
styling.

### 3. `sealed interface` + `data object`, not `enum class`, for variants

Costs nothing today and lets a variant carry data later without a breaking change. Awake's `Size`
enums are a fair counter-example — a size genuinely is a closed set of valueless cases — so this is
a variant rule, not a blanket one.

### 4. Comments cite the upstream file and name the deliberate divergences

```kotlin
// Matches shadcn/ui's real button.tsx (github.com/shadcn-ui/ui) as closely as our
// Style API allows: most variants have zero border (only Outline has one), hover
// is an alpha-blended background rather than a whole-node dim. Every variant gets
// the same `focusRing(...)` ring -- real shadcn's shared `.cn-button` class applies
// focus-visible:ring-* regardless of variant, including Link.
```

Names the source, then names each place it knowingly differs and why. This is already Awake's house
style; the port is the moment to apply it, because a recipe rewritten from a headless control is
exactly where an accidental divergence becomes invisible.

The `iconTint` helper in the same file is the other half: it documents why it *ignores* hover and
disabled rather than leaving a reader to wonder whether that was an oversight.

### 5. `rememberShadcnStyle(key) { … }`

One memoisation wrapper, keyed on the variant, rather than each style deciding for itself. Worth
having before 94 recipes each invent their own caching — and the `cacheKey` history in
`awake-ui-performance` is what an opt-in, per-call-site answer to this costs.

### Where the focus ring actually comes from

Asked because it matters whether this is upstream behaviour or theirs. Split three ways:

| Piece | Owner |
|---|---|
| `focused { }` state rule | **Compose Foundation** — `androidx.compose.foundation.style.focused` |
| `dropShadow(Shadow)` | **Compose Foundation** |
| `ringShadow(color, width)` | **theirs** — `io.github.awakelab.tailwind.style` |
| `focusRing(shape)` / `focusRingAlways(shape)` | **theirs** — `styles/FocusRing.kt` |

So the *mechanism* is Foundation and the *shadcn ring* is custom, in both libraries. Awake's
equivalent is `internal fun UiThemeValues.shadcnFocusRing(): Style` in `ShadcnInputStyles.kt`, and it
is much narrower — two call sites, both inputs, where theirs covers ~15 components.

One idea in theirs is worth taking independently of the implementation: **`focusRing(shape)` sets the
shape *and* the ring in one call**, because a `dropShadow` carries no shape of its own and silently
falls back to a sharp rectangle. Their `AccordionTrigger` shipped a ring with no matching
`shape(...)` and it only surfaced when someone tabbed to it. Pairing the two structurally means there
is no way to add a ring without a shape.

## What not to adopt

**Their `focusRing` implementation.** Do not port it as-is.

Reported symptom: **the ring's thickness and shape do not match real shadcn, and read as confusing.**
The source agrees, and names the mechanism — `focusRingShadow` folds shadcn's `ring-offset` into the
shadow's single `spread` as `width + offset`:

```kotlin
fun ShadcnThemeData.focusRingShadow(color: Color = colors.borderFocus): Shadow =
    ringShadow(color = color.copy(alpha = ring.opacity), width = ring.width + ring.offset)
```

A real `ring-offset` is a transparent gap *before* the ring, which needs two stacked shadows — an
invisible spacer, then the coloured ring. Folded into one `spread`, the offset becomes extra
thickness instead of a gap, so the ring is **too thick by exactly `ring.offset`**, and because spread
grows the silhouette uniformly the corner radii come out wrong with it. Both halves of the reported
symptom fall out of that one line.

Their own comment concedes the simplification and argues it is exact today because every reachable
preset has `offset = 0`. Two problems with that: it is a correctness claim resting on a value nothing
enforces, and it does not hold — the symptom is visible, so some reachable path has a nonzero offset
or the spread-to-radius relationship diverges independently.

**For Awake:** model the ring as `width` and `offset` separately from the start. If only one shadow
is available, a nonzero offset must be a hard error rather than silently folded — the failure this
produced is a wrong-looking ring with no diagnostic, which is the exact "silent when unarmed" class
`awake-ui-performance` Rule 4 is about.

Take the shape-pairing *idea* — `focusRing(shape)` sets shape and ring together — and write the ring
itself against Awake's own `Style`.

**`ShadcnSpacing.kt`.** `shadcn-compose` keeps a spacing token file; Awake deliberately deleted
`ShadcnSpacing` and `UiSpacing` in the B12 decision, leaving `Tw` as the one named scale, because a
Tailwind step should name the step. Re-introducing a parallel spacing vocabulary during the port
would undo that. See `awake-ui-authoring`'s `references/shadcn-translation.md`.

**The directory split wholesale.** `components/` + `styles/` already match. `tokens/`, `theme/`,
`overlay/` and `interaction/` as siblings are a reasonable end state, but moving files is not this
port's job — Stage 3 is explicit that nothing relocates.

## The `@Composable` difference costs exactly two things

`shadcn-compose` is `@Composable` with a compiler plugin; Awake is `context(_: Composer)` with none.
Checked rather than left as a caveat — the mapping is otherwise complete, and only two concrete gaps
fall out.

### Gap 1 — Awake's `StyleScope` cannot read a `CompositionLocal`

Their `focusRing(shape)` takes no theme parameter. It reads the theme off the scope:

```kotlin
fun StyleScope.focusRing(shape: Shape) {
    val theme = ShadcnTheme.LocalShadcnTheme.currentValue   // StyleScope : CompositionLocalAccessorScope
    …
}
```

Awake's `StyleScope` exposes `state` and the property setters, nothing else. So a style fragment
cannot reach the theme from inside the scope.

**Not a blocker — Awake already has the other answer,** and it is arguably the better one:
`internal fun UiThemeValues.shadcnFocusRing(): Style` makes the fragment an extension *on the theme*.
The theme is read once in the `context(_: Composer)` function and captured, which is safe here for a
reason worth stating: their capture-freeze hazard (a `remember { Style { … } }` closing over a stale
theme) requires the closure to outlive a theme change. Awake rebuilds every frame, so an *unremembered*
`Style` cannot go stale. A `remember`ed one still can — see Gap 2.

**Decision: style fragments are extensions on the theme values, not free `StyleScope` functions.**
Do not add `CompositionLocal` access to `StyleScope` to match theirs; it buys one saved parameter and
reintroduces the staleness class.

### Gap 0 — `Style` takes the scope as a parameter, so every line needs `scope.`

Found by shaping the snippet below against the real API instead of assuming it. Awake today:

```kotlin
// StyleTest.kt, actual current syntax
private val buttonStyle = Style { scope ->
    scope.background(base)
    scope.cornerRadius(4.dp)
    scope.contentPadding(8.dp)
    scope.hovered { it.background(hover) }
    scope.pressed { it.background(press) }
    scope.disabled { it.alpha(0.5f) }
}
```

shadcn-compose, same thing:

```kotlin
Style {
    background(colors.primary)
    contentColor(colors.onPrimary)
    hovered { background(colors.primary.copy(alpha = 0.9f)) }
}
```

The difference is one word in the declaration — `fun applyStyle(scope: StyleScope)` takes the scope
as a **parameter**, so SAM conversion hands it to the lambda as an argument rather than a receiver.
Every property line pays `scope.`, and every nested state rule pays `it.`.

**Change it to a receiver before the port, not after:**

```kotlin
fun interface Style {
    fun StyleScope.applyStyle()
}

fun StyleScope.hovered(style: Style) { if (state.isHovered) with(style) { applyStyle() } }
```

Then the Awake form reads exactly like the one above, with no `scope.`/`it.` at all.

This is worth doing **now** specifically because of the port's size: 94 recipes and ~62 style helpers
are about to be written against whichever form exists. Changing it afterwards is a sweep across all
of them; changing it first is one file and its tests. The call sites today are tests and a handful of
foundation controls.

Not cosmetic at the margin either — `it.background(hover)` inside a nested rule is where a reader
stops being able to see the CSS the style is translating, which is the whole job of these files.

### Gap 2 — `remember` takes one key, and this pattern wants two

```kotlin
fun <T> remember(calculate: () -> T): T
fun <T> remember(key: Any?, calculate: () -> T): T
```

Their `rememberShadcnStyle(vararg keys)` calls `remember(theme, *keys)` — theme *and* the variant.
Awake has no vararg or multi-key overload, and this is the first pattern that genuinely needs one:
key on theme alone and a variant switch goes stale; key on variant alone and a theme change does.

Two ways out, both small. Prefer the first:

1. **Add `remember(key1, key2, calculate)`.** Compose has 1–4 key overloads for exactly this reason.
2. Key on a holder — `remember(theme to variant)` — which allocates a `Pair` every frame and so is
   the wrong trade in per-frame code.

**This is a prerequisite for the pattern, not a follow-up.** It is one overload plus a test.

## What it looks like in Awake

Shaped against the real API — `context(_: Composer)`, `rememberStyleState`, `Modifier.styleable`,
and the six state rules — assuming **Gap 0 (receiver form) and Gap 2 (two-key `remember`) are done
first**. Both are prerequisites, and both are small.

```kotlin
// styles/ShadcnButtonStyles.kt

sealed interface ShadcnButtonVariant {
    data object Default : ShadcnButtonVariant
    data object Outline : ShadcnButtonVariant
    data object Secondary : ShadcnButtonVariant
    data object Ghost : ShadcnButtonVariant
    data object Destructive : ShadcnButtonVariant
}

/**
 * shadcn's real `button.tsx`: only Outline carries a border, hover is an alpha-blended background
 * rather than a whole-node dim, and every variant gets the same focus ring because upstream applies
 * `focus-visible:ring-*` from a shared class regardless of variant.
 */
context(_: Composer)
fun ShadcnButtonVariant.rememberStyle(): Style {
    val theme = LocalShadcnTheme.current
    return remember(theme, this) {
        when (this) {
            ShadcnButtonVariant.Default -> Style {
                background(theme.colors.primary)
                textColor(theme.colors.primaryForeground)
                hovered { Style { background(theme.colors.primaryHover) } }
                theme.shadcnFocusRing(Tw.Radius.md)
                theme.shadcnDisabledDim()
            }

            ShadcnButtonVariant.Outline -> Style {
                background(theme.colors.background)
                border(Tw.Border.px1, theme.colors.border)
                textColor(theme.colors.foreground)
                hovered { Style { background(theme.colors.accent) } }
                theme.shadcnFocusRing(Tw.Radius.md)
                theme.shadcnDisabledDim()
            }

            // … Secondary, Ghost, Destructive
        }
    }
}

// styles/ShadcnFocusRing.kt -- one concern, its own file, composed into any variant.
// Sets the radius AND the ring together: a ring with no matching radius is the bug class
// shadcn-compose's AccordionTrigger shipped, invisible until someone tabs to the control.
internal fun ShadcnThemeValues.shadcnFocusRing(radius: Dp): Style = Style {
    cornerRadius(radius)
    if (ring.enabled) focused { Style { border(ring.width, colors.ring) } }
}

// components/ShadcnButton.kt -- the recipe authors no style at all.
context(_: Composer)
fun shadcnButton(
    label: String,
    modifier: Modifier = Modifier,
    variant: ShadcnButtonVariant = ShadcnButtonVariant.Default,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = remember { InteractionSource() }
    val state = rememberStyleState(interaction, enabled = enabled)

    Box(
        modifier
            .styleable(state, variant.rememberStyle())
            .clickable(interaction, enabled = enabled, onClick = onClick)
    ) {
        Text(label)
    }
}
```

Three things to check in that snippet, because they are the whole point:

- **`enabled` is a parameter of the *recipe*, never of the style.** It reaches the style through
  `rememberStyleState`, and `disabled { }` reads it off `StyleState`.
- **`variant.rememberStyle()` is the only style expression in the recipe.** No `shadcnButtonStyle(…)`
  helper name, no `then` chain assembled at the call site.
- **`shadcnFocusRing` is called from the variant, not the recipe** — so a new variant that forgets it
  is a visible omission in one `when`, not a defect spread across 94 files.
