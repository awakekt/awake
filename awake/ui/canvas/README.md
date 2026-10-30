# `awake:ui:canvas`

The screen-anchored game UI capability: text, panels, bars, buttons and the rest of a HUD, as a
flat list of elements drawn over whatever is beneath. No `awake:scene` dependency and no ECS: a
host supplies the elements and answers a [`CanvasResolver`][resolver] for the few things it alone
knows, such as where a followed point lands or what a camera sees.

Not to be confused with Compose's `Canvas` (a surface you draw pixels on) or
`awake:ui:node-graph-canvas` (the graph editor). For the scene document form of this capability,
the `canvas_element` component saved with a scene and drawn by `SceneCanvas(world)`, see
`awake:scene:canvas`.

[resolver]: src/commonMain/kotlin/com/awakekt/awake/ui/canvas/CanvasTree.kt

## Components

An element is made of focused components, one of each type, which `CanvasElement` holds together:

| Component | What it holds |
|---|---|
| `CanvasRect` (always) | anchor, offsets, size, order, visibility, touch-only, safe area, grow, element and control ids; the `screenBounds` it was drawn at |
| `CanvasText` | the words: text or key and arguments, font, size, alignment, links, numerals |
| `CanvasAppearance` | colour, background, `CanvasStyle` and a control's skin |
| `CanvasLayout` or `CanvasCluster` | how it places its children |
| `CanvasBinding` | game state it shows without code |
| `CanvasFollow`, `CanvasFollowArrow` | the node it follows, and the arrow of a follower pinned to the edge |
| `CanvasInput`, `CanvasTooltip` | pressable, draggable, drop target, wheel; a hover hint |
| one `CanvasWidget` | `CanvasButtonWidget`, `CanvasBarWidget`, `CanvasSweepWidget`, `CanvasStepperWidget`, `CanvasJoystickWidget`, `CanvasTextFieldWidget`, `CanvasViewWidget`, `CanvasLookAreaWidget` or `CanvasImageWidget`: its own values and its `settings` |
| `CanvasPointerState` (always) | what the player is doing to it right now: taps, hover, drag and drop, wheel, links, focus, stick, look and aim. Runtime only, never saved |

Set one with `element.set(component)` and read it with `element.get<CanvasText>()`. The element's
`kind` follows from its components: its widget's, else `Text` when it has words, else `Panel`.
Setting `kind` gives it that kind's widget.

The flat properties described below, such as `width`, `text` or `value`, read and write those
components. Each reads its default while its component is absent. Writing another value adds the
component, but never adds a widget to an element that has a different one, and never gives a Panel
words, so an option set on the wrong kind still does nothing.

## An element

`CanvasElement` has:

- **`kind`**: `Text`, `Panel`, `Bar`, `Button`, `TextField`, `Joystick`, `Image`, `View` or `LookArea`.
- **`anchor`**: one of nine screen points (`TopLeft` … `BottomRight`). The element sits against that
  point, and **`offsetX` / `offsetY`** move it inward: a right or bottom anchor moves it left or up,
  and a negative offset at an edge is held there. On a centred axis an offset moves it right or down
  of centre, and a negative one left or up.

  A centred element lands on whole pixels of its box, the canvas or the element it is drawn in. Its
  size and twice its offset are each rounded to whole pixels, halves up, the doubled offset before
  the element when positive and after it when negative. The box's spare pixels around the two are
  halved and rounded down, so an odd one goes after them: in a box 101 pixels wide, a 50-pixel
  element sits 25 pixels from the left and 26 from the right, half a pixel left of the exact
  centre. A host that works out where a centred element lands, such as an editor catching it on a
  guide, rounds the same way.
- **`width` / `height`** in dp.
- **`text`** (Text content, Button label), **`fontSize`**, **`color`** (text, or a Bar's fill) and
  **`background`**, as `#RRGGBB` or `#RRGGBBAA`.
- **`textKey`**: a Text's or Button's words by key, looked up in the `Strings` passed as
  `CanvasOverlay(strings = …)` in the active locale, with **`textArgs`** in its placeholders; a
  locale switch shows on the next frame. Without strings, or for a key no table has, the key shows.
- **`placeholder`**: a TextField's hint, a text key shown in muted text (the field's colour at half
  strength) while the field is empty and does not hold the keyboard. **`padding`**: the space between
  a TextField's edge and its text, `horizontal` 12 dp (shadcn's input) and `vertical` 0 by default;
  caret, selection and scrolling work inside it, and the field clips at its edge, so a leading `j`'s
  tail and an `Å`'s ring keep their ink. **`tooltip`**: a text key for a hint any element shows near
  the pointer after 0.5 s over it, kept inside the canvas, gone when the pointer leaves or presses.
