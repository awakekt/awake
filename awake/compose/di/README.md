# Awake Compose DI (`:awake:compose:di`)

Bridge between **`:awake:core:di`** and **`:awake:compose:runtime`**. Delivers ambient dependency injection down the Awake Compose UI tree via `CompositionLocal`.

---

## 1. Core Primitives

- **`LocalContainer`**: `CompositionLocal<Container?>` ambient container holder.
- **`ProvideContainer(container) { ... }`**: Convenience container provider for Compose hierarchies.
- **`resolve<T>(qualifier = null)`**: Direct dependency resolution in any `context(_: Composer)` composable.
- **`rememberResolve<T>(qualifier = null)`**: Resolves and memoizes a dependency across composition frames for the current node.

---

## 2. Usage Example

```kotlin
// 1. Configure DI module & container
val appContainer = container(
    module {
        singleton<StudioPluginRepository> { DefaultStudioPluginRepository() }
        factory<ProjectService> { ProjectService() }
    }
)

// 2. Provide at the root or window boundary
context(_: Composer)
fun AppRoot() {
    ProvideContainer(appContainer) {
        MainScreen()
    }
}

// 3. Resolve dependencies in any descendant composable
context(_: Composer)
fun MainScreen() {
    val repository = resolve<StudioPluginRepository>()
    val projectService = rememberResolve<ProjectService>()
    
    // Use dependencies
}
```
