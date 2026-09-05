# D29: Physics — When Awake Would Write Its Own Engine

## Decision

Awake builds on Jolt ([D5](../reference/decision-log.md)) and does **not** hold "write our own
physics" as a fallback for a Jolt capability gap. A missing binding is answered per-feature,
by a four-rung ladder, and the top rung — a hand-written rigid-body engine — is reached only
by a *product* trigger (cross-target determinism), never by a Jolt shortfall.

Two things follow immediately:

1. Policy that sits *over* physics queries is written once in Kotlin above `PhysicsWorld`,
   not bound per backend. The kinematic character controller is the first case
   ([physics-open-world.md](../plans/physics-open-world.md), Phase 2).
2. The one binding this depends on — `CastShape` on JoltPhysics.js — is spiked before Phase 1
   ends, as a dated check, not left to be discovered mid-Phase 2.

## Context

D5 chose Jolt and three bindings: jolt-jni (desktop + Android), custom JoltC cinterop (iOS),
JoltPhysics.js (wasmJs). Four implementations of one facade. The open question this decision
closes: what happens when one of them cannot supply something the engine needs, and does that
justify writing physics in Kotlin given `:awake:core:math` and `:awake:core:geometry` already
exist.

"If we notice we can't support KMP with Jolt, then we start writing our own" is not usable as
a trigger. It fires late — mid-phase, after commitment — or never, because the failure it
imagines does not happen in that shape.

## Why the failure will be partial

There is no plausible event where "Jolt cannot do KMP". Desktop and Android sit on jolt-jni
with a multi-year maintainer track record; iOS sits on JoltC, which is complete C bindings
over the same core. The realistic failure is one target missing one entry point — almost
certainly wasm, where JoltPhysics.js is an Emscripten port whose exposed surface is narrower
than the C++ library's.

A per-target, per-feature gap does not justify a from-scratch engine. It justifies filling
that gap.

## The response ladder

Applied per missing feature, cheapest rung first:

1. **Implement it in Kotlin above the facade.** Works whenever the gap is a *query* rather
   than constraint solving — a shape sweep against a BVH of static geometry is bounded work
   with no solver in it.
2. **Declare a capability gap on that target.** wasm ships kinematic-only, no dynamics, and
   the conformance suite records it. Awake already has `PhysicsCapabilityException` for
   exactly this shape of answer.
3. **Swap the backend on that one target.** Rapier has a real wasm story; the facade exists
   so this does not ripple into gameplay code, which is the whole point D5 gave for it.
4. **Write our own.** See below.

Phase 2 of the physics plan was designed to land on rung 1 by construction: the character
controller needs one query (`shapeCast`), not Jolt's `CharacterVirtual` class, so a wasm gap
in the character classes cannot block it.

## Why rung 4 is not a capability answer

Math and geometry are roughly 5% of a rigid-body engine. Awake has `Vec3f`, `Quat`, `Matrix`,
`Aabb`, `Plane`, `Ray`, `Frustum`; it does not have, and would have to write and *tune*:

- an incrementally refit BVH broadphase
- GJK, EPA, contact manifold generation, and multi-frame manifold persistence — persistence
  is where stacked boxes stop jittering, and it is the routinely underestimated part
- a sequential-impulse or TGS solver with warm starting, friction cones, restitution
- islands, sleeping, deterministic ordering
- CCD, stacking stability, robustness at high mass ratios

None of it is hard to write badly. All of it is hard to write stably. Jolt is a decade-plus of
shipped tuning under a production AAA title, Apache-2.0, and supplies most of the physics
plan's Tier 1–3 items as a dependency. Replacing it means owning every stacking artifact with
no reference implementation to diff against, and the cost does not shrink by deferring it.

## The trigger that would actually justify it

**Bit-level determinism across targets.** Three different Jolt builds — jolt-jni, JoltC,
JoltPhysics.js — at potentially different versions, on different float paths, will not produce
identical results. No amount of fixed-timestep work changes that; a fixed step buys
determinism *within* a build, not *across* builds.

