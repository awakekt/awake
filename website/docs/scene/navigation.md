# Navigation

The navigation module provides grid-based pathfinding and ECS systems for asynchronous path
requests.

## Features

- `NavGridTile` represents a 2D or 3D navigation grid with height, slope, and obstacle constraints.
- `NavFieldSearch` provides A* pathfinding and Dijkstra navigation-field search.
- `PathRequestSystem` processes path requests across frames.
- `NavGridDebugLines` provides optional debug geometry for navigation tools.
