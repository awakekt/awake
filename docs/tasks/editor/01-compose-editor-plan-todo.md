# Awake Editor Plan — rebuild, do not port

Drafted 2026-08-22. Revised 2026-08-25. Status: Stages 0 and 1 foundations complete;
Stage 2's host-owned interaction seam and reusable scene helpers, plus Stage 3's shell, hierarchy,
inspector, toolbar, and viewport controls are complete. Studio is one fixture with no dock or
catalogue; generic selection, tools, and play state are exercised through `EditorStore`.

## Decision

`awake:editor` is Awake's generic editor library. It is rebuilt for the retained
`awake:compose:*` UI engine; Studio's existing editor implementation is not ported. A port would
carry `ui-core`'s trial-measure model and its old UI assumptions into a library intended to outlive
them.

The library owns generic editor behaviour: selection, editor camera, viewport interaction,
gizmos, panels, and provider-facing extension points. It owns no game content or world policy.
Visible editor controls use the design-system recipes; `awake:editor` may use Compose layout and
foundation APIs for structure and interaction, but does not recreate a local button, field, or
theme system.

```text
awake:editor              generic editor behaviour, UI, and tests
awake:engine:compose      one application Compose host and frame integration
samples:studio            host composition, one registered fixture, smoke proof
private packs / games     provider implementations and authored world policy
```

The application, not the editor, owns `ComposeAppRuntime`, presentation, and backend resources.
For a scene-backed application, it installs `sceneComposeAppModule` before the scene module so the
single host stages the editor UI before the scene render pass presents the frame.

### UI dependency delivery

The host constructs the explicit `EditorSession` adapter and `EditorProviders` collection. At the
editor UI boundary it supplies them with `CompositionLocalProvider`; panels read the scoped values
rather than accept them through every intermediate layout function.

```kotlin
context(_: Composer)
fun EditorContent(session: EditorSession, providers: EditorProviders, store: EditorStore) {
    CompositionLocalProvider(
        LocalEditorSession provides session,
        LocalEditorProviders provides providers,
        LocalEditorStore provides store,
    ) {
        AwakeEditor()
    }
}
```

Composition locals are UI-scoped delivery, not a lifecycle or service locator: creation, disposal,
testing, and the non-UI provider/session contracts remain explicit at the application boundary.

What carries over from Studio is behaviour, not source:

| Decision | Reason |
|---|---|
| The editor camera is a persistent entity separate from an authored camera | Inspecting a camera through itself makes its frustum and preview degenerate |
| The shell invokes panels once per frame | A resizable layout can measure content more than once |
| Debug-line submission replaces the frame buffer | Independent gizmo features must not erase one another |
| Offscreen preview renders clear the scene viewport temporarily | An inset must not inherit the main viewport clip |

## Extension and session boundary

The [framework/plugin plan](../2026-08-24-framework-boundary-and-plugin-ecosystem-plan.md) is the
canonical definition of provider contracts, persistence, installation, permissions, and removal.
This plan must not create a second registry model.

`awake:editor` consumes these KMP-safe provider kinds:

| Provider | Editor responsibility | Provider responsibility |
|---|---|---|
| Component | Display/edit schema and route codec operations | Stable ID, versioned codec, validation, inspector fields, ECS mapping |
| Asset | Show metadata, thumbnail state, and errors | Import/parser, cache, lifetime, and format policy |
| Environment | Select and preview when supported by the render plan | Environment source and renderer composition |
| Animation | Show clips, bind animator, provide isolated scrub preview | Source import and animation/skin binding |
| Build | Surface run, cancel, rebuild, dispose, and bake actions | Algorithm, progress, dirty-region policy, preview resources, deterministic seed |

Every provider has a stable ID, versioned configuration codec, metadata, validation, and disposal.
An unavailable provider never discards authored data: the editor preserves its opaque payload and
reports the capability as unavailable.

The editor operates on two distinct states:

```text
authored SceneDocument --start play--> isolated Play world --stop--> destroyed
        ^                                                |
        +--- explicit provider-owned Apply changes ------+
```

Starting Play snapshots authored data. Stopping Play destroys only the isolated world. A provider
may explicitly apply a reviewed change to authored configuration; simulation output never writes
back implicitly. Placement and generation providers persist a specification (seed, asset IDs,
parameters), not generated entities; they own preview output, cancellation, cleanup, and optional
baking. Awake supplies the seam, never the generation or world-placement policy.

The host creates and passes an explicit editor/session adapter and provider collection to that UI
scope. The editor does not access `SceneAppLifecycleRuntime`, own a renderer, call `present`, or
retain a thumbnail cache. Render previews are requested through the relevant provider or
host-facing preview service so resource lifetime stays with the renderer-aware owner.

## Compose readiness

