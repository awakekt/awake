# `awake:node-graph`

Graph documents, node registry and validation shared by every node-graph editor and runtime in
Awake: blueprint logic, AI state trees and shader graphs. No UI and no runtime semantics. What a
node does belongs to whoever owns its graph kind.

Design and sequencing: [node-graph plan](../../docs/tasks/2026-09-27-node-graph-plan.md).

## What is here

- **`NodeGraph`**, **`GraphNode`**, **`GraphEdge`**: the document. Edge order is kept, which is
  how ordered children are stored.
- **`GraphKind`**: the rules a family of graphs shares, meaning which port types connect and
  whether cycles are allowed.
- **`NodeSpec`**, **`PortSpec`**, **`ConfigFieldSpec`**: what a node type looks like, with the
  descriptions a palette and a catalogue show.
- **`NodeRegistry`**: the explicit list of node types for one kind. `validate(graph)` reports
  every problem as a `GraphIssue` keyed by node id. `catalogue()` exports the vocabulary.
- **`NodeGraphJson`**: strict, pretty-printed JSON for graphs and catalogues. `load(text,
  registry)` refuses any graph that does not validate, whole.

## Usage

```kotlin
val kind = object : GraphKind {
    override val id = "my.logic"
    override val allowsCycles = false
}
val registry = NodeRegistry(kind)
    .register(NodeSpec("event.start", "On Start", outputs = listOf(PortSpec("then", "exec"))))
    .register(NodeSpec("action.log", "Log", inputs = listOf(PortSpec("exec", "exec", multiple = true))))

val graph = NodeGraphJson.load(text, registry) // throws InvalidNodeGraphException with every issue
```

## Architecture

Depends on `kotlinx-serialization-json` only. Game servers and headless tools that load or run
graphs never pull in UI. The editing canvas is a separate module, `:awake:ui:node-graph-canvas`.

## Not here yet

- Config values are checked for unknown keys only. Their types belong to the owning kind.
- Graph kinds do not migrate their own node vocabularies between versions; `formatVersion` covers
  the document format only.
