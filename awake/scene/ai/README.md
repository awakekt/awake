# `awake:scene:ai`

The `patrol`, `chase` and `flee` scene components, and the placement the behaviour systems move a
scene's entities by. It is the wrapper; the behaviours themselves are `awake:ai:behavior`, which has
no scene dependency.

```kotlin
implementation(project(":awake:scene:ai"))
```

## What is in it

- `ScenePatrol`, `SceneChase`, `SceneFlee` -- the scene document schemas, with their validation, and
  `PatrolBinding`, `ChaseBinding`, `FleeBinding`, which attach `PatrolBehavior`, `ChaseBehavior` and
  `FleeBehavior` with the `PathRequest` each asks for routes through.
- `registerAiBehaviors()` and `AiBehaviorBindings` -- registers the three, and the `navigation`
  component from `awake:scene:navigation` that the behaviours route over.
- `TransformAgentPlacement` -- the `AgentPlacement` for a scene: an agent is where its `Transform`'s
  `position` says, and moves by rewriting it. Pass it to `PatrolAiSystem`, `ChaseAiSystem` and
  `FleeAiSystem`.
- `MovementAgentPlacement` -- the same, except that an agent whose `MovementControl` has the `Agent`
  driver is steered through that control: its world-space intent, and `moveSpeed` set to the
  behaviour's speed, which wins over `movement_control.speed`. With a character controller, walls
  stop the agent and slopes and steps carry it. Run `AgentIntentResetSystem` before the behaviours
  each frame, so an agent no behaviour steers any more (it arrived, or lost its target) stops.

## Why it is its own module

These were in `ai:behavior`, so the behaviours could not be used without the scene layer, and the
scene bindings lived outside `awake/scene/`. The dependency now runs one way: this module and
`scene:navigation` use `ai:behavior` and `navigation`, and nothing there knows a scene exists.
