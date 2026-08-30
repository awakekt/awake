# Compose Modifier node lifecycle plan

## Purpose

Move retained modifier behavior from per-pass `Modifier.Element` instances into Compose-shaped
`Modifier.Node` instances. This closes the lifetime gap in which an interaction, animation, or
resource owned by a modifier vanished on the next reconciliation pass.

## Contract

- `ModifierNodeElement<N>` is immutable per-pass configuration and supplies `create` plus `update`.
- `Modifier.Node` is retained at its modifier-chain position while the element class remains the
  same; it receives `onAttach` once and `onDetach` once.
- A replacement detaches the old node before updating and attaching its replacement.
- Removing a layout node recursively detaches the retained modifier nodes it owns.
- `Modifier.Node` is not a `Modifier.Element`, so a retained implementation cannot be inserted in
  a chain directly; every node enters through its `ModifierNodeElement`.

## Delivery sequence

1. **Complete:** Add the core element/node split and reconcile it in `LayoutNode`.
2. **Complete:** Dispose retained nodes when a modifier entry or a layout subtree is removed.
3. **Complete:** Prove reuse, update, replacement ordering, modifier removal, and subtree removal
   with `ModifierNodeLifecycleTest`.
4. **Complete:** Migrate stateful foundation modifiers first: `focusRequester`, text input,
   clickable, combinedClickable, hoverable, draggable, ordinary scrolling, and lazy scrolling now
   use retained nodes.
5. **Complete:** Migrate stateless layout, draw, semantics, focus, and cursor modifiers. Focus
   callbacks/targets/properties, placement callbacks, key handlers, pointer cursor, generic
   `layout {}`, `layoutId`, intrinsic sizing, offset, aspect-ratio, bounded/required size, default
   minimum size, padding, fixed size, fill, wrap-content, draw-behind, draw-with-content, scale,
   alpha, drop shadow, graphics layers, clipping, background/border, semantics, parent-data
   weights, foundation focusable, and shimmer now use the retained model.
6. **Complete:** Remove the legacy direct-node bridge after downstream consumers migrated and run
   the Compose UI and Foundation desktop test suites.

## Non-goals

This does not add AndroidX snapshot state, compiler-plugin recomposition, or asynchronous effects.
It only gives retained modifier implementation state a correct, deterministic lifetime.
