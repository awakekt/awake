# Jolt physics backend

The Jolt backend implements Awake’s physics contract with native Jolt bindings. It provides rigid
body dynamics, collision detection, and raycasting for the targets supported by the selected
release.

Keep application code dependent on the [Physics API](api.md) where possible, and install the Jolt
backend at the platform edge.
