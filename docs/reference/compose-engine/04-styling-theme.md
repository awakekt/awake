# 04 — Styling and theme

**Decision: `Style` and `Modifier.styleable` do not exist in `:awake:compose:ui`.** They belong in
`:awake:compose:foundation`, and land in Stage 3.

## Compose has `styleable`, and it is in foundation

An earlier version of this page claimed real Compose has no `styleable`. That is false. Compose
1.11.1 ships `androidx.compose.foundation.style` — an experimental package with a `Style` interface,
a `Modifier.styleable(state, style)` chain entry, a `StyleState` carrying
enabled/focused/hovered/pressed/selected/checked plus typed extra keys, state-rule helpers
(`hovered { }`, `pressed { }`, `disabled { }` …), style combination and animation. Shape:

```kotlin
Modifier.styleable(state, buttonStyle)
```

Two consequences:

- **Awake's `Style` and `Modifier.styleable` are convergent with Compose, not a divergence.** The
  2026-08 parity audit's "collapse the five style channels into `Style` alone" is the same collapse
  Compose made. `mirror-map.md` should record parity here, not a `Diverges` row.
- **`Style` is a foundation concern, not a Material one.** It goes in `:awake:compose:foundation`
  beside `background`/`border`, and `ui-shadcn` consumes it to build shadcn recipes — the same
  relationship Material3 has to it.

The lesson for this doc set: claims about Compose are checkable against the jars in
`~/.gradle/caches`. Check them.

## The cut

`Style` carries nine fields. Exactly one reaches measurement: `textStyle`, which sets font size and
scale. Everything else — `background`, `foreground`, `borderWidth`, `shape`, `shapeSpec`, and the
`*Token` variants — is paint-time.

So:

- **`:foundation` ships primitives.** `Modifier.background(color, shape)`, `Modifier.border(width,
  color, shape)`, `Modifier.alpha`, `Modifier.scale` — plain `DrawModifierNode`s over colors, no
  tokens, no state rules. `Modifier.clip(shape)` and `Modifier.drawBehind` stay in `:ui`
  (`ui.draw`), which is where Compose keeps them.
- **`:foundation` ships the interaction-state source.** `Modifier.hoverable(source)` /
  `focusable(enabled, source)` / `clickable(source, onClick)`, fed from last frame's placed bounds.
  Compose's parameter order, verified against the jar.
- **`:foundation` owns `Style`** as a resolver that *produces* those primitives, and
  `ui-shadcn` consumes it for shadcn recipes.

An earlier draft of this page put all of the above in `:ui`. Compose owns them in `foundation` and
no evidence was ever cited for diverging, so they moved. `:ui` is left with the chain, the phases
and the node, and nothing that paints a colour.

## Interactions without Flow

Compose's `InteractionSource` is `Flow<Interaction>`, read through `collectIsHoveredAsState()`,
whose job is to bridge the flow into recomposition-tracked state. Neither half applies here:
coroutines are an explicit non-goal (see the README), and there is no recomposition to track for.
The flow collapses to a property, `tryEmit` — already non-suspending in Compose — becomes the only
emit path, and a styling layer reads the properties during the pass that runs every frame anyway.

What is kept is the part that matters: interactions are **sets**, not booleans. Two presses and one
release leave a node still pressed. A boolean flag loses that, and the bug it produces is a button
stuck in its pressed style.

## Porting `Style` gets easier, not harder

`hasResolvedVisuals()` must resolve a style *before* claiming a slot, so it guesses:

> Avoid claiming a slot (which may be WrapContent) just to check hover. […] assume not hovered for
> this initial style resolution; actual hover is checked later once a slot is claimed.

That guess is why `smartColumn` needs a three-strategy dispatch at all — a container has to decide
"am I a surface?" before it knows its own geometry.

On a retained tree, hover, active and focus come from last frame's placed bounds and are known at
build time. The guess goes away, and the dispatch it feeds goes with it.

## Open for Stage 3

- Whether the 2026-08 parity audit's "collapse the style channels into `Style` alone" lands as part
  of the port or before it.
- Token resolution (`backgroundToken`, `textStyleToken`) against `CompositionLocal` theme values.
