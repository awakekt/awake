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

## Why it is its own module

These were in `ai:behavior`, so the behaviours could not be used without the scene layer, and the
scene bindings lived outside `awake/scene/`. The dependency now runs one way: this module and
`scene:navigation` use `ai:behavior` and `navigation`, and nothing there knows a scene exists.
