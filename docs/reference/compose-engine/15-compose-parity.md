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
| Automatic skipping | `$changed` masks, subtree skipping | None -- the whole tree composes every frame | **Divergence** | An explicit `recomposeScope` shipped and was removed 2026-08-29. Without compiler-generated restart boundaries a scope is placed by hand, and every value it reads has to be observable or the scope silently never re-runs: hover, focus and typing each shipped that bug. It also could not deliver the win, because invalidation had to dirty the whole ancestor chain to reach a nested scope -- there is no slot table to seek into. Measured over Studio's shell it executed 0 scopes and skipped 1 per frame |
| Node identity | Compiler-generated keys | `(childIndex, nodeType)` counters | **Parity** | Same failure mode on reorder, same `key()` escape hatch |
| `key(value) { }` | Runtime function, scopes a group | Same | **Parity** | Verified by test: every sibling inside the block is keyed, not just the first |
| `remember` | Slot table | Slots on the node | **Divergence** | Slots die with the node. `ui-core`'s string-keyed store let entries outlive their widget and be adopted by the next writer |
| `remember(vararg keys)` | Present | Zero-, one-, and two-key overloads | **Subset** | Awake deliberately avoids the allocating vararg form in its per-frame path; add a fixed-arity overload only when a real caller needs it |
| Snapshot state / read tracking | `mutableStateOf`, snapshots | None | **Divergence** | Removed with skipping: nothing observes because nothing skips. Retained state is a plain class held by `remember` (`ScrollState`, `TextFieldState`, `InteractionSource`). See `runtime/State.kt`, which is now only that rationale |
| `CompositionLocal` | `compositionLocalOf`, `staticCompositionLocalOf` | `compositionLocalOf` only | **Subset** | The static variant is a recomposition optimisation, and there is no recomposition |
| Phase separation | Composition, layout and draw read state independently; `BasicTextField` reads its text in layout/draw so typing never recomposes | Same discipline, enforced by hand | **Parity** | `TextMeasurePolicy` takes a `() -> String` rather than a captured string, so a keystroke reaches the glyphs through measure. Capturing at composition is what the deleted `TextFieldState.collectAsState` existed to work around |
| Provider scoping | Fails closed on throw | Same, `try`/`finally` | **Parity** | Beats `ui-core`'s push/pop pair, which leaks its value on a throw |
| Effects (`LaunchedEffect`, `DisposableEffect`) | Present | Explicit non-goal | **Divergence** | Frame-driven, no coroutines. Node `onAttach`/`onDetach` covers disposal |

## Layout — `01`

| Capability | Compose | Awake | Status | Remark |
|---|---|---|---|---|
| Single-pass constraints | Yes | Yes | **Parity** | The whole point. Each child measured exactly once, proven at depth 8 |
| Layout invalidation | Remeasure/relayout flags; a node whose constraints and inputs are unchanged is not measured again | None -- `layoutTree` measures, places and resolves the whole tree every frame | **Divergence** | Follows from composing every frame: with no dirty tracking above it there is nothing to tell layout what changed. Cost is O(tree) per frame regardless of what moved. Undocumented until 2026-08-29 |
| `Constraints` packing | Value class over `Long`, **variable** bit widths chosen per dimension | Value class over `Long`, fixed 16 bits per field | **Subset** | Compose can express a larger dimension when the other axis is small. Ours caps at 65534px; widen if a larger viewport exists |
| `Infinity` | `Int.MAX_VALUE` | `0xFFFF` sentinel | **Divergence** | Forced by fixed-width packing |
| Constraint arithmetic | `offset`, `constrain`, `constrainWidth/Height` | Same, **saturating** | **Upgrade** | `ui-core` shipped the overflow bug this prevents — `UNBOUNDED_MAIN_AXIS` poisoning `origin + extent` |
| `Measurable` / `IntrinsicMeasurable` | `measure` + `parentData` + 4 intrinsics | Same | **Parity** | Checked signature-for-signature |
| `MeasureScope.layout` | `layout(w, h, alignmentLines, block)` and a `Rulers` overload | `layout(w, h, alignmentLines, block)` with `FirstBaseline` and `LastBaseline` | **Subset** | `AlignmentLine`, `FirstBaseline`, `LastBaseline`, `alignBy`, and `alignByBaseline` built; `Rulers` deferred |
| `MeasurePolicy` intrinsic defaults | Present | Present | **Parity** | Ours default Box-shaped; Row/Column override to sum the main axis |
| `IntrinsicSize.Min/Max` | `foundation.layout` | Same package, same enum | **Parity** | |
| Intrinsic cost visibility | none | `UiLayoutStats.intrinsicQueries` | **Upgrade** | An extra subtree walk that is silent when unarmed is the class that ships wrong |
| `BoxWithConstraints` | Composes its content **with** its measured constraints, via `SubcomposeLayout` | `SubcomposeLayout` + `BoxWithConstraints` | **Parity** | Subcomposes during measurement pass; exposes `maxWidth`/`maxHeight`/`minWidth`/`minHeight` and `constraints` |
| Layout direction & RTL | `LayoutDirection.Ltr` / `LayoutDirection.Rtl`, `LocalLayoutDirection` | Same, with automatic relative placement mirroring in `PlacementScope.placeRelativeAt` vs `placeAbsoluteAt` | **Parity** | `offset` mirrors in RTL; `absoluteOffset` preserves coordinate placement |
| Viewport size in composition | `LocalWindowInfo` / `BoxWithConstraints` | `LocalViewportSize`, provided by the host | **Upgrade** | Known before composition and unchangeable by the tree, so it is provided rather than measured -- exact on the first frame, where anything recovered from layout would read zero |

