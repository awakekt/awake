---
name: awake-ui-authoring
description: Which UI layer to write in - ui-core vs ui-headless vs ui-designsystem - and the size/spacing rules that keep them separate. Read before adding or changing any UI widget, before adding a `.dp` or pixel constant to a widget, and before naming a new primitive. Trigger keywords - ui-core, ui-headless, ui-designsystem, shadcn, widget, primitive, component, padding, spacing, size, theme token, UiComponentStyles, Modifier, headless, headless.
---

# Authoring UI in Awake

Three layers, each with one job. Putting code in the wrong one is the single most common
defect in this stack, and it fails *silently* -- the UI still renders, it just renders someone
else's design language.

| Layer | Owns | Never contains |
|---|---|---|
| `ui-core` | Geometry, layout, slots, drawing, anchoring, clipping, input/state mechanics, and neutral CompositionLocal-like theme/text mechanics | Any widget recipe, variant, or brand name |
| `ui-headless` | Widget **behaviour**, structure, and application of caller-supplied generic `Style` | Any named design-language vocabulary, theme-provider API, or branded visual policy |
| `ui-designsystem` | The shadcn **look** -- named themes, sizes, colours, radii, variants, and component styles | Behaviour that another skin would also need |

Decide with one question: **"would a differently-skinned product still need this code?"** Yes ->
`ui-headless`. No -> `ui-designsystem`.

## The two terms to reach for when something belongs to the skin

Name the mechanism, not the intent. "Unstyled" is a goal and goals get argued with; these are
greppable.

**Ambient fallback** — reaching for the ambient theme instead of a parameter. Two shapes, same
ban, different severity:

| shape | example | severity |
|---|---|---|
| ambient fallback | `resolved.fill ?: theme.colors.primary` | debt — a skin can still win |
| ambient override | `drawCheckmark(slot, theme.colors.primaryForeground)` | **bug** — a skin cannot win |

> **ui-headless holds zero visual policy.** Every colour, radius and inset arrives as a
> parameter, or nothing is drawn. Sizes still follow the size rule below — derivable from
> content, never from a theme.

Audited 2026-08-21: 31 ambient fallbacks and 14 ambient overrides. The overrides are why a
shadcn skin cannot currently restyle a checkbox's checkmark, its indeterminate dash, a radio
dot, or a toggle's on/off colours. `verifyUiHeadlessAmbientTheme` fails the build on any new
one; its exemption list is the existing debt and only ever shrinks.

**Second path** — a capability that already has a home getting another one. The repo's audit
calls the noun form "twin nouns"; this is the same defect in behaviour.

> **One canonical mechanism per concern.** Look the registry up before drawing it yourself.

Live example: the checkbox hand-builds a checkmark from transcribed coordinates
(`ShapePainter.drawCheckmark`) while `UiIcons`/`HeroIcons.check` already holds a generated
vector — and the dropdown chevron in the same file family renders through the registry. Note
this also evades `awake-ui-icons`' hand-transcription ban purely by being typed `UiPath`
instead of `UiImageVector`, so state that rule by content, not type: **if it is a glyph, it
comes from the registry; being a path is not an exemption.**

## Which reference to check a control against

`ui-headless` has no reference today, which is why `combobox` accumulated five defects and
`select` three before anyone rendered them open. Use two, for different things — and take the
*runtime* shape from neither, because Awake is immediate-mode and both references are retained:

| Take | From | Why it transfers |
|---|---|---|
| Part anatomy, roles, keyboard, state attributes | **Radix / Base UI**, and WAI-ARIA APG for behaviour | A decomposition is not a state model, so it is mode-independent |
| Tokens, variant/size enums, Tailwind-class translations | **`shadcn-compose`** (same author, already-solved Kotlin translation) | Pure data |
| *Nothing* | either one's `remember`/recomposition/callback shape | Awake resolves state by explicit `id` and returns the outcome; see `kmp-api-mimicry`'s "mimic shape, not runtime" |

