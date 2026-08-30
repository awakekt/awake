# 17 — Modifier parity

**Goal: every stable Compose `Modifier` extension, or a recorded reason it does not apply here.**

Enumerated from the jars with `javap`, not from memory — `ui-desktop 1.9.3`, `foundation-desktop
1.9.3`, `foundation-layout-desktop 1.9.3` in `~/.gradle/caches`. `javap` prints declarations only,
never bodies; see the README's provenance section.

Internal and platform-only entries are excluded and listed at the bottom, so "missing" here means
missing on purpose or missing as work.

## Have

`alpha` · `background` · `border` · `clickable` · `clip` · `draggable` · `drawBehind` ·
`dropShadow` ·
`fillMaxHeight` · `fillMaxSize` · `fillMaxWidth` · `focusRequester` · `focusable` · `height` ·
`hoverable` · `onFocusChanged` · `onPlaced` · `padding` · `semantics` · `size` · `testTag` ·
`verticalScroll` · `weight` · `width` · `wrapContentWidth` · `wrapContentHeight` ·
`wrapContentSize`

Added 2026-08-23: `offset` · `aspectRatio` · `widthIn` · `heightIn` · `sizeIn` · `requiredWidth` ·
`requiredHeight` · `requiredSize` · `defaultMinSize` · `scale` · `drawWithContent` ·
`clipToBounds` (renamed from `clip`) ·
`horizontalScroll` · `toggleable` · `selectable` · `selectableGroup` ·
`onSizeChanged` · `layout {}` · `layoutId` · `clearAndSetSemantics` · `focusTarget` ·
`onKeyEvent` · `onPreviewKeyEvent`

The core `Modifier` chain also provides Compose's `foldIn`, `foldOut`, `any`, and `all` operations.

Awake-only: `styleable` — see `04-styling-theme.md`. Convergent with Compose's own experimental
`foundation.style`, not an invention.

## Missing, ordered by what unblocks work

### Batch 1 — layout (done 2026-08-23)

Landed above. Not shipped, with reasons:

| Not shipped | Why |
|---|---|
| `absoluteOffset` | Identical to `offset` until RTL is settled (`14-density-resize.md`). Shipping both today means one silently changes behaviour the day it is. Absent beats inert |
| `wrapContentWidth` / `wrapContentHeight` / `wrapContentSize` | **Done 2026-08-26.** The child measures with relaxed minimums and is aligned in the parent-constrained reported size; `unbounded` deliberately permits child overflow |
| `fillMaxWidth(fraction)` | **Done 2026-08-26.** `fillMaxWidth`, `fillMaxHeight`, and `fillMaxSize` accept fractions in `(0, 1]`, matching Compose's bounded-axis sizing rule |

### Batch 2 — draw and transform (partly done 2026-08-23)

