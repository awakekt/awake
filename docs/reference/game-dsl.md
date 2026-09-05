# Application & Game DSL

This page is the quick guide for Awake's root application and game shell DSL.

## Goal

Keep the application shell small, readable, and transparent while letting reusable game content live in modules.

## The Two Core Concepts

### 1. Root Application (`app { ... }`)

Use `app { ... }` at application entrypoints (`main()`) to configure the window shell and install feature modules:

```kotlin
fun main() {
    val game = app {
        window {
            title = "My Awake Application"
            size(1280, 720)
            backend.vulkan()
        }
        render { frame ->
            // Update game state
        }
    }

    game.run()
}
```

### 2. Reusable Feature Modules (`appModule { ... }`)

Use `appModule { ... }` (or `gameModule { ... }`) to package scene ECS content, Compose UI features, and services into reusable, testable slices:

```kotlin
fun myFeatureModule(state: MyRuntimeState) = appModule {
    ecs(mySceneSpec(state))
    ui(myUiFeature(state))
}

// Composed at the application root:
fun main() {
    val state = MyRuntimeState()

    val game = app {
        window {
            title = "Engine Showcase"
            size(1600, 900)
            backend.vulkan()
        }
        module(myFeatureModule(state))
    }

    game.run()
}
```

This keeps:
- The **application root** responsible for window configuration and platform lifecycle.
- The **feature module** responsible for scene composition, UI features, and runtime state wiring.

---

## Composing Multiple Modules

Modules can stack other sub-modules seamlessly:

```kotlin
val debugModule = appModule { ... }
val hudModule = appModule { ... }

val mainModule = appModule {
    module(debugModule)
    module(hudModule)
}
```

## Proof & Verification

The application DSL is backed by tests in `:awake:engine:bootstrap`:

- [AppLifecycleDslTest.kt](../../awake/engine/bootstrap/src/commonTest/kotlin/io/github/awakelab/awake/engine/bootstrap/AppLifecycleDslTest.kt)

Run tests with:

```bash
./gradlew :awake:engine:bootstrap:desktopTest
```