## Modifier — `02`

| Capability | Compose | Awake | Status | Remark |
|---|---|---|---|---|
| `Modifier` chain | `foldIn`, `foldOut`, `any`, `all`, `then` | `foldIn`, `foldOut`, `any`, `all`, `then` | **Parity** | The complete traversal/query surface is available |
| `*ModifierNode` interfaces | `ui.node` | `ui.node` | **Parity** | Layout, Draw, ParentData, Semantics, PointerInput |
| `Modifier.Node` lifecycle | `ModifierNodeElement` splits the per-pass *element* from the retained *node*, with `create`/`update` | `ModifierNodeElement` and retained `Modifier.Node` are reconciled by chain position | **Subset** | The core preserves node identity and calls `update`; `Modifier.Node` cannot enter a chain directly. See `2026-08-27-compose-modifier-node-lifecycle-plan.md`. |
| `Modifier.Node` `onAttach`/`onDetach` | Present | Present for `Modifier.Node` | **Subset** | Exact attachment, replacement, modifier removal, and subtree removal are pinned by `ModifierNodeLifecycleTest`. |
| Draw links honour chain position | Yes | Yes | **Parity** | Was a defect until 2026-08-22: every draw link painted the node's outer bounds, so `padding().background()` and `background().padding()` emitted identical quads. Order being silently *meaningless* is worse than Compose's order being silently meaningful — there is no wrong result to notice. `ModifierOrderTest` pins it |
| Pointer links honour chain position | Yes | Yes | **Parity** | The same fix on the hit-testing side: `padding().clickable()` shrinks the hit target. `PointerOrderTest` pins it |
| Chain-order diagnostics | none | `ModifierChainDiagnostics` | **Gap, rescoped** | `11-refinements.md` proposed flagging clickable-after-padding and background-after-padding as *mistakes*. Both are now correct, meaningful orderings, so the diagnostic as written would be a false alarm. What it should flag instead is undecided — do not build it to the old spec |
| `graphicsLayer` | Real offscreen layer isolation and transform | `Modifier.graphicsLayer(alpha/scale/translation/rotation/blendMode/renderEffect/shadowElevation/shape = …)` plus the renderer-aware host compositor | **Subset** | Vulkan and WebGPU target rendering, nested paint-order composition, alpha, transforms, verified `SourceOver`/`Plus`/`Screen`/`Overlay`, expanded nine-tap blur, and rectangle/uniform-rounded or generic-path solid elevation land. Other destination-colour modes and generic-shadow gradients/spread remain missing — see `10-graphics-layer.md` |
| `alpha` | `ui.draw`, a real layer | `ui.draw`, per-primitive multiply | **Subset** | Composes when nested, which `ui-core` did not. Not a layer: two overlapping children under one dim double-darken where they overlap. All colour-bearing primitives, including `emit()` output, are dimmed; textures have opaque material data and remain the documented exception |
| `scale` | `ui.draw` | `ui.draw`, CPU affine at emission | **Parity** | `PaintScope` maps every primitive kind, including paths, gradients, and shadows; nesting composes multiplicatively |
| `dropShadow(shape, shadow)` | Node-local shadow painter | Rectangle/uniform-rounded caster with solid or four-corner linear-gradient brush; generic path-mask caster with solid zero-spread shadow | **Subset** | It preserves node-local placement, scale, alpha, and modifier order. Generic paths route through a padded target and nine-tap blur, pixel-verified on Vulkan and desktop WebGPU. Inner shadows, generic gradients/spread, arbitrary brush coordinate spaces, and blend modes remain absent. |
| `DrawScope` stroke/outline drawing | `drawRoundRect(style = Stroke(width))`, `drawOutline(outline, style = Stroke(width))`, native path stroking | `DrawScope.drawStrokedPath(path, stroke, color)` via `DrawCommand.StrokedPath`/`DrawPath.tessellateStroke` | **Built, 2026-08-25** | Was a real gap — `Modifier.border` faked a rounded outline by stacking filled `RoundedQuad`s (broken at corners; shipped, found by rendering the actual PNG). Fixed by routing through this pipeline, which already existed for icon glyphs (`ShadcnIcon.kt`) and needed zero new backend/shader code on either Vulkan or WebGPU. See `awake-render-pipeline/SKILL.md` §6 |
| `Shape` (`RectangleShape`, `RoundedCornerShape`, `CircleShape`, ...) | A shared type `background`/`border`/`clip` all take | `Shape`/`ShapeOutline` in `compose:ui`; shape-aware `background`/`border`/`clip` | **Built, 2026-08-25** | Rectangle and uniform rounded shapes preserve `Quad`/`RoundedQuad`; independent corners and circles use the existing path pipeline. `RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)` supplies the joined-control case. `clip(shape)` emits the existing balanced path-clip primitives; `clipToBounds()` remains the rectangle fast alias. |
| Partial border sides | No direct Foundation `BorderStroke` equivalent; custom draw or CSS border-side rules | `BorderSides(top, end, bottom, start)`, `Modifier.border(..., sides)` | **Parity** | Independent per-side border stroke control for joined controls like `ButtonGroup`, segmented tabs, and card dividers |
| `Modifier.zIndex` | `Modifier.zIndex(float)` | `Modifier.zIndex(float)` | **Parity** | Sibling visual paint ordering and pointer hit-testing prioritization with stable tie-breaking on equal z-indices |
| `Modifier.drawWithCache` | `drawWithCache { onDrawBehind / onDrawWithContent }` | `Modifier.drawWithCache` with `CacheDrawScope` | **Parity** | Re-evaluates cache lambda only on dimensions, density, or layout direction changes; zero per-frame path or allocation rebuilds |