- **`font`**: a project `.ttf` or `.otf` file, or a built-in family, `default` or `bitmap`; empty
  is the default Roboto. A host loads the file and passes it in `CanvasOverlay(fonts = …)`.
- **`numerals`**: `FontNumerals.Tabular` (equal-width digits for counters, timers and score tables,
  via OpenType `tnum`) or `FontNumerals.Proportional` (`pnum`); null keeps the font's default digits.
- **`value`**: a Bar's or a Sweep's fill from 0 to 1.
- **`bar`**, **`sweep`**, **`stepper`**: a Bar as a progress bar (`CanvasBar`: up, down, right to
  left or radial, a trailing fill, segments, ticks and a label), a Sweep's pie or ring
  (`CanvasSweep`: start angle, direction, mask, ring and track; an action button's cooldown is the
  same drawing), and a Stepper's number (`CanvasStepper`: range, step, decimals and how a held
  button repeats). A Stepper's live `number` changes by a press, a hold, its keys or `stepBy`, and
  `consumeChange()` reports it. The trail, the sweep's geometry and the stepping are shared with
  `ui:shadcn`'s `ShadcnProgressBar`, `ShadcnRadialSweep` and `ShadcnNumberStepper`, through
  `TrailingValue`, `sweepPath` and `NumberStepping`.
- **`order`**: lower draws first. Of the same order, the later in `CanvasOverlay`'s `items` draws on
  top and takes the pointer; for a scene that is the later in the hierarchy. **`visible`** hides it.
  `canvasDrawOrder(items)` lists the items in the order they draw, and `canvasItemAt(items, x, y)`
  finds the one last drawn on top at a point, so a host that picks from the canvas, such as an
  editor, follows the overlay's own rule rather than a copy of it.
- **`style`**: how it looks beyond its colours, as the saved form of a Compose `Style` drawn
  through `Modifier.styleable`: fill, gradient, corner radius, border, shadow, text colour, alpha,
  `hovered`, `pressed` and `disabled` states (`CanvasStateStyle`: fill, gradient, image, border and
  text colour, `shadow`, centre `scale` without moving layout or hit-testing, and `alpha`),
  `transitionSeconds` (interpolated between states, falling back to
  `CanvasControlTheme.transitionSeconds` and snapping at `0`), and images. `style.image` is an Image's picture, the
  frame of a Panel, Button or Text, or a Bar's track; `style.fillImage` is a Bar's fill, cut at
  `value`, or with `style.fillImageMode` `Squeeze` drawn at the filled width with both end caps kept,
  `Capped` with both caps whole at every value, or `Sweep` swept clockwise over the box.
  A `CanvasImage` names a project file, the region of it to use and the slice insets that
  keep its corners whole. A host decodes the images and passes them in `CanvasOverlay(images = …)`.

  `CanvasGradient(start = "#FF0000", end = "#0000FF", middle = "#00FF00")` runs through
  green at 50%, top to bottom (`horizontal = true` runs left to right). `middle` is optional;
  omitting it retains the existing two-colour fill and JSON. All three colours accept
  `#RRGGBB`/`#RRGGBBAA`, or theme tokens in a control skin. State styles use the same gradient.
  The midpoint follows opacity, transforms and the element's rounded clip. It uses the shared
  background brush and coloured-mesh path on CPU, Vulkan and WebGPU, with no canvas-specific shader.

## Drawing

`CanvasOverlay(items, resolver = …)` draws a flat list of `CanvasItem(id, element, parent, scaler)`:
each item's `parent`, when it is also in the list, is the nearest element above it, so the child draws
anchored and offset inside the parent's box, and an optional `scaler` scales only that item and what
sits inside it from the canvas's box. A host that keeps elements in an ECS world, a plain
list or anything else builds this list its own way; `awake:scene:canvas`'s `SceneCanvas(world)`
builds it from a `World`.

A `CanvasResolver` answers the few things only the host knows: where a `follow`ed node's point or
box lands in the frame, what a `View`'s camera sees, and a bound field's text. Every member
answers null by default, so a host that implements none of it still draws every element, minus
that one thing.

## A 3D view

A `View` shows what the camera named by its `camera` field sees, filling its box under its
children, through `CanvasResolver.texture(element, camera, width, height)`. Without a resolver
that answers it a `View` draws only its style.

`element.view = CanvasView(transparent = true, mask = CanvasMask.Circle)` opts into a
transparent camera picture and a circular outline. A `Box` mask uses `style.cornerRadius`;
`Pill` and `CutCorner` use the same shapes as a Sweep. The mask clips the camera picture,
leaving the element's own background and overlay children as authored. Explicit ancestor
clips also apply. The resolver supplies a premultiplied texture: a transparent camera target
must clear to transparent and omit its sky. The default stays opaque with its black backing.

