# 15 — Parity matrix against Compose

What this engine mimics, what it leaves out, and where it genuinely improves. **Every "Compose does"
claim here was checked against the artifacts in `~/.gradle/caches` (Compose Multiplatform 1.11.1),
not recalled.** Anything unchecked is marked `Unverified` rather than asserted — this page exists
because a confident-from-memory claim about `Modifier.styleable` turned out to be wrong.

## Read this first

**There is no comparative benchmark against Compose.** Not one number on this page says we are
faster than Compose, because nothing has measured that. The only measured figures are our own
allocation probe against `ui-core`. Any "better" below means *a defect class removed* or *a cost made
visible* — never *measured faster*.

Status vocabulary:

| Status | Means |
|---|---|
| **Parity** | Same shape, same semantics |
| **Subset** | Compose has more; we deliberately ship less for now |
| **Upgrade** | We do something Compose does not, with evidence for why |
| **Divergence** | Deliberately different, forced by a recorded constraint |
| **Gap** | Compose has it, we want it, not built |
| **Unverified** | Claim not yet checked against the jar |

## Runtime and composition — `01`, `03`

| Capability | Compose | Awake | Status | Remark |
|---|---|---|---|---|
| Calling convention | `@Composable` + compiler plugin | `context(_: Composer)` | **Divergence** | No plugin artifact, no Kotlin-version lock for consumers. Enforcement is equal: calling outside a composition is a compile error either way |
| Automatic skipping | `$changed` masks, subtree skipping | none | **Gap** | The plugin's one genuine unique value. A 60 fps loop redraws anyway; Stage 2 decides on measurement |
| Node identity | Compiler-generated keys | `(childIndex, nodeType)` counters | **Parity** | Same failure mode on reorder, same `key()` escape hatch |
| `key(value) { }` | Runtime function, scopes a group | Same | **Parity** | Verified by test: every sibling inside the block is keyed, not just the first |
| `remember` | Slot table | Slots on the node | **Divergence** | Slots die with the node. `ui-core`'s string-keyed store let entries outlive their widget and be adopted by the next writer |
| `remember(vararg keys)` | Present | Single-key only | **Subset** | A vararg allocates an array every pass. Add a two-key overload when something needs one |
| Snapshot state / read tracking | `mutableStateOf`, snapshots | none | **Gap** | Stage 2 |
| `CompositionLocal` | `compositionLocalOf`, `staticCompositionLocalOf` | `compositionLocalOf` only | **Subset** | The static variant is a recomposition optimisation, and there is no recomposition yet |
| Provider scoping | Fails closed on throw | Same, `try`/`finally` | **Parity** | Beats `ui-core`'s push/pop pair, which leaks its value on a throw |
| Effects (`LaunchedEffect`, `DisposableEffect`) | Present | Explicit non-goal | **Divergence** | Frame-driven, no coroutines. Node `onAttach`/`onDetach` covers disposal |

## Layout — `01`

| Capability | Compose | Awake | Status | Remark |
|---|---|---|---|---|
| Single-pass constraints | Yes | Yes | **Parity** | The whole point. Each child measured exactly once, proven at depth 8 |
| `Constraints` packing | Value class over `Long`, **variable** bit widths chosen per dimension | Value class over `Long`, fixed 16 bits per field | **Subset** | Compose can express a larger dimension when the other axis is small. Ours caps at 65534px; widen if a larger viewport exists |
| `Infinity` | `Int.MAX_VALUE` | `0xFFFF` sentinel | **Divergence** | Forced by fixed-width packing |
| Constraint arithmetic | `offset`, `constrain`, `constrainWidth/Height` | Same, **saturating** | **Upgrade** | `ui-core` shipped the overflow bug this prevents — `UNBOUNDED_MAIN_AXIS` poisoning `origin + extent` |
| `Measurable` / `IntrinsicMeasurable` | `measure` + `parentData` + 4 intrinsics | Same | **Parity** | Checked signature-for-signature |
| `MeasureScope.layout` | `layout(w, h, alignmentLines, block)` and a `Rulers` overload | `layout(w, h, block)` | **Subset** | No `AlignmentLine`, no `Rulers`. Text baseline alignment needs `AlignmentLine` eventually |
| `MeasurePolicy` intrinsic defaults | Present | Present | **Parity** | Ours default Box-shaped; Row/Column override to sum the main axis |
| `IntrinsicSize.Min/Max` | `foundation.layout` | Same package, same enum | **Parity** | |
| Intrinsic cost visibility | none | `UiLayoutStats.intrinsicQueries` | **Upgrade** | An extra subtree walk that is silent when unarmed is the class that ships wrong |

