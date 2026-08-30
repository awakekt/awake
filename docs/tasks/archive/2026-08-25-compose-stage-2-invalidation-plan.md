# Compose Stage 2 — state invalidation and retained skip scopes

Status: completed — runtime scopes, state invalidation, host retention, focused tests, Studio chrome
consumer, controlled profiling, and parity ledger updated.

## Decision trigger

The Stage 1 rule was to avoid copying Compose snapshots merely for API resemblance: Awake rebuilds
the UI every frame, while a normal Compose application can sleep between state changes. That made
skipping a measurement question, not an automatic goal.

The first current-tree measurement clears the investigation threshold:

| Scene | Target | Warmup / sample | Result |
|---|---|---:|---:|
| Studio shell, 1440×900, Noop renderer | desktop JVM | 60 / 300 frames | 1.86593 ms/frame |

That is about 11% of a 16.67 ms frame before scene rendering. It justifies designing an opt-in
skip path. It does **not** prove that every subtree should be skipped, nor that a compiler plugin
would pay for itself.

## Constraint

Awake has Kotlin context parameters, not Compose's compiler plugin. The plugin creates restart
groups and supplies changed-parameter masks at every composable call site. An ordinary Kotlin
function has neither boundary nor parameter-change information.

Therefore this must not claim automatic Compose-equivalent skipping. Adding only
`mutableStateOf` would record writes but cannot safely decide whether arbitrary caller parameters
changed. Skipping an arbitrary function would retain stale children, modifiers, and semantics.

## Chosen first slice

Build an explicit runtime scope:

```kotlin
context(Composer)
fun recomposeScope(
    input: Any?,
    content: context(Composer) () -> Unit,
)
```

Two-input and no-input overloads follow without vararg allocation. A scope re-executes when either
an input changed or a state value it read since its last execution changed. Otherwise it retains its
already-reconciled child subtree and skips its content lambda for this frame.

`mutableStateOf(value)` is added beside the scope, with a deliberately small `MutableState<T>` API.
Reads register the innermost active scope; writes dirty only scopes that read that state. The host
still paints and dispatches input every frame, so state does not need to "wake" it.

This is explicit input ownership, not a new component state pattern. App/session/document state
continues to be caller-owned; a state value only gives retained UI-local state an invalidation
signal.

## Runtime design

1. Add a retained composition-group tree alongside the existing applier tree. A group owns its
   child/layer/remember cursors, nested groups, dirty flag, and prior inputs. It has no `LayoutNode`
   and cannot be painted, hit-tested, or found through semantics.
2. Each `recomposeScope` consumes one group position under its parent. When it runs, it compares
   its inputs, clears the previous read subscriptions, executes content under that group, then
   trims undeclared groups and unsubscribes them. When it skips, it advances its parent position but
   leaves the retained descendant applier nodes untouched.
3. A state read registers the currently executing group. A state write marks each subscribed group
   dirty. Registration is idempotent within one pass; writes that compare equal do nothing.
4. Group removal unsubscribes from every state. A removed dialog/list row must never retain a
   reference that keeps its old tree alive or later invalidates a different keyed row.
5. Keep the root unskippable in the first slice. Existing functions keep their current every-frame
   semantics until a caller introduces a scope around a proven hot, input-explicit subtree.

## Explicit non-goals

- AndroidX snapshot isolation, concurrent recomposition, mutation snapshots, or coroutine effects.
- Compiler-generated automatic restart groups or changed masks.
- Skipping layout, paint, input dispatch, or frame-clock advancement. A skipped composition still
  uses the retained tree's current layout and paint passes.
- Treating `FrameClock`, pointer input, density, or viewport locals as state reads. Their changing
  values are caller inputs and must make a scope run when it depends on them.
- Broad migration of every recipe. The first consumer is selected by an after/before Studio profile.

## Implementation sequence

1. **Done.** Add `ComposeFrameStats` with disabled-by-default group execution/skip counters and
   time spent in composition. Extend the Studio probe to print composition separately from layout
   and paint.
2. **Done.** Implement `MutableState<T>` and read/write subscription tests.
3. **Done.** Implement the retained group cursor and no-input scope. Prove a scope with no reads
   executes once while its parent continues to reconcile every frame.
4. **Done.** Add one/two-input overloads; prove a changed input re-executes exactly that scope and
   updates its retained tree.
5. **Done.** Prove invalidation, sibling isolation, group removal/unsubscription, nested dirty
   propagation, keyed reordering, and a two-frame click sequence. The click test guards the
   stateful-modifier regression.
6. **Done.** Apply one scope around the profile-selected Studio scene-picker chrome. Desktop
   and wasm both report 300 steady-state skips, and the real scene-runtime fast-click test still
   opens its menu.
7. **Done.** Update the parity matrix from **Gap** to **Subset**: explicit retained scopes, not compiler
   generated automatic skipping.

## Acceptance evidence

| Evidence | Required result |
|---|---|
| Runtime tests | Equal state writes do not invalidate; changed writes invalidate only readers; removed scopes unsubscribe |
| Scope tests | Stable inputs + no dirty state skip; changed input or state read re-executes; nested scope ownership is correct |
| UI regression tests | Existing pointer, focus, layout, semantics, and shadow tests stay green across frames |
| Studio probe | Reports composition time and group execution/skip counts before and after one real consumer |
| Browser probe | Same consumer runs under wasmJs; no desktop-only time claim is used as a cross-platform result |
| Frame correctness | Semantics and primitive lists match the unscoped scene for idle and state-change frames |

## Stop conditions

Stop after phases 1–5 if the Studio profile shows composition below a material share after the
retained engine's normal layout/paint work is separated. Do not spread a new scope API through the
design system merely because it exists. If a hot consumer cannot express every external dependency
as an input, leave it unscoped rather than ship stale UI.
