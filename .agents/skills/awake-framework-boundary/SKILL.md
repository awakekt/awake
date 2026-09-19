---
name: awake-framework-boundary
description: Decide whether a proposed capability belongs in the Awake framework or in a consuming game repository. Use before adding an engine module, promoting sample code, or introducing networking, persistence, server, or MMO-oriented abstractions.
metadata:
  author: awake
  last-updated: '2026-08-20'
---

## The Three-Layer Ecosystem Architecture

Awake enforces strict boundaries across three distinct architectural layers:

1. **Layer 1: Awake Core Engine (`awaken`)** (Apache 2.0):
   Runtime engine libraries required to compile, execute, and ship games on Desktop, iOS, Android, and WASM (`:awake:scene`, `:awake:physics`, `:awake:render`, `:awake:ui:shadcn`, `:awake:project`, etc.).
2. **Layer 2: Awake Core Editor (`awaken:awake:editor:contract`)** (Apache 2.0):
   Public, vendor-neutral editor contracts and extension points (`com.awakekt:awake-editor-contract`). Enables any developer or toolmaker to author editor plugins without closed-source Studio dependencies.
3. **Layer 3: Awake Studio Pro (`awake-pro`)** (Commercial):
   Commercial desktop application (`:app:studio`), visual inspectors, collaborative workflows, and the secure runtime loader (`StudioPluginPipeline`).

## Framework vs Game Boundary Rules

1. A future MMORPG is one consumer, not justification for a new Awake module.
2. Prefer consumer-side composition. Promote only after two credible consumers demonstrate the
   same stable need, or a concrete public-API limitation makes that impossible.
3. Keep accounts, combat, quests, inventories, economy, zones, shards, protocol messages,
   databases, authentication, and live operations in the consuming game.
4. A promoted capability exposes the smallest backend-neutral contract and must not make a
   network library, database, renderer, or UI stack mandatory for ECS/core runtime modules.
5. Record exceptions with consumers, missing API, contract owner, excluded policy, dependency
   direction, and validation plan.
6. Keep water, biomes, vegetation, prop placement, procedural generation, and game assets in a
   consumer/private pack. Awake may supply neutral terrain data and extension seams, never the
   authored world policy.

Use `awake-architecture-auditor` for the decision. Use implementation skills only after it.

## What Belongs in `awake:scene:*` (The Scene-Binding Boundary)

**`awake:scene:*` = the scene-binding layer only.**
A module belongs under `awake:scene:*` if and only if its primary job is to **bind an engine capability into the ECS scene graph** as components and systems. The capability's own API lives outside `scene/`; the `scene:*` module is only the ECS glue.

### The Admission Test (Two Questions)

1. **Does it manipulate the scene graph or scene document directly?**
   (Transform, Entity hierarchy, scene serialization, scene lifecycle)
2. **Is it the binding layer between `scene-core` and an external capability?**
   (e.g. `scene:physics` = ECS glue between `physics:api` and ECS entities)

- If **yes to either** → belongs in `awake:scene:*` (e.g., `scene:scene-core`, `scene:physics`, `scene:rendering`, `scene:runtime`, `scene:authoring`).
- If **no to both** → top-level module (e.g., `awake:navigation`, `awake:ai`, `awake:ai:behavior`).

```
New capability X:
  Has its own API contract independent of the scene? -> awake:X (top-level)
  Needs to bind X into ECS entities/components?     -> awake:scene:X (scene binding layer)
  Both?                                              -> awake:X:api + awake:scene:X
```