## Modifier — `02`

| Capability | Compose | Awake | Status | Remark |
|---|---|---|---|---|
| `Modifier` chain | `foldIn`, `foldOut`, `any`, `all`, `then` | `foldIn`, `then` | **Subset** | Nothing has needed the other three |
| `*ModifierNode` interfaces | `ui.node` | `ui.node` | **Parity** | Layout, Draw, ParentData, Semantics, PointerInput |
| `Modifier.Node` lifecycle | `ModifierNodeElement` splits the per-pass *element* from the retained *node*, with `create`/`update` | No split: a link is rebuilt every pass | **Divergence, with a cost** | Any state a link holds is lost at the next reconcile. It shipped one real bug: `clickable` remembered its own press, so a click spanning two frames never fired — every direct-dispatcher test passed because none of them re-reconciled mid-gesture. The gesture links are stateless now and the retained memory lives on the node or in a remembered `InteractionSource`. **A consumer writing a stateful modifier hits this**; the element/node split is the real fix |
| `Modifier.Node` `onAttach`/`onDetach` | Present | Designed, not built | **Gap** | `05-animation.md` depends on it |
| Draw links honour chain position | Yes | Yes | **Parity** | Was a defect until 2026-08-22: every draw link painted the node's outer bounds, so `padding().background()` and `background().padding()` emitted identical quads. Order being silently *meaningless* is worse than Compose's order being silently meaningful — there is no wrong result to notice. `ModifierOrderTest` pins it |
| Pointer links honour chain position | Yes | Yes | **Parity** | The same fix on the hit-testing side: `padding().clickable()` shrinks the hit target. `PointerOrderTest` pins it |
| Chain-order diagnostics | none | `ModifierChainDiagnostics` | **Gap, rescoped** | `11-refinements.md` proposed flagging clickable-after-padding and background-after-padding as *mistakes*. Both are now correct, meaningful orderings, so the diagnostic as written would be a false alarm. What it should flag instead is undecided — do not build it to the old spec |
| `graphicsLayer` | Real layers | Node properties composed down the tree | **Subset** | Nesting composes correctly, which `ui-core` documented that it does not. True layer semantics need render-to-texture — see `10-graphics-layer.md` |
| `alpha` | `ui.draw`, a real layer | `ui.draw`, per-primitive multiply | **Subset** | Composes when nested, which `ui-core` did not. Not a layer: two overlapping children under one dim double-darken where they overlap. An `emit()` of a primitive other than a quad is not dimmed |
| `scale` | `ui.draw` | not built | **Gap** | The primitives already carry a GPU-applied `UiPrimitiveTransform`, so this is per-primitive plumbing rather than new capability. Deferred with no consumer waiting — a half-transform that silently missed `emit()` would be worse than none |

## Styling — `04`

| Capability | Compose | Awake | Status | Remark |
|---|---|---|---|---|
| `Modifier.styleable` | **Exists**, `foundation.style`, experimental | Exists in `ui-designsystem` today, moves to `:compose:foundation` | **Parity** | Previously recorded as an Awake invention. It is convergent |
| `Style` state rules | `hovered`/`pressed`/`focused`/`disabled`/`selected`/`checked` | `Style.then` state-rule merges | **Parity** | Same collapse the 2026-08 audit asked for |
| `StyleState` | enabled/focused/hovered/pressed/selected/checked + typed extra keys | Same six, no typed keys | **Subset** | The six are what state rules branch on; typed keys are an extension point nothing needs yet |
| Style home | `foundation` | `:awake:compose:foundation` | **Parity, corrected** | Was recorded as `ui-designsystem`'s; Compose puts it at foundation level and this engine now agrees |
| `Modifier.styleable` resolution | A node with `create`/`update` | Resolved at build time into ordinary modifiers | **Divergence** | The state is known before the chain is assembled, so a style is a way of *writing* modifiers rather than a mechanism layout must understand. Cheaper, and it is why `ui-core`'s not-hovered guess disappears |
| Auto-clipping a rounded surface | No — `background(shape)` paints, `clip(shape)` clips | Same | **Parity, and `ui-core` is the outlier** | `ui-core`'s `surface` always emits `ClipPathPush`/`ClipPop`. Found by the differ: the `RoundedQuad` matched byte for byte and the clip pair did not |

## Interaction and gestures — `12`

