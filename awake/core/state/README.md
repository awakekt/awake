# Awake Core State (`:awake:core:state`)

A lightweight, zero-overhead Kotlin Multiplatform state management library for Awake Engine, Studio, and Compose applications across **Desktop (JVM), Mobile (Android / iOS), and Web (WasmJs)**.

---

## 1. Architectural Patterns & Equivalents

`awake:core:state` provides two complementary patterns under unified lifecycle scoping:

| Pattern | Mental Model | React / Web Equivalent | Android / Kotlin Equivalent | Best Used For |
| :--- | :--- | :--- | :--- | :--- |
| **`Store<S>`** | Lightweight reactive state holder | **Zustand** (`create((set) => ...)`) / **Pinia** | **StateHolder** (plain class with `StateFlow`) | Dialogs, inspector panels, toggles, form fields, UI preferences |
| **`ReducerStore<S, I, E>`** | Unidirectional Data Flow (UDF) contract | **Redux Toolkit** (`createSlice` + actions) / **Elm Architecture** (TEA) | **MVI** (Orbit MVI / Ballast) / **UDF Contract** | Scene editing, undo/redo, file save/load, background workflows |

---

## 2. Usage Examples

### A. `Store<S>` (Lightweight Reactive Store)

```kotlin
data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<String> = emptyList(),
)

// Create store
val store = store(SearchUiState())

// Direct synchronous value snapshot
println(store.value.query)

// Atomic transform update
store.update { it.copy(query = "terrain", isSearching = true) }

// Derived distinct sub-state selector
val isSearchingFlow: StateFlow<Boolean> = store.select { it.isSearching }

// Lifecycle cleanup
store.close()
```

### B. `ReducerStore<S, I, E>` (UDF / MVI Contract)

Follows the engine's Store/Contract boundary defined in `.agents/skills/awake-state-management`.

```kotlin
// 1. Contract
data class SceneEditorState(
    val selectedId: String? = null,
    val isModified: Boolean = false,
)

sealed interface SceneIntent {
    data class SelectObject(val id: String?) : SceneIntent
    data object RequestSave : SceneIntent
}

sealed interface SceneEffect {
    data class NotifyUser(val message: String) : SceneEffect
    data object TriggerFileSave : SceneEffect
}

// 2. Pure Reducer (Zero IO, zero coroutines)
val editorStore = reducerStore<SceneEditorState, SceneIntent, SceneEffect>(
    initialState = SceneEditorState()
) { state, intent ->
    when (intent) {
        is SceneIntent.SelectObject -> state.copy(selectedId = intent.id) to null
        is SceneIntent.RequestSave -> state.copy(isModified = false) to SceneEffect.TriggerFileSave
    }
}

// 3. Dispatch user intent
editorStore.dispatch(SceneIntent.RequestSave)

// 4. Frame-Loop Safe Effect Draining (Synchronous, single-consumption)
// In a game engine or render loop, drain once per frame:
editorStore.drainEffects().forEach { effect ->
    when (effect) {
        is SceneEffect.TriggerFileSave -> saveCurrentDocument()
        is SceneEffect.NotifyUser -> showToast(effect.message)
    }
}

// Or asynchronously in Compose / Coroutines:
coroutineScope.launch {
    editorStore.effects.collect { effect ->
        // Handle effect
    }
}
```

---

## 3. Platform Lifecycle & Coroutine Scopes

Every store implements `StoreScope` (`AutoCloseable`):
- **Desktop (JVM / GLFW / Vulkan)**: Bound to the session or window. Call `store.close()` when a workspace or dialog is closed to cancel background jobs.
- **Mobile (Android / iOS)**: Bound to the Activity/Screen lifecycle or Android `ViewModel.viewModelScope`. Calling `store.close()` releases all observers.
- **Web (WasmJs)**: Operates within the browser's cooperative single-threaded event loop on `Dispatchers.Default`.
