- **A canvas Button's hover and press pictures load.** `loadCanvasImages`, `checkCanvasImages` and
  `hasCanvasImages` read only `style.image` and `style.fillImage`, so a Button whose
  `style.hovered.image` or `style.pressed.image` named a file of its own drew its state without
  that picture. They now read both states' images too, each file once.
