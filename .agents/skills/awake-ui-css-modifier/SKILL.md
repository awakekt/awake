---
name: awake-ui-css-modifier
description: Maps CSS/Tailwind utility classes to the legacy Awake ui-core UiModifier/Style API. Use only while maintaining the current ui:* stack; route new Compose-native website ports to awake-web-to-compose and awake-tailwind-to-compose instead. Trigger keywords - legacy UiModifier, legacy Style, w-auto, w-min, p-x, px-, py-, m-, margin, CSS property, flexbox, justify-content, align-items, css to UiModifier.
---

# CSS / Tailwind → Awake `UiModifier`/`Style`

> **Legacy-stack boundary:** New website ports start with `awake-web-to-compose`; Tailwind source
> adds `awake-tailwind-to-compose`. This guide documents the existing `ui:*` API during the
> migration only. It must not define the target Compose-native surface.

Awake's UI is not CSS -- there is no cascade, no `auto` sizing solved by a layout algorithm
guessing intent, and no box-model margin. Every mapping below is checked against the real
`UiModifier`/`Style`/`Dimension` source in `ui-core`, not assumed by CSS-name similarity.
Where there is no equivalent, this doc says so instead of inventing one.

> **Before translating**: read
> [`docs/reference/compose-modifier-layout-guidance.md`](../../docs/reference/compose-modifier-layout-guidance.md)
> for the authoritative Awake modifier reference — every field, its Compose equivalent, divergences,
> and known unsafe patterns (e.g. `fillMaxWidth()` in unbounded parents). This CSS skill maps
> *from* Tailwind; that doc maps *to* Compose — together they give the full picture.

## Sizing (`w-*`, `h-*`, `min-w-*`, `max-w-*`)