Material is not the model for this layer: its signatures fuse `colors`/`elevation` into the
control, which is the ambient-override defect above promoted to API. It stays useful only as a
checklist of which controls ought to exist.

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
to `40f` to match, and shadcn's real button is `h-9` = **36px**. The comment in
`ShadcnAvatars.kt` says the quiet part out loud -- the avatar size was chosen to match "this
module's pre-existing 40dp default", not to match shadcn (`size-8` = 32px).

An headless default is not a neutral placeholder. It becomes the spec.

Do not add a `UiComponentStyles` registry to `ui-core`. A per-component size/recipe registry is
visual policy and inevitably turns Core into a hidden design system. Headless may accept a
generic visual-state contract; `ui-designsystem` supplies the actual branded sizes, colours, and
radii by mapping its named variant to that neutral contract. A Headless fallback must remain
content-derived, metric-derived, or a physical constraint.

### Where a designsystem constant lives

The rule above governs whether `ui-headless` may have a fallback at all. Once you are in
`ui-designsystem` supplying a real branded value, the question becomes *where it lives*, and
that has its own answer — see "Which home a number belongs in" in `awake-ui-shadcn-styling`.
Short form: a Tailwind step names the step (`Tw.Spacing.s1`, not `4f.dp`) even when used once, a
component's own geometry goes on its `Size` enum, and related values are derived from one
another rather than restated. As of 2026-08-21 that module holds 75 unnamed inline literals, so
the pattern is aspirational in most files — follow it in new code rather than matching what is
already there.

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

## Fill the axis through `Modifier`, never `Dimension.FillMax`

`Dimension.FillMax` is a Core layout-resolution sentinel, not authored UI vocabulary. Public
Headless calls express intent with Compose-style modifiers:

```kotlin
Modifier.fillMaxWidth()
Modifier.fillMaxHeight()
Modifier.fillMaxSize()
```

Use `width(48.dp)` or `height(48.dp)` for a fixed authored dimension. Do not expose a public
`width(Dimension)` or `height(Dimension)` overload just to make `FillMax` available.

## New `Modifier`/layout extensions must match Compose's real API, or don't add them

This rule covers modifiers and the layout DSL only. Do **not** extend it to controls: Compose
Foundation has no `Button`/`Checkbox`/`Switch`/`Slider` to check against, and Material's own
signatures fuse in visual parameters this layer cannot carry, so "does Compose have it?" has no
usable answer there. Check a control's shape against Radix/Base UI anatomy instead — see
`mirror-map.md`'s "Which upstream a given API mirrors".

