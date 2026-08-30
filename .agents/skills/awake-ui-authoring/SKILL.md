---
name: awake-ui-authoring
description: Which UI layer to write in - Compose Foundation, Material 3, or shadcn - and the size/spacing rules that keep them separate. Read before adding or changing any UI widget, before adding a `.dp` or pixel constant to a widget, and before naming a primitive. Trigger keywords - material3, shadcn, widget, primitive, component, padding, spacing, size, theme token, Modifier, foundation.
---

# Authoring UI in Awake

Three layers, each with one job. Putting code in the wrong one is the single most common
defect in this stack, and it fails *silently* -- the UI still renders, it just renders someone
else's design language.

| Layer | Owns | Never contains |
|---|---|---|
| `:awake:compose:runtime` | Retained composition, invalidation, and lifecycle | Widgets, branded recipes, or sample policy |
| `:awake:compose:ui` | Layout, drawing, modifiers, text, input dispatch, and runtime contracts | Widget recipes, variants, or brand policy |
| `:awake:compose:foundation` | Foundation-shaped layout and neutral controls/interaction behavior | Branded colors, variants, or shadcn policy |
| `:awake:ui:material3` | Material 3's color scheme, component contracts, and Material recipes | shadcn tokens, recipes, or product branding |
| `:awake:ui:shadcn` | The shadcn **look** -- named themes, sizes, colors, radii, variants, and component recipes | Behavior that another skin would also need |
| `:awake:compose:*` | Compose's own surface on this runtime -- `runtime`, `ui`, `foundation`, mirroring the `androidx.compose.*` package of the same name | **Anything Compose does not ship.** A local invention here spends the parity claim the whole group exists for |

Decide with one question: **"would a differently-skinned product still need this code?"** Yes ->
`:awake:compose:foundation`. If AndroidX Compose exposes it from `androidx.compose.material3`, use
`:awake:ui:material3`. Otherwise, visual shadcn work belongs in `:awake:ui:shadcn`.

## References

The rules below are the ones that apply to nearly every change. The detail lives next door, one
file per question, loaded when you need it.

| Read | When |
|---|---|
| [`references/layer-ownership.md`](references/layer-ownership.md) | Deciding which module something belongs in; ambient fallback vs override; which upstream reference to check a control against |
| [`references/api-shape.md`](references/api-shape.md) | Adding a public widget function, an overload, or a name; the 4 foundational primitives; return-value vs callback |
| [`references/compose-parity.md`](references/compose-parity.md) | **Adding anything at all to `:awake:compose:*`**; a `Modifier`/layout function, a `UiLocal`, or a `remember*` hook; escalating a layout-engine defect |
| [`references/shadcn-translation.md`](references/shadcn-translation.md) | Porting a shadcn class string; `Tw.Spacing` naming; CSS `border-box` |
| [`references/identity-and-catalog.md`](references/identity-and-catalog.md) | Adding a stateful widget, a loop that emits widgets, or a showcase page |

## The two terms to reach for when something belongs to the skin

Name the mechanism, not the intent. "Unstyled" is a goal and goals get argued with; these are
greppable.

- **Ambient fallback** — `resolved.fill ?: theme.colors.primary`. Debt: a skin can still win.
- **Ambient override** — `drawCheckmark(slot, theme.colors.primaryForeground)`. **Bug**: a skin
  cannot win.
- **Second path** — a capability that already has a home getting another one. One canonical
  mechanism per concern; look the registry up before drawing it yourself.

> **ui-headless holds zero visual policy.** Every colour, radius and inset arrives as a
> parameter, or nothing is drawn.

Counts, the live examples, and the `verifyUiHeadlessAmbientTheme` exemption list are in
[`references/layer-ownership.md`](references/layer-ownership.md).

## The size rule (this is the one that bites)

> A `ui-headless` widget may only fall back to a size it can derive from **its own content**,
> **its own font/vector metrics**, or a **physical constraint** (1 device pixel). Any size it
> cannot derive must be supplied as neutral widget visual/layout data by the caller above it.

Not "no defaults". Defaults are mandatory here -- this is an immediate-mode UI with a single
measure pass, so a widget with no intrinsic size measures to **zero** and silently draws
nothing. The question is never *whether* to have a fallback, only whether it is derivable.

`textarea()` is the reference implementation: its height is
`fontHeight * minLines + lineGap + contentPadding` -- no magic numbers, scales with the font,
correct under any theme. Copy that shape.

### Why this matters more than it looks

A Material-flavoured `40.dp` button fallback sat in `ui-headless`. Nothing overrode it, so it
became the de-facto default -- and then it propagated **upward**: `ShadcnButtonSize.Md` was set
to `40f` to match, and shadcn's real button is `h-9` = **36px**. The comment in `ShadcnAvatars.kt`
said the quiet part out loud -- the avatar size was chosen to match "this module's pre-existing
40dp default", not to match shadcn (`size-8` = 32px).

**Both are fixed in code: `ShadcnButtonSize.Md` is 36dp and the avatar default is 32dp.** Stated
because this passage read as a live defect long after it stopped being one, which is its own
version of the failure it describes -- a claim nothing re-checks. The lesson is what stands.

An headless default is not a neutral placeholder. It becomes the spec.

Once you are in `ui:shadcn` supplying a real branded value, *where it lives* has its own
answer — see [`references/shadcn-translation.md`](references/shadcn-translation.md).

## Units: authored values are `Dp`, never raw pixels

Widget code lives in density-independent units. A raw `Float` added to a pixel-space coordinate
renders half-size on a 2x display and looks fine on the machine that wrote it.