| Modifier | Verdict |
|---|---|
| `scale` | **Done 2026-08-23.** A CPU-side affine in `PaintScope`, not the GPU `DrawTransform` -- only four of the nine primitive kinds carry that field, so a gradient or path under a scaled subtree would silently not scale. Nesting composes, closing a divergence `ui-core` documents about itself |
| `clipToBounds` | **Done 2026-08-23.** Rectangle fast alias. Shape-aware `clip(shape)` is also built through `Shape`/`ShapeOutline`; see `15-compose-parity.md` |
| `drawWithContent` | **Done 2026-08-23.** With `ContentDrawScope`, so a modifier can paint on both sides of its content |
| `rotate` | **Blocked**, and this page previously said otherwise. `DrawTransform` carries `scaleX/scaleY/pivotX/pivotY` and nothing else; rotation is out of scope by design in `docs/tasks/2026-08-02-graphicslayer-rotation-scale.md` and needs a wider vertex layout plus shader changes across four shaders on both backends |
| `zIndex` | **Deferred.** Needs a per-parent z-sort: sorting every frame allocates, and caching it needs invalidation. No blocked recipe needs it -- `Layer` already covers overlay ordering |
| `drawWithCache` | **Defer.** Caching across frames is `cacheKey`'s silent-staleness class — see `awake-ui-performance`. Needs a design, not an afternoon |
| `graphicsLayer` | **Subset.** `Modifier.graphicsLayer(alpha/scale/translation/rotation/blendMode/renderEffect/shadowElevation/shape = …)` isolates a subtree and applies alpha/transforms once at texture composite time on Vulkan and WebGPU. Blur, rectangle/uniform-rounded and generic-path solid elevation, and `SourceOver`/`Plus`/`Screen`/`Overlay` are built; generic gradients/spread and remaining destination modes are not — see `10-graphics-layer.md` |
| `dropShadow` | **Done, subset.** Rectangle/uniform-rounded shapes support solid and four-corner gradients; generic shapes support a solid zero-spread path mask through target blur. `innerShadow`, generic gradients, and generic spread remain absent. |
| `paint`, elevation `shadow`, `innerShadow` | **Blocked** on render-to-texture and, for generic shadows, shape-mask rendering |

### Batch 3 — input (done 2026-08-23)

| Modifier | Verdict |
|---|---|
| `horizontalScroll` | **Done 2026-08-23.** One `ScrollNode` parameterised by `Orientation`, not a second copy |
| `toggleable`, `selectable`, `selectableGroup` | **Done 2026-08-23.** Value in, change out, state reported to semantics as well as the callback |
| `pointerInput` | **N/A.** Compose's version is a coroutine API -- `awaitPointerEventScope` inside a `suspend` block -- and coroutines are an explicit non-goal here. Implementing `PointerInputNode` is the equivalent and is already public |
| `scrollable` | **Deferred.** Compose's is gesture-only, without the layout and clip the two axis helpers already carry. No caller needs the split |
| `onClick`, `onDrag` | **N/A.** Compose-desktop only, keyed on a `PointerMatcher` for specific mouse buttons. `clickable` and `draggable` cover the behaviour. Input *does* carry button identity as of 2026-08-30 (`PointerButton`, `InputSnapshot.buttonsDown`), but on the snapshot rather than as new `PointerEventType`s -- the consumer that wanted it is the viewport camera, which reads the snapshot directly, and adding press types would risk the property `SecondaryPress` exists for |
| `transformable` | **Defer.** Pointer capture supports concurrent contacts, but platform touch adaptation and a transform contract are not yet designed |
| `nestedScroll` | **Defer.** Wants a design; no blocked recipe needs it |
| `indication` | **N/A.** Compose's ripple abstraction. Styling here goes through `Style`/`InteractionSource` |
| `pointerHoverIcon` | **Defer.** Needs a platform cursor API that does not exist yet |

### Batch 4 — focus, position, semantics (done 2026-08-23)

| Modifier | Verdict |
|---|---|
| `onSizeChanged` | **Done 2026-08-23.** Fires on change, where `onPlaced` fires every pass |
| `layout { }` | **Done 2026-08-23.** Built on `LayoutModifierNode`, which was already the shape |
| `layoutId` | **Done 2026-08-23.** A `ParentDataModifierNode`, read via `Measurable.layoutId` |
| `clearAndSetSemantics` | **Done 2026-08-23.** `SemanticsConfiguration.isClearingDescendants`, honoured in `SemanticsTreeBuilder.collect` |
| `focusTarget` | **Done 2026-08-23.** The `:ui` primitive `foundation`'s `focusable` sits on, as in Compose |
| `onGloballyPositioned` | **N/A.** Differs from `onPlaced` by reporting `LayoutCoordinates`, which this engine has no equivalent of. Without that it is a second spelling of `onPlaced` |
| `onKeyEvent`, `onPreviewKeyEvent` | **Done 2026-08-23.** `FrameInput.keyEvents` is a third channel beside `typedText` and `editCommands`; `KeyInputDispatcher` routes it along the focus path, Preview root-to-focused then Main focused-to-root |
| `focusGroup`, `focusProperties`, `onFocusEvent` | **Deferred.** `FocusOwner.collectFocusable` builds one flat ring; grouping and explicit ordering both change that traversal, and no blocked recipe needs them |
| `focusRestorer` | **Deferred.** Wants saved state |
| `composed` | **N/A.** Exists so a modifier can hold composition-scoped state. This engine rebuilds the chain every frame and has no such scope — `remember` at the call site is the equivalent, and the frame clock already works that way |
| `approachLayout`, `onFirstVisible`, `onVisibilityChanged`, `onLayoutRectChanged` | **Defer.** No blocked recipe needs them |

