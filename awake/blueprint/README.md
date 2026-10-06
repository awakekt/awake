# `awake:blueprint`

The runtime for blueprints: event-driven game logic stored as [`awake:node-graph`](../node-graph/README.md)
documents, edited in Awake Studio and reloaded into a running game. Coders write node types in
Kotlin; non-coders wire them.

Design and decisions: the ECS binding and the nodes that reach into scene components are `:awake:scene:blueprint`.

## Writing a node

A node type is one object carrying its `NodeSpec` beside its behaviour. There are four shapes:
- `EventNode`: starts a chain;
- `ActionNode`: runs, then continues;
- `PureNode`: computes outputs on demand;
- `LatentNode`: an action that waits.

```kotlin
object PlaySound : ActionNode {
    private val then = Step.Continue("then")
    override val spec = NodeSpec(
        type = "audio.play",
        displayName = "Play Sound",
        category = "Audio",
        inputs = listOf(PortSpec("exec", PortTypes.EXEC, multiple = true), PortSpec("clip", PortTypes.ASSET)),
        outputs = listOf(PortSpec("then", PortTypes.EXEC)),
    )
    override val effect = Effect.Presentation

    override fun run(ctx: BlueprintContext): Step {
        audio.play(ctx.asset("clip"))
        return then
    }
}
```

- Keep `Step.Continue` instances in the node, so returning one allocates nothing.
- Registering a node makes each data input settable on the node, which is how an unwired input gets
  its value.
- `Effect.Presentation` marks nodes a server skips.

## Running a graph

```kotlin
val nodes = BlueprintNodes.core().register(PlaySound)
val program = BlueprintCompiler.compile(graph, nodes)    // throws InvalidNodeGraphException with every issue
val instance = BlueprintInstance(program, owner = entity)
val interpreter = BlueprintInterpreter()

interpreter.start(instance)                              // fires On Start once
interpreter.fire(instance, "event.sensor.enter") { it.setEntity("other", body) }
interpreter.tick(instance, delta)                        // every fixed step: polls waits
interpreter.reload(instance, BlueprintCompiler.compile(edited, nodes))
```

- **Execution.** Execution wires run depth-first, and several wires from one output run in wire
  order.
- **Data.** Data is read when the node that needs it runs, and a pure node runs at most once per
  step.
- **Waits.** A wait triggered again while it is waiting restarts; it never runs twice at once.
- **Reload.** Reload keeps variables whose name and type still exist, cancels waits and fires
  nothing.
- **Variables.** `instance.setVariable(name, JsonPrimitive(2.5))` sets a starting value, as scene
  overrides do.
- **Tracing.** Set `instance.trace = BlueprintTrace()` to record executed nodes for a debugger.

## Core nodes

`CoreNodes`, with no scene dependency:
- `On Start`;
- `Branch` and `Delay`;
- `Add` and `Greater`;
- `Get Variable` and `Set Variable` for float, int, bool, string and entity.

There is no sequence node: wire one execution output to several nodes instead.

## Guarantees

- A tick, a pending wait, and firing a chain of core nodes all allocate nothing; see
  `BlueprintAllocationProbe`.
- The compiler rejects data cycles, execution loops without a latent node in them, and a variable
  used as two types.
