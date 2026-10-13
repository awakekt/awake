- **A host that draws the scene canvas itself gets the runtime's projector and views.** A host
  that turns `SceneAppLifecycleRuntime.drawsSceneCanvas` off to draw `SceneCanvas` in part of its
  window, such as an editor's game view, could not pass a `projector` or `views`, so its followers
  never drew and its Views showed only their style. `canvasProjector(world)` and
  `canvasViews(world)` now hand it the runtime's own, for the runtime's world by default or for
  another world its render system draws, such as a Play world. The runtime still renders every
  View picture before the frame's UI, frees one nothing draws, and frees all of them on `dispose`.
  `SceneCanvas` now places a follower by where its own box sits in the window, so one drawn over
  part of it lands on the node rather than offset by the box's corner; `CanvasProjector` reports
  the window's pixels, as before for a canvas that fills it.
