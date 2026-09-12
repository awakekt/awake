# Maven coordinates

The frozen publication surface: what an external consumer resolves, under what coordinates, and
what deliberately stays unpublished. Phase 1 of the
[Maven Central publication plan](../tasks/2026-08-29-maven-central-publication-plan.md).

Nothing here is aspirational — every coordinate below was printed from the build itself, not
derived by hand. Regenerate with:

```bash
./gradlew -I <init-script> printAwakeCoordinates -q
```

## Namespace

| Concern | Value |
|---|---|
| Maven group root | `com.awakekt.awake` |
| Kotlin package root | `com.awakekt.awake` |
| Version | derived from the nearest `v*` tag |

The group keeps the hyphen and the package cannot: a hyphen is legal in a group ID and illegal in a
Kotlin package name. They are intentionally spelled differently, and this is the one place that
says so.

## How a coordinate is derived

`group` carries the module's **parent path**; `artifactId` is the module's own directory name.

```text
:awake:compose:runtime  ->  com.awakekt.awake.compose:runtime
:awake:scene:runtime    ->  com.awakekt.awake.scene:runtime
:awake:ecs              ->  com.awakekt.awake:ecs
```

The parent path is load-bearing rather than decorative. Gradle identifies a project by the
capability `group:name`, and `name` is only the last path segment, so a single flat group would
give `:awake:compose:runtime` and `:awake:scene:runtime` the same coordinate — and Gradle resolves
that by silently substituting one project for the other. Five names collide in this repo:
`animation`, `benchmark`, `physics`, `runtime` and `ui`. A configuration-time check in the root
build fails the build if any pair ever shares coordinates again, because the last occurrence
surfaced as a circular task graph pointing at neither culprit.

## Version scheme

Derived from `git describe --tags --match v*`:

| Git state | Version | Publishable |
|---|---|---|
| HEAD exactly on `v0.1.0-dev.1` | `0.1.0-dev.1` | Yes — immutable release |
| 3 commits past that tag | `0.1.0-dev.2-SNAPSHOT` | Snapshot only |
| No reachable tag | `0.1.0-dev.0-SNAPSHOT` | Snapshot only |

A stable version therefore cannot drift from its tag, and an untagged build cannot be mistaken for
a release.

## Published surface

The template consumes four artifacts directly:

| Coordinate | Module |
|---|---|
| `com.awakekt.awake.engine:bootstrap` | `:awake:engine:bootstrap` |
| `com.awakekt.awake.asset:shaders` | `:awake:asset:shaders` |
| `com.awakekt.awake.backend:vulkan` | `:awake:backend:vulkan` |
| `com.awakekt.awake.backend:webgpu` | `:awake:backend:webgpu` |

Their `api` and `implementation` closure over **main** source sets is 38 modules. Test-only edges
are excluded — a `commonTest` dependency never reaches a consumer.

Two modules override the derived coordinate deliberately, and keep the names their consumers
already use:

| Module | Coordinate |
|---|---|
| `:awake:backend:vulkan:bindings` | `com.awakekt.awake:vulkan-kmp` |
| `:awake:backend:vulkan:bindings:android-native` | `com.awakekt.awake:vulkan-kmp-android-native` |

`android-native` is a plain Android library rather than a KMP one -- AGP's KMP plugin has no
`externalNativeBuild`, which is why the CMake/NDK build lives in its own module -- so it declares an
`AndroidSingleVariantLibrary` publication of its own instead of taking the shared convention.

### Already publishing before Phase 2 (21)

`com.awakekt.awake.asset`: `gltf`, `shader-compiler`, `shader-dsl`, `shader-pack`, `shaders`,
`terrain` · `com.awakekt.awake.backend.vulkan`: `bindings` ·
`com.awakekt.awake.core`: `animation`, `color`, `geometry`, `graphics2d`, `host`, `image`,
`input`, `logging`, `math`, `math2d` · `com.awakekt.awake.engine.render`: `passes2d` ·
`com.awakekt.awake`: `ecs`, `scene`

### Enabled in Phase 2 (18)

All of these now apply `awake.publish-convention`.

