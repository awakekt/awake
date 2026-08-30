---
name: awake-app-composition
description: >
  Compose an Awake application from engine:platform, engine:bootstrap, an optional Compose UI
  host, and SceneSession. Use before wiring an app root, AppModule, scene lifecycle, or UI host;
  it keeps Platform UI-free and prevents duplicate app, scene, and Compose hosts.
license: Apache-2.0
metadata:
  author: awake
  last-updated: '2026-08-25'
  keywords: Awake, Platform, Bootstrap, AppSpec, AppModule, ComposeHost, SceneSession, lifecycle
---

# Awake App Composition

Use this skill when assembling an application, sample, editor, or tool. It governs composition;
it does not replace the ECS, rendering, or UI-authoring skills.

Read [the scene-session simplification plan](../../docs/tasks/2026-08-25-scene-session-simplification-plan.md)
and the `README.md` files in `awake/engine/platform`, `awake/engine/bootstrap`, and `awake/scene`
before changing a cross-module boundary.

## Ownership

| Layer | Owns | Must not own |
|---|---|---|
| `engine:platform` | frame/lifecycle, graphics engine, window, input, app services, app contracts | Compose UI, scene documents, editor or game policy |
| `engine:bootstrap` | the application root and reusable module composition | a second lifecycle host or feature scheduling policy |
| optional Compose integration | one `ComposeHost`, root content, UI staging | ECS scheduling, document loading, renderer backend policy |
| scene session | `World`, document load/switch, `SceneSchedule`, scene assets and extension registry | a second application host or `ComposeHost` |

`SceneAppLifecycleRuntime` is a compatibility bridge during the migration. Do not add new UI,
editor-preview, service-container, or application-lifecycle responsibilities to it.

## Composition Rules

- Build one application root. Reusable features are `AppModule`s; they do not independently start
  or dispose the app.
- Install at most one `ComposeHost` for an application with UI. A headless application installs no
  Compose host.
- The optional Compose module provides a `SceneSession` or `World` to normal application content;
  entities are never composables.
- `SceneSchedule` determines system order only. Keep each system with the capability components it
  implements, such as rendering, physics, controls, or gameplay.
- Use the app-level service lookup supplied by Platform. Do not create a nested scene service
  container.
- Registered extension providers may carry private asset, environment, animation, terrain,
  placement, or generation configuration. Preserve unknown versioned payloads when a provider is
  unavailable; never silently discard them.
- Keep Platform and the public scene model free of genre, editor, water, biome, placement, and
  generation policy.

## Callback Discipline

Lifecycle callback order is part of the application contract: install in declared order, become
ready in declared order, render in declared order, and dispose in reverse order. Characterize and
test that order before changing bootstrap or platform lifecycle code.

The scene session may run fixed and frame schedules, but it is installed by the app root rather
than creating another application loop.

## Routing

- Components, systems, entity lifetime, and scene content: `awake-ecs-authoring` and
  `awake-ecs-scene-runtime`.
- Compose UI or recipes: `awake-ui-authoring` and `awake-shadcn-recipe-consuming`.
- Renderer passes or graphics-device work: `awake-render-pipeline` and the relevant backend skill.
- Public versus game/editor/private-pack ownership: `awake-framework-boundary`.

## Verification

Run the targeted Platform, Bootstrap, Scene, and UI-host tests for every affected target. For a
composition refactor, include a lifecycle-order test, a scene-load/switch test when scenes are
involved, and a smoke test that proves exactly one app loop and (when applicable) one Compose host
are installed.
