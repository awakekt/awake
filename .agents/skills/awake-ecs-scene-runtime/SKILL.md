---
name: awake-ecs-scene-runtime
description: >
  Consume Awake ECS scenes from a game or sample: scene documents, entity content, scene sessions,
  scheduling, and lifecycle-safe activation. Use before wiring 3D scene content; it prevents
  duplicate scene/UI hosts and keeps ECS systems with their capability components.
license: Apache-2.0
metadata:
  author: awake
  last-updated: '2026-08-25'
  keywords: Awake, ECS, World, Entity, SceneDocument, SceneSession, SceneSchedule, TransformSystem, RenderSystem
---

# Awake ECS Scene Runtime

Use this skill for a game or sample with ECS-managed 3D content: a `World`, entities, scene
documents, and scheduled systems. For the surrounding Platform/Bootstrap/Compose composition,
read [awake-app-composition](../awake-app-composition/SKILL.md) first.

Read [the scene-session simplification plan](../../docs/tasks/2026-08-25-scene-session-simplification-plan.md)
and inspect the actual scene runtime API before writing a DSL call. The session names in that plan
are the target shape; use the current compatibility API only where it exists today.

## Ownership

- A scene session owns one `World`, scene document load/switch, its `SceneSchedule`, and scene
  asset/extension access.
- The application root installs the session. A scene is not an application host and must not
  create a second frame loop or `ComposeHost`.
- Entities remain ECS data. Compose renders application UI around a supplied session or `World`;
  entities are not composables.
- Keep systems and their main components together by capability. `SceneSchedule` chooses order;
  it is not a home for rendering, physics, controls, or gameplay implementations.

## Scene Content

- Prefer an authored `SceneDocument` for persistent scene data. Use a loader/manager to select and
  instantiate it into a `World`.
- Let each feature own and tear down the entities it creates when that feature or preview ends.
  Do not discover ownership by scanning the whole world.
- Separate fixed simulation from frame/render work. Register the renderer's normal scene systems
  once; duplicate render registration produces duplicate submissions.
- Treat `Transform` as source data. If its system derives a world matrix from position, rotation,
  scale, and hierarchy, do not rely on a manually assigned derived matrix surviving the next pass.

## Extension Data

Scene documents may reference registered extension providers for assets, environments, animation,
terrain, placement, or generation. Public scene code owns only the generic extension seam.

- Store a provider type/ID, version, and configuration as the authored source of truth.
- Treat generated placement or preview results as disposable until an explicit bake operation.
- Preserve unknown extension payloads verbatim when the provider is absent so another machine or
  private pack can restore them later.
- Do not add genre-specific world rules, water, biomes, vegetation, or procedural algorithms to
  the public scene runtime.

## Lifecycle and Verification

`SceneAppLifecycleRuntime` is a migration compatibility bridge, not the place for new editor,
Compose, or service-container duties. New composition belongs in the app root and the future
session boundary described by the plan.

For changes, run targeted scene and ECS tests on relevant targets. Cover document load/switch,
system order, and entity teardown. A UI-bearing sample also needs a smoke test showing one app
loop and one Compose host.

## Route Elsewhere

- Component, pool, family-query, and hot-path rules: `awake-ecs-authoring`.
- App root, Platform, Bootstrap, and Compose host ownership: `awake-app-composition`.
- Render pipeline or backend resources: the render-pipeline and backend skills.
- Game/private-pack versus public framework scope: `awake-framework-boundary`.