Before adding a new function to `UiModifier` (any file in `awake/ui/ui-core/.../modifier/`)
or to the layout DSL (`row`/`column`/`box`/`Arrangement`/`Alignment`), check
[`docs/reference/mirror-map.md`](../../docs/reference/mirror-map.md) (status table) and
[`docs/reference/compose-modifier-layout-guidance.md`](../../docs/reference/compose-modifier-layout-guidance.md)
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
[`compose-modifier-layout-guidance.md`](../../docs/reference/compose-modifier-layout-guidance.md)'s
"Layout DSL" section, and `animateFloat`/`animateFloatTween`/`animateFloatRepeatable`/`Easing`/
`rememberTransition`/`animatedVisibility` by the sibling
[`compose-animation-guidance.md`](../../docs/reference/compose-animation-guidance.md) (a separate
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
[`docs/tasks/2026-08-21-modifier-layout-compose-parity-plan.md`](../../docs/tasks/2026-08-21-modifier-layout-compose-parity-plan.md)
for the full two-axis framing (Compose behavioral parity vs. shadcn visual parity) and why
a visible shadcn drift is often a `Modifier`/layout defect wearing a design-system costume.

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
the `Local*` stacks directly — it consumes the generic `Style` it is handed (see the
checklist rule below, and "Headless consumes `Style`, not a theme recipe" above). The
existing `ui-designsystem` locals (`LocalShadcnTheme`, `LocalShadcnButtonGroup`) live in
designsystem *because* branded ambient state is designsystem's to own. Adding a new
`UiLocal` in `ui-headless` to pass visual policy down is the ownership violation this skill
exists to prevent, wearing a different hat.

## Translating shadcn: name the Tailwind step, and remember CSS is `border-box`

Two rules, both learned from real parity drift, both about translating a source class
*faithfully* rather than eyeballing a number.

**1. Use `Tw.Spacing.sN`, not the raw `Dp` it happens to equal.** shadcn's `p-1` is
Tailwind spacing step 1. `Tw.Spacing.s1` says that; `4f.dp` says nothing and forces the
next reader to rediscover where the number came from. Same for `gap-2` →
`Arrangement.spacedBy(Tw.Spacing.s2)`.

This is currently under-applied, not a settled convention: `ui/designsystem/styles/` holds
**86 raw `Nf.dp` literals and zero `Tw.Spacing` references**, even though `Tw` is the
designated design-system spacing scale (the B12 decision that deleted `ShadcnSpacing` and
`UiSpacing` left `Tw` as the one named scale). Prefer `Tw` for any value that is genuinely
a translated Tailwind step. Do not mass-rewrite the existing literals as a drive-by — not
all 86 are spacing (border widths, radii, and sizes are in there too), and a blind sweep
would launder wrong values into looking intentional.

**2. CSS `border-box` means a border CONSUMES layout space; Awake's does not.** A shadcn
container that is `p-1` plus a `border` insets its content by **border + padding = 5px**,
not 4px. Awake's border is paint-only: `surfaceCore` insets content by
`resolved.contentPadding` alone and never by `borderWidth`.

That is deliberate and **Compose-faithful** — Compose's `Modifier.border` is a draw
modifier, not a layout one, which is exactly why Compose code chains
`.border(...).padding(...)` to inset content. So do **not** "fix" this in `ui-core`; doing
so would break the Compose parity the engine is built on.

The correct translation is at the recipe: fold the border width into that recipe's own
`contentPadding`.

```kotlin
private val DropdownBorderWidth = 1f.dp
border(DropdownBorderWidth, values.colors.border)
contentPadding(Tw.Spacing.s1 + DropdownBorderWidth)   // shadcn `p-1` + border, border-box
```

Real cost of getting this wrong: `shadcnDropdownMenu` rendered its items at x=4 width=152
inside a 160px surface instead of the reference's x=5 width=150, and the surface measured
136 tall instead of 138 — exactly 1px per side on both axes, invisible by eye, caught only
by the parity report. Ten bordered styles share this shape; `ShadcnBorderBoxInsetTest`
pins the rule for the one with captured reference evidence.

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
   [`compose-animation-guidance.md`](../../docs/reference/compose-animation-guidance.md)).
2. Two unrelated widgets that end up passing the identical `id` string silently *share* one
   `WidgetState` bucket. `UiContext`'s duplicate-id throw (see the "Every stateful widget needs a
   real, unique `id`" section above) does **not** catch this for bare hook usage — that throw
   lives in `recordSemantic`, a path a raw `rememberStateValue` call never goes through on its
   own. Only a widget that separately claims that same id as a semantic node gets the throw.

Full identity model (the two-level `id`/`key` shape, trial-measurement guard behavior, and the
`rememberPopupState`/`rememberScrollState` specifics) is in
[`mirror-map.md`](../../docs/reference/mirror-map.md)'s "State hooks" section — read it before
adding a new `remember*` hook or reasoning about whether an existing one is safe to call from a
loop or a dynamically-keyed list. `docs/reference/ui-ownership.md`'s "Identity Params" table
already documents the `id`/`testTag`/`cacheKey` three-way split and the two-level `id`/`key`
convention; this section is the Compose-parity framing for the same rule, not a restatement of it.

## Naming: Radix is canonical

Adopt the Compose name only where Compose and Radix agree, or where Radix has no concept.

