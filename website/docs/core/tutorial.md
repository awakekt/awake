# Core tutorials and samples

This is the task index for Awake's platform-neutral runtime. Start with the shared [installation
and first application guide](../getting-started.md), then add only the capability your application
needs.

## Available now

| Task | Guide | Evidence |
|---|---|---|
| Normalize a direction or choose a math primitive | [Math guide and verified samples](math.md) | Extracted from compiled math tests |
| Create the application shell | [Install and first app](../getting-started.md) | Compiler-checked showcase entry point |
| Understand entities, components, and systems | [ECS overview](../engine/ecs.md) | API and architecture overview |

## In progress

The next core walkthrough is an end-to-end ECS sample: create a world, define a component, run a
query, and connect the update step to an application. It will be added from a compiled test or
showcase fixture before it is presented as a copyable example.

This page is deliberately an index, not a copy of a module README. API signatures belong in KDoc;
these pages explain the task flow and link to the verified source that proves it.