## Styling — `04`

| Capability | Compose | Awake | Status | Remark |
|---|---|---|---|---|
| `Modifier.styleable` | **Exists**, `foundation.style`, experimental | Exists in `ui-shadcn` today, moves to `:compose:foundation` | **Parity** | Previously recorded as an Awake invention. It is convergent |
| `Style` state rules | `hovered`/`pressed`/`focused`/`disabled`/`selected`/`checked` | `Style.then` state-rule merges | **Parity** | Same collapse the 2026-08 audit asked for |
| `StyleState` | enabled/focused/hovered/pressed/selected/checked + typed extra keys | Same six, no typed keys | **Subset** | The six are what state rules branch on; typed keys are an extension point nothing needs yet |
| Style home | `foundation` | `:awake:compose:foundation` | **Parity, corrected** | Was recorded as `ui-shadcn`'s; Compose puts it at foundation level and this engine now agrees |
| `Modifier.styleable` resolution | A node with `create`/`update` | Resolved at build time into ordinary modifiers | **Divergence** | The state is known before the chain is assembled, so a style is a way of *writing* modifiers rather than a mechanism layout must understand. Cheaper, and it is why `ui-core`'s not-hovered guess disappears |
| Auto-clipping a rounded surface | No — `background(shape)` paints, `clip(shape)` clips | Same | **Parity, and `ui-core` is the outlier** | `ui-core`'s `surface` always emits `ClipPathPush`/`ClipPop`. Found by the differ: the `RoundedQuad` matched byte for byte and the clip pair did not |

