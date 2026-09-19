# Jolt physics backend

The Jolt backend implements Awake’s physics contract with native Jolt bindings. It provides rigid
body dynamics, collision detection, and raycasting for the targets supported by the selected
release.

Add [`awake:backend:jolt`](../getting-started.md#physics) to the supported platform source sets.
Check the release page for target support; Jolt is not a browser WebGPU backend.

Keep application code dependent on the [Physics API](api.md) where possible, and install the Jolt
backend at the platform edge.

## Simulate a falling body

This body creation and fixed-step example is extracted from the compiled Jolt backend smoke test.
The surrounding test creates and destroys the physics world and checks that gravity moved the box.

```kotlin
--8<-- "awake/backend/jolt/src/commonTest/kotlin/com/awakekt/awake/physics/jolt/JoltBackendSmokeTest.kt:jolt-falling-body"
```
