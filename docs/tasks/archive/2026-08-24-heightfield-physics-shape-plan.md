# Heightfield collision shape plan

**Status:** in progress — implementation authorized 2026-08-24. Reviewed against the checked-in
Jolt sources, pinned jolt-jni source jar, target adapters, and CI configuration.

## Decision

Add `HeightFieldShape` to `:awake:physics:api` as a portable **collision** description. It is
not a terrain renderer, mesh generator, texture-layer system, navigation mesh, or mutable world
terrain API. `:awake:backend:jolt` adapts it to Jolt on every target it currently supports.

```kotlin
data class HeightFieldShape(
    val heights: FloatArray,
    val sampleCount: Int,
    val scale: Vec3f,
) : PhysicsShape
```

`heights` is row-major: `index = z * sampleCount + x`. `scale.x` and `scale.z` are horizontal
sample spacing; `scale.y` multiplies the stored height. World placement remains the existing
`PhysicsWorld.createBody(position, rotation, motionType)` contract.

Do not encode a Jolt implementation preference such as a power-of-two grid in the common type
until the native binding proves it is required. A future general terrain asset may need rectangular
`width`/`depth`; that is a separate API decision.

## Why this belongs here

- `PhysicsShape` already owns backend-neutral, native-handle-free shape descriptions
  (`BoxShape`, `SphereShape`).
- `:awake:physics:api` stays free of rendering and native dependencies.
- `:awake:backend:jolt` already translates shapes in each target-specific `JoltPhysicsWorld`.
- Backends must not define terrain content. A Jolt heightfield is a collision capability, not a
`Terrain` rendering feature. The first implementation maps it through verified jolt-jni support
on desktop and Android. iOS and wasmJs reject it with the same explicit capability exception
until their binding surfaces expose a constructor; that is deliberate, not an API claim of parity.

## Verify before starting — do not begin step 3 on an assumption

**`PhysicsWorld.kt`'s own doc comment is stale and must not be trusted for this plan.** It
currently reads: *"a JoltC cinterop backend for iOS and a JoltPhysics.js backend for wasmJs are
deferred, since jolt-jni/JoltC/JoltPhysics.js share zero code."* That is false today — both
`awake/backend/jolt/src/iosMain/.../JoltPhysicsWorld.kt` (393 lines, real `cnames.structs.JPC_*`/
`platform.joltc.*` cinterop) and `.../wasmJsMain/.../JoltPhysicsWorld.kt` (407 lines, real
`@JsFun`-bound `jolt-physics` calls) already implement `BoxShape`/`SphereShape` in full. Fix this
comment in the same change that adds the fourth shape, so the next reader is not misled the way
this plan's own drafting nearly was.

**The Jolt engine has a heightfield shape, but our binding surfaces do not have parity:**

| Target | Binding | Verified how |
|---|---|---|
| Desktop / Android | `com.github.stephengold.joltjni.HeightFieldShapeSettings` | Present in the pinned 5.2.0 source jar, with the native constructor and `ShapeSettings.create()` mapping. |
| wasmJs | `jolt-physics` JS bridge | The existing bridge exposes only the direct Box/Sphere construction path. Do not infer a usable `HeightFieldShapeSettings` constructor from upstream Jolt alone. |
| iOS | JoltC (`SecondHalfGames/JoltC`) | The checked-out wrapper exposes enum values but no heightfield settings/create functions. Adding it requires an intentional JoltC native-wrapper change. |

JoltC is not a full API mirror. The common API must communicate that limitation explicitly
rather than adding vendored native code incidentally to a Kotlin collision-shape change.

**Nothing currently runs `:awake:backend:jolt`'s desktopTest in CI.** It is not named in
`.github/workflows/ci.yml` — it only *compiles*, via the module-agnostic
`compileTestKotlinDesktop` step. Step 5 below will produce real tests that pass locally and never
run automatically unless this plan also adds a CI step naming them. Do that in the same change,
not as a follow-up — a test nobody runs is indistinguishable from no test, which is the exact
failure this repo lost several source sets to on 2026-08-24.

## Contract decisions to settle first

1. **Validation.** Require `sampleCount >= 4` (Jolt's default two-sample block requires at least
   two blocks), an exact `sampleCount * sampleCount` array length
   with overflow-safe checking, and strictly positive X/Z/Y scale. Define whether a non-finite
   sample fails at construction or at `createBody`; prefer construction-time validation. Confirm
   in step 2 whether Jolt's real `HeightFieldShapeSettings` constructor imposes its own
   constraint beyond `>= 2` (its quantization/compression scheme has historically wanted a sample
   count related to an internal block size) — it is enforced here, not discovered only at the
   native call. Power-of-two counts remain an optimization, not an API requirement.
