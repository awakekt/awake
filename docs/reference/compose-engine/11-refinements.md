# 11 — Refinement register

Every deliberate divergence from Compose, with the evidence that justifies it.

**This page is a review gate.** A change that adds a refinement not in the table, or whose evidence
column is a rationale rather than a citation, does not land.

## Rules

1. **Keep Compose's shape where it is load-bearing** — `Constraints`, `Measurable`, `Placeable`,
   `MeasurePolicy`, `layout(w, h) { place() }`, the `Modifier` chain, `key()`, `CompositionLocal`.
   Familiarity is the point of mimicry; diverging here spends the budget rules 2 and 3 need.
2. **Refine only with evidence** — a shipped bug, a documented `Diverges` row, or a measured cost.
   Never taste.
3. **Every refinement is a diagnostic or a stricter default, never a different concept.** A Compose
   developer should be surprised by better errors, never by different semantics.

Rule 3 is how this codebase already behaves: `UiLayoutDiagnostics.allowUnplannedWeight`,
`UiWeightCacheConsistencyCheck.enabled` and `UiMeasureTrialStats.enabled` are all opt-in,
zero-cost-when-disabled switches that make an invisible failure loud in tests.

## Register

| # | Compose behavior | Refinement | Evidence |
|---|---|---|---|
| 1 | `Constraints.Infinity == Int.MAX_VALUE`; `maxWidth - padding` silently wraps | Same API and packing; **saturating** `offset`/`constrain`, plus a debug assert on infinity arithmetic | `UNBOUNDED_MAIN_AXIS`'s own doc: *"a real unbounded value would poison the `origin + extent` arithmetic every measure pass does"*. This repo already hit the class with a sentinel |
| 2 | Modifier order is silently meaningful | Keep the chain; add `ModifierChainDiagnostics.enabled` flagging known-wrong orderings (`clickable` after `padding`; `background` after `padding`) | The reconciler owns the chain and can walk it; Compose's compiler cannot. Same shape as `UiWeightCacheConsistencyCheck` |
| 3 | Missing `key()` in a reordering list loses state, no signal | Reconciler detects it — same node type, reordered sibling set, no keys — under the same flag | `mirror-map.md` documents the identical class for `animateFloat`'s unstable `id` and state-hook collisions: *"no exception, no warning"* |
| 4 | `remember` has no lifecycle; disposal bolted on via `DisposableEffect` | Node lifecycle is first-class: `onAttach`/`onDetach` | `mirror-map.md` State-hooks row: *"no disposal-on-leaving-composition equivalent"*, listed as `Diverges` |
| 5 | Intrinsics trigger an extra tree walk, silently | Count them — `UiLayoutStats.intrinsicQueries` | `awake-ui-performance` Rule 4: an expensive path that is *silent when unarmed* is the one that ships wrong |
| 6 | `Modifier.weight` outside a Row/Column scope silently does nothing | **Already better — do not regress.** Throw, naming symptom and fix | `ColumnScope.claimSlot`'s existing error cites the real "sidebar footer painted over its menu" bug |
| 7 | Layout errors report at the wrong node with opaque text | Every layout invariant failure names the **node path** and the fix | House style already — see `requireScrollableContainer`'s message |
| 8 | — | **Revert `ui-core`'s accidental defaults to Compose's**: packed zero-gap arrangement, `Box` shrink-wraps | `mirror-map.md:148` lists both under Scope/DSL `Diverges` |
| 9 | `@Composable` needs a compiler plugin | Kotlin **context parameter** instead | Verified on 2.4.10, all 5 targets: no flag needed, nesting works, outside-composition is a compile error. See `README.md` |

## Not refinements

Things that look like divergences but are consequences of the model, recorded so they are not
re-argued:

- **No automatic skipping.** No `$changed` masks without a compiler plugin. Manual `memo(inputs){}`
  covers hot spots; whether it is worth a plugin is a Stage 2 measurement.
- **No `LaunchedEffect`/`SideEffect`.** The engine is frame-driven with no coroutines, and node
  `onAttach`/`onDetach` covers what `DisposableEffect` exists for.
- **Partial `graphicsLayer` semantics.** Offscreen isolation, alpha and texture transforms are
  implemented; effects still need texture-pipeline support. See
  `10-graphics-layer.md`.
