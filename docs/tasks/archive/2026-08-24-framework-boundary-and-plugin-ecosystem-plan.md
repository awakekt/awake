# Framework Boundary and Plugin Ecosystem Plan

Date: 2026-08-24  
Status: Draft

## Decision

Awake is a public framework. It owns portable primitives, extension contracts, diagnostics, and
generic editor infrastructure. It does not own a game, world policy, or a private package.

```text
awake framework <- private awake-game-packs <- private genre template <- private game
       ^
       └── public awake-game-template (separate consumer repository)
```

Awake never depends on a private repository, Git submodule, database, transport, or game domain.

## Keep, Move, Create

| Keep in Awake | Move/create outside Awake |
|---|---|
| Core, ECS, scene, render contracts/passes, backends, physics, UI, diagnostics, assets | Gameplay, combat, quests, progression, narrative, protocol, persistence, and operations belong to a genre template or game. |
| `asset:terrain`: heightmaps, edits, meshes, local queries, and patch data | Procedural terrain, erosion, biomes, water, vegetation, placement, and world assets belong in private packs or games. |
| `awake:editor` once its generic registry seam exists | Creative/game-authoring playbooks belong in private templates and games. |
| Framework agents and skills | Genre/game authoring skills are created beside their private owner. |

Do not create empty packs or speculative skills. Promotion requires two credible consumers or a
documented smallest public Awake gap that a consumer cannot bridge.

## Editor Extension Contracts

`awake:editor` uses explicit KMP-safe registries, not reflection.

The first landed code seam is `EditorPlugin`: a build-time-linked artifact supplies metadata,
declares its required editor-plugin API version, and returns its provider batch. The host's
`EditorPluginRegistry` rejects incompatible or duplicate plugin IDs and delegates atomic provider
registration to `EditorProviders`. It is deliberately below manifest discovery and permission
approval; those remain later installer work.

| Provider | Editor owns | Provider owns |
|---|---|---|
| Component | schema display/edit and codec routing | ID, versioned codec, validation, inspector fields, ECS mapping |
| Asset | metadata/thumbnail/error presentation | import/parser, caching, lifetime, format policy |
| Environment | selection and preview when enabled by render plan | skybox/environment source and renderer composition |
| Animation | clip list, animator binding, isolated scrub preview | source import and animation/skin binding |
| Build | run/cancel/rebuild/dispose/bake commands | algorithm, progress, dirty-region strategy, preview resources, deterministic seed |

Each provider declares a stable ID, versioned configuration codec, validation, metadata, and
disposal. Missing providers retain authored data and report an unavailable capability.

### Persistence and lifecycle

`SceneDocument` currently has a closed `SceneComponent` hierarchy. Add one generic, versioned
extension record: provider/type ID plus opaque JSON payload. Built-in components remain typed;
registered providers decode/instantiate extension data; unknown data round-trips untouched.

The editor snapshots authored data into an isolated Play world. Stop destroys it. Only explicit
provider-owned **Apply changes** writes back to authored configuration.

A private placement/generation provider saves a specification (seed, asset IDs, parameters), not
generated entities. It creates disposable ordinary scene preview output, consumes explicit input
changes such as `HeightmapChange(revision, dirtyRegion)`, and optionally **Bakes** static output.
It owns patch-versus-rebuild, cancellation, progress, determinism, and cleanup. Awake therefore
needs no placement or generation algorithm.

### Current gaps

- `SceneDocumentEditSession` now owns an isolated document-snapshot Play world; it still needs a
  host adapter that drives the application's schedule and maps editor effects at the app boundary.
- `SceneAssetLibrary` handles mesh/material factories only; it is not yet a provider registry.
- Animation runtime exists; a provider supplies source binding and preview.
- Studio's procedural render-plan skybox is not an importable/persistent skybox asset; that needs
  an environment provider.

## Plugin Installation

Plugins may come from public or private repositories, but installation resolves a released,
pinned, verified artifact. Never clone a Git default branch and execute it.

```text
catalogue -> choose version -> verify manifest/signature/checksum -> write lock
          -> Gradle resolves artifact -> rebuild/relaunch -> providers register
```

Build-time installation is the KMP baseline. It is portable and preserves Gradle dependency
verification and native packaging. A JVM-only hot-load path may be considered later, but is not
the plugin API because Web and Kotlin/Native cannot load arbitrary code dynamically.

The signed manifest declares ID, vendor, licence/docs/source, version, plugin/Awake compatibility,
targets, immutable artifact coordinate/checksum, provider kinds, configuration migrations, and
requested permissions. Private credentials stay in the host credential store. A committed lock
file records repository identity, exact version/checksum, and approved permissions. Removal first
reports scene payloads that still require the plugin.

## Delivery Plan

1. Define component/asset/environment/animation/build provider contracts and editor tests.
2. Add scene extension persistence, unknown-data round trip, codec validation, and isolated
   edit/play lifecycle.
3. Define manifest, permissions, public/private catalogue discovery, lock file, and Gradle
   resolution.
4. Prove public and credential-protected private fixture plugins; reject invalid signatures,
   incompatible targets/APIs, unapproved permissions, and unsafe removal before loading.
5. Create public templates and private packs only when existing plans and real consumers justify
   them.

## Gates

- `awake:editor` has no dependency on Studio, packs, or games.
- Built-in and unknown extension data survive load/save as specified.
- Private plugins are optional; Awake runs with none installed.
- No private repository becomes a Gradle dependency or Git submodule of Awake.