```kotlin
// Wrong -- physical pixels, does not scale
private const val LABEL_GAP = 8f
x += LABEL_GAP

// Right -- authored in Dp, converted at the point of use
private val LABEL_GAP = 8f.dp
x += LABEL_GAP.toPx()
```

If surrounding arithmetic is genuinely px-based, still source the value from a `Dp` and convert
at use. `awake.ui-authored-units-convention` is meant to police this; do not rely on it alone.

### The inverse trap: pixels wrapped as Dp (applies to SAMPLES too, not just widgets)

A value computed from a slot (`slot.height - bar.toPx()`) is **pixels**. Wrapping it `.dp`
re-multiplies by `UiDensity.scale` at resolve time -- 2x on every Retina display, invisible on
the density-1 machine (and every density-1 test) that wrote it. Studio shipped exactly this:
`Modifier.height(workspaceHeightPx.dp)` doubled the workspace at scale 2 and pushed the dock
handle and status bar off-frame, which read as "the resizable is broken".

- Slot-derived or otherwise pixel-valued numbers go back into modifiers via `.px`
  (`Float.px` divides by the scale), never `.dp`.
- This rule is layer-independent: sample shells violate it as easily as widgets.
- A pre-commit grep for `[a-zA-Z]Px\.dp\b` catches the naming-convention cases mechanically.
- Any layout suite guarding a live shell needs at least one case at `UiDensity.scale = 2` --
  at scale 1 the whole bug class is definitionally invisible
  (`StudioShellLayoutTest.workspaceStaysInsideTheShellAtRetinaDensity` is the model).

## Modifier order defines the hit target

Modifier order is deliberately Compose-faithful. `clickable().padding(...)` makes the padded
area interactive; `padding(...).clickable()` deliberately leaves that padding outside the hit
target. Do not change global pointer dispatch to make those orders equivalent.

A leaf control owns its internal order once: pass the caller's `modifier` to its root first, then
append its own click/hover behavior before its visual content padding. A consumer must not need to
know this implementation detail. If generic `Style` expansion hides `contentPadding`, move that
composition into the neutral headless control; do not add a design-system-only interaction helper.
Test a real click in blank field padding, not only its semantics or screenshot.

## Checklist

- [ ] Would another skin need this code? If yes it is not design-system code.
- [ ] Every size in a Headless widget is content-derived, metric-derived, a physical minimum,
      or provided through a generic Headless visual/layout contract -- never Core's component
      style registry.
- [ ] No raw-pixel `Float` constants; authored values are `Dp`, converted at use.
- [ ] No decoration choice (border width, chosen semantic colour), named variant, or direct
      `UiContext`/`Local*` stack read in `ui-headless`.
- [ ] New visual state is represented by generic `Style` and implemented in that component's
      design-system style file, never by a parallel visual DTO.
- [ ] Name matches the Radix concept; file name matches what the file exports.
- [ ] A public `context(Composer)` `Unit` visual component is a PascalCase noun (`ShadcnButton`),
      not a lowercase helper. Radix decides the component concept; Compose decides visual API
      capitalization. See `references/api-shape.md` for compatibility migration rules.
- [ ] New behaviour landed in `ui-headless`, not in the `shadcn*` wrapper.
- [ ] Interactive controls own `clickable` before their internal visual content padding; ordinary
      caller-authored modifier order remains untouched and Compose-faithful.
- [ ] One public function on `UiScope` -- no per-scope overload that only changes a default.
- [ ] No new `ui-headless` `label:`/content-shape overload -- a text label is
      `widget(id) { text("...") }` through the existing slot, not a second resolution path.
      `shadcn*` may still expose `label:` as sugar, but its body must call that same slot.
- [ ] Built exclusively from the 4 Foundational Primitives (`surface`, `row`/`column`/`box`/`spacer`, `text`/`icon`, `Modifier.*`) -- no raw canvas emitters or `interactiveSurface`.
- [ ] Every `id` param is required, not defaulted/nullable, unless the widget genuinely
      never constructs a child widget's id from it. Any loop or repeated call site derives
      a unique id per iteration (`"$id.sep.$index"`), never relies on a shared default.
- [ ] Nothing was added to `:awake:compose:runtime`/`ui`/`foundation` that `androidx.compose.*`
      does not ship. The test is upstream's package, not where it feels like it fits -- `Slider` is
      behaviour and Compose still puts it in Material. Anything carrying or parameterised by a
      design-system number is design-system code however behavioural it looks.
- [ ] New `Modifier`/layout DSL function checked against `mirror-map.md` +
      `compose-modifier-layout-guidance.md` first -- matches Compose's real API if Compose
      has one, or is clearly named/documented as engine-specific if it doesn't. Not added
      just because it "feels like it should exist." New animation or `remember*` state-hook
      surface gets the same check against `mirror-map.md` + `compose-animation-guidance.md`
      (animation) or `references/compose-parity.md` (state hooks).
- [ ] All three sources read before writing a shadcn component, in order: upstream `.tsx` for the
      values, `shadcn-compose` for a solved Kotlin translation, Awake's existing code for the bugs
      it already fixed. The comparison is noted in the file. A difference none of them explains is
      a finding, not noise.
- [ ] Translated Tailwind spacing names its step (`Tw.Spacing.sN`), not the raw `Dp` it
      equals. A bordered shadcn surface folds its border width into `contentPadding` --
      CSS is `border-box`, Awake's border is paint-only (and Compose's is too).
- [ ] Component verified with pure-JVM visual snapshot testing (`composeFrame { ... }.captureImage(...)`)
      in its desktop test suite, and semantic test tags verified.
