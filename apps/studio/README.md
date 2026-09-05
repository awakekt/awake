# Awake Studio (`:apps:studio`)

**Awake Studio** is the official visual development and inspection environment for Kotlin Multiplatform 3D/2D games powered by Awake Engine.

It provides a scene viewport, camera orbit controls, transform gizmos, hierarchy trees, dockable tooling, asset viewers, and live component inspection.

---

## 1. Quick Start: Launching Studio

### Standalone Default Launch
```kotlin
import io.github.awakelab.awake.studio.app.studioApp

fun main() {
    studioApp().start()
}
```

### Launching for Your Game (Custom Scene / Fixture Bypassed)
```kotlin
fun main() = studioApp {
    title = "My Game Studio"
    width = 1600
    height = 900
    includeDefaultFixture = false // disables demo cube & ground plane
    install(MyGamePlugin())
}.start()
```

---

## 2. Using Studio for Your Game (Data & Logic Only — Zero UI)

As a game developer, you **do not** need to write Compose UI or custom panels. You only need to contribute your game's:
1. **ECS Data Components** (pure Kotlin data classes)
2. **Standard Inspector Fields** (via `SceneComponentInspector` — generates standard property fields in the Inspector panel)
3. **ECS Systems** (via `SceneSystemPlugin` — executes in the scene frame loop)
4. **Custom Mesh Assets** (via `AssetResolverPlugin` — provides meshes, materials, and GPU assets)

### Complete Example

```kotlin
package com.mygame.combat

import io.github.awakelab.awake.ecs.Entity
import io.github.awakelab.awake.ecs.System
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.editor.EditorPlugin
import io.github.awakelab.awake.editor.EditorPluginApi
import io.github.awakelab.awake.editor.EditorPluginId
import io.github.awakelab.awake.editor.EditorPluginMetadata
import io.github.awakelab.awake.editor.EditorProvider
import io.github.awakelab.awake.editor.EditorProviderId
import io.github.awakelab.awake.editor.EditorProviderMetadata
import io.github.awakelab.awake.editor.scene.inspector.SceneComponentInspector
import io.github.awakelab.awake.editor.scene.inspector.SceneFieldScope
import io.github.awakelab.awake.editor.scene.plugin.AssetResolverPlugin
import io.github.awakelab.awake.editor.scene.plugin.SceneSystemPlugin
import io.github.awakelab.awake.scene.authoring.SceneAssetsDsl
import io.github.awakelab.awake.scene.authoring.SceneSystemsDsl
import io.github.awakelab.awake.studio.app.studioApp
import kotlin.reflect.KClass

// -------------------------------------------------------------
// 1. Data Component (Pure state, zero UI dependencies)
// -------------------------------------------------------------
class HealthComponent(
    var currentHp: Float = 100f,
    var maxHp: Float = 100f,
    var isInvulnerable: Boolean = false,
)

// -------------------------------------------------------------
// 2. Logic System (Simulation step in the scene loop)
// -------------------------------------------------------------
class CombatRegenSystem : System {
    override fun update(world: World, delta: Float) {
        world.family<HealthComponent>().forEach { _, health ->
            if (health.currentHp < health.maxHp) {
                health.currentHp = (health.currentHp + 5f * delta).coerceAtMost(health.maxHp)
            }
        }
    }
}

// -------------------------------------------------------------
// 3. Component Inspector (Emits typed fields in Studio Inspector)
// -------------------------------------------------------------
class HealthComponentInspector : SceneComponentInspector {
    override val metadata = EditorProviderMetadata(
        id = EditorProviderId("mygame.combat.health"),
        displayName = "Health",
    )
    override val componentType: KClass<out Any> = HealthComponent::class

    override fun fields(scope: SceneFieldScope, world: World, entity: Entity) {
        val health = world.get<HealthComponent>(entity) ?: return
        scope.scalar("Current HP", health.currentHp) { health.currentHp = it }
        scope.scalar("Max HP", health.maxHp) { health.maxHp = it }
        scope.toggle("Invulnerable", health.isInvulnerable) { health.isInvulnerable = it }
    }
}

// -------------------------------------------------------------
// 4. Game Plugin Bundle
// -------------------------------------------------------------
class CombatGamePlugin : EditorPlugin, SceneSystemPlugin, AssetResolverPlugin {
    override val metadata = EditorPluginMetadata(
        id = EditorPluginId("mygame.combat"),
        displayName = "Combat System",
        version = "1.0.0",
        requiredApiVersion = EditorPluginApi.currentVersion,
    )

    override fun createProviders(): List<EditorProvider> = listOf(
        HealthComponentInspector(),
    )

    override fun registerSystems(dsl: SceneSystemsDsl) {
        dsl.frameSystem("combat-regen") { CombatRegenSystem() }
    }

    override fun registerAssets(dsl: SceneAssetsDsl) {
        // Register custom game meshes, textures, materials
    }
}

// -------------------------------------------------------------
// 5. Studio Entry Point
// -------------------------------------------------------------
fun main() = studioApp {
    title = "Awake Studio - My RPG"
    includeDefaultFixture = false
    install(CombatGamePlugin())
}.start()
```

