# ECS tutorial and samples

The ECS is the platform-neutral foundation for entities, components, and systems. Begin with the
[ECS overview](ecs.md) and [install Awake](../getting-started.md) before adding a renderer, scene,
physics world, or UI.

## Current scope

A complete copyable ECS tutorial is not published yet. This is an explicit placeholder so the
navigation is honest while the example is being reduced to a small, compiler-checked fixture.

The first tutorial will cover this sequence:

1. Create a `World`.
2. Define and attach one component.
3. Query matching entities from a system.
4. Make structural changes at a safe update boundary.

Until that sample lands, use the [ECS overview](ecs.md) for the model and the release-matched
KDoc for exact APIs. Do not copy an unverified snippet from a README into an application.
