# Awake Navigation (`:awake:navigation`)

The `:awake:navigation` module provides A* grid pathfinding, navigation field search, path smoothing, and asynchronous path request ECS systems for Awake Engine.

## Features
- **NavGridTile**: 2D/3D tile-based navigation grid supporting heightfield clearance, slope constraints, and static obstacles.
- **NavFieldSearch**: A* pathfinding and Dijkstra navigation field search with grid cell cost maps.
- **PathRequestSystem**: Asynchronous ECS system processing path requests across frames without blocking the main thread.
- **NavGridDebugLines**: Debug rendering utilities for path gizmos, waypoints, and grid cell bounds.