An element that `follow`s a node is placed by `CanvasResolver.point`/`bounds`, which report where
the node lands in the frame's pixels. `CanvasOverlay` moves that into its own box by where the box
sat in the frame when it was last placed, so a canvas drawn over part of the window lands on what
it follows from its second frame; one filling the window does from its first.

## Many followers

A follower's `followRules` (`CanvasFollowRules`) are data on the element. A follower is left out
while its box is wholly off the canvas (`cullOffScreen`) or its point is behind the camera, beyond
`maxDistance`, or occluded (`occlusion`, asked of `CanvasResolver.occluded`). `scale` and `fade` are
`CanvasCurve`s by distance, and `pinToEdge` holds it to the canvas's edge, `edgeMargin` in, toward a
target off screen or behind, reporting `pinned` and `pinAngle`. A `followArrow` child points to the
target while its follower is pinned. `CanvasOverlay(followLimits = listOf(CanvasFollowLimit(…)))`
draws at most `max` of a `group`, ranked by distance, `priority` or the `followScore` code sets.

Every follower is placed in one pass before any is composed, each projected once through
`CanvasResolver.project`, which reports a `CanvasProjection`: the point, its view depth, its
distance and whether it is behind the camera. A resolver that answers only `point` still works, at
no known distance, with a point behind the camera hidden. The overlay keys each element's
composition by the element object, so a host that keeps its elements and hides the ones not in use,
rather than making new ones, reuses what was composed for them.

## Nesting and scale

An item whose `parent` is also in the list is drawn inside it, anchored and offset within the
parent's box, and moves and hides with it. `CanvasOverlay(items, scale = 2f)` draws everything
twice the size, with every edge on a whole pixel.

`CanvasOverlay(items, scaler = CanvasScaler(…))` works the scale out from the canvas's box each
frame instead, measured before anything in it is composed: `ScaleWithScreenSize` from a reference
size and a `match` between following the width and the height, `ConstantPixelSize` from pixels per
unit, or `ConstantPhysicalSize` from the dots per inch the canvas is designed at. `minScale` and
`maxScale` hold the result, and `playerScale` multiplies it after them. `CanvasScaler.scaleFor(width,
height, density)` is the whole rule, for a host that wants the number itself. It is serializable;
`awake:scene:canvas` saves it with a scene as `canvas_scaler`.

A `CanvasScaler` on a `CanvasItem` (`CanvasScaleScope.Subtree` in a scene, vs `CanvasScaleScope.Canvas`,
the default) applies the same rule from the canvas's host box to only that item and the items inside
it, while sibling subtrees keep the canvas's own scale (or a `ConstantPixelSize` subtree stays `1:1`
inside a scaled canvas). Layout, clipping, hit-testing, drag-and-drop and follower placement in that
subtree use its scaled coordinates, and `snap` rounds that subtree's scale on its own.

Text is crisp at a fractional scale without help: a text element's em is rounded to whole device
pixels after the scale and the density, so up to 24 pixels it draws from exact-size glyphs (13 at
1.4125 is an 18 pixel glyph, 2% under the exact size) and its layout box keeps the size the scale
gives it. `CanvasScaler.snap` is for pixel art, which a fractional scale draws with uneven pixels:
`CanvasScaleSnap.WholeStep` or `HalfStep` rounds the scale to the nearest whole number or half before
`minScale` and `maxScale` hold it, and the canvas is laid out at the rounded scale. That moves every
size and offset by up to half a step in proportion: 1.4125 draws at 1.5 with `HalfStep`, 6% larger, and
at 1 with `WholeStep`, 29% smaller. A scale under one step is left as it is, and `None`, the default,
leaves the scale as the rule gives it.