| Coordinate | Module | Why it ships |
|---|---|---|
| `com.awakekt.awake.engine:bootstrap` | `:awake:engine:bootstrap` | Direct template dependency |
| `com.awakekt.awake.backend:vulkan` | `:awake:backend:vulkan` | Direct template dependency |
| `com.awakekt.awake.backend:webgpu` | `:awake:backend:webgpu` | Direct template dependency |
| `com.awakekt.awake.engine:platform` | `:awake:engine:platform` | App lifecycle and windowing |
| `com.awakekt.awake.engine:compose` | `:awake:engine:compose` | UI host used by bootstrap |
| `com.awakekt.awake.engine.render:contract` | `:awake:engine:render:contract` | `Renderer`, `Mesh`, `Material` |
| `com.awakekt.awake.engine.render:passes` | `:awake:engine:render:passes` | Render features and pipelines |
| `com.awakekt.awake.compose:runtime` | `:awake:compose:runtime` | Composer used by every UI |
| `com.awakekt.awake.compose:ui` | `:awake:compose:ui` | Modifiers, layout, semantics |
| `com.awakekt.awake.core:text` | `:awake:core:text` | Font and shaping types |
| `com.awakekt.awake.physics:api` | `:awake:physics:api` | Physics facade |
| `com.awakekt.awake.scene:scene-core` | `:awake:scene:scene-core` | Components, systems, streaming |
| `com.awakekt.awake.scene:scene3d` | `:awake:scene:scene3d` | Camera, lights, mesh renderer |
| `com.awakekt.awake.scene:runtime` | `:awake:scene:runtime` | Scene documents and lifecycle |
| `com.awakekt.awake.scene:authoring` | `:awake:scene:authoring` | The scene/app DSL |
| `com.awakekt.awake.scene:controls` | `:awake:scene:controls` | Camera and input controls |
| `com.awakekt.awake.scene:physics` | `:awake:scene:physics` | Physics components and systems |
| `com.awakekt.awake.scene:navigation` | `:awake:scene:navigation` | NavGrid, streamed navigation, path requests |
| `com.awakekt.awake.compose:foundation` | `:awake:compose:foundation` | Layout and modifier API |

`:awake:scene:navigation` and `:awake:compose:foundation` are in the list although nothing the four
template artifacts reach depends on them. Both are consumer-facing — a game needs navigation, a UI
needs `Column`, `Row` and the modifier API — and a coordinate is free to add before the first
release and permanent after it. That was open decision 2; it is now taken.

### Deliberately not published

| Module | Reason |
|---|---|
| `:samples:*` | Demonstrations. They consume the engine; nothing consumes them. |
| `:awake:*:benchmark`, `:awake:ecs:benchmark` | Measurement harnesses, not API. |
| `:awake:backend:vulkan:generator`, `:awake:tailwind-generator`, `:awake:ui:font-atlas-generator` | Build-time code generators. |
| `:awake:asset:mesh-optimizer` | Asset-cooking tool, not runtime. |
| `:awake:compose:ui-testing` | Test infrastructure. |
| `:awake:editor`, `:awake:editor:scene` | Editor shell — not part of the runtime a game ships. |
| `:awake:ui:shadcn`, `:awake:ui:material3`, `:awake:heroicons`, `:awake:tailwind` | Design-system surface pending the `:awake:compose` migration; publishing now would freeze names that are about to move. |
| `:awake:backend:jolt` | Physics backend not yet in the template's supported matrix. |

## Guarding what is produced

Two scripts, both runnable by hand and both cheap:

| Script | Answers |
|---|---|
| `tools/verify_publication_closure.py` | Does a published module depend on an unpublished one? |
| `tools/verify_published_artifacts.py` | Did `publishToMavenLocal` produce POMs Central will accept, with sources, javadoc and module metadata? |

The second needs a local publish first:

```bash
./gradlew publishToMavenLocal -PisMainHost=true
python3 tools/verify_published_artifacts.py
```

## Guarding the closure

`tools/verify_publication_closure.py` fails when a published module depends on an unpublished one.
A project dependency becomes a coordinate in the published POM, so an unpublished edge builds,
publishes and passes every test here, then fails in a consumer's build — the worst place to learn
about it. Test-source edges are ignored, because a `commonTest` dependency never reaches anyone.

It reports no failures. The one it used to -- `:awake:backend:vulkan` needing
`:awake:engine:render:testing` from `commonMain` -- is resolved: see decision 1.

## Open decisions

**1. `:awake:engine:render:testing` was in the shipped closure. Resolved.**
`FrameCapture` and `PixelMap` now live in `:awake:engine:render:contract` under
`com.awakekt.awake.render.capture`, and `:awake:backend:vulkan` depends on
`:awake:engine:render:testing` from tests only. Rendering into an offscreen target and reading its
pixels back is a renderer capability; that it was first needed by tests is how it ended up in a
module named *testing*, not what it is. The two detekt findings on `PixelMap.blend` moved with the
file rather than being re-suppressed.

**2. `:awake:scene:navigation` and `:awake:compose:foundation` — decided: both publish.**
Neither is reachable from the four template artifacts, so neither would have shipped, and a
consumer could have used neither navigation nor foundation's layout API. Both now publish.

**3. WebGPU's snapshot dependency.**
`:awake:backend:webgpu` depends on snapshot `wgpu4k` artifacts. A released Awake artifact must not
depend on a snapshot an external consumer cannot resolve — either the dependency reaches a stable
version first, or the repository hosting it is documented as required.
