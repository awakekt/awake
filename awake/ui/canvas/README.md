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
- **`width` / `height`** in dp.
- **`text`** (Text content, Button label), **`fontSize`**, **`color`** (text, or a Bar's fill) and
  **`background`**, as `#RRGGBB` or `#RRGGBBAA`.
- **`font`**: a project `.ttf` or `.otf` file, or a built-in family, `default` or `bitmap`; empty
  is the default Roboto. A host loads the file and passes it in `CanvasOverlay(fonts = …)`.
- **`value`**: a Bar's fill from 0 to 1.
- **`order`**: lower draws first. **`visible`** hides it.
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

## Nesting and scale

An item whose `parent` is also in the list is drawn inside it, anchored and offset within the
parent's box, and moves and hides with it. `CanvasOverlay(items, scale = 2f)` draws everything
twice the size, with every edge on a whole pixel.

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
