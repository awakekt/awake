# Compose parity: modifiers, layout, scoped values, state hooks

Extends `SKILL.md`. Read before adding to `ui-core`'s Compose-mimicking surface, before adding a
`UiLocal`, or before adding/auditing a `remember*` hook.

## Nothing enters `:awake:compose:*` that Compose does not have

**The hard rule, and it applies to the whole module group, not just modifiers.**

> A public declaration in `:awake:compose:runtime`, `:awake:compose:ui` or
> `:awake:compose:foundation` must correspond to something `androidx.compose.runtime`,
> `androidx.compose.ui` or `androidx.compose.foundation` actually ships. If it does not, it belongs
> in `ui-designsystem` — or in the consuming module — not here.

These modules exist to *be* Compose's surface on a different runtime. That claim is the entire
reason a Compose-literate reader can predict what they do, and it is what `mirror-map.md` audits.
One local invention with a plausible-looking name spends that credibility everywhere, because a
reader can no longer tell which half of the module they are looking at.

**The test is upstream's package, not your judgement of where it fits.** "This is behaviour, not
styling, so it belongs in foundation" is the reasoning that fails: `Slider` is behaviour, and Compose
still puts it in Material.

Worked example, from a real attempt during Stage 3:

| | |
|---|---|
| Proposed | `sliderTrack()`, `sliderValueAt()`, `nearerThumb()` in `:compose:foundation:gestures` |
| Argument for | Pointer-to-value mapping is behaviour; the recipe should not re-derive it |
| Why it was wrong | Compose Foundation has no slider anything. `Slider` is Material. Foundation ships `draggable`, and Material builds on it |
| Where it went | `ui-designsystem`, as `shadcnSliderTrack`/`shadcnSliderValueAt` — which is also the layer that owns the thumb size those functions take |

A second reason the boundary held here: the old `ui-headless` slider baked in `6.dp` and `16.dp`
with a comment naming their source as shadcn v4. Anything that carries a design-system number, or
takes one as a parameter it then encodes a rule about, is design-system code by the size rule in
`SKILL.md` regardless of how behavioural it looks.

**If Compose genuinely lacks an engine-specific concern** — something with no Compose analog at all,
like a frame clock this runtime needs and Compose gets from the platform — that is the one case for
a non-Compose-shaped declaration. Name it so nobody reads it as parity, and record it in
`mirror-map.md` as **Not implemented in Compose** with the real reason.

### The narrower rule for `Modifier` and the layout DSL

Do **not** extend the naming rule below to controls: Compose Foundation has no
`Button`/`Checkbox`/`Switch`/`Slider` to check against, and Material's own signatures fuse in visual
parameters this layer cannot carry, so "does Compose have it?" has no usable answer there. Check a
control's shape against Radix/Base UI anatomy instead — see `mirror-map.md`'s "Which upstream a given
API mirrors". Note this cuts both ways: a control having no Foundation counterpart is exactly why the
control does not go in foundation.

Before adding a new function to `UiModifier` (any file in `awake/ui/ui-core/.../modifier/`)
or to the layout DSL (`row`/`column`/`box`/`Arrangement`/`Alignment`), check
[`docs/reference/mirror-map.md`](../../../docs/reference/mirror-map.md) (status table) and
[`docs/reference/compose-modifier-layout-guidance.md`](../../../docs/reference/compose-modifier-layout-guidance.md)
(how-to + code examples) first:

- **A real Compose function with this name/shape already exists and Awake doesn't have
  it yet** → implement it matching Compose's real signature and semantics (verify
  against Compose's actual current source/docs, not memory or assumption — this doc's
  own header states every row is "backed by a direct read of the current source", not
  recollection). Add it to `mirror-map.md` as **Faithful** once it lands.
- **Awake needs something Compose genuinely has no equivalent for** (an engine-specific
  concern with no Compose analog) → this is the one case where a new, non-Compose-shaped
  function is legitimate. Name it so it's obviously not attempting Compose parity (avoid
  a name that looks like a Compose function but isn't), and record it in `mirror-map.md`
  as **Not implemented in Compose** with the real reason, so a future contributor doesn't
  assume Compose semantics apply.
- **You're tempted to add a function because it "feels like it should exist" but you
  haven't checked whether Compose actually has it, or whether it behaves the way you're
  about to implement it** → stop, check first. This is exactly how the **Diverges**
  category happens — a function that looks Compose-shaped, passes a glance-review, then
  causes a real layout/render bug later because its actual behavior quietly differs from
  what a Compose-literate reader assumes. `mirror-map.md`'s own framing calls this out as
  "the dangerous category" for a reason.

