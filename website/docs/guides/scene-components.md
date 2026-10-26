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

Outside Core, apply the published plugin at the same version as your Core SDK and name the registry.
Resolve plugins from Maven Central and the Gradle Plugin Portal in `pluginManagement.repositories`;
include Google's repository for the Android build tools used by Awake's build plugins.

```kotlin title="build.gradle.kts"
plugins {
    id("com.awakekt.awake.plugin.scene-components") version "<version>"
}

awakeSceneComponents {
    registry.set("com.awakekt.awake.scene.core.SceneCoreSceneComponents")
}
```

The module must already apply Kotlin Multiplatform. Add `com.awakekt.awake.scene:binding:<version>` to
`commonMain` for the annotations and runtime contracts, and apply Kotlin serialization for serializable
schemas. Configure at least two targets so Kotlin creates the shared metadata compilation. The plugin
runs KSP once on `commonMain`, then every target compiles that generated source.
It resolves `com.awakekt.awake.scene.binding:processor` at the plugin's own release version, independent
of your project's version. `awakeSceneComponents.processorVersion.set("<version>")` explicitly overrides
that selection for a locally published SDK. Inside Core, the same plugin uses its processor project.

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
