# KMP Architecture Stance

Awake is a Kotlin Multiplatform project that deliberately skips most of what people mean by
"KMP architecture". That is a set of decisions, not an oversight, and each one gets
re-litigated when it is not written down. This is the record.

The short version: **KMP the build system, not KMP the app-architecture genre.** That genre's
idioms — `expect`/`actual` everywhere, Compose Multiplatform, a DI container, Flow-shaped state
— exist to share business logic behind platform-native UI. Awake shares the entire product and
has no platform-native UI, so it reaches for interfaces, modules, and build-time gates instead.

Scope: this doc covers the engine (`awake:*`). Games and tools built on it
(`samples:*`) are ordinary KMP applications and the genre's idioms *do* apply there — see
[Where the genre does fit](#where-the-genre-does-fit).

---

## 1. `expect`/`actual` is a last resort, never a way to choose an implementation

**The rule:** if a common implementation is *possible*, write it in common. Reach for
`expect`/`actual` only when common Kotlin cannot express the thing at all.

Every `expect` in the engine falls into one of four buckets, all genuine capability gaps:

| Bucket | Examples |
|---|---|
| OS APIs with no common form | `readResourceBytes`, `createBitmap`, the snapshot read/write/diff trio |
| Native FFI surface | `Vulkan`, `VulkanBuffers`/`Images`/`Descriptors`/`Window`, `createSurface` and friends — JNI on JVM/Android, cinterop on iOS |
| Language/runtime gaps | `newComponentArray`, `createComponentInstance`, `componentTypeKey`, `componentTypeKeyOf` — the JVM has reflection, wasmJs and Native do not |
| The application seam | `AwakeApplication` |

None of them exist to swap between two implementations that could both be written in common.
That case is Position 2.

## 2. Backend choice is a dependency, not a target

`Renderer` is an interface in `awake:engine:render:contract`. `awake:backend:vulkan` implements
it in `commonMain` spanning Android + desktop + iOS; `awake:backend:webgpu` implements it in
`wasmJsMain`. A consumer selects a backend by depending on a module.

This is not a stylistic preference. `expect`/`actual` **cannot** express it: two `actual`s for
one target is a compile error, so "Vulkan and WebGPU are both candidates on desktop" is
inexpressible that way. An interface has no such limit.

The `appMain`/`vulkanMain` intermediate source sets exist for the mechanical consequence:
backend modules do not publish a wasmJs variant, and a `commonMain.dependencies` entry on one
fails Gradle resolution the moment wasmJs becomes a declared target — not just compilation. See
[decision-log.md](decision-log.md)'s D14 for that reasoning, and D13 for where the
`vulkanMain` pattern came from.

## 3. Common is the product, not a shared slice

The genre assumes a thin shared core beneath a thick platform UI layer. Awake inverts that:
`commonMain` source sets outnumber every platform set several times over, and platform code is
a driver shim.

The operating rule that follows: **anything appearing in both backends is a bug.** Shared
render logic belongs in `awake:engine:render:passes`, backend modules hold only the translation
into real graphics calls. This is stated as a hard rule in the `awake-render-pipeline` skill
(§5) and is what `SharedOpaqueRenderFeature` / `SharedSkyboxRenderFeature` /
`SharedUiRenderFeature` and the `CommandRecorder` / `UiRunRecorder` ports are for.

## 4. No Compose Multiplatform — the UI *is* the product

`compose-multiplatform` appears in `gradle/libs.versions.toml` with zero module consumers.
`awake:ui:*` is a hand-written immediate-mode stack: its own layout, rasterizer, and font.
Adopting Compose means deleting it.

There is also a concrete blocker on record: Compose Multiplatform's wasmJs target has no
first-class way to embed a native `<canvas>` inside the Compose layout tree
([decision-log.md](decision-log.md), D14) — only community CSS-overlay hacks. A 3D engine
needs that canvas.

Ownership rules for the UI stack itself live in [ui-ownership.md](ui-ownership.md).

## 5. The frame loop is deliberately not idiomatic Kotlin

Inside `System.update` and the render path, no-per-frame-allocation wins over readable Kotlin:
indexed `while` loops rather than `forEach`, pooled objects rather than fresh ones, mutating
`Mat4.setTranslationScale` rather than `translate().scale()`.

This is measured, not assumed — pooling particle model matrices benchmarked at 0.496 µs/op
against 8.695 µs/op allocating. The rules and the traps are in the `awake-core-math` and
`awake-ui-performance` skills.

Outside the frame loop — asset loading, tooling, editor logic — ordinary idiomatic Kotlin is
correct and expected.

## 6. Layering is enforced by the build, not by convention

A rule that lives only in a document drifts. Gradle checks that fail the build:

| Check | Enforces |
|---|---|
| `verifyUiOwnership` | [ui-ownership.md](ui-ownership.md)'s placement rules |
| `verifyRenderExtensibility` | Authored shader content stays out of the shader contract module |

The cost of *not* having one is on record: the render layer's "no duplicated backend logic"
rule (`awake-render-pipeline` SKILL.md §5) existed as mandatory reading while both backends
carried their own copy of the UI paint-order loop, until it was found by hand.

That rule is enforced by the **skill and agent layer** rather than a build task, and
deliberately so. The gates above work because their rules are *nominal*: a name or pattern
must not appear in a given module, which a task can check. "The same logic must not exist
twice" is not nominal — the two defects were semantic duplication (identical loop bodies
under different class names) and structural absence (one backend never adopting the shared
feature list). Name comparison catches neither, so a check would carry a gate's authority
without a gate's coverage.

What actually failed was the rule's *wording*. `awake-render-backend-engineer.md` and
`awake-render-pipeline` SKILL.md §5 both enumerated "rendering math, vertex writers, uniform
packing, batch coalescers, pool policies" — and neither defect is any of those, so both read
as out of scope. Both now name frame orchestration and paint-order loops, and both say the
list is illustrative.

So there are three enforcement kinds here, not two. A new architectural rule should say which
it is: a build check (nominal), a skill/agent rule (semantic — then keep its examples current,
because an incomplete list reads as permission), or explicitly unenforced.

## 7. No DI container

Constructor injection with nullable optionals and function-type factories. Koin was considered
and declined: the engine's wiring is a fixed graph built once at startup, which is what
constructors already express.

The opt-in-content-versus-capability rule that shapes those nullable constructor params is
[render-extensibility.md](render-extensibility.md).

---

## Where the genre *does* fit

Everything above is about the engine. Applications built on it are ordinary KMP apps:

- `samples:studio` already uses an MVI Contract/Store.
- Feature-module layering (`:model` / `:api` / `:domain` / `:data` / `:presenter` / `:ui`) is
  the right shape for a game or tool with real domain logic. It is the wrong shape for
  `awake:core` — there is no domain layer in a `Mat4`.
- DI, Flow-shaped state, and coroutine-heavy code are all fine outside the frame loop.

Do not "fix" a sample to match the engine's stance, or the engine to match a sample's.

## Related

- [api-layering.md](api-layering.md) — core / helpers / sugar, the public API surface split
- [library-api-boundaries.md](library-api-boundaries.md) — DSL sugar versus runtime contracts
- [render-extensibility.md](render-extensibility.md) — opt-in content versus always-available capability
- [ui-ownership.md](ui-ownership.md) — which UI module owns what
- [framework-game-boundary.md](framework-game-boundary.md) — what belongs in Awake at all
