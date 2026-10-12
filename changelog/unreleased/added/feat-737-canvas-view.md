- **A canvas element showing a 3D view.** `CanvasElementKind.View` is a `canvas_element` whose
  `camera` names a camera node: each frame it is drawn, that camera renders the scene into a target
  the size of the element's box in pixels, which fills the box, opaque, inside its
  `style.cornerRadius` and under its children. The target is rebuilt when the box changes size and
  freed when the element or its camera goes. `SceneCanvas` takes the `CanvasViews` that supplies
  each picture, and `SceneAppLifecycleRuntime` passes its own, rendering every View before the UI
  that samples it. With no renderer or no such camera a View draws only its style.
  `DrawScope.drawTexture` gains `premultiplied` for a render target's colours.
