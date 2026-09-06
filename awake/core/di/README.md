<!--
SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz

SPDX-License-Identifier: Apache-2.0
-->

# Awake Core DI

Lightweight, zero-reflection, multiplatform dependency injection container for Kotlin Multiplatform.

Designed for high-performance game engines, simulation runtimes, and desktop/web/mobile applications without GC churn or runtime bytecode analysis.

## Features

- **Zero Reflection**: Uses Kotlin lambda factories and `KClass<T>` keys. 100% compatible with WasmJS, Native (iOS/Desktop), Android, and JVM.
- **Idiomatic Naming**: Clean names (`container`, `module`, `inject`, `get`) without redundant `Di` prefixes.
- **Binding Strategies**:
  - `singleton { ... }`: Lazy, thread-safe evaluation cached for the container lifecycle.
  - `factory { ... }`: Evaluated fresh on every request.
  - `instance(value)`: Pre-instantiated object.
- **Hierarchical Scoping**: Child containers (`container.createChild { ... }`) inherit parent bindings and can override or provide local scoped bindings (ideal for scene or session scopes).
- **Property Delegation**: `val service: MyService by container.inject()`.
- **Diagnostics**: Detects cyclic dependencies and missing bindings at resolution time.

## Usage

```kotlin
import com.awakekt.awake.core.di.container
import com.awakekt.awake.core.di.module
import com.awakekt.awake.core.di.get
import com.awakekt.awake.core.di.inject

val engineModule = module {
    singleton<PhysicsEngine> { DefaultPhysicsEngine() }
    factory<Actor> { Actor(get()) }
}

val container = container(engineModule) {
    singleton<GameLoop> { GameLoop(get()) }
}

// Direct resolution
val physics = container.get<PhysicsEngine>()

// Property delegate
class GameSession(di: Container) {
    val loop: GameLoop by di.inject()
}

// Child scope
val sceneContainer = container.createChild {
    singleton<SceneContext> { SceneContext() }
}
```
