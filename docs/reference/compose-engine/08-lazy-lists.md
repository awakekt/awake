# 08 — Lazy lists

Stage 3. Recorded now because the current design exists for a reason that this engine removes.

## Why today's `lazyColumn` takes a fixed `itemHeight`

`LazyList.kt` requires every item to share one caller-supplied `itemHeight`/`itemWidth`, and
computes total extent as `itemCount * itemHeight` — zero measurement of any item, on-screen or off.
`mirror-map.md` records why: it was picked over measure-and-cache-per-id *specifically to avoid
re-triggering trial-measure cost for every item scrolled into view*.

That constraint is a workaround for the model this engine replaces. Measuring an item is a measure
policy call, not a re-execution of a content lambda.

## Shape

- Items measure for real, and per item. Total extent comes from measured items plus an estimate for
  the unmeasured tail, refined as scrolling reveals them.
- `key` becomes real identity, not a convenience. Today `key` is threaded into `itemContent(index,
  key)` so callers can build stable widget ids by hand; with positional identity plus `key(value){}`
  the reconciler keeps item state attached to the item, so slot reuse cannot leak state between
  indices.
- Scroll state stays an explicit `Modifier.verticalScroll(state)` argument rather than
  list-owned — `ui-core`'s existing divergence from Compose's `LazyListState`, kept deliberately.

## Open

- Overscan policy and how estimated extent interacts with a scrollbar's thumb size.
- Whether variable-height items need a measured-extent cache keyed by item key, and what invalidates
  it.