| CSS / Tailwind | Awake | Notes |
|---|---|---|
| `width: 200px` / `w-[200px]` | `.width(200f.dp)` | |
| `height: 200px` / `h-[200px]` | `.height(200f.dp)` | |
| `width: 100%` / `w-full` | `.fillMaxWidth()` or `.width(Dimension.FillMax)` | |
| `w-4`, `w-8`, ... (Tailwind spacing scale) | `.width(Tw.Spacing.s4)` | Use the vendored scale (`Tw.Spacing`, `ui-core`'s `tailwind/Tw.kt`) instead of hand-converting px, so a class and its Awake call stay numerically pinned to the same source. |
| `min-width: 100px` | `.widthIn(min = 100f.dp)` | `widthIn`/`heightIn` take nullable `min`/`max`, same shape as Compose's `Modifier.widthIn`. |
| `max-width: 400px` | `.widthIn(max = 400f.dp)` | |
| `width: auto` (`w-auto`) | **No 1:1 equivalent.** Omit the width modifier (leaf widgets resolve their own intrinsic size), or `.width(Dimension.WrapContent)` if the widget is a composite container that supports it (`WrapContent` is reserved for containers that can measure their own children -- see `Dimension`'s own doc comment, most leaf widgets don't implement it). |
| `width: min-content` (`w-min`) | **No equivalent exists.** `Dimension` is `Fixed` / `FillMax` / `WrapContent` only -- there is no min-content/max-content sizing mode. `WrapContent` is the closest available behavior but is not the same algorithm; don't claim parity. |
| `width: max-content` (`w-max`) | Same gap as `w-min` -- no equivalent. |

## Spacing (`p-*`, `m-*`)

| CSS / Tailwind | Awake | Notes |
|---|---|---|
| `padding: 16px` (`p-4`) | `.padding(Tw.Spacing.s4)` | Single-arg `padding(all: Dp)` sets all four sides. |
| `padding: 8px 16px` (`px-4 py-2`) | `.padding(horizontal = Tw.Spacing.s4, vertical = Tw.Spacing.s2)` | The two-arg `padding(horizontal, vertical)` overload is the direct Tailwind `px-*`/`py-*` match. |
| `padding-top: 8px` (`pt-2`) | `.paddingTop(Tw.Spacing.s2)` | Also `paddingBottom`/`paddingStart`/`paddingEnd` (logical start/end, not left/right -- matches RTL-aware Compose naming, not CSS's physical `pl-`/`pr-`). |
| `padding: 4px 8px 12px 16px` (`p-1 px-2 pb-3`-style explicit box) | `.padding(start, top, end, bottom)` | Four-arg overload, same order as CSS shorthand's *top/right/bottom/left* is NOT this order -- Awake's is `start, top, end, bottom`. Don't transcribe CSS shorthand order directly. |
| `margin: *` (`m-*`, `mx-*`, `my-*`) | **No equivalent. `UiModifier` has no margin concept at all.** | This is deliberate, not a gap to fill: Awake follows the Compose/immediate-mode convention where inter-sibling spacing is the *parent's* job (`row`/`column`'s `Arrangement.spacedBy(dp)`), not each child's own margin. A CSS `margin` between siblings translates to `Arrangement.spacedBy(...)` on the enclosing `row`/`column`, not a per-child modifier. A CSS margin that pushes a single child away from a container edge translates to that container's own `padding`, or `.align(...)` + `.offset(...)` for an isolated push -- see `awake-ui-authoring/SKILL.md` before reaching for `.offset()` on anything that isn't a one-off visual nudge. |

## Flexbox (`flex`, `justify-*`, `items-*`)

| CSS / Tailwind | Awake | Notes |
|---|---|---|
| `display: flex; flex-direction: row` | `row(id = "...") { }` | Container choice, not a modifier. |
| `display: flex; flex-direction: column` | `column(id = "...") { }` | |
| `flex: 1` (`flex-1`) | `.weight(1f)` | Only meaningful inside `row`/`column`; a no-op in `Box`/`Absolute` scopes (see `LayoutWeight`'s own doc comment). |
| `flex-grow: 0` (fixed-size sibling) | omit `.weight(...)` | |
| `justify-content: flex-start/center/flex-end/space-between/space-evenly/space-around` | `row(horizontalArrangement = Arrangement.Start / .Center / .End / .SpaceBetween / .SpaceEvenly / .SpaceAround)` | Passed to the container, not a per-child modifier -- matches CSS's own "justify-content lives on the flex container" semantics. Corrected 2026-08-21: `Arrangement.SpaceEvenly` is real (`ui-core`'s `Arrangement.kt`) and matches CSS `space-evenly`'s real free-space division (`freeSpace / (childCount + 1)`, equal gaps including both edges) -- a previous version of this row claimed no `SpaceEvenly` equivalent existed. |
| `gap: 8px` (flex `gap`) | `Arrangement.spacedBy(Tw.Spacing.s2)` on the container | Same primitive as `justify-content`'s row/column param -- gap and justify-content share one `Arrangement` value, they can't be set independently the way CSS allows both `gap` and `justify-content: space-between` simultaneously. Pick one. |
| `align-items: center` (cross-axis) | `row(verticalAlignment = ...)` / `column(horizontalAlignment = ...)`, or per-child `.align(UiAlignment...)` | Container-level default plus a per-child override, same split Compose uses. |

## Visual (border, radius, background, opacity)

These live on `Style`, not `UiModifier` -- `UiModifier` is layout-only, `Style` is paint/decoration.
Confirmed against `Style.kt`'s real builder surface:

| CSS | Awake (`Style { }` block) | Notes |
|---|---|---|
| `background-color: #fff` | `background(Color(0xFFFFFFFF))` | |
| `color: #000` (text color) | `foreground(Color(0xFF000000))` | |
| `border: 1px solid #ccc` | `borderWidth(1f.dp); borderColor(Color(0xFFCCCCCC))` | Two separate calls -- no single-line CSS-shorthand equivalent. |
| `border-radius: 8px` | `shape(8f.dp)` | Also `shape(UiShapeSpec.Pill)` etc. for named shapes, not just a radius `Dp`. |
| `padding` (as decoration box, not layout) | `contentPadding(all/horizontal-vertical/start-top-end-bottom)` | Distinct from `UiModifier.padding` above -- `Style.contentPadding` is what a *component's own* default inset resolves to (e.g. a card's internal padding), `UiModifier.padding` is what a *caller* adds around a widget instance. Don't conflate them; see `awake-shadcn-recipe-authoring/SKILL.md`'s note on `shadcnCard`'s default `contentPadding`. |
| `font-size: 14px` | `textSize(14f.sp)` | |
| `opacity: 0.5` | `UiScope.withGraphicsLayerAlpha(0.5f) { ... }` | Not a `Style` field -- a scope wrapper around the draw calls it applies to (group alpha over an entire painted region), see `ShadcnButtons.kt`'s `buttonSlotInternal` doc for why it's structured this way instead of a per-color alpha multiply. |
| `display: none` (hide from layout) | **No equivalent — check before assuming one exists.** Nothing in this pass found a documented conditional-render primitive; the practical answer today is not calling the widget at all inside the `UiScope` block (immediate-mode has no persistent node tree to hide/show). If a future caller needs a fade-out-then-remove, `graphicsLayer`'s alpha compositing (`awake-shadcn-recipe-authoring/SKILL.md`'s `AnimatedVisibility` note) is the closest primitive, not `display: none`. |

