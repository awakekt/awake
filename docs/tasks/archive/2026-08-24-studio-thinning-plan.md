# Studio Thinning Plan

Date: 2026-08-24  
Status: Complete — reusable editor contracts, shell, scene panels, gizmos, previews, and viewport
controls live in editor modules. Studio has one fixture, no demo catalogue or placeholder dock, and
a fixture-only render plan. Its host adapter retains only fixture/camera policy; generic selection,
tools, and play state live in `EditorStore`.

## Decision

`samples:studio` remains an Awake editor integration sample. It is not the editor library or a
renderer-demo catalogue.

```text
awake:editor              generic editor behavior and tests
samples:engine-showcase   renderer, asset, physics, and scene demonstrations
samples:studio            launcher, registered fixture, integration smoke proof
samples:ui-showcase       UI component/gallery proof
```

See the [Compose editor plan](editor/01-compose-editor-plan-todo.md) for capability gates and the
[framework/plugin plan](2026-08-24-framework-boundary-and-plugin-ecosystem-plan.md) for extension
registries, persistence, and installation.

## Target Studio Shape

```text
samples/studio/
  app/                 platform launchers and window/backend configuration
  demo/                one registered scene/component/system/asset fixture
  StudioApp.kt         editor host composition root
  integration tests/   registry and cross-backend smoke tests
```

Studio may choose a theme and compose the editor. It must not own reusable panels, gizmos,
renderer helpers, shader contracts, generic scene loading, or a selectable demo catalogue.

## Audit

| Current Studio responsibility | Decision | Destination |
|---|---|---|
| Panels, dock, toolbar, editor state, selection, previews, orientation/transform gizmos | Move | `awake:editor`, rebuilt on `awake:compose:*`; do not port the old UI. |
| Gizmo math, editor camera, viewport interaction, editor-mode systems | Move | `awake:editor`, with generic entity/transform/camera vocabulary. |
| `ExampleLoader`, glTF/skinning/instancing/particle/terrain drivers, demo scenes/assets | Move | `samples:engine-showcase`. |
| Terrain visual fixture | Move | Engine showcase; source-data tests stay in `asset:terrain`. |
| `AslShaderDriftTest` | Move | Shader DSL/shader-pack verification near source/generator. |
| Sample mesh/bounds helpers | Classify | Promote only after two neutral consumers; otherwise retain in engine showcase. |
| Launchers, backend/window configuration, one registered fixture, integration smoke tests | Keep | Studio. |

No current Studio source moves directly to a private repository: it is editor/demo code, not game
product code.

## Delivery Plan

### Phase 0 — Freeze scope

1. Permit new Studio work only for launchers, the registration fixture, and integration tests.
2. Route reusable editor behavior to `awake:editor`; route capability demonstrations to the engine
   showcase; route shader invariants to their owning shader module.

### Phase 1 — Register through the editor seam

1. Follow Compose-editor Stage 0 and use component, system, and asset registries while the
   current Studio UI remains temporary.
2. Register one small Studio fixture through those contracts.
3. Prove Studio's host has no direct knowledge of cube, duck, particle, terrain, skybox,
   animation, or game-specific component names.

Acceptance: integration tests register synthetic content and verify host composition only.

### Phase 2 — Separate public demonstrations

1. Create `samples:engine-showcase` with focused glTF, skinning, instancing, particles, terrain,
   and first end-to-end physics scenes.
2. Move Studio drivers, assets, resources, and visual baselines there.
3. Move shader-drift verification beside shader source/generator. **Complete:** the generated
   `triangle.wgsl` artifact and its drift guard now live in `awake:asset:shader-pack`.
4. Delete Studio's selectable demo catalogue, leaving its one fixture.

Acceptance: each moved demonstration keeps a focused build/run/test command, and Studio's editor
integration coverage does not decrease. **Complete:** `:samples:engine-showcase:desktopTest` loads
and instantiates every moved scene; run a specific desktop demonstration with
`./gradlew :samples:engine-showcase:run -Pawake.showcase=<id>`.

Progress: `samples:engine-showcase` is registered as an independent KMP app/sample module. glTF,
skinning, instancing, particles, and heightfield terrain drivers, assets, scenes, and tests have
moved there. Studio now owns only its rotating-cube fixture and no longer declares showcase
pipelines, resources, catalogue state, or a placeholder dock.

### Phase 3 — Retire the old Studio editor — complete

1. Follow Compose-editor Stages 1–3 to rebuild viewport, selection, camera, gizmos, hierarchy,
   inspector, toolbar, assets, and thumbnails in `awake:editor`.
2. Remove Studio state, systems, panels, previews, gizmos, and shell only after focused parity
   tests exist in `awake:editor`.

Acceptance: reusable editor implementation/tests live in `awake:editor`; Studio is the thin host.

The reusable scene viewport presentation and renderer viewport confinement live in
`awake:editor:scene`; Studio binds only host-owned renderer/camera policy, fixture lifecycle, theme,
and integration-specific status text.

## Gates

- Studio uses only public Awake/editor APIs on supported targets.
- `awake:editor` tests never require Studio fixtures.
- Demo removal does not reduce renderer, asset, terrain, animation, or physics proof.