Reasoning: there is no Compose here -- no `@Composable`, no recomposition, no androidx
`Modifier`. A Compose name promises a runtime this engine does not have. The behaviour-parity
target is Radix, so names should track the contract they are held to. In practice the two
conventions agree on nearly everything (Checkbox, Switch, Slider, Separator, Button, Card,
Dialog, Tabs), and where they differ the Compose name has been the one that was *wrong about
the behaviour*.

- `select()`, not `dropdown()` -- it returns a chosen value, it is not a menu.
- `progress()`, not `progressBar()` -- the latter is Android View-era.
- A file's name must match what it exports. A file called `BasicText.kt` that exports no
  `basicText()` is lying.
- Styled counterparts are `shadcn<Widget>` over the same base name, so the base names must
  *be* the shadcn names for the pairing to read.

For anything Radix has no name for, the Awake name wins -- `*Slot` overloads are an Awake
invention and a good one; keep them consistent.

## Behaviour belongs underneath, even when only one skin exists today

If a component owns real structure -- measurement, selection resolution, open/close state,
traversal -- that behaviour belongs in `ui-headless` even if `ui-designsystem` is its only
current caller. Several components (Tabs, Collapsible, Dialog, DropdownMenu) were built
styled-first and now own behaviour that cannot be reused or reskinned without dragging shadcn
in. That is a debt, not a pattern to copy.

The exception is genuine *composition*: a branded arrangement of existing primitives with no new
behaviour is correctly design-system-only.

## Headless consumes `Style`, not a theme recipe

Runtime-free theme value contracts live in `ui-api`; `ui-core` owns their runtime locals and
the neutral `CoreUiTheme` fallback. Headless does not reach into `UiContext` or `Local*` stacks
itself and does not expose `provideTheme`/`provideTextStyle`; it consumes Core's scope-level
accessors and generic `Style` values (and slots where appropriate), then applies the interaction
state supplied by that style.

`ui-designsystem` maps `Primary`, `Ghost`, or a shadcn preset to component `Style` factories.
Stateful details such as rest, hover, pressed, and disabled live beside that component's design
system style, not in a Headless visual DTO. `Style` is the only Headless visual contract.

Expose branded composition through a lower-case design-system extension such as
`fun UiScope.shadcnTheme(...)`. It delegates to Core's neutral providers and establishes the
ambient values that shadcn recipes read. Do not add `ShadcnTheme` or other branded APIs to Core
or Headless.

Use `shadcnThemeValues(...)` when only immutable theme values are needed; it does not install a
composition scope. Every `shadcn*` recipe must execute inside `UiScope.shadcnTheme { ... }`.
There is no Core-theme fallback: a missing scope is an error, so metrics and branded roles can
never be silently reconstructed from a generic Core theme.

## Design System may use Core infrastructure, never Core widget primitives

`ui-designsystem` may have an **internal** (`implementation`) dependency on `ui-core` for
`Style`, theme/text providers, CompositionLocal mechanics, and runtime-free value adapters. It
must not expose Core types from its public API.

A design-system recipe still calls Headless widgets for behavior. It must not claim slots, draw,
hit-test, record semantics, or call Core layout/control primitives directly. If a recipe needs
that, extend Headless with the missing generic behavior or slot API; duplicated behavior is a
Headless API gap, not a reason to bypass it.

## One entry point per widget: `UiScope`, not one overload per scope

A widget gets ONE public function, on `UiScope`. Do not add a `ColumnScope`/`RowScope`/
`BoxScope`/`AbsoluteScope` overload of it.

`ui-core`'s `surface()` is the counter-example still in the tree -- six functions for one widget:

| overload | what it actually changes |
|---|---|
| `UiPrimitiveScope.surface` (public) | entry point |
| `UiPrimitiveScope.surface` (impl) | the real body |
| `ColumnScope.surface` | width defaults to `FillMax` |
| `RowScope.surface` | height defaults to `FillMax` |
| `AbsoluteScope.surface` | **nothing** -- `modifier = modifier` |
| `BoxScope.surface` | **nothing** -- `modifier = modifier` |

