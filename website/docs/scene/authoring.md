# Scene authoring

The scene authoring DSL creates entities and attaches scene components without coupling the
authoring code to a particular renderer or physics implementation.

Add [`awake:scene:authoring`](../getting-started.md#scene-authoring) to `commonMain`. It brings the
scene authoring DSL and its public scene API dependencies.

## Authoring concepts

- `EntityScope` configures an entity and attaches components.
- Transform, camera, lighting, and mesh extensions provide concise scene syntax.
- Systems can be installed beside the authored entities.

The DSL is intended for application code and tools that produce a scene at runtime. For serialized
scene documents, use the scene runtime’s document model and loader.

## Author entities

The following scene setup is extracted from the compiled scene-authoring tests:

```kotlin
--8<-- "awake/scene/authoring/src/commonTest/kotlin/com/awakekt/awake/scene/authoring/SceneDslTest.kt:author-scene"
```
