# Awake Navigation (`:awake:navigation`)

The `:awake:navigation` module provides A* grid pathfinding, navigation field search, path smoothing, and asynchronous path request ECS systems for Awake Engine.

## Features
- **Walkable surface** (`walkable` package): `bakeNavSurface` bakes where an agent can stand from terrain heights (slope limit), static colliders (height and step height), water and no-walk areas, as a per-sample clearance so any agent radius is answered at query time. `NavTiledSurface` tiles a large region without seams and `NavSurfaceCodec` caches a bake as bytes.
- **NavPathQuery**: reusable, allocation-free once warm, node-budgeted and deterministic A* with smoothing, answering with the nearest reachable point when a target cannot be reached. `NavSurfaceMesh` adapts it to `NavMesh` for `PathRequestSystem`.
- **NavGridTile**: 2D/3D tile-based navigation grid supporting heightfield clearance, slope constraints, and static obstacles.
- **NavFieldSearch**: A* pathfinding and Dijkstra navigation field search with grid cell cost maps.
- **PathRequestSystem**: Asynchronous ECS system processing path requests across frames without blocking the main thread.
- **NavGridDebugLines**: Debug rendering utilities for path gizmos, waypoints, and grid cell bounds.