Two do literally nothing. The two that matter encode one rule -- "fill my cross axis by default"
-- which belongs on the scope (ask it for its main axis) rather than being spelled out once per
scope type. `ui-headless` and `ui-designsystem` already made this move; `ui-core` is the holdout.

Why this matters beyond tidiness: overload sets hide capability gaps. Resolution picks the most
specific receiver silently, so a scope that lacks the overload falls through to a base version
that quietly ignores what the caller asked for. That is the same failure mode as
`Modifier.verticalScroll` on `row()`/`box()` -- accepted, dropped, no error -- which cost a full
session to find. One function per widget makes an unsupported combination a compile error or an
explicit `error(...)`, not a silent no-op.

When a scope genuinely needs different behaviour, branch on data the scope exposes inside the one
function. Add an overload only when the signature itself must differ (different parameters, not
different defaults), and say why in a comment.

## Headless: no separate overload for a content shape -- compose it through the slot

`ui-headless` gets exactly ONE widget entry point, and it is the slot/content-lambda form. Do
not add a parallel headless overload that takes a `label: String` (or any other "convenience"
data shape) and independently resolves/draws that content itself:

```kotlin
// Wrong -- a second headless code path that resolves/draws the label on its own
fun UiScope.button(id: String, label: String, ...)

// Right -- headless has one entry point; the label is composed through its slot
button(id) { text("Save") }
```

`button()` currently still has three headless overloads (label, callback, slot) -- that is
existing debt, not a pattern to extend. Do not add a fourth. This is the same principle as "one
entry point per widget" above, one level down: it is not just the *scope* that must not fork,
the headless *content shape* must not fork either.

Why this is stricter than it looks: a `label:` overload has to independently resolve the exact
same style (weight, size, colour, intrinsic width) the slot path already resolves once via the
widget's real style-resolution machinery (`resolveInteractiveSurface`/`buttonSlotInternal` for
`button()`). Re-deriving it a second time for the label path drifts from the first the moment
either one changes -- caught in this repo only by a snapshot-signature pixel diff (label text
rendered visibly smaller than the slot path), not by any type check or unit test, after a
same-session attempt to route the label overload through the slot API silently regressed font
resolution. One content-shape, one resolution path, no drift possible.

### `shadcn*` MAY offer `label:` -- as long as it calls exactly one headless component

The restriction above is on `ui-headless`, not on `ui-designsystem`. A `shadcn*` recipe is
allowed a `label: String` convenience parameter -- callers of a design system expect that
sugar -- but its OWN body must still be built on the single headless slot component, composing
the label through it, never by calling a headless label-shaped overload:

```kotlin
// Correct: shadcnButton(label = ...) is fine -- as long as internally it is
fun UiScope.shadcnButton(id: String, label: String, ...): Boolean =
    button(id, style = ...) { shadcnText(label, centered = true) }   // one headless component: button's slot

// Wrong: reaching for headless's label overload just because one exists
fun UiScope.shadcnButton(id: String, label: String, ...): Boolean =
    button(id, label = label, style = ...)   // second headless entry point, drifts from the slot path
```

The point of both rules together: every `shadcn*` component maps to exactly ONE headless
component, called exactly ONE way (its slot). That is what keeps a shadcn recipe's visual
result guaranteed identical to what its headless primitive actually resolves -- the label-vs-
slot font-resolution drift above happened because two different code paths existed to resolve
the same style; collapsing to one path per layer is what prevents it from recurring.

### The exception, found by trying it

A per-scope default is NOT always collapsible, because **overload resolution is static and a
`when (this)` is not**. `surface`'s `ColumnScope`/`RowScope` pair defaults the caller's cross axis
to `FillMax`. A call whose receiver is typed `UiPrimitiveScope` deliberately gets no default even
when the runtime instance happens to be a `ColumnScope` -- so replacing the pair with one function
that inspects `this` hands a default to every such call site. Tried exactly that: `PanelTest`, both
signature matrices and the parity screenshots all moved.

