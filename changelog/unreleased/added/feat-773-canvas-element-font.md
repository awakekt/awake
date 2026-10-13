- **A canvas element can draw in a project font or a built-in family.** `canvas_element` and
  `CanvasElement` gain `font: String = ""`: a `.ttf` or `.otf` path from the project's root, or the
  built-in `default` (Roboto) or `bitmap`. A Text, TextField or Button draws its words in it.
  `loadProject` loads each file once with a renderer and none without; `loadCanvasFonts` loads them
  for a host of its own, `SceneCanvas` takes them as `fonts`, and `SceneAppLifecycleRuntime` keeps
  them in `canvasFonts`. A file that cannot be read or is not a font is logged once, and its elements
  draw in the default font. Another family name, or a path with another extension, is reported.
