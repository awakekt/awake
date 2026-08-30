# Widget API shape

Extends `SKILL.md`. Read before adding a public widget function, an overload, or a new name.

## Naming: Compose is canonical for visual recipe functions; Radix names the concept

A public `context(Composer)` function that returns `Unit` and emits a visible UI element is a
Compose-shaped visual component. Name it as Jetpack Compose does: a PascalCase noun, such as
`ShadcnButton`, `ShadcnDialog`, or `ShadcnButtonGroup`. This applies even though Awake has its
own retained runtime rather than AndroidX's compiler plugin: callers read and compose these APIs
the same way. Android's Compose guidance makes this distinction explicitly: Unit visual
composables are PascalCase nouns; returning helpers retain ordinary Kotlin camelCase.

Use lowercase only for non-visual/returning helpers and modifiers (`remember*`, `shadcnMoveSplit`,
`Modifier.clickable`). Radix remains canonical for *what* a component is called and does --
`Select`, not `Dropdown`; `Progress`, not `ProgressBar` -- but it does not override Compose's
capitalization convention. Existing lowercase `shadcn*` APIs are compatibility debt. Do not add
new ones; migrate them through PascalCase entry points and explicit deprecations in a dedicated
API-migration change rather than silently breaking consumers.

- `select()`, not `dropdown()` -- it returns a chosen value, it is not a menu.
- `progress()`, not `progressBar()` -- the latter is Android View-era.
- A file's name must match what it exports. A file called `BasicText.kt` that exports no
  `basicText()` is lying.
- Styled counterparts are `Shadcn<Widget>` over the same base concept, so the pairing remains
  obvious without breaking Compose call-site conventions.

For anything Radix has no name for, the Awake name wins -- `*Slot` overloads are an Awake
invention and a good one; keep them consistent.

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
// Correct: ShadcnButton(label = ...) is fine -- as long as internally it is
fun UiScope.ShadcnButton(id: String, label: String, ...): Boolean =
    button(id, style = ...) { shadcnText(label, centered = true) }   // one headless component: button's slot

// Wrong: reaching for headless's label overload just because one exists
fun UiScope.ShadcnButton(id: String, label: String, ...): Boolean =
    button(id, label = label, style = ...)   // second headless entry point, drifts from the slot path
```

The point of both rules together: every `shadcn*` component maps to exactly ONE headless
component, called exactly ONE way (its slot). That is what keeps a shadcn recipe's visual
result guaranteed identical to what its headless primitive actually resolves -- the label-vs-
slot font-resolution drift above happened because two different code paths existed to resolve
the same style; collapsing to one path per layer is what prevents it from recurring.

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

## The 4 Closed Foundational Primitives Rule

In Awake, every single UI widget is an atomic composition of exactly 4 primitive categories:

1. **Visual Container**: `surface` is the **ONLY** painted rectangular container (background, border, shape, elevation, hover/pressed state). `interactiveSurface` is deprecated/eliminated -- use `surface` with `Modifier.clickable`.
2. **Spatial Layout Containers**: `row`, `column`, `box`, `spacer` are the **ONLY** spatial layout containers. They do not paint backgrounds; they only distribute bounds.
3. **Content Leaf Nodes**: `text` and `icon` are the **ONLY** leaf content primitives.
4. **Behavioral Modifiers**: `Modifier.clickable`, `padding`, `size`, `weight`, `align`, `alpha` are the **ONLY** way to attach behaviors.

Leaf widgets (`button`, `checkbox`, `switch`, `tabs`, `collapsible`, etc.) must **never** call raw canvas graphics emitters (`emitFillAndBorder`, `emitCheckmark`, `emitRadioDot`) or manually calculate coordinate layouts; they must compose from these 4 primitives.

## Widget event idiom: return-value, not callbacks (verified against real source 2026-08-20)

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

`Shadcn*` wrappers may still offer an `onClick`/`onXChange` convenience param as sugar
over the return value (`ShadcnButton(onClick = {...})` = `if (button(...)) onClick()`),
documented synchronous-same-frame -- but the underlying `ui-headless` widget itself
should be return-value shaped, not the other way around.