A wrapping `layout` decides what fits a line from the sizes as authored. When the pixels per unit
(the host's density times the scale) are not whole, each child, each gap and the box round to whole
pixels on their own, so a row sized to exactly its children and gaps can come out a few pixels
over; up to one pixel per child on the line counts as fitting. The children still draw on whole
pixels, and a row over by more than that wraps. At a whole number of pixels per unit, whole-unit
sizes do not round, so nothing is allowed.

## Safe area

`CanvasOverlay(items, safeArea = CanvasInsets(left, top, right, bottom))` is what covers the
canvas's edges, in the host's dp: a notch, rounded corners, a home indicator, overscan. An element
anchored on the canvas itself is placed inside it, its anchor and offsets measured from the safe
area's edges and centre; an element whose `safeArea` is false is placed from the canvas's own edges.
An element inside another, and a follower, are placed as they would be without it. The insets do
not grow with the canvas's scale. A played scene gets them from its platform through
`awake:scene:runtime`; a host drawing the overlay itself passes its own, and an editor passes a
device's to preview it.

`CanvasOverlay(dpi = …)` is the screen's real dots per inch, for a `ConstantPhysicalSize` scaler; 0,
the default, takes 160 per dp.

## Placing on a point

`PointAnchor(x, y, pivot = Alignment.BottomCenter, offsetX, offsetY) { … }` is a Compose layout that
puts its content's pivot point on a point of its box, for any UI that places a label, marker or
tooltip over a point it works out itself. It fills the room it is given and never moves its content
to stay inside it.

## Reacting to a button

```kotlin
if (jumpButton.consumePress()) jump()
```

`consumePress()` is true once per tap. A tap goes to the topmost element under the pointer that handles
it: a Panel, Text or Image drawn over a Button does not take the Button's taps, and elements anchored
at the same corner with different offsets do not cover one another.

`consumePress()` takes the tap, so of two readers only the first sees it. A reader that shares the
Button with another counts `presses` instead, the number of taps since the element was made: it keeps
the count it saw last, and takes nothing.

## Touch controls

A `Joystick` is a round pad of `width` across; dragging its knob sets `stickX` and `stickY` from -1 to
1, up being negative, and releasing centres it. A Button reports `isHeld` while pressed, as well as
taps through `consumePress()`. `action` names what an element does for the game, such as `move` or
`jump`, and a `touchOnly` element is drawn only when `CanvasOverlay(items, showTouchControls = true)`
says a touch screen is in use.

A Joystick's `joystick` settings, a `CanvasJoystick`, shape it:

- `mode`: `Fixed` keeps the pad centred in the element; `Floating` makes the element a zone, such as
  the left half of the screen, where the pad appears under the thumb; `Dynamic` also drags the pad
  along once the thumb goes past its reach. An untouched floating pad waits at `restX`, `restY`.
- `radius`: the knob's reach in dp, by default half the element's shorter side when fixed.
- `deadZone`, then `response` (`Linear`, `Quadratic`, `Cubic`) or a `curve` of points, then
  `directions` (`Analog`, `Four`, `Eight`) turn the knob's position into `stickX` and `stickY`.
- `snapBack` false holds the last deflection after a release.
- `sprintThreshold` sets `sprinting`, and `consumeSprintStart()` once, while the knob is that far out;
  a played project holds `sprintAction` then.
- `idleAlpha`, `idleDelay` and `fadeSeconds` fade the stick while nobody touches it.

A Button's `button` settings, a `CanvasButton`, make it an action button: its `trigger` fires on a
`Tap` or the moment it goes `Down`; `consumeDown()`, `consumeRelease()` and `consumeLongPress()`
report the rest of a press, with `heldSeconds`; and its live `cooldown` (swept over it), `charges` (in
a badge) and `enabled` hold it back, set from code or read through `bind`'s `cooldown`, `charges` and
`enabled` fields, or run by the button itself over its `cooldownSeconds`. `consumeReady()` says when
it became usable again. With `aim` (a `CanvasAim`), a
drag away from it aims (`aiming`, `aimX`, `aimY`), the canvas draws the reach, a cursor and the
`cancel` zone, and letting go fires with the aim, or cancels over the zone (`consumeAimCancel()`). An
`aimLock` set from code locks the aim onto a target the game picks.

A `LookArea` is a region, such as the right half of the screen, where one thumb's swipe looks and
two thumbs pinch to zoom: `consumeLook()` reads the swipe in dp times its `look.sensitivity`, right and
down positive unless `invertX` or `invertY`, and `consumeZoom()` the factor the pinch spread by.

An element's `cluster`, a `CanvasCluster`, places its children as a button cluster instead of a
`layout`: the first on a pivot and the rest `radius` dp around it, from `startAngle` and `spacing`
degrees apart, clockwise from the right. Keep the element big enough to hold the arc.

Every visual part of a control is a slot (`CanvasSlot`): a style, images (single, nine-slice, atlas,
per state, or `frames`), or `custom` content from `CanvasOverlay(slotContent = ...)` given a
`CanvasSlotInfo`. Each `CanvasControlState` may change a slot, faded over its `transitionSeconds`. A
`CanvasControlTheme` on `CanvasControls.theme` fills every control's slots from a `CanvasThemePreset`
and `@name` colour and image tokens, an element's own `skin` over it. `CanvasControls.playerLayout`
(`CanvasPlayerLayout`) scales, moves and fades controls by their `controlId`, saved as JSON.

`CanvasOverlay(controls = CanvasControls(...), inputDevice = ...)` shows touch-only elements by
`touchVisibility`: `Auto` follows the `InputDevice` the host last saw, from core:input's
`InputDeviceTracker`, so touch controls show after a touch and hide after a controller, a key or the
mouse. A control hidden mid-press lets go of its pointer. `minTouchSize`, 44 dp by default, widens a
small control's touch target.

Each stick, action button and look area follows only the pointers that landed on it first, so one thumb steers while another
presses buttons, and a second thumb landing on the stick does not take it.
