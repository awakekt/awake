# Awake Compose State (`:awake:compose:state`)

Bridge between **`:awake:core:state`** and **`:awake:compose:runtime`**. Integrates reactive stores (`Store<S>`) and unidirectional contract stores (`ReducerStore<S, I, E>`) into the Awake Compose UI engine.

---

## 1. Core Primitives

- **`rememberStore(key = null) { initialState }`**: Creates and memoizes a lightweight `Store<S>` across composition passes.
- **`rememberReducerStore(key = null, initialState = { ... }) { state, intent -> ... }`**: Creates and memoizes a `ReducerStore<S, I, E>` with pure reducer transitions.
- **`LocalStore`**: `CompositionLocal<Store<*>?>` ambient store holder.
- **`ProvideStore(store) { ... }`**: Provides a store to descendant composables.
- **`useStore<S>()`**: Resolves the ambient store managing state of type `S`.

---

## 2. Usage Examples

### A. Lightweight Local Panel State (`rememberStore`)

```kotlin
data class FilterState(
    val query: String = "",
    val activeCategory: String = "All",
)

context(_: Composer)
fun MarketplaceSearchPanel() {
    val store = rememberStore { FilterState() }
    
    // Direct frame snapshot read
    val currentQuery = store.value.query

    ShadcnInput(
        value = currentQuery,
        onValueChange = { q -> store.update { it.copy(query = q) } }
    )
}
```

### B. MVI / UDF Reducer State (`rememberReducerStore`)

```kotlin
data class CounterState(val count: Int = 0)
sealed interface CounterIntent { data object Increment : CounterIntent }
sealed interface CounterEffect { data class Toast(val message: String) : CounterEffect }

context(_: Composer)
fun CounterWidget() {
    val store = rememberReducerStore<CounterState, CounterIntent, CounterEffect>(
        initialState = { CounterState() }
    ) { state, intent ->
        when (intent) {
            is CounterIntent.Increment -> state.copy(count = state.count + 1) to CounterEffect.Toast("Count: ${state.count + 1}")
        }
    }

    ShadcnButton(
        label = "Count: ${store.value.count}",
        onClick = { store.dispatch(CounterIntent.Increment) }
    )

    // Synchronously drain one-shot effects without per-frame coroutines
    store.drainEffects().forEach { effect ->
        when (effect) {
            is CounterEffect.Toast -> showToast(effect.message)
        }
    }
}
```
