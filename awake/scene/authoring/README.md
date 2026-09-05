# Awake Scene Authoring DSL (`:awake:scene:authoring`)

The `:awake:scene:authoring` module provides a component-agnostic, type-safe DSL for building 3D scene entity hierarchies, lighting, camera rigs, system schedules, and scene-level Compose UI overlays.

## Core Concepts

1. **`EntityScope`**: Pure, component-agnostic configurator managing an entity ID and attaching components via `with(component)` or `configure(::Component)`.
2. **Domain-Specific DSL Extensions (`io.github.awakelab.awake.scene.authoring.dsl.*`)**:
   - **`SceneTransformDsl`**: `transform(x, y, z, sx, sy, sz)` / `transform(position)`
   - **`SceneLightingDsl`**: `sun()`, `lamp()`, `directionalLight(...)`, `pointLight(...)`
   - **`SceneCameraDsl`**: `camera(...)`, `defaultOrbitCamera(...)`, `cameraEntity(...)`
   - **`SceneMeshDsl`**: `mesh(...)`, `meshRenderer(...)`, `meshEntity(...)`

---

## Minimal Example

```kotlin
val game = app {
    window {
        title = "3D Scene Example"
        size(1280, 720)
    }

    ecs {
        name("main-scene")

        // 1. Primary Camera
        entity("camera") {
            camera(mode = CameraMode.ThirdPerson, primary = true)
            transform(y = 2f, z = 8f)
        }

        // 2. Directional Sunlight
        sun(direction = Vec3f(0.4f, 0.8f, 0.4f), intensity = 1.2f)

        // 3. Mesh Entity
        entity("cube") {
            transform(position = Vec3f(0f, 1f, 0f))
            mesh(mesh = cubeMesh, material = defaultMaterial)
        }

        // 4. System Schedules
        fixedSystem("physics") { PhysicsSystem() }  // Deterministic simulation
        frameSystem("camera") { CameraFollowSystem() } // Per-present frame rendering

        // 5. Scene UI Overlay
        ui {
            ShadcnButton("Pause Game")
        }
    }
}
```
