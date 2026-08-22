# 13 — Semantics

`UiFrameOutput.semantics` keeps its shape, so `ui-testing` and the parity tooling keep working. What
changes is where the nodes come from.

## Traversal order

Today `recordSemantic` appends in emission order, and `UiContextFrameState.recordSemantic` throws on
a duplicate id — the codebase's one collision safety net, and it only covers widgets that claim a
semantic id.

With a tree, semantics are collected by walking the **placed** tree. Order is spatial and stable,
which is what a screen reader and a test both want. `Modifier.semantics { }` and `Modifier.testTag`
attach to a node.

## Identity

`UiSemanticNode` keeps its `id` for now, because `ui-testing` fixtures and the shadcn parity tooling
key off it. But identity is the node, so the duplicate-id throw stops being the only guard against
two widgets sharing state — see `mirror-map.md`'s state-hook collision row, which notes the throw
does **not** cover bare `rememberStateValue` calls.

## Open

- Whether `UiSemanticRole` stays an enum or becomes composable semantics properties.
- Merging: a button with a text child should report as one node, not two. Compose's
  `mergeDescendants` is the model; decide before porting `ui-headless`, since every control has this
  shape.