Same rule for `Arrangement`/`Alignment`/scroll/graphics-layer helpers — anything under
`ui-core`'s Compose-mimicking surface, not just `Modifier` itself. That surface now has two
more first-class how-to docs beyond the Modifier/layout one: `row`/`column`/`box`/`Arrangement`
usage (including the trial-measure model's real consequence for callers) is covered by
[`compose-modifier-layout-guidance.md`](../../../docs/reference/compose-modifier-layout-guidance.md)'s
"Layout DSL" section, and `animateFloat`/`animateFloatTween`/`animateFloatRepeatable`/`Easing`/
`rememberTransition`/`animatedVisibility` by the sibling
[`compose-animation-guidance.md`](../../../docs/reference/compose-animation-guidance.md) (a separate
doc since animation lives in its own `awake:ui:animation` module). Check the relevant one before
adding to either surface, same rule as above.

## Escalate layout-engine defects; do not hide them in a recipe

Treat the following parity evidence as a `ui-core` `Modifier`/layout contract investigation,
not as an invitation to add component-specific dimensions, offsets, or separators:

- the parent geometry is correct but a child does not receive or honor its allocated bounds;
- `fillMax*` changes meaning during an intrinsic or wrap-content measurement trial, or expands
  to an unbounded viewport instead of the resolved parent axis;
- a recipe needs to inspect a measurement pass, member count, or use negative spacing merely to
  preserve ordinary parent/child layout; or
- a measured pixel slot is converted back into authored `Dp` to force a child into place.

Preserve the parity report's expected/actual/delta evidence. Add the smallest reusable
`ui-core` test matrix first (fixed and wrapping parent bounds, both axes where relevant, density
1 and 2). Fix the generic constraint propagation or modifier behavior, then rerun core,
design-system, and parity verification. Simplify the recipe only after the core contract is
proved. A fixed-width vertical button group whose members become intrinsic during a wrap-height
trial is the canonical cross-axis/intrinsic-sizing example.

**Why core-first, not recipe-first — this is a repeatedly-proven pattern here, not a
preference.** One `ui-core` primitive defect surfaces as *many* apparently-unrelated
design-system symptoms, each of which invites its own local workaround:

- The `weight()`+`fillMax*` starvation bug appeared as three separate symptoms
  (`shadcnField*` controls, a checkout-form grid row, `shadcnToggleGroup`), each patched
  locally with `weight(1f)`. The single `ui-core` fix (`9455bc51`) retired the entire class
  and made all three workarounds optional — see `mirror-map.md`'s `weight()` row.
- The vertical button-group fill bug was fixed in `RowScope`/`ColumnScope`'s `FillMax`
  resolution (`ui-core`), not in the button-group recipe.

A recipe-local fix for a primitive defect does not just leave the other symptoms broken —
it actively hides the shared root cause and makes the eventual core fix harder, because
each workaround must then be identified and unwound. The parity tool's own triage table
encodes the same rule from the other direction ("child geometry drifts but parent passes →
inspect `fillMax*`, intrinsic measurement, weights, and child modifiers"). See
[`docs/tasks/2026-08-21-modifier-layout-compose-parity-plan.md`](../../../docs/tasks/2026-08-21-modifier-layout-compose-parity-plan.md)
for the full two-axis framing (Compose behavioral parity vs. shadcn visual parity) and why
a visible shadcn drift is often a `Modifier`/layout defect wearing a design-system costume.

## Bound-derived interiors must not report intrinsic size

A widget whose interior layout is `fraction x its own resolved bound` (a resizable panel
group, a scroll viewport's content) has **no intrinsic size** -- yet inside a WrapContent
parent's measure trial its children's claims land at trial-inflated positions and register as
phantom content extent. The showcase's preview card grew by the drag delta every frame this
way: the group's handle claim at `fraction x trial-bound` out-measured the card's real text
content, and the re-measured card re-based the panel budget mid-gesture.

Wrap the real content dispatch of such widgets in `boundDerivedContent { ... }`
(`ui-core/scope/UiScopeNesting.kt` -- the stronger sibling of `compositeContent`, isolating
wrap-extent trackers, not just the child list). Scroll viewports already do the equivalent via
`withMeasuredSubtreeIsolated`. Symptom to recognize: "dragging/interacting inside a card
re-sizes the card itself".

## `UiLocal`: scoped values, and when NOT to reach for one

`UiLocal` is Awake's `CompositionLocal` equivalent (`ui-core/context/UiLocal.kt`) — provide
a value on the way into a subtree, read it anywhere below, restored on the way out. Provide
via `Provide(LocalX, value) { }` (or the `provideTextStyle`/`provideTheme`/`provideFont`
shorthands), read via `context.current(LocalX)`.

**Two ways it deliberately differs from Compose — know both before porting Compose habits:**

1. **No `compositionLocalOf` / `staticCompositionLocalOf` split.** That split exists in
   Compose purely to decide whether a read invalidates a recomposition scope. Awake rebuilds
   every frame from scratch — no composition, nothing to invalidate — so shipping the pair
   would ship a distinction with no observable difference. One `uiLocalOf` is the whole API.
2. **`combine` has no Compose equivalent, and it matters.** A scoped value is not always
   "replace the parent": `LocalTextStyle` *merges* with the style it nests inside,
   `LocalAlpha` *multiplies* so nested fades compound. That rule travels with the local's
   declaration, not with each call site:
   ```kotlin
   val LocalAlpha: UiLocal<Float> = uiLocalOf(1f) { parent, incoming -> (parent * incoming).coerceIn(0f, 1f) }
   ```
   A local that should compound but was declared with the default replace-combiner is a real
   rendering bug that surfaces far from its cause (nested fade silently stops compounding).
   Decide replace vs. merge vs. multiply **when declaring**, not later.

**Hard rule — declare at file scope, never inside a function or loop.** Each `uiLocalOf`
allocates one process-lifetime slot; declaring one inside a function leaks a slot per call,
a frame at a time. The slot model is also why reads are an array index rather than a hash
lookup, so this isn't a style preference — it's the invariant the performance depends on.

**When a `UiLocal` is the right tool** (all three should be true):

- The value is genuinely *cross-cutting* over a subtree — theme, font, text style, alpha —
  not data one specific child needs.
- Threading it as an explicit parameter would mean passing it through widgets that don't
  themselves use it, purely to reach a descendant.
- It has a sensible default that lets a widget render standalone without a provider.

**When it is the wrong tool** — same reasoning Compose gives for using `CompositionLocal`
sparingly: it makes a widget's behavior depend on invisible ambient state, which is harder
to reason about, harder to test in isolation, and harder to reuse. Prefer an explicit
parameter when the caller reasonably needs to control the value, when only one or two levels
separate provider from consumer, or when the value is really *data* rather than *environment*.
An explicit param that's slightly tedious to thread beats an ambient dependency nobody can
see at the call site.

**Layer ownership still applies, and constrains this further:** `ui-headless` must not read
the `Local*` stacks directly — it consumes the generic `Style` it is handed (see
[`layer-ownership.md`](layer-ownership.md)). The existing `ui-designsystem` locals
(`LocalShadcnTheme`, `LocalShadcnButtonGroup`) live in designsystem *because* branded ambient
state is designsystem's to own. Adding a new `UiLocal` in `ui-headless` to pass visual policy
down is the ownership violation this skill exists to prevent, wearing a different hat.

## State hooks (`remember*`): explicit `id`, not call-site identity

`UiContext.rememberStateValue`/`rememberBooleanState`/`rememberFloatState`/`rememberIntState`/
`rememberPopupState`/`rememberScrollState` (`ui-core/state/UiStateHooks.kt`) are Awake's
`remember { mutableStateOf(...) }` equivalent — a per-widget value that survives across frames,
backed by `WidgetState`.

**The one way it deliberately differs from Compose, and it's the one that bites**: Compose's
`remember` gets its identity for free from where the call sits in the composition's slot table —
two different call sites can never accidentally collide, and the same call site keeps its state
automatically. Awake has no composition and no slot table, so every hook takes a required
`id: String` that **is** the entire identity — a flat lookup, nothing else backs it up. Two real
failure directions follow from this, both silent:

1. An `id` built from a value that itself changes resets state with no compile-time signal —
   the same footgun class `animateFloat(id, ...)` already has (see
   [`compose-animation-guidance.md`](../../../docs/reference/compose-animation-guidance.md)).
2. Two unrelated widgets that end up passing the identical `id` string silently *share* one
   `WidgetState` bucket. `UiContext`'s duplicate-id throw (see
   [`identity-and-catalog.md`](identity-and-catalog.md)) does **not** catch this for bare hook
   usage — that throw lives in `recordSemantic`, a path a raw `rememberStateValue` call never
   goes through on its own. Only a widget that separately claims that same id as a semantic node
   gets the throw.

Full identity model (the two-level `id`/`key` shape, trial-measurement guard behavior, and the
`rememberPopupState`/`rememberScrollState` specifics) is in
[`mirror-map.md`](../../../docs/reference/mirror-map.md)'s "State hooks" section — read it before
adding a new `remember*` hook or reasoning about whether an existing one is safe to call from a
loop or a dynamically-keyed list. `docs/reference/ui-ownership.md`'s "Identity Params" table
already documents the `id`/`testTag`/`cacheKey` three-way split and the two-level `id`/`key`
convention; this section is the Compose-parity framing for the same rule, not a restatement of it.