So the rule is narrower than "one function per widget":

- an overload that only forwards (`modifier = modifier`) is dead -- delete it
- an overload whose default depends on the STATIC receiver has to stay
- either way the rule BODY is written once and called from each, never copy-pasted

Delete-and-run-the-suite is the check. If nothing moves, the overload was dead.

## The showcase catalog is the only catalog

`samples:ui-showcase` publishes one list -- `ShowcasePages` in `ShowcaseCatalog.kt`. The app
renders it and every preview/layout-signature fixture is derived from it. Do not maintain a
second list of preview entries in test code.

Three rules keep it honest:

1. **One file per page**, under `ui/pages/<category>/`. The page object owns its own metadata
   *and* its `hero`/`variants`/`states` renderers. A preview function with no page object is
   dead code, not a hidden feature.
2. **`showcasePageOrNull` returns null for an unknown id.** Never reintroduce a fallback to
   `ShowcasePages.first()`. The old fallback let five test fixtures fingerprint the
   Introduction page while claiming to cover Range Slider, State, Shimmer, and Field Demo --
   green tests, zero coverage.
3. **Preview size lives on the page** (`previewWidth`/`previewHeight`), not in a JVM
   annotation. Reading fixture metadata by reflection forces a skip on iOS and wasmJs, so the
   same test silently passes there without asserting anything.

A component shadcn ships that Awake cannot build yet gets a `showcasePlaceholder(...)` entry
naming the missing primitive, not silence.

## The 4 Closed Foundational Primitives Rule

In Awake, every single UI widget is an atomic composition of exactly 4 primitive categories:

1. **Visual Container**: `surface` is the **ONLY** painted rectangular container (background, border, shape, elevation, hover/pressed state). `interactiveSurface` is deprecated/eliminated -- use `surface` with `Modifier.clickable`.
2. **Spatial Layout Containers**: `row`, `column`, `box`, `spacer` are the **ONLY** spatial layout containers. They do not paint backgrounds; they only distribute bounds.
3. **Content Leaf Nodes**: `text` and `icon` are the **ONLY** leaf content primitives.
4. **Behavioral Modifiers**: `Modifier.clickable`, `padding`, `size`, `weight`, `align`, `alpha` are the **ONLY** way to attach behaviors.

Leaf widgets (`button`, `checkbox`, `switch`, `tabs`, `collapsible`, etc.) must **never** call raw canvas graphics emitters (`emitFillAndBorder`, `emitCheckmark`, `emitRadioDot`) or manually calculate coordinate layouts; they must compose from these 4 primitives.

### Widget event idiom: return-value, not callbacks (verified against real source 2026-08-20)

Most discrete-interaction widgets are pure **return-value**, not callback-based --
`checkbox`, `switch`, `slider`, `rangeSlider`, `select`, `combobox` take no
`onXChange`/`onClick` param at all; the caller reads the widget's return (`Boolean`/
`Float`/`Pair`/`Int?`) and reacts. `button` returns `Boolean` (`if (button(id)) { ... }`).
This is the immediate-mode-native shape (see `docs/audits/2026-08-17-ui-refactor-vs-recreate-audit.md`
row C9) -- prefer it for any new widget.

Two known exceptions, not yet fixed (tracked in C9, both real, both narrower than this
doc used to claim -- the previous version of this section listed `toggle`/`checkbox`/
`switch`/`slider` as uniformly callback-based, which stopped being true once C3/C5
landed; corrected here):
- `toggle` carries **both** a `Boolean` return and an `onCheckedChange` callback
  simultaneously -- pick the return value, the callback is redundant, don't add a third
  widget that relies on the callback firing.
- `toggleGroup` (both overloads) is `Unit`-returning with only a callback -- an
  inconsistency with its sibling `slider`, not yet reconciled.