## Trap: CSS is `border-box`; Awake's border is paint-only

**A CSS/Tailwind `border` consumes layout space. Awake's does not.** A container that is
`p-1` plus `border` insets its content by **border + padding = 5px** in CSS, but Awake's
`surfaceCore` insets by `resolved.contentPadding` alone and never by `borderWidth`.

That is deliberate and **Compose-faithful** — Compose's `Modifier.border` is a draw
modifier, not a layout one, which is exactly why Compose code chains
`.border(...).padding(...)` to inset content. **Do not "fix" it in `ui-core`**; that breaks
Compose parity. Fold the border into the recipe's own `contentPadding`:

```kotlin
private val DropdownBorderWidth = 1f.dp

border(DropdownBorderWidth, values.colors.border)
contentPadding(Tw.Spacing.s1 + DropdownBorderWidth)   // shadcn `p-1` + border, border-box
```

Real cost of missing it: `shadcnDropdownMenu` laid its items out at x=4 width=152 inside a
160px surface instead of the reference's x=5 width=150, and the surface measured 136 tall
instead of 138 — exactly 1px per side, both axes. Invisible by eye; caught only by the
parity report (fixed in `b92245195`, pinned by `ShadcnBorderBoxInsetTest`).

Ten styles pair a 1px border with `contentPadding` (`alert`, `table`, `toast`, `kbd`, `tab`,
`surface`, `popover`, `sheet`, `drawer`, `dropdown`). Only `dropdown` is fixed and verified;
the other nine need a registered parity case first, since there's no captured reference to
check a fix against.

## Trap: a fixed height and vertical padding are not redundant

shadcn's default button is `h-9 px-4 py-2` — fixed height **and** vertical padding. The
height fixes the outer box; the padding insets content within it. Dropping `py-*` because
"the height already covers it" is wrong, and stays invisible at default size because a
centered single-line label doesn't move.

`ShadcnButtonSize` carried only `(heightDp, paddingX)` with vertical hardcoded `0f.dp`, so
every button in both themes was 8px short top and bottom (fixed in `30b9c749f`). Carry every
axis the source class declares, even when one looks redundant.

## Trap: a Tailwind `text-*` class sets TWO values, and `Tw.Text` only carries one

`text-sm` is not "font-size 14px". It is `font-size: 14px; line-height: 20px` — every `text-*`
utility ships a paired line-height:

| class | size | line-height |
|---|---|---|
| `text-xs` | 12 | 16 |
| `text-sm` | 14 | 20 |
| `text-base` | 16 | 24 |
| `text-lg` | 18 | 28 |
| `text-xl` | 20 | 28 |
| `text-2xl` | 24 | 32 |

`Tw.Text.sm` is a bare `14f.sp`. Translating `text-sm` to it alone silently drops the line-height
and the text falls back to whatever the font's own metrics give — about 1.31x, so 18.36px instead
of 20px. That is invisible on one line and compounds on every wrap: the alert's two-line
description measured 3.28px short against the pinned shadcn capture, purely from 1.64px lost per
line (found 2026-08-21).

So: when a source class sets `text-*`, set the line height too. Do not read a passing
single-line snapshot as proof — the divergence only appears once the text wraps, or once a
parent sizes to its content.