| Capability | Compose | Awake | Status | Remark |
|---|---|---|---|---|
| Three pointer passes | `Initial`, `Main`, `Final` | Same three, same order | **Parity** | Verified name-for-name |
| Consumption | Flag on the change | Flag on the event | **Parity** | A later pass can see what an earlier one took |
| `InteractionSource` | `Flow<Interaction>`, read via `collectIsHoveredAsState()` | Sets, read as properties | **Divergence** | No coroutines. `tryEmit` is already non-suspending in Compose, so only the read side changes |
| Interactions as sets | Yes — two presses, one release, still pressed | Same | **Parity** | A boolean flag loses this and sticks a button in its pressed style |
| `hoverable` / `focusable` | `foundation` | Same package, Compose's parameter order | **Parity** | |
| Capture | By node | By node identity | **Upgrade** | `ui-core` keyed capture by id string and could collide |
| Focus identity | By node | By node | **Upgrade over `ui-core`, parity with Compose** | `UiRuntimeCoordinator` held a single `focusedId: String?`; two widgets could pick the same id and steal each other's focus silently |
| `FocusDirection` | `Next`, `Previous`, `Left`, `Right`, `Up`, `Down`, `Enter`, `Exit` | `Next`, `Previous` only | **Subset** | Spatial directions are *absent* rather than present-and-inert. An enum entry that silently does nothing is the failure class this repo keeps hitting |
| Focus traversal order | Derived from the tree | Depth-first placed order, wrapping | **Parity** | `ui-core` had no order at all, because it had no tree |
| Focus survives node removal | No | No — `FocusOwner.revalidate` | **Parity** | Same class as a stranded pointer capture |
| Modal focus trapping | A dialog bounds the ring | Layers extend the ring, do not bound it | **Gap** | Lands with `07-overlay-layering.md`'s modal work |
| `onFocusChanged` | Present | Present | **Parity** | Observes without joining the tab ring — `canFocus` is false, so a container that wants to know about focus does not become a Tab stop |
| `FocusRequester` | Present | Present | **Parity** | An object, not `ui-core`'s `requestFocus(id)` string, so a collision is impossible. Refuses rather than throwing when its node is not in this pass — a dialog's field does not exist until the dialog opens |
| `focusGroup` | Present | not built | **Gap** | |
| Capture release on removal | — | Ends when the node leaves the tree | **Upgrade** | Otherwise a reconcile mid-drag strands the pointer |
| Drag, scroll wheel, long-press, multi-touch | Present | `draggable` only | **Gap** | |

## Semantics — `13`

| Capability | Compose | Awake | Status | Remark |
|---|---|---|---|---|
| Typed property keys | `SemanticsPropertyKey<T>` | Same | **Parity** | Replaces `ui-core`'s flat 18-field class |
| `mergeDescendants` | Present | Present | **Parity** | Merging node wins on conflict |
| Traversal order | From the tree | From the tree | **Parity** | `ui-core` used emit order |

## Overlays and lists — `07`, `08`

| Capability | Compose | Awake | Status | Remark |
|---|---|---|---|---|
| Popups / dialogs | Separate composables over subcomposition | `Slot.Layers`, a second child list on every node | **Divergence** | A layer belongs to the node that declared it, which is what an anchored popup anchors to. Landed with the reconciler rather than retrofitted |
| Lazy lists | Present | not built | **Gap** | Stage 3 |

## Measured against `ui-core`, not against Compose

`:awake:ui:testing`'s `PrimitiveDiff` runs one scene through both engines and compares the
`UiDrawPrimitive` lists field by field. It lives there because neither engine may depend on the
other, and `ui:testing` already depends on `ui-core`; the compose dependency is test-only.

This is the check coverage cannot give. A line-coverage number says every line ran. The differ says
the replacement paints what the engine it replaces painted — or names which field of which primitive
moved. `clickable` sat at 100% line coverage while every two-frame click silently did nothing.

First results:

| Scene | Result |
|---|---|
| Single box | **Identical** |
| Row of two boxes | **Identical** |
| Stacked boxes, gap matched explicitly | **Identical** |
| Stacked boxes, default gap | Diverges by `y` only — `ui-core` defaults to an 8 dp column gap, this engine to Compose's zero. Recorded in `mirror-map.md`, classified as expected |

Every divergence has to be classified with a reason or the diff fails. Scenes are still small — a
box, a row, a column. The Checkout Form is the target and is not reachable until `04`'s `Style` and
`06`'s text land.

## Keeping this page honest

1. Before writing "Compose does X", check it: `unzip` the jar from `~/.gradle/caches`, `javap` the
   class. Declarations only — no bodies, ever. See the Provenance rule in `README.md`.
2. A row claiming **Upgrade** cites a shipped bug, a measured cost, or a documented `Diverges` row.
   No citation, no Upgrade.
3. A row claiming anything is *faster* cites a benchmark. There are none yet, so there are none.
