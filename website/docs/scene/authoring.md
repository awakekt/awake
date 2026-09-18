# Scene authoring

The scene authoring DSL creates entities and attaches scene components without coupling the
authoring code to a particular renderer or physics implementation.

## Authoring concepts

- `EntityScope` configures an entity and attaches components.
- Transform, camera, lighting, and mesh extensions provide concise scene syntax.
- Systems can be installed beside the authored entities.

The DSL is intended for application code and tools that produce a scene at runtime. For serialized
scene documents, use the scene runtime’s document model and loader.
