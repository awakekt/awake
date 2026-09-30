# Node graph plan

Date: 2026-09-27
Status: **active** — phases 0–2 are done (#108, #110). Each consumer has its own plan or section,
listed under "Sequence".

## Goal

Studio is for Kotlin coders and for non-coders. LLM chat-based game generation is a planned
future extension. All three author through node graphs:

- **Coders** write nodes in Kotlin.
- **Non-coders** wire those nodes in Studio.
- **The LLM, later,** composes the same graphs from the same catalogue.

This plan builds the shared layer in Core: a graph document model, a node registry and a canvas
widget. The consumers, in order, are:

1. blueprints (event-driven game logic);
2. AI state trees;
3. shader graphs.

## Sequence

| Step | Work | Where |
|---|---|---|
| 1 | Graph model and node registry | Phase 1 here, [#104](https://github.com/awakekt/awake/issues/104) |
| 2 | Canvas widget | Phases 0 and 2 here, [#105](https://github.com/awakekt/awake/issues/105) |
| 3 | Blueprint runtime: event graphs as data, interpreted by an ECS system, reloadable while running | [blueprint-runtime](2026-09-27-blueprint-runtime-plan.md) (active) |
| 4 | Studio blueprint editor and live debugger | awake-pro, planned when step 3's runtime lands |
| 5 | AI state trees on the same canvas: the hybrid behavior tree and state machine runtime | Own plan, not yet written |
| 6 | Shader graph, preceded by shader hot reload and a per-material shader render plan | [shader-graph](2026-09-27-shader-graph-plan.md) |
| Later | LLM authoring tools over the registry and validator | Studio Pro, planned when the LLM extension starts |

Steps 1 and 2 share no files with step 3's runtime work and can run in parallel with it.

## Decisions made

1. The graph model is its own Core module, `:awake:node-graph`. The canvas is
   `:awake:ui:node-graph-canvas`, beside the other UI libraries built on compose, as
   [ui-ownership](../reference/ui-ownership.md) describes. No node-graph code goes into any
   `:awake:compose` module, so compose stays a general UI engine.
2. Blueprints are the first consumer, AI state trees the second, and shader graphs the third.
3. Every graph is data the engine validates. Generated Kotlin is never an output. This keeps web
   and iOS working, since neither can load code, and it keeps later LLM output safe.
4. The node registry is explicit and can list every node, with docs. There is no reflection
   scanning: Kotlin/Native and wasm have little reflection, and a scanned palette floods.
5. Shader graphs launch with both terrain and mesh targets.

## Current state

### Logic and AI

- `awake:ai` behaviour trees and state machines are built in code. Conditions and actions are
  lambdas, and nothing serialises.
- The behavior tree plan's rung 3, behaviour composed as data by someone who cannot rebuild, was
  gated on a trigger. The decision that Studio serves non-coders meets that trigger.
- Patrol, chase and flee parameters are already scene data, with live inspector editing.

### UI

`:awake:compose` has everything a node canvas needs except the canvas itself:

- drawing: `Canvas`, `DrawPath.cubicTo`, `Modifier.scale` and `graphicsLayer`;
- gestures: `Modifier.draggable` and `Modifier.transformable` (pinch and pan; one pointer pans);
- pointer events `Press`, `SecondaryPress`, `Move`, `Release` and `Wheel`, with modifier keys;
- public `ModifierNodeElement` and `PointerInputNode` for custom gesture handling.

### Studio Pro

- `plugins:commercial:visual-blueprints` is an empty module. It is the natural home for the
  blueprint editor.
- `EditorWorkspaceContribution` (awake-pro `editor:ui`) is the seam for a full-workspace editor.
  The UI builder plugin already uses it.

### Shaders

- ASL expressions form a typed tree (`AslExpr`). The builder has name-based entry points
  (`let(name, …)`, `namedField`, `fn(name)`), so a compiler can generate a definition from data.
- ASL checks structure, not types. A type error surfaces from naga as an error on an emitted WGSL
  line, which a graph user cannot act on.
- `AslEvaluator.traceFragment` returns the value of every top-level `let` for one pixel. It is
  fragment-only and throws on texture sampling and matrix math.
- `aslShaderSet(definition)` wraps runtime-emitted WGSL as `ShaderSource.InlineText`:
  - WebGPU consumes it directly.
  - Vulkan compiles it with `NagaShaderCompiler`, uncached and once per stage.
- The terrain surface seam is a working runtime target:
  - `TerrainSurfaceProvider` returns a `ShaderSet` plus textures, and `TerrainContentSystem`
    attaches it at runtime.
  - A missing or failing provider falls back to base shading, with the payload kept.
  - A surface calls `terrainClipmapVertexStage` (and `terrainClipmapDiscardUnderFinerRing` in its
    fragment stage), declares textures from binding 3, computes its
    own lighting, and can add no uniform fields.
- Meshes cannot name a shader:
  - `PipelineTable` is keyed only by `VertexFormat`, is built once at engine start, and never
    changes.
  - `drawUniformPlan` fixes the uniform layout.

### Reference

`graphyn-editor` (Apache-2.0, same author) has a comparable model and an LLM drafting flow with an
MCP server. Use it as a design reference, not a dependency:

- its UI is Compose Multiplatform, not `:awake:compose`;
- its model is built for executing workflows.

## Boundary

| Layer | Owns |
|---|---|
| Core (Apache-2.0) | graph model, node registry, canvas widget, blueprint runtime and engine nodes, shader graph compiler, graph asset providers |
| Studio Pro (commercial) | the editors (palette, inspector, debugger, previews), and later the LLM chat, client and tool server |
| Game repositories | game-specific nodes (`AttackTarget`, `DealDamage`) and authored graphs |

Formats and runtimes are open so that a shipped game runs graphs without Studio code. That
matches decision 1 of [terrain-surface-layers](2026-09-27-terrain-surface-layers-plan.md): buyers
pay for tools, not for a format. No LLM or HTTP code reaches Core, which is the behavior tree
plan's hard rule.

## Design

### Modules

| Module | Holds | Depends on |
|---|---|---|
| `:awake:node-graph` | document, node registry, graph kinds, validation, codec | kotlinx-serialization |
| `:awake:ui:node-graph-canvas` | canvas widget | `:awake:node-graph`, `compose:foundation` |

- The model and the UI live apart. Consumers that only load or run graphs, such as game servers
  and headless tools, depend on `:awake:node-graph` and never pull in UI.
- The canvas sits in `:awake:ui`, the group that holds UI libraries built on compose
  (`material3`, `shadcn`). It gets a row in [ui-ownership](../reference/ui-ownership.md) when it
  lands.
- Packages are `com.awakekt.awake.nodegraph` and `com.awakekt.awake.ui.nodegraph`.
- Consumer modules are listed in their own plans.
- The modules are named "node-graph" rather than "graph" because "render graph" is already a
  term in this repo.

### Document model

Indicative shape:

```kotlin
@Serializable
data class NodeGraph(
    val kind: String, // e.g. "awake.logic.event-graph"
    val version: Int,
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>,
)

@Serializable
data class GraphNode(
    val id: String,
    val type: String,
    val x: Float,
    val y: Float,
    val config: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class GraphEdge(val fromNode: String, val fromPort: String, val toNode: String, val toPort: String)
```

- **Kinds.** Port types are opaque strings. Each graph kind supplies `canConnect(from, to)` and
  says whether cycles are allowed.
- **Validation.** Issues are keyed by node id:
  - unknown node type or port;
  - dangling edge;
  - second edge into a single-edge input;
  - incompatible port types;
  - a cycle in a kind that forbids cycles.
- **Neutral by requirement.** The model must fit all three consumers:
  - event graphs mix execution wires and data wires;
  - state trees need ordered children and cycles;
  - shader graphs are acyclic data flow.

  The order of a port's edges is their order in `edges`.
- **No evaluation.** The model knows nothing about evaluating or compiling a graph.

### Node registry

- **`NodeSpec`.** Each node declares:
  - a stable type id;
  - a display name, category and description;
  - input and output ports, each with a name, a type, a description, and whether it accepts
    several edges;
  - a config schema, for values set on the node rather than wired in.
- **Registration.** Registration is explicit, per module:
  - engine nodes register from Core modules;
  - game nodes register from the game.

  The same registry serves the Studio palette, validation and, later, the LLM's schema.
- **Catalogue export.** The registry can export the whole catalogue as JSON, for documentation
  now and for the LLM later.
- **Unknown node types fail validation.** A graph that names one is refused, never partially
  loaded.

### Canvas widget

- **Controlled component.**
  `NodeGraphCanvas(graph, registry, viewport, selection, onIntent, nodeContent)`.
  - It emits intents: move nodes, connect, disconnect, select, delete, context menu at a point.
  - The caller applies them and owns undo.
  - The viewport (pan and zoom) is hoisted state.
- **Wires.** One `Canvas` layer under the nodes draws wires with `DrawPath.cubicTo`.
  - Execution wires and data wires are drawn differently.
  - It draws the wire being dragged and marks a rejected target using the kind's `canConnect`.
- **Input.**
  - Drag on empty space pans.
  - The wheel zooms around the cursor. Pinch is a follow-up: `transformable` pans with one pointer,
    which would take over node dragging.
  - Shift-click adds to the selection.
  - Box select.
- **Nodes.** The canvas draws each node's header and ports. The node body is the caller's slot.
  The widget depends only on `compose:foundation`, so Studio styles it.
- **Live state.** An optional per-node highlight channel lets a debugger mark executing nodes and
  active states. The canvas knows nothing about runtimes.
- **Culling.** Nodes outside the viewport are not composed.

## Phases

### Phase 0 — confirm prerequisites (done)

- **Transformed pointers: no.** `Modifier.scale` transforms painting only, and the pointer
  dispatcher hit-tests laid-out positions. The canvas therefore zooms node content through a
  scaled `LocalDensity`, which really lays it out larger, so hits land where things are drawn.
- **Legibility.** Titles are hidden below zoom 0.5 and port labels below 0.6. These are style
  values, and layout does not change when they toggle.
- **Budget.** `ComposeFrameProbe` measures about 38 KB per frame for 60 plain boxes. The engine
  cannot reach "nothing per frame", so the canvas gate is a ratchet (phase 2).

### Phase 1 — `:awake:node-graph`

Document, node registry, kinds, validation, catalogue export, and a versioned JSON codec.

**Gate:**
- A codec round trip is lossless.
- Every validation rule has a fixture that must fail and one that must pass.
- Three test-only kinds validate: an event graph with execution and data wires, an
  ordered-children state graph with cycles, and an acyclic data flow. This proves the model is
  not shaped around one consumer.
- A graph naming an unregistered node type is refused.
- The module has no compose or render dependency.

### Phase 2 — `:awake:ui:node-graph-canvas`

The canvas widget as designed above.

**Gate:**
- Interaction tests: connect; reject an incompatible wire; box select; zoom keeps the world
  point under the cursor fixed.
- A headless raster baseline of a fixed graph, including a highlighted node.
- At rest, a 200-node graph stays under a bytes-per-frame ratchet (`NodeGraphCanvasFrameProbe`).
  Measured 343 KB with every node visible and 451 KB for an editor view; nearly all of it is the
  engine's per-node text work, and the canvas's own share (wires, culling, zoomed-out labels) was
  cut to a few KB.

## Limits and follow-ups

- **Not in v1:** subgraphs, groups, comments, minimap, auto-layout, copy and paste.

## Open decisions

1. **Undo.** The canvas holds no history and the caller applies intents (recommended). Confirm
   that Studio's command stack can take them.
2. **Studio plugin home for blueprints.** `visual-blueprints` (recommended).
