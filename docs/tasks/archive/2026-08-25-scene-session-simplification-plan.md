# Scene Session Simplification Plan

Date: 2026-08-25  
Status: Draft

## Decision

Awake scene is an ECS session, not a second application framework and not a Compose replacement.
The application host owns lifecycle, renderer ordering, input routing, and the Compose host. A
scene session owns a `World`, document load/unload, and an explicit ECS schedule. Compose reads a
session through locals and presents it as normal application content.

```text
Composed app root
  ├── App platform: lifecycle, backend frame handoff, input, window configuration
  ├── optional Compose app module: ComposeHost + root content + UI staging
  └── SceneSession
        ├── World
        ├── SceneDocument load/save/switch
        ├── SceneSchedule
        └── asset/component extension resolution
```

`engine:platform` remains UI-free. “App host owns Compose” means the composed application root,
not the low-level platform module: an optional Compose app module depends on Platform and
`awake:compose:ui`. Do not make ECS entities composables. ECS retains deterministic
update/lifetime semantics; Compose is the presentation shell around a session.

## Illustrative End Shape

This is the intended consumer shape, not an API that exists yet. The important properties are one
application root, one returned session, one normal Compose content tree, and providers registered
by installed plugins rather than hard-coded in the editor or scene runtime.

```kotlin
fun studioApp(): AppSpec = awakeApp {
    installPlugins(projectPlugins)

    val session = installScene(
        sceneSession("island") {
            document(loadScene("scenes/island.scene.json"))

            schedule {
                fixed("physics") { PhysicsSystem(requireService()) }
                frame("animation") { AnimationSystem() }
                standardRender() // transform -> render -> optional debug visualization
            }

            assets {
                use("gltf")
                use("terrain-heightmap")
            }
        },
    )

    module(
        composeAppModule {
            content {
                ProvideSceneSession(session) {
                    StudioEditor(
                        onPlay = session::play,
                        onStop = session::stop,
                    )
                }
            }
        }
    )
}
```

The physical module direction stays narrow:

```text
engine:platform        AppFrame, AppLifecycle, GraphicsEngine, window/input/services; no UI
engine:bootstrap       app { }, appModule { }, AppSpec composition DSL
engine:compose         optional ComposeHost integration and composeAppModule { }
compose:ui             Compose UI runtime and host implementation
scene:runtime          SceneSession and SceneSchedule
scene:authoring        sceneSession { }, document { }, entity { } sugar
```

`compose:ui -> engine:platform` is allowed; `engine:platform -> compose:ui` is not.

The session's document can reference a private plugin without Awake understanding its world
policy:

```json
{
  "component": "awake.private.terrain-placement",
  "version": 1,
  "payload": {
    "heightmap": "assets/island.heightmap",
    "seed": 4201,
    "rules": "coastal-trees"
  }
}
```

The provider/persistence contract is owned by the
[framework and plugin plan](2026-08-24-framework-boundary-and-plugin-ecosystem-plan.md). This
plan consumes its two scene-facing rules: the saved payload is the source of truth and unavailable
payloads round-trip unchanged.

## Why the Current Shape Feels Complex

`SceneAppLifecycleRuntime` currently combines session state, fixed/frame scheduling, app
lifecycle, `ComposeHost`, input ownership, UI staging, assets, services, scene switching, frame
metrics, offscreen preview/readback, and convenience lookup. Its builder also exposes multiple
meanings of `scene`, plus app installation, assets, renderables, services, scheduling, and UI.

The reduction goal is one owner per concern, not a large rewrite or a new module for every file.

## Naming Rules

Use nouns for durable state and verbs only for operations:

| Current term | Target term | Reason |
|---|---|---|
| `SceneAppLifecycleRuntime` | `SceneSession` | It represents one active ECS scene session, not the whole app lifecycle. |
| app-facing lifecycle bridge | `SceneSessionAppModule` or `SceneSessionLifecycleAdapter` | Makes the app integration role explicit and separate from the session. |
| `SceneAppSpec` | `SceneSessionSpec` | Configuration for a session, not an application. |
| `sceneApp {}` | `sceneSession {}` | Avoids a second competing app root. |
| `scene(document)` | `document(document)` | Removes the DSL/document overload. |
| `infrastructureSystems` | `SceneSchedule` | Exposes ordering as a schedule instead of an opaque system list. |
| future typed provider registry | `SceneExtensionRegistry` | Distinguishes extension registration from cached runtime assets. |

Keep `SceneAssetLibrary` while it means a mesh/material cache. Do not rename it to a registry
until typed providers replace its current narrow factory role. Keep `SceneDocument`, `SceneLoader`,
and `SceneManager`: they already name their responsibilities accurately.

Compatibility aliases/deprecations are required for public names. Do not perform a mass rename
before the extracted responsibilities and tests exist.

## Package Plan Before Module Splits

Keep the current `scene:runtime` Gradle module during the first extraction. Organize source by
owner, not by historical file placement:

```text
io.github.awakelab.awake.scene
  document/     SceneDocument, loader, validator, writer, export model
  session/      SceneSession, SceneManager, session lifetime
  schedule/     SceneSchedule, phase/handles, system registrations
  assets/       SceneAssetLibrary and later extension/provider contracts
  app/          thin AppModule/AppLifecycle adapter only
  ui/           Scene locals and bridge; no ComposeHost ownership
```

`scene:authoring` keeps author-friendly DSL/builders only:

```text
scene.authoring
  dsl/          sceneSession { }, document { }, entity { }, schedule registration
  assets/       asset registration sugar
  blueprints/   camera/entity convenience builders
```