The original capability block is obsolete. The retained Compose engine now provides layout,
styling, click/focus/text-input, frame processing, and the required pointer primitives. `LazyColumn`
is available but still settles its visible window one frame late; hierarchy work must validate that
trade-off with a representative scene. Real render layers remain unavailable, so previews must use
the existing renderer-owned offscreen path rather than `graphicsLayer`.

No editor-local replacements for controls or input are permitted. Capability gaps are resolved in
Compose or the design system before an editor surface depends on them.

## Delivery stages

Each stage is independently useful and verified before the next begins.

1. **Stage 0 — contracts and state, no UI.** **Core complete.** `awake:editor` owns editor state,
   selection, the session adapter, canonical provider registry interfaces, and synthetic-provider
   tests. Studio's temporary UI may register one fixture through those contracts during its
   thinning phase.
2. **Stage 1 — document and play lifecycle.** **Foundation complete.** `SceneDocument` persists
   opaque versioned extension records; `SceneExtensionRegistry` validates available providers and
   reports unavailable data without discarding it; `SceneDocumentEditSession` snapshots into and
   destroys an isolated Play world. The optional `awake:editor:scene` module's
   `SceneDocumentEditorSession` is the host-owned `EditorSession` adapter; the app remains
   responsible for scheduling its Play world. No provider is allowed to mutate an authored
   document from Play.
3. **Stage 2 — viewport.** **Root and interaction seam complete.** `ProvideAwakeEditor` provides
   explicit session and provider locals, while `AwakeEditor` exposes a full-size host-rendered
   viewport slot. The full-size viewport turns an unconsumed release into `EditorStore`'s
   viewport-pick request, allowing a future gizmo to consume its own interaction first.
   `EditorEffectRouter` is owned and drained by the host; its renderer/world-aware picker returns
   only a stable result through selection. `awake:editor:scene` now owns the editor camera,
   transform gizmo, viewport rectangle/projection helpers, preview/orientation-gizmo primitives,
   display/debug/camera controls, and the persistent Scene-view camera system. The host retains
   renderer lifetime and maps renderer/camera policy into explicit controls and controller adapters.
4. **Stage 3 — shell and panels.** **Complete.**
   `AwakeEditorShell` provides resizable hierarchy, viewport, and inspector regions with
   application-owned slots and named shadcn surfaces. `EditorScaffold`, `EditorDock`,
   `EditorProviderPanel`, and `EditorToolbar` exist. `SceneHierarchyPanel`, `SceneInspectorPanel`,
   the generic tool palette, and scene viewport controls now live in editor modules; Studio binds
   them as an integration host. Validate a
   representative hierarchy against the lazy-list one-frame-settling behaviour.
5. **Stage 4 — assets and capability panels.** **Complete.** Added `EditorAssetPanel`, `EditorAnimationPanel`, `EditorEnvironmentPanel`, and `EditorBuildPanel` with comprehensive test coverage in `awake:editor`. Provider-owned preview paths supply thumbnails and render resources; the
   editor does not cache renderer objects.
6. **Stage 5 — thin Studio.** **Complete.** Generic editor tests and behaviour live in
   `awake:editor` and `awake:editor:scene`; Studio has one registered fixture and integration
   smoke coverage. The catalogue, dock, showcase render plan, reusable viewport presentation, and
   renderer viewport helper are gone from Studio. Its bridge adapts only host scene/camera policy
   to reusable editor state.

## Acceptance gates

- `awake:editor` depends on neither Studio, a pack, nor a game; it has no application-host or
  backend-present dependency.
- Synthetic providers prove registration, stable ordering, validation, cancellation, and disposal.
- Built-in and unknown extension records survive load/save unchanged when their provider is absent.
- Edit and Play worlds are isolated; stopping Play releases its world and does not mutate authored
  data unless an explicit provider-owned Apply operation is invoked.
- A scene-backed host proves a single Compose host, UI staging before scene presentation, and no
  duplicate present.
- Studio registers only a small fixture and has no direct knowledge of concrete demo assets,
  terrain, skybox, animation, or game-specific component names.
- Viewport, interaction, and panel behaviour have focused editor tests; Studio is not used as a
  unit-test fixture.

## Non-goals

- Porting Studio's existing panels or preserving its implementation details.
- A general docking framework. The first editor uses a fixed layout.
- Arbitrary system registration as an editor plugin API. Scheduling belongs to the session/runtime;
  a future neutral editor-mode seam needs its own proven contract.
- Procedural terrain, erosion, water, biomes, vegetation, placement algorithms, or game assets in
  Awake. These remain private providers or game code.
- Runtime code loading. Plugin installation remains build-time, pinned, verified artifact
  resolution as specified by the framework/plugin plan.

## Open only when the stage requires it

- Whether a future non-ECS document adapter is justified by two real consumers. The first editor
  targets Awake scenes through the explicit adapter.
- The smallest renderer-aware preview service API, to be defined only when two provider kinds need
  more than their existing renderer-owned paths.