`shadcn*` wrappers may still offer an `onClick`/`onXChange` convenience param as sugar
over the return value (`shadcnButton(onClick = {...})` = `if (button(...)) onClick()`),
documented synchronous-same-frame -- but the underlying `ui-headless` widget itself
should be return-value shaped, not the other way around.

## Every stateful widget needs a real, unique `id` -- collisions are silent without the check below

`id` is the lookup key into `WidgetState` (hover/active/animation/scroll/caret state).
Two widgets that end up with the same `id` string silently share one state slot --
hovering one visually reacts on the other, one's animation glitches, clicking one can
toggle the other's checked state. This has shipped as a real bug multiple times (see
`docs/audits/2026-08-17-ui-refactor-vs-recreate-audit.md`'s 2026-08-20 re-audit section
and the fixes in commits `c1a6dab10`/`b6e2759ae`).

The two failure shapes to watch for, both already fixed once each but easy to
reintroduce:
1. **A defaulted/optional `id` that feeds a *different* child widget's required-id
   slot.** `shadcnAvatarGroup(id: String = "avatar")` used to interpolate
   `"$id.$index"` into each child avatar's id -- two `shadcnAvatarGroup`s on one
   screen without an explicit `id` collided. Fix: every widget that constructs a
   child widget's id from its own id must take a **required** `id`, not a defaulted
   one.
2. **A loop or sibling-call site that doesn't derive a unique id per iteration.**
   `shadcnButtonGroupSeparator()` called inside `forEachIndexed` with no `id` fell
   back to the same orientation-derived string every iteration -- a single button
   group with 3+ members collided with *itself*. Fix: pass a derived id
   (`"$id.sep.$index"`) at every call site inside a loop or repeated composition,
   never rely on a shared default.

**Safety net, not a substitute for getting it right:** `UiContext` throws immediately
if the same `id` is claimed twice in one real (non-measuring) frame
(`UiContextFrameState.recordSemantic`, `awake/ui/ui-core/.../context/UiContextFrameState.kt`).
This turns the silent-bug class above into a loud crash during development/tests
instead of a shipped visual glitch -- but it only fires once you actually render two
colliding instances together, so it doesn't replace picking real, unique ids up front.
If you hit this throw, the message names the colliding literal id; the fix is almost
always shape #1 or #2 above.

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
- [ ] New behaviour landed in `ui-headless`, not in the `shadcn*` wrapper.
- [ ] One public function on `UiScope` -- no per-scope overload that only changes a default.
- [ ] No new `ui-headless` `label:`/content-shape overload -- a text label is
      `widget(id) { text("...") }` through the existing slot, not a second resolution path.
      `shadcn*` may still expose `label:` as sugar, but its body must call that same slot.
- [ ] Built exclusively from the 4 Foundational Primitives (`surface`, `row`/`column`/`box`/`spacer`, `text`/`icon`, `Modifier.*`) -- no raw canvas emitters or `interactiveSurface`.
- [ ] Every `id` param is required, not defaulted/nullable, unless the widget genuinely
      never constructs a child widget's id from it. Any loop or repeated call site derives
      a unique id per iteration (`"$id.sep.$index"`), never relies on a shared default.
- [ ] New `Modifier`/layout DSL function checked against `mirror-map.md` +
      `compose-modifier-layout-guidance.md` first -- matches Compose's real API if Compose
      has one, or is clearly named/documented as engine-specific if it doesn't. Not added
      just because it "feels like it should exist." New animation or `remember*` state-hook
      surface gets the same check against `mirror-map.md` + `compose-animation-guidance.md`
      (animation) or the "State hooks" section above (state hooks).
- [ ] Translated Tailwind spacing names its step (`Tw.Spacing.sN`), not the raw `Dp` it
      equals. A bordered shadcn surface folds its border width into `contentPadding` --
      CSS is `border-box`, Awake's border is paint-only (and Compose's is too).
