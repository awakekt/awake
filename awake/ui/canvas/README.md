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

## An element

`CanvasElement` has:

- **`kind`**: `Text`, `Panel`, `Bar`, `Button`, `TextField`, `Joystick`, `Image` or `View`.
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
- **`font`**: a project `.ttf` or `.otf` file, or a built-in family, `default` or `bitmap`; empty
  is the default Roboto. A host loads the file and passes it in `CanvasOverlay(fonts = …)`.
- **`value`**: a Bar's fill from 0 to 1.
- **`order`**: lower draws first. Of the same order, the later in `CanvasOverlay`'s `items` draws on
  top and takes the pointer; for a scene that is the later in the hierarchy. **`visible`** hides it.
  `canvasDrawOrder(items)` lists the items in the order they draw, and `canvasItemAt(items, x, y)`
  finds the one last drawn on top at a point, so a host that picks from the canvas, such as an
  editor, follows the overlay's own rule rather than a copy of it.
- **`style`**: how it looks beyond its colours, as the saved form of a Compose `Style` drawn
  through `Modifier.styleable`: fill, gradient, corner radius, border, shadow, text colour, alpha,
  `hovered` and `pressed` states for a Button, and images. `style.image` is an Image's picture, the
  frame of a Panel, Button or Text, or a Bar's track; `style.fillImage` is a Bar's fill, cut at
  `value`, or with `style.fillImageMode` `Squeeze` drawn at the filled width with both end caps kept.
  A `CanvasImage` names a project file, the region of it to use and the slice insets that
  keep its corners whole. A host decodes the images and passes them in `CanvasOverlay(images = …)`.

## Drawing

`CanvasOverlay(items, resolver = …)` draws a flat list of `CanvasItem(id, element, parent)`: each
item's `parent`, when it is also in the list, is the nearest element above it, so the child draws
anchored and offset inside the parent's box. A host that keeps elements in an ECS world, a plain
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
`enabled` fields. `consumeReady()` says when it became usable again. With `aim` (a `CanvasAim`), a
drag away from it aims (`aiming`, `aimX`, `aimY`), the canvas draws the reach, a cursor and the
`cancel` zone, and letting go fires with the aim, or cancels over the zone (`consumeAimCancel()`). An
`aimLock` set from code locks the aim onto a target the game picks.

Each stick and action button follows only the pointer that landed on it first, so one thumb steers while another
presses buttons, and a second thumb landing on the stick does not take it.