**But do not fix this by defaulting every Tailwind-sized style to its pair.** That was tried on
2026-08-21 and measurably regressed parity — `card.login` went from 16.0px to 21.0px max delta
and the alert's height error grew from −3.28px to +4.0px, with nothing improving. The pair is
only the *default*; shadcn overrides it per component, and applying the default everywhere
overshoots exactly where an override exists. `AlertTitle` is `font-medium leading-none` (14/14,
not 14/20), so a blanket 20px there is 6px too tall.

The line height has to be translated per recipe from that component's own class string, like
every other utility. Note also that `Tw` is generated by `:awake:tailwind-generator` — adding
a scale means changing the generator and committing the regenerated output, never hand-editing
`Tw.kt`.

## Current state (audited 2026-08-21)

`ui/designsystem/styles/` holds **86 raw `Nf.dp` literals and zero `Tw.Spacing` references**,
despite B12 deleting `ShadcnSpacing`/`UiSpacing` specifically to leave `Tw` as the one named
scale. Most of `Tw`'s API (`Tw.Radius`, `Tw.Text`, `Number.tw`, `Modifier.p/px/py/w/h/sz`) is
unused.

Not all 86 should convert:

| Consumer | Count | Verdict |
|---|---|---|
| `border(...)` | 25 (24 are `1f.dp`) | Not spacing — Tailwind's `border` is 1px but off the spacing scale. Converting to `Tw.Spacing` would be wrong; worth one named constant. |
| `contentPadding(...)` | 12 sites | **The real candidates.** |
| `shape(...)` | 3 | Radii — `values.shapes.*` or `Tw.Radius`. |
| size enums | 5 | Component heights, legitimately fixed. |

Every `contentPadding` value but two lands exactly on the Tailwind scale (`8→s2`, `6→s1_5`,
`12→s3`, `4→s1`, `16→s4`, `24→s6`, `10→s2_5`) — evidence they were translated from real
classes then written as bare numbers. Exceptions: `0.0`, and one genuine off-scale value at
`ShadcnNavigationStyles.kt:37` (`contentPadding(3f.dp)` — Tailwind has no 3px step, so it's
deliberate or a mistranslation; worth a look).

Convert per-site with the source class in view. **Never mass-sed** — a blind sweep launders
wrong values into looking intentional, which is worse than leaving them obviously raw.

## Checklist before porting a CSS/Tailwind reference

- [ ] Reach for `Tw.Spacing.sN` for any numeric spacing/sizing value with a Tailwind-scale
      match, instead of hand-typing a `Dp` literal that can drift from the scale.
- [ ] `margin` → `Arrangement.spacedBy` on the parent, or the parent's own `padding`. Never
      invent a per-child margin modifier -- it doesn't exist and shouldn't be added ad hoc
      (see `awake-ui-authoring/SKILL.md`'s size-rule discipline before adding new modifier
      surface).
- [ ] `w-auto`/`w-min`/`w-max` have no faithful equivalent -- say so to whoever's reading the
      port, don't silently substitute `WrapContent` and call it equivalent.
- [ ] Layout (`UiModifier`) and paint (`Style`) are separate blocks passed to different
      parameters -- a CSS `padding` maps to one or the other depending on whether it's the
      *caller* spacing a widget out, or the widget's *own* internal inset.
- [ ] Bordered container? Fold the border width into its own `contentPadding` -- CSS is
      `border-box`, Awake's border is paint-only (and so is Compose's). Skipping this is a
      silent 1px-per-side under-inset on both axes.
- [ ] Ported every axis the source class declares, including ones that look redundant
      against a fixed size (`h-9 px-4 py-2` needs the `py-2` too).

## Related Skills

- `awake-ui-authoring` -- which layer (`ui-core`/`ui-headless`/`ui-designsystem`) owns a
  given size/spacing decision, and the size-derivation rule that governs adding any new
  modifier here.
- `awake-shadcn-recipe-authoring` -- `Style.then`'s per-state-rule merge semantics, needed once a
  ported style has to compose with an existing `shadcn*` component's own variant style.
