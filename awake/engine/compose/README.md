# Awake Compose App Integration

`awake:engine:compose` connects one `ComposeHost` to an Awake application. It depends on Platform
and `awake:compose:ui`; Platform never depends on it.

```kotlin
val lifecycle = app {
    module(
        composeAppModule(content = {
            Text("Hello, Awake")
        }),
    )
}
```

The module converts the app frame's input, stages UI primitives, mirrors text-focus and cursor
ownership, and presents UI-only applications. An application may install exactly one.

For a 3D scene, use `sceneComposeAppModule` before `sceneSession {}`. It uses the same host but
does not present; the scene's render schedule performs the single presentation.