If lockstep netcode or cross-platform replay ever becomes a goal, that forces a single pure
Kotlin simulation on every target regardless of how good Jolt is. That is a deliberate product
decision with a known price, not a capability failure to be discovered. It is the only
condition under which rung 4 is correct.

**Consequence:** decide whether determinism is a goal *before* Phase 2, because it constrains
the API shape (body creation order, system iteration order feeding island ordering) and is
expensive to retrofit.

## Where the line sits

`PhysicsWorld` stays a thin backend-neutral contract: bodies, shapes, stepping, queries.
Anything that is policy over queries rather than constraint solving belongs above it in
commonMain Kotlin — the character controller first, later foot IK, camera collision, and spawn
validity. That confines the four-backend duplication tax to code that genuinely needs native,
and makes each of those features testable in `commonTest` against a stub `PhysicsWorld`.

## Spike result: `CastShape` is available on all four backends (2026-08-30)

The open action is closed. Phase 2's pure-Kotlin character controller is unblocked.

**wasmJs — `jolt-physics` 1.1.0, executed, not just declared.** The `.d.ts` is not evidence:
Emscripten binds whatever the upstream `.idl` declares, and a missing binding throws only when
called. So the check ran the real thing under Node against the exact bundle the default
entrypoint resolves to (`dist/jolt-physics.wasm-compat.js`) — build a static box wall at
x = +5, sweep a character-sized capsule (r = 0.3, half-height = 0.9) 10m along +X, take the
closest hit:

```
fraction     = 0.4200   (expected (4.5 - 0.3) / 10 = 0.42)
contact x    = 4.5000   (expected 4.5, the wall's near face)
slide normal = 1.000, 0.000, 0.000
hit body is the wall: true
```

Exact, not approximate. `NarrowPhaseQuery.CastShape`, `RShapeCast`, `ShapeCastSettings`,
`CastShapeClosestHitCollisionCollector`, `CapsuleShape` and the full filter set
(`BroadPhaseLayerFilter` / `ObjectLayerFilter` / `BodyFilter` / `ShapeFilter`) are all present
and working. `CharacterVirtual` is bound too, which was the thing most likely to be missing —
so rung 1 was not even forced here, it is chosen on the merits in
[physics-open-world.md](../plans/physics-open-world.md).

**Desktop + Android — jolt-jni 5.2.0.** `NarrowPhaseQuery.castShape` verified by `javap` on
the resolved artifact: four overloads, progressively adding `BroadPhaseLayerFilter`,
`ObjectLayerFilter`, `BodyFilter`, `ShapeFilter`. `RShapeCast`, `ShapeCastSettings`,
`ShapeCastResult`, `CapsuleShape` all shipped.

**iOS — vendored JoltC.** `JPC_NarrowPhaseQuery_CastShape` exists, taking a
`JPC_NarrowPhaseQuery_CastShapeArgs` struct with the same filter set.
`JPC_CapsuleShapeSettings` is present, and `JPC_NarrowPhaseQuery_CollideShape` covers the
overlap query item 10 wants later.

### One real wrinkle, iOS only

JoltC ships **no prebuilt collector** — no `ClosestHit`/`AnyHit`/`AllHit` equivalent anywhere
in `Functions.h`. The collector must be constructed via `JPC_CastShapeCollector_new(fns)` with
an `AddHit` function pointer, which on Kotlin/Native means a top-level `staticCFunction` and a
context slot to write the best hit into — no captured state.

Not a blocker: the iOS backend already uses exactly this pattern for its broadphase and object
layer filter tables, and the existing `raycast` sidesteps it only because `JPC_..._CastRay`
returns a `bool` directly. `castShape` will be the first collector on that backend. Budget for
it explicitly rather than discovering it during Phase 2.

## Status

**DECIDED (2026-08-30).** `CastShape` spike closed — available and verified on all four
backends. Open question, owner-decided, needed before Phase 2: is cross-target determinism a
goal.