Do not create `scene:document`, `scene:session`, or `scene:assets` Gradle modules yet. Split only
after package extraction shows a stable lower dependency direction and at least two consumers need
the isolation. The existing `scene` facade continues to shield consumers from physical moves.

## Delivery Plan

### Phase 0 — Characterize behavior and freeze vocabulary

1. Add focused tests for lifecycle order: input ownership, UI build/staging, fixed update, frame
   systems, transform/render schedule, load/switch/dispose, and resource cleanup.
2. Record every public Scene entry point and its consumer count.
3. Ban new responsibilities in `SceneAppLifecycleRuntime`; new code must identify the eventual
   owner from the package plan.

Acceptance: a deterministic test trace describes the current ordering and a dependency scan has
no new `SceneAppLifecycleRuntime` imports outside existing consumers.

### Phase 1 — Extract session and schedule inside existing modules

1. Extract `SceneSession` for `World`, `SceneManager`, lifecycle, and asset lifetime.
2. Extract `SceneSchedule` for fixed/frame/infrastructure ordering; make its standard transform,
   render, and debug stages named and testable.
3. Keep a deprecated `SceneAppLifecycleRuntime` façade delegating to the session during migration.
4. Move name-based lookup helpers out of the session unless a documented runtime consumer needs
   them; editor/test usage goes to an editor/diagnostic service.

Acceptance: the session has no `ComposeHost`, UI primitive, cursor, semantic, or readback field;
schedule-order tests retain existing behavior.

### Phase 2 — Establish one application root

1. Add an optional `composeAppModule` outside `engine:platform`; it owns `ComposeHost`, UI frame
   construction/staging, cursor, semantics, and input ownership.
2. Keep `engine:platform` UI-free. The bootstrap composition root installs this module only when
   an application has UI.
3. Provide `LocalSceneSession`/`LocalWorld` from the Compose app module around normal content.
4. Make frame order a single documented app pipeline. Preserve current behavior first; change UI
   versus simulation timing only through a separately tested decision.
5. Remove scene-local `content {}` ownership after consumers migrate to the root content model.
6. Keep `app {}` as the normal root and `appModule {}` as the reusable feature shape.
7. Retain `appSpec {}` for tests/tools; retain `appDefinition {}` only where a real per-app state
   factory is required.
8. Deprecate duplicate `AppModule.createApp()`/`createAppSpec()` paths once consumers use the
   primary root/module APIs.
9. Add contract tests for module install order, ready/render order, reverse disposal, and Compose
   module staging. Move Bootstrap dependencies unused by `commonMain` into the appropriate test
   source set.
10. Audit `GraphicsEngine` startup separately for structured cancellation/error behavior; do not
   change its thread/lifecycle semantics incidentally in the scene extraction.

Acceptance: one app owns one Compose host and one render frame; a scene can run with no UI; one
documented construction path serves applications; one reusable module path serves features;
Platform remains UI-free; and callback ordering is explicitly tested.

### Phase 3 — Separate authored data from live resolution

1. Move document/model/load/save/export code into `scene.document` packages and keep it free of
   renderer handles and editor policy.
2. Implement the versioned extension record and `SceneExtensionRegistry` contract specified by the
   framework/plugin plan. Unknown payloads must round-trip unchanged.
3. Keep live mesh/material resolution and resource destruction in `scene.assets`/session code.
4. Add isolated edit/play session support; Play may not mutate the authored document unless an
   explicit provider-owned Apply operation is invoked.

Acceptance: document tests run without a renderer; private extension fixture data loads, saves,
is unavailable safely when its provider is absent, and migrates through its declared codec.

### Phase 4 — Simplify authoring surface

1. Make `sceneSession {}` the primary builder installed into an app.
2. Reserve `scene {}` for authored entity/document structure only.
3. Replace `scene(document)` with `document(document)` and fold duplicate `ecs`/`scene` app
   entry points into deprecated forwarding sugar.
4. Move app-level service registration to the app composition root. Scene systems read explicit
   session services rather than a second hidden DI layer.
5. Replace `infrastructureSystems { List<System> }` with explicit schedule extension points.

Acceptance: a new consumer can create an app, session, document, schedule, and UI without
encountering overloaded `scene` meanings or two app-root abstractions.

### Phase 5 — Earn physical module splits

Only after Phase 3 proves the dependency graph:

1. Consider `scene:document` if the package is renderer/ECS/session independent and has two
   consumers (editor plus game/tool is sufficient).
2. Consider `scene:assets` only if typed asset/provider contracts become independently consumed.
3. Keep `scene:session` as the current runtime module unless compilation ownership or dependency
   isolation demonstrates a real benefit.

Acceptance: each split removes an illegal dependency or protects an independently consumed API;
no split exists merely to mirror package names.

## Non-goals

- No entity-as-composable API.
- No rewrite of ECS storage, `TransformSystem`, or `RenderSystem` in this effort.
- No new terrain, skybox, animation, placement, or procedural algorithm. Those integrate through
  the generic provider contracts from the framework/plugin plan.
- No compatibility-breaking rename before an extracted replacement and migration path exist.

## Verification

- Common lifecycle/schedule trace tests plus desktop and wasm compilation.
- Existing scene document round-trip and validation tests, extended for unknown extensions.
- One ECS + UI integration test proving a single app host/ComposeHost frame.
- One editor-style isolated play test proving Stop restores authored state.
- Dependency checks: session has no Compose host/readback/editor code; documents have no GPU
  handles; app bridge remains thin.
