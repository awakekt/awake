# Scene components

<p class="awake-lede">A scene component is a live ECS component and the serializable schema a scene document saves it as. Annotate the schema with <code>@SceneBinding</code> and the build writes the binding between them, both ways, and lists it in the module's registry.</p>

<div class="awake-badges" markdown>
<span class="awake-badge">module: <code>awake:scene:binding</code></span>
<span class="awake-badge">plugin: <code>com.awakekt.awake.plugin.scene-components</code></span>
<span class="awake-badge awake-badge--ok">JVM</span>
<span class="awake-badge awake-badge--ok">Android</span>
<span class="awake-badge awake-badge--ok">iOS</span>
<span class="awake-badge awake-badge--ok">Wasm</span>
</div>

Every component id a scene document names is loaded and saved by a `SceneComponentBinding`: it attaches
the live component when a scene loads and exports it when a world is saved. Most bindings copy each field
across and back. The binding processor writes those at compile time, on every target, so a field added on
one side and forgotten on the other is a build error rather than data lost on save.

## Add a scene component

Apply the plugin to the scene module and name the module's registry:

```kotlin title="build.gradle.kts"
plugins {
    id("com.awakekt.awake.plugin.scene-components")
}

awakeSceneComponents {
    registry.set("com.awakekt.awake.scene.core.SceneCoreSceneComponents")
}
```

Then annotate the schema with the live component it saves. This is the `tag` component, whose live
`Tags` names its property `names`:

```kotlin title="Kotlin"
--8<-- "awake/scene/scene-core/src/commonMain/kotlin/com/awakekt/awake/scene/core/SceneTag.kt:tag"
```

The build generates `TagBinding`, with `SceneTag.toComponent()` and `Tags.toSceneComponent()`, and lists
it in `SceneCoreSceneComponents`. The annotation goes on the schema, never on the live component: the live
component often lives in a capability module that knows nothing of scenes.

A binding that is mostly copies and a little logic adds converters and hooks. `fog` converts its saved
`SceneColor` to a live `Color`, and reads the separate channels older documents wrote:

```kotlin title="Kotlin"
--8<-- "awake/scene/scene3d/src/commonMain/kotlin/com/awakekt/awake/scene/rendering/fog/SceneFog.kt:fog"
```

## Field rules

| Marker | Goes on | What it does |
| --- | --- | --- |
| `@SceneBinding(component)` | the schema | Generates `<Component>Binding`, or `name`, in the schema's package |
| `@SceneBinding(component, whole = "value")` | the schema | Loads the whole live value from that property and saves it back under the same property |
| `@RuntimeOnly` | a live property | Never saved; it keeps its constructor default on load |
| `@SceneBinding(runtimeOnly = [...])` | the schema | The same, for a live component the module can't annotate |
| `@SavedAs("name")` | a live property | Saves it under another schema property |
| `@SceneBinding(renamed = [SavedAs("name", property = "live")])` | the schema | The same, for a live component the module can't annotate |
| `@SavedOnly` | a schema property | Has no live field, such as a legacy one; it is written with its default |
| `@SceneBinding(converters = [...])` | the schema | `SceneValueConverter` objects for a field whose two sides differ in type |
| `@SceneBinding(hooks = ...)` | the schema | A `SceneBindingHooks` object run around the copy |
| `@RegisteredBinding` | a hand-written binding object | Lists it in the module's registry |
| `@UnboundSceneComponent("reason")` | a schema | Passes the check below for a schema this module doesn't bind |

Fields pair by name. Equal types copy as they are, two enums with the same constants map by name, and any
other pair needs a converter. The build fails, naming the field, when:

- a live constructor property or public property holding state has no saved field, or a saved field has
  no live one;
- the two types differ and no converter matches, or one side is nullable and the other is not;
- a live constructor parameter that is not saved has no default;
- a scene component in the module has no binding, or a public binding object isn't `@RegisteredBinding`.

## Whole-value components

A saved form may wrap the live component instead of repeating its fields:

```kotlin
@Serializable
@SerialName("canvas_layout")
@SceneBinding(CanvasLayout::class, whole = "layout")
data class SceneCanvasLayout(val layout: CanvasLayout = CanvasLayout()) : SceneComponent
```

The generated `toComponent()` returns `saved.layout`; `toSceneComponent()` builds
`SceneCanvasLayout(layout = live)`. No live constructor or property setters are needed. The named
property must be a readable constructor property with the live component's non-null type, or a type
converted to it by a listed `SceneValueConverter`. Every other schema constructor parameter must have
a default, which export uses; copy hooks can read or adjust those fields. `@SavedOnly` cannot mark
the whole property, and `runtimeOnly`/`renamed` cannot be combined with `whole`. The live value's own
serialization determines its nested saved fields. Omit `whole` to keep the field-copy behavior.

## Hooks

`SceneBindingHooks<C, S>` has four methods, each doing nothing by default; the generated binding calls only
the ones its hooks object overrides.

| Hook | Runs | Use it for |
| --- | --- | --- |
| `afterCopyToLive(saved, live)` | in `toComponent()`, after the copy | A migration from a `@SavedOnly` field |
| `afterCopyToSaved(live, saved)` | in `toSceneComponent()`, after the copy | A value derived when saving |
| `afterAttach(world, entity, saved, live, context)` | after the component is added | Work that needs the world, such as `context.deferNodeLink` |
| `beforeExport(world, entity, live, saved)` | when a world is saved | Replacing the saved form, or returning null to save none |

Keep a binding hand-written, and mark it `@RegisteredBinding`, when it resolves asset references to loaded
handles (meshes, materials, textures, audio), links entities through `SceneResolutionContext`, creates
native or platform objects (physics bodies, audio players, terrain pages), or reshapes a whole format.

## Register the components

Nothing registers a module by itself. Core's default modules are in
`DefaultSceneComponentResolvers.modules`; an app or a scene registers the ones it uses:

```kotlin title="Kotlin"
SceneComponentRegistry.scoped().register(SceneCoreSceneComponents).register(Scene3dSceneComponents)
```

A scene capability passes `MyGameSceneComponents.bindings` as its `components`.

## How it works

The plugin runs the KSP processor once, over `commonMain`, so every target compiles the same generated
sources and common code can name them. Kotlin/Native and Wasm cannot scan classes at runtime, which is why
the registry is generated rather than discovered. A generated binding is an ordinary
`SceneComponentBinding` `object`, and uses the schema's `serializer()`, so registering it also registers
the component id for decoding.

## See also

- [Scene documents](scene-documents.md) for loading, saving and registries.
- [Scene capabilities](scene-capabilities.md) for adding a game's own components and systems.
- [Scene document components](../reference/scene-document-components.md) for every component id.
