<!--
SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz

SPDX-License-Identifier: Apache-2.0
-->

# Awake Core DI

Lightweight, zero-reflection, thread-safe, multiplatform dependency injection container for Kotlin Multiplatform.

Designed for high-performance game engines, simulation runtimes, and desktop/web/mobile applications without GC churn or runtime bytecode analysis.

## Features

- **Zero Reflection**: Uses Kotlin lambda factories and `KClass<T>` keys. 100% compatible with WasmJS, Native (iOS/Desktop), Android, and JVM.
- **Idiomatic Naming**: Clean names (`container`, `module`, `inject`, `get`, `verify`, `close`) without redundant `Di` prefixes.
- **Binding Strategies**:
  - `singleton { ... }`: Thread-safe lazy evaluation cached per container; `AutoCloseable` singletons are closed automatically in reverse creation order on `container.close()`.
  - `factory { ... }`: Evaluated fresh on every request.
  - `instance(value, closeOnDispose = false)`: Pre-instantiated object (opt-in `closeOnDispose = true` transfers `AutoCloseable` ownership to the container).
- **Hierarchical Scoping & Teardown**: Child containers (`container.createChild { ... }`) inherit parent bindings, support overrides, and close before parent singletons when the parent closes.
- **Fail-Fast Verification**: `container.verify()` validates that every binding in the container resolves without missing bindings or cycles.
- **Property Delegation**: `val service: MyService by container.inject()`.
- **Diagnostics**: Detects cyclic dependencies (reporting the full cycle chain), missing bindings, and post-close access.

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

// Fail-fast graph verification in tests
container.verify()

// Direct resolution
val physics = container.get<PhysicsEngine>()

// Property delegate
class GameSession(di: Container) {
    val loop: GameLoop by di.inject()
}

// Child scope with automatic AutoCloseable teardown
container.createChild {
    singleton<SceneContext> { SceneContext() }
}.use { sceneContainer ->
    val context = sceneContainer.get<SceneContext>()
}
```
