- **A canvas element can sit left of or above centre.** Every negative `offsetX` and `offsetY` was
  clamped to 0, so on a centred axis (`TopCenter`, `Center`, `BottomCenter` across, `CenterLeft`,
  `Center`, `CenterRight` down) an element could only move right of or below the centre. A
  negative offset there now moves it that far left or up, its hit area with it. At an edge a
  negative offset is still held at the edge, and the scene format is unchanged.
