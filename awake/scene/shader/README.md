# `awake:scene:shader`

The `shader_effect` scene component: a shader a project ships as data, drawn at a node. The document
itself, its checks and its compiler are `awake:asset:shader-document`, which has no scene dependency;
this is the wrapper that loads a scene's documents and runs them over its world.

```kotlin
implementation(project(":awake:scene:shader"))
```

## What is in it

- `SceneShaderEffect` -- the scene schema: the document's project path, parameter values, the image for
  each texture, and whether it draws. `ShaderEffectBinding` puts it on an entity as a
  `ShaderEffectSource`. `DefaultSceneComponentResolvers.install()` registers it.
- `loadShaderEffects(document, assets)` -- reads and compiles each distinct document a scene names once,
  decodes the images, and checks each effect against its document. It never throws: a failure is
  logged with the node and the parameter or texture, and only that effect is not drawn.
- `ShaderEffectSystem` -- attaches each effect through the renderer's `ContentFeatureHost`, advances
  its clock, copies the node's place into a plane's model matrix, writes changed parameters in place,
  and attaches again when the document, its textures or the loaded assets change. A failed attach
  keeps what was drawing.

`loadProject` and `runProject` do all of this for a project, so a project that ships a shader
document needs no Kotlin of its own to draw it.
