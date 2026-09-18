# ECS runtime

Awake’s ECS stores entities and components in a compact, queryable world. Systems operate on the
world during the application update and render phases.

## Core concepts

- An entity is an opaque identity in a `World`.
- Components hold data associated with an entity.
- Systems update matching entities through queries and families.
- Structural changes are explicit, so a system can control when entities enter or leave a query.

The ECS is platform-neutral and does not depend on Vulkan, WebGPU, physics, or UI. Those systems
can be layered on top of the same world.

For exact APIs and target availability, consult the KDoc and release notes for the version you are
using.
