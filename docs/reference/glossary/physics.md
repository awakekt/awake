# Physics glossary

Terms used by Awake's portable collision API and its Jolt backend. These describe collision,
not terrain rendering; see [3d.md](3d.md) for rendering vocabulary.

| Term | Plain meaning | In Awake |
|---|---|---|
| **collision shape** | A geometric description used to detect contacts and ray hits. | A portable value passed to `PhysicsWorld.createBody`. It is not a native handle, render mesh, or mutable simulation object. |
| **heightfield** | A surface whose height is sampled over a regular grid. | `HeightFieldShape` is square and row-major: sample `(x, z)` is `heights[z * sampleCount + x]`. It is a collision surface only, not a terrain renderer, material system, navigation mesh, or editable terrain API. |
| **sample spacing** | The horizontal distance between adjacent grid samples. | `scale.x` and `scale.z` are the spacing; `scale.y` multiplies each stored height. All scale components are finite and positive. |
| **corner origin** | A shape coordinate system whose origin lies at one corner rather than its centre. | A heightfield body's position is sample `(0, 0)`. Sample `(x, z)` is at `(x * scale.x, heights[index] * scale.y, z * scale.z)` relative to that position. Awake does not apply an undocumented centering offset. |
| **static-only** | A collider that cannot be simulated as moving. | Jolt heightfields support `MotionType.STATIC` only. `DYNAMIC` and `KINEMATIC` requests fail explicitly rather than producing an unstable or misleading body. |
| **capability gap** | A common API feature absent from one platform's installed binding. | The API remains portable, but the adapter throws `PhysicsCapabilityException` with the target-specific reason. This is preferable to a silently incomplete shape dispatch or pretending that a Jolt engine feature is necessarily exposed by every wrapper. |
| **height quantization** | Compressing stored samples into a finite representation. | Jolt heightfields are lossy at a small tolerance. Physics tests validate placement and row-major mapping with a meaningful tolerance, not bit-exact raycast heights. |