2. **Ownership.** `FloatArray` is mutable and `data class` array equality is referential. Document
   that the input must not change after shape construction; the Jolt adapter copies or consumes it
   once while creating the native shape. Do not use `HeightFieldShape` as a structural cache key.
   (This is a real, already-hit bug class in this repo — `ShaderStages`' identity equality
   surprised a test written the same day this plan was drafted. Treat the warning as concrete,
   not hypothetical.)
3. **Local origin and bounds.** Jolt defines a sample as
   `offset + scale * (x, sample[z * sampleCount + x], z)`. The common contract adopts that
   corner origin directly: body position is sample `(0, 0)`, with an adapter offset of zero.
   It must not add a hidden centering correction.
4. **Motion support.** Start with `STATIC` only unless Jolt's heightfield shape is demonstrated to
   support the requested dynamic/kinematic semantics. Fail clearly rather than creating a body
   with surprising behavior. **Decide the failure mechanism once, here, not per backend**:
   `PhysicsWorld.createBody` returns a non-null `BodyHandle` today, with no `Result` type and no
   declared exception — so "fail clearly" currently has no defined shape to fail *in*. Add one
   shared function in `:awake:physics:api`, e.g.
   `HeightFieldShape.requireSupportedMotionType(motionType: MotionType)` throwing one named
   exception type, and have all four backends call it. The alternative — four backends each
   inventing their own rejection — is the same "same decision made four times" defect this
   repo's rendering side spent 2026-08-24 fixing (`RenderCapabilities`/`narrowedTo`); do not
   reintroduce it here.
5. **Target rule.** Every Jolt adapter handles the new sealed subtype: desktop/Android construct
   it; iOS/wasmJs throw `PhysicsCapabilityException` with a target-specific explanation. Do not
   leave a platform-specific `when (shape)` branch silently incomplete.

## Implementation sequence

1. Add the common `HeightFieldShape` plus unit tests for indexing, dimensions, validation, and
   documented array-ownership behavior in `:awake:physics:api`. Fix `PhysicsWorld.kt`'s stale
   "iOS/wasmJs deferred" doc comment in this same step.
2. Inspect the pinned Jolt/JoltC/JoltPhysics.js heightfield constructors and their constraints;
   record the exact sample-count, scale, origin, and lifetime mapping before exposing bindings.
3. Add the shared `requireSupportedMotionType` validation and `PhysicsCapabilityException` in
   `:awake:physics:api`, then use the narrow verified jolt-jni settings constructor. iOS and
   wasmJs receive explicit rejections; native/JoltPhysics.js bridge expansion is a later task.
4. Extend each `JoltPhysicsWorld.createBody` dispatch and native cleanup path. Native shapes must
   be released through the existing body/world lifecycle, never retained by the Kotlin shape.
5. Add backend tests: raycast known samples and body placement/local-origin check. A dynamic
   settling test is invalid for Jolt heightfields, which are static-only.
6. Add a small physics demo only after the shape contract is verified. It may use a temporary
   visual mesh, but does not promote rendering code into `physics:api`.
7. **Add a CI step that runs `:awake:backend:jolt:desktopTest`.** It is not there today; do not
   let this feature's tests join the pile of tests that only compile.

## Verification matrix

| Concern | Proof |
|---|---|
| Common contract | Invalid dimensions/scales fail; row-major samples map to expected coordinates. |
| Native mapping | A deliberately asymmetric 3×3 field raycasts at known X/Z points and returns expected Y — this is the fixture that proves or disproves the corner-origin risk in contract decision 3. |
| Placement | Body `position` translates the local field without an undocumented center/origin correction. |
| Simulation | Dynamic/kinematic creation rejects consistently; Jolt heightfields are static-only. |
| Target parity | Every current Jolt adapter supports the subtype or emits `PhysicsCapabilityException`; no `when` is incomplete. |
| Lifecycle | Destroying the body/world releases native heightfield state; repeated create/destroy is covered. |
| CI | `:awake:backend:jolt:desktopTest` runs as a named CI step, not only as a compile check. |

## Explicit non-goals

- GPU terrain rendering, clipmaps, LOD, splat maps, displacement shaders, or terrain materials.
- Editing heights after body creation, dirty-region uploads, or collider rebuild scheduling.
- A shared `HeightField` rendering asset. Revisit only when collision and rendering demonstrate a
  real common data contract.
- New backend terrain vocabulary or a Vulkan/WebGPU-specific terrain pipeline.

## Exit criteria

The feature is done when the common shape contract, jolt-jni mapping, explicit unsupported
adapters, lifecycle, and physics behavior are proven by tests; no rendering module or backend
declares terrain content; and `:awake:backend:jolt:desktopTest` runs in CI rather than only
compiling.
