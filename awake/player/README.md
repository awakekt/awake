# `awake:player`

Standalone Kotlin Multiplatform runtime for playing an Awake project **or** a single declarative
`.scene.json` without Awake Studio.

## Play a full Awake project

```kotlin
val game = loadPlayerProject(bundledProjectAssets())
val app = createPlayerApp(game)
```

Pass any `AssetSource` — bundled classpath resources (`bundledProjectAssets()`), a packed directory
(`directoryAssetSource(path)` on desktop), Android assets, or a remote `IndexedAssetSource`.

## Play a single declarative scene (no `awake.project.json` required)

```kotlin
val game = loadPlayerScene(
    scenePath = "scenes/island.scene.json",
    assets = myAssets,
)
val app = createPlayerApp(game)
```

Or directly from a scene JSON string:

```kotlin
val game = loadPlayerSceneDocument(sceneJson, name = "Sandbox")
val app = createPlayerApp(game)
```

## Custom Kotlin gameplay features and HUD overlays

Add custom `SceneFeature`s through `AwakePlayerConfig.features`, and pass an optional Compose
`overlay` lambda to `createPlayerApp(game, config) { ... }`. Always call `game.close()` when the
player stops so native physics memory is freed.