---

## 3. Contributing Custom UI: Left Panel, Right Panel & Bottom Dock

If your plugin wants to render custom UI tabs or tools:

### Left Sidebar Tab (`EditorSidebarContribution`)
Adds a view alongside the default **Hierarchy** tree in the left panel:

```kotlin
class ProjectExplorerSidebar : EditorSidebarContribution {
    override val metadata = EditorProviderMetadata(
        id = EditorProviderId("game.explorer"),
        displayName = "Project Explorer",
    )
    override val tab = EditorSidebarTab("project-explorer", "Explorer")

    context(_: Composer)
    override fun content() {
        ShadcnText("Custom Project Files & Prefabs")
    }
}
```

### Right Inspector Tab (`EditorInspectorContribution`)
Adds a global view alongside the entity **Inspector** in the right panel:

```kotlin
class WorldEnvironmentInspector : EditorInspectorContribution {
    override val metadata = EditorProviderMetadata(
        id = EditorProviderId("game.env"),
        displayName = "Environment Settings",
    )
    override val tab = EditorInspectorTab("env-settings", "Environment")

    context(_: Composer)
    override fun content() {
        ShadcnText("Skybox, Sun Angle, Fog, and Ambient Color")
    }
}
```

### Bottom Dock Tab (`EditorDockContribution`)
Adds a panel alongside **Console**, **Assets**, and **Files** in the bottom drawer:

```kotlin
class CombatLogDock : EditorDockContribution {
    override val metadata = EditorProviderMetadata(
        id = EditorProviderId("game.combat.log"),
        displayName = "Combat Log",
    )
    override val tab = EditorDockTab("combat-log", "Combat Log")

    context(_: Composer)
    override fun content() {
        ShadcnText("Live combat events and DPS meters")
    }
}
```

---

## 4. How Scene Fixtures & Default Assets Work

- **Default Meshes:** Studio's procedural `cube` and `ground` meshes are supplied through `StudioPrimitiveAssetsPlugin` (implementing `AssetResolverPlugin`), rather than hardcoded in `StudioModule`.
- **Default Scene Fixture:** By default, `StudioFixtureSystem` runs to instantiate demo scene files (`rotating-cube.scene.json`, `cesium-man.scene.json`). Setting `includeDefaultFixture = false` disables the sample demo fixture so your game code or systems own entity creation.

---

## 5. Keyboard Shortcuts & Overridable Keybinding System

Awake Studio provides an overridable keybinding architecture (`EditorKeymap`) with industry-standard defaults.

### Default Shortcuts
| Action | Key Combination | Description |
|---|---|---|
| **Select Tool** | `Q` | Select interaction mode |
| **Translate Tool** | `W` | Activate 3D Translation Gizmo |
| **Rotate Tool** | `E` | Activate 3D Rotation Gizmo |
| **Scale Tool** | `R` | Activate 3D Scale Gizmo |
| **Focus Selection** | `F` | Centers camera view on selected entity |
| **Toggle Transform Space** | `X` | Toggles World vs Local transform orientation |
| **Toggle Snapping** | `Shift + S` | Toggles grid/angle snapping |
| **Undo** | `Ctrl/Cmd + Z` | Reverts last scene modification |
| **Redo** | `Ctrl/Cmd + Shift + Z` / `Ctrl/Cmd + Y` | Re-applies reverted modification |
| **Duplicate** | `Ctrl/Cmd + D` | Clones the selected entity |
| **Delete** | `Delete` / `Backspace` | Removes the selected entity |

### Rebinding Keys (e.g. Blender Style G/R/S)
You can easily rebind any action on startup using `keymap { ... }`:

```kotlin
fun main() = studioApp {
    keymap {
        // Rebind Translate to G, Rotate to R, Scale to S
        rebind(EditorStandardActions.TOOL_TRANSLATE, KeyChord(Key.G))
        rebind(EditorStandardActions.TOOL_ROTATE, KeyChord(Key.R))
        rebind(EditorStandardActions.TOOL_SCALE, KeyChord(Key.S))
    }
}.start()
```

### Contributing Custom Plugin Keybindings (`EditorKeybindingContribution`)
Any plugin can contribute its own shortcuts:

```kotlin
class LightBakingKeybindingContribution : EditorKeybindingContribution {
    override val metadata = EditorProviderMetadata(
        id = EditorProviderId("game.lighting.keybindings"),
        displayName = "Lighting Keybindings",
    )
    override val bindings = listOf(
        EditorKeybindingDefinition(
            action = EditorAction(EditorActionId("game.lighting.bake"), "Bake Lightmaps", "Baking"),
            defaultChord = KeyChord(Key.B, ctrl = true),
            execute = { context ->
                startBaking()
                true // consumed
            },
        ),
    )
}
```