## Text and typography

| Capability | Compose | Awake | Status | Remark |
|---|---|---|---|---|
| `FontFamily` face selection | A family contains `Font` entries keyed by weight; the resolver selects the closest available face | `WeightedUiFont` combines weighted `UiFont` atlases and resolves the closest face | **Parity** | `UiFonts.default()` ships Roboto Thin/Light/Regular/Medium/SemiBold/Bold/Black; intermediate weights use deterministic nearest-face selection |
| `TextStyle.fontWeight` | `Text` measurement and painting use the resolved weight | `TextStyle.weight` flows through measurement, wrapping, caret offsets, and glyph UV selection | **Parity** | A weight is not paint-only metadata; it participates in intrinsic geometry and raster face selection |
| Custom weighted family | `FontFamily(...)` accepts caller-provided faces | `UiFonts.family(mapOf(FontWeight to UiFont))` creates one combined atlas | **Subset** | Faces must share glyph coordinate space and sampling mode; platform font loading is outside this retained engine |
| `BasicTextField(singleLine = true)` | Single-line fields use a single line box and horizontal overflow behavior; decorated fields center that line box inside their fixed control height | `BasicTextField(singleLine = true)` uses an unwrapped run, keeps the caret visible in the horizontal viewport, centers the authored line box, and sizes the caret to that line; multiline fields remain top-aligned | **Parity for the supported model** | Covered by `TextFieldAlignmentTest`; shadcn input uses `singleLine = true`, while textarea keeps multiline top alignment. See [Android BasicTextField](https://developer.android.com/reference/kotlin/androidx/compose/foundation/text/BasicTextField.composable) |

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
| `FocusDirection` | `Next`, `Previous`, `Left`, `Right`, `Up`, `Down`, `Enter`, `Exit` | `Next`, `Previous`, `Left`, `Right`, `Up`, `Down` | **Parity** | Depth-first 1D tab ring traversal for `Next`/`Previous` and geometric 2D beam search with arrow-key dispatch for `Left`/`Right`/`Up`/`Down` |
| `focusProperties` | `up`, `down`, `left`, `right`, `canFocus` | Same | **Parity** | Explicit directional navigation overrides and focusability control via `Modifier.focusProperties` |
| Focus traversal order | Derived from the tree | Depth-first placed order, wrapping | **Parity** | `ui-core` had no order at all, because it had no tree |
| Focus survives node removal | No | No — `FocusOwner.revalidate` | **Parity** | Same class as a stranded pointer capture |
| Modal focus trapping | A dialog bounds the ring | A modal layer bounds the ring | **Parity** | `ActiveModal` is shared by focus and pointer dispatch, so an opened dialog cannot leave focus behind it |
| `onFocusChanged` | Present | Present | **Parity** | Observes without joining the tab ring — `canFocus` is false, so a container that wants to know about focus does not become a Tab stop |
| `FocusRequester` | Present | Present | **Parity** | An object, not `ui-core`'s `requestFocus(id)` string, so a collision is impossible. Refuses rather than throwing when its node is not in this pass — a dialog's field does not exist until the dialog opens |
| `focusGroup` | Present | Present | **Parity for sequential and spatial traversal** | Depth-first ring and spatial 2D beam search respect focus group boundaries. |
| Capture release on removal | — | Ends when the node leaves the tree | **Upgrade** | Otherwise a reconcile mid-drag strands the pointer |
| Drag, scroll wheel, long-press, multi-touch, transform | Present | Present | **Parity** | Concurrent contact tracking via `FrameInput.pointers`, multi-pointer pinch-to-zoom/pan/rotation via `Modifier.transformable` |
| Nested scroll | `nestedScroll(connection, dispatcher)` | `nestedScroll(connection, dispatcher)` | **Parity** | Pre-scroll, post-scroll delta dispatching up the ancestor layout tree |

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
| Lazy lists | Present | `LazyColumn`, measure-time windowing via `SubcomposeLayout` | **Parity** | Subcomposes items during measurement pass to fill the viewport |

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