## Excluded as platform or internal

Not counted as gaps. Window insets (`imePadding`, `statusBarsPadding`, `navigationBarsPadding`,
`systemBarsPadding`, `safeDrawingPadding`, `displayCutoutPadding`, `waterfallPadding`,
`captionBarPadding`, `windowInsets*`, `consumeWindowInsets`, `recalculateWindowInsets`) — an Android
window concept with no analogue in a game surface. Text-field internals (`textField*`,
`tapPressTextFieldModifier`, `legacyTextInputAdapter`, `heightInLines`, `cursor`,
`selectionMagnifier`, `drawSelectionHandle`, `updateSelectionTouchMode`) — private to Compose's own
`BasicTextField`. Context menus (`*ContextMenu*`), lazy-layout internals (`lazyLayout*`,
`traversablePrefetchState`), tooling (`inspectable*`, `toolingGraphicsLayer`,
`trackInteropPlacement`), Android/iOS-specific (`pointerInteropFilter`, `cupertino*`, `stylus*`,
`*RotaryScrollEvent`, `*IndirectTouchEvent`, `keepScreenOn`, `sensitiveContent`,
`preferredFrameRate`, `interceptDPadAndMoveFocus`), drag-and-drop (`dragAndDrop*`), and
`bringIntoView*`/`overscroll`/`anchoredDraggable` which want scroll infrastructure this engine has
not designed.

## Rules

**A batch lands with tests, or it does not land.**

**A verdict cites the symbol that makes it true.** This page's enumeration was verified with `javap`;
its verdicts, at first, were not — and five of them were wrong, each discovered only by trying to
implement it. `rotate` was called "arithmetic on primitive coordinates" without reading
`DrawTransform`, which carries scale and pivot and no rotation. `zIndex` was called cheap without
reading `Painter.paintChildren`, which would need a per-parent sort. `pointerInput` was called the
large one when it is a coroutine API this engine has no equivalent of.

So: `Add` names what it will be built on, `Defer` and `N/A` name what makes them so. This is
`11-refinements.md`'s existing "no evidence, no row" applied one page over.

**Verdict the batch you are about to do, not the one after.** Batch 1's verdicts held because they
were written and executed the same hour. Batches 2 and 3 were verdicted well ahead, and that is
where every correction landed. Enumerating the whole surface is cheap and checkable; judging it is
neither.

## What the corrections were worth

Being wrong produced the two most useful findings on this page. "No offscreen render-target support
anywhere in `awake/render`" was false, and correcting it showed `graphicsLayer` is **one named piece
of backend work away** — `drawUi` accepting a `RenderTarget` — rather than the open problem it was
filed as. And implementing batch 3 found that `draggable` produced no drag at all in a live frame
loop, with a green test suite, because it held state on a modifier instance.

That second one is a class, not an incident: `clickable` had it before. Both are now stateless, and
an audit of every `*ModifierNode` in `:ui` and `:foundation` finds no third. The durable guard is
`GestureAcrossFramesTest` — direct dispatch cannot see this bug, because a modifier instance
survives a test that never reconciles and never survives an app that does.
