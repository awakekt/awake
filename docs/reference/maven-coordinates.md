# Maven coordinates and publication dependency graph

This page is the current Maven publication inventory, generated from the module build scripts. CI checks
that every published main-source `api` and `implementation` project dependency also has a publication.

Regenerate the table and machine-readable inventory with:

```bash
./gradlew awakeVerify --no-configuration-cache
```

The Gradle modules listed here are published KMP root coordinates or single-platform artifacts; KMP
root publications may also publish platform-specific companion coordinates.

## Dependency guidance for consumers

Declare the Awake artifacts your code directly uses. Gradle and Maven follow the published metadata
to bring in their internal dependencies; consumers should not copy the entire closure into their own
version catalog. `api` edges expose dependency types on the published compile API. `implementation`
edges remain required runtime dependencies but are not part of the consumer-facing compile API.

A CI closure failure means a published POM or Gradle module file would name an Awake project that has
no published artifact. Test-source dependencies are intentionally excluded.

## Release families

| Family | Published modules | Version source |
|---|---:|---|
| Core | 59 | Existing root `v*` tags and the shared Core release train |
| Vulkan | 3 | `vulkan-v*` tags; renderer, raw bindings, and Android JNI bridge move together |

Vulkan snapshots pin the exact Core snapshot used for integration. Vulkan releases depend on an
exact, published non-snapshot Core release. The family workflow assigns that Core version to
non-Vulkan dependencies and verifies the complete pinned Core dependency closure exists on Maven
Central before release upload. The published POM and Gradle module metadata carry the same exact
dependency versions. No published Core module has a main-source dependency on the Vulkan family.

For development, use `0.1.0-SNAPSHOT` for the Vulkan family until its first `vulkan-vX.Y.Z` tag.
Commits after a family release use the next patch `-SNAPSHOT`; a family tag publishes only the three
Vulkan modules. Core `v*` releases and main snapshots do not publish or change their Vulkan versions.

## Publication inventory

| Coordinate | Gradle module | Release family | Direct main-source project dependencies |
|---|---|---|---|
| `com.awakekt.awake:ai` | `:awake:ai` | `core` | `api` → `:awake:ecs` |
| `com.awakekt.awake.ai:behavior` | `:awake:ai:behavior` | `core` | `api` → `:awake:ai`; `api` → `:awake:core:math`; `api` → `:awake:scene:scene-core`; `api` → `:awake:scene:document`; `api` → `:awake:scene:binding`; `api` → `:awake:navigation` |
| `com.awakekt.awake.asset:gltf` | `:awake:asset:gltf` | `core` | `implementation` → `:awake:core:image`; `implementation` → `:awake:core:io`; `implementation` → `:awake:core:math`; `implementation` → `:awake:core:geometry`; `implementation` → `:awake:core:animation` |
| `com.awakekt.awake.asset:shader-compiler` | `:awake:asset:shader-compiler` | `core` | — |
| `com.awakekt.awake.asset:shader-dsl` | `:awake:asset:shader-dsl` | `core` | `api` → `:awake:core:geometry`; `api` → `:awake:engine:render:contract` |
| `com.awakekt.awake.asset:shader-pack` | `:awake:asset:shader-pack` | `core` | `api` → `:awake:engine:render:contract`; `api` → `:awake:asset:shaders`; `api` → `:awake:engine:render:passes`; `api` → `:awake:asset:shader-dsl`; `api` → `:awake:asset:terrain` |
| `com.awakekt.awake.asset:shaders` | `:awake:asset:shaders` | `core` | `implementation` → `:awake:core:host`; `api` → `:awake:engine:render:contract`; `api` → `:awake:engine:render:passes`; `api` → `:awake:asset:shader-dsl` |
| `com.awakekt.awake.asset:terrain` | `:awake:asset:terrain` | `core` | `api` → `:awake:core:color`; `api` → `:awake:core:geometry`; `api` → `:awake:core:image`; `api` → `:awake:core:math` |
| `com.awakekt.awake.backend:jolt` | `:awake:backend:jolt` | `core` | `api` → `:awake:physics:api`; `implementation` → `:awake:core:math` |
| `com.awakekt.awake.backend:vulkan` | `:awake:backend:vulkan` | `vulkan` | `implementation` → `:awake:engine:render:passes2d`; `implementation` → `:awake:core:graphics2d`; `implementation` → `:awake:core:math2d`; `implementation` → `:awake:core:color`; `implementation` → `:awake:core:host`; `implementation` → `:awake:core:image`; `implementation` → `:awake:core:input`; `implementation` → `:awake:core:logging`; `implementation` → `:awake:core:math`; `implementation` → `:awake:core:text`; `api` → `:awake:engine:render:contract`; `implementation` → `:awake:engine:render:passes`; `api` → `:awake:backend:vulkan:bindings`; `api` → `:awake:engine:platform`; `api` → `:awake:asset:shaders`; `implementation` → `:awake:asset:shader-compiler`; `api` → `:awake:engine:render:testing`; `implementation` → `:awake:asset:shader-pack` |
| `com.awakekt.awake:vulkan-kmp` | `:awake:backend:vulkan:bindings` | `vulkan` | `api` → `:awake:backend:vulkan:bindings:android-native` |
| `com.awakekt.awake:vulkan-kmp-android-native` | `:awake:backend:vulkan:bindings:android-native` | `vulkan` | — |
| `com.awakekt.awake.backend:webgpu` | `:awake:backend:webgpu` | `core` | `implementation` → `:awake:engine:render:passes2d`; `implementation` → `:awake:core:graphics2d`; `implementation` → `:awake:core:math2d`; `implementation` → `:awake:core:color`; `implementation` → `:awake:core:host`; `implementation` → `:awake:core:input`; `implementation` → `:awake:core:math`; `implementation` → `:awake:core:text`; `api` → `:awake:engine:render:contract`; `implementation` → `:awake:engine:render:passes`; `api` → `:awake:asset:shaders`; `implementation` → `:awake:asset:shader-pack`; `api` → `:awake:engine:render:testing`; `api` → `:awake:engine:platform` |
| `com.awakekt.awake.compose:di` | `:awake:compose:di` | `core` | `api` → `:awake:compose:runtime`; `api` → `:awake:core:di` |
| `com.awakekt.awake.compose:foundation` | `:awake:compose:foundation` | `core` | `api` → `:awake:compose:ui` |
| `com.awakekt.awake.compose:runtime` | `:awake:compose:runtime` | `core` | — |
| `com.awakekt.awake.compose:state` | `:awake:compose:state` | `core` | `api` → `:awake:compose:runtime`; `api` → `:awake:core:state` |
| `com.awakekt.awake.compose:ui` | `:awake:compose:ui` | `core` | `api` → `:awake:core:graphics2d`; `api` → `:awake:core:math2d`; `implementation` → `:awake:core:math`; `api` → `:awake:core:color`; `api` → `:awake:core:input`; `api` → `:awake:compose:runtime`; `api` → `:awake:core:text` |
| `com.awakekt.awake.compose:ui-testing` | `:awake:compose:ui-testing` | `core` | `api` → `:awake:compose:foundation`; `implementation` → `:awake:core:color`; `implementation` → `:awake:core:graphics2d`; `implementation` → `:awake:engine:render:testing`; `implementation` → `:awake:core:text` |
| `com.awakekt.awake.core:animation` | `:awake:core:animation` | `core` | `implementation` → `:awake:core:math` |
| `com.awakekt.awake.core:audio` | `:awake:core:audio` | `core` | `api` → `:awake:core:math`; `implementation` → `:awake:core:io` |
| `com.awakekt.awake.core:color` | `:awake:core:color` | `core` | — |
| `com.awakekt.awake.core:config` | `:awake:core:config` | `core` | — |
| `com.awakekt.awake.core:di` | `:awake:core:di` | `core` | — |
| `com.awakekt.awake.core:geometry` | `:awake:core:geometry` | `core` | `api` → `:awake:core:math` |
| `com.awakekt.awake.core:graphics2d` | `:awake:core:graphics2d` | `core` | `api` → `:awake:core:math2d`; `api` → `:awake:core:color`; `api` → `:awake:core:geometry` |
| `com.awakekt.awake.core:host` | `:awake:core:host` | `core` | — |
| `com.awakekt.awake.core:image` | `:awake:core:image` | `core` | — |
| `com.awakekt.awake.core:input` | `:awake:core:input` | `core` | — |
| `com.awakekt.awake.core:io` | `:awake:core:io` | `core` | — |
| `com.awakekt.awake.core:logging` | `:awake:core:logging` | `core` | — |
| `com.awakekt.awake.core:math` | `:awake:core:math` | `core` | `api` → `:awake:core:math2d` |
| `com.awakekt.awake.core:math2d` | `:awake:core:math2d` | `core` | — |
| `com.awakekt.awake.core:state` | `:awake:core:state` | `core` | — |
| `com.awakekt.awake.core:text` | `:awake:core:text` | `core` | `implementation` → `:awake:core:math2d`; `implementation` → `:awake:core:color` |
| `com.awakekt.awake:ecs` | `:awake:ecs` | `core` | — |
| `com.awakekt.awake.editor:contract` | `:awake:editor:contract` | `core` | `api` → `:awake:core:math`; `api` → `:awake:core:input`; `api` → `:awake:core:state`; `api` → `:awake:core:di` |
| `com.awakekt.awake.engine:bootstrap` | `:awake:engine:bootstrap` | `core` | `implementation` → `:awake:core:di`; `implementation` → `:awake:core:graphics2d`; `implementation` → `:awake:core:math2d`; `implementation` → `:awake:core:color`; `implementation` → `:awake:core:input`; `api` → `:awake:engine:platform` |
| `com.awakekt.awake.engine:compose` | `:awake:engine:compose` | `core` | `api` → `:awake:engine:platform`; `api` → `:awake:compose:ui`; `api` → `:awake:core:text` |
| `com.awakekt.awake.engine:platform` | `:awake:engine:platform` | `core` | `implementation` → `:awake:core:math2d`; `implementation` → `:awake:core:math`; `api` → `:awake:core:host`; `implementation` → `:awake:core:image`; `implementation` → `:awake:core:input`; `implementation` → `:awake:core:logging`; `api` → `:awake:engine:render:contract` |
| `com.awakekt.awake.engine.render:contract` | `:awake:engine:render:contract` | `core` | `api` → `:awake:core:graphics2d`; `api` → `:awake:core:text`; `implementation` → `:awake:core:color`; `implementation` → `:awake:core:math`; `api` → `:awake:core:geometry` |
| `com.awakekt.awake.engine.render:passes` | `:awake:engine:render:passes` | `core` | `implementation` → `:awake:core:graphics2d`; `implementation` → `:awake:core:math2d`; `implementation` → `:awake:core:color`; `api` → `:awake:core:math`; `api` → `:awake:engine:render:contract` |
| `com.awakekt.awake.engine.render:passes2d` | `:awake:engine:render:passes2d` | `core` | `api` → `:awake:engine:render:passes`; `api` → `:awake:engine:render:contract`; `api` → `:awake:core:math`; `api` → `:awake:core:graphics2d`; `implementation` → `:awake:core:color`; `implementation` → `:awake:core:geometry` |
| `com.awakekt.awake.engine.render:testing` | `:awake:engine:render:testing` | `core` | `implementation` → `:awake:core:color`; `api` → `:awake:engine:render:contract` |
| `com.awakekt.awake:heroicons` | `:awake:heroicons` | `core` | `implementation` → `:awake:core:graphics2d`; `implementation` → `:awake:core:math2d`; `api` → `:awake:compose:ui` |
| `com.awakekt.awake:navigation` | `:awake:navigation` | `core` | `implementation` → `:awake:core:math`; `implementation` → `:awake:core:color`; `api` → `:awake:scene:scene-core`; `api` → `:awake:scene:world`; `api` → `:awake:asset:terrain`; `api` → `:awake:engine:render:contract` |
| `com.awakekt.awake.net:api` | `:awake:net:api` | `core` | — |
| `com.awakekt.awake.physics:api` | `:awake:physics:api` | `core` | `implementation` → `:awake:core:math`; `implementation` → `:awake:core:geometry` |
| `com.awakekt.awake:project` | `:awake:project` | `core` | — |
| `com.awakekt.awake.scene:audio` | `:awake:scene:audio` | `core` | `api` → `:awake:core:audio`; `api` → `:awake:core:math`; `api` → `:awake:ecs`; `api` → `:awake:scene:scene-core` |
| `com.awakekt.awake.scene:authoring` | `:awake:scene:authoring` | `core` | `implementation` → `:awake:core:graphics2d`; `implementation` → `:awake:core:math2d`; `implementation` → `:awake:core:color`; `implementation` → `:awake:core:input`; `api` → `:awake:scene:scene-core`; `api` → `:awake:scene:scene3d`; `api` → `:awake:scene:controls`; `api` → `:awake:scene:runtime`; `api` → `:awake:scene:audio`; `api` → `:awake:engine:bootstrap` |
| `com.awakekt.awake.scene:binding` | `:awake:scene:binding` | `core` | `api` → `:awake:ecs`; `api` → `:awake:core:logging`; `api` → `:awake:scene:document` |
| `com.awakekt.awake.scene:controls` | `:awake:scene:controls` | `core` | `api` → `:awake:core:input`; `api` → `:awake:scene:scene-core`; `implementation` → `:awake:core:math`; `api` → `:awake:scene:scene3d`; `api` → `:awake:compose:ui` |
| `com.awakekt.awake.scene:document` | `:awake:scene:document` | `core` | `api` → `:awake:core:math`; `api` → `:awake:core:color`; `api` → `:awake:core:logging`; `api` → `:awake:core:host`; `api` → `:awake:core:io` |
| `com.awakekt.awake.scene:physics` | `:awake:scene:physics` | `core` | `implementation` → `:awake:core:math`; `api` → `:awake:scene:scene-core`; `api` → `:awake:scene:world`; `api` → `:awake:physics:api`; `api` → `:awake:core:animation`; `api` → `:awake:engine:render:contract` |
| `com.awakekt.awake.scene:runtime` | `:awake:scene:runtime` | `core` | `implementation` → `:awake:core:graphics2d`; `implementation` → `:awake:core:math2d`; `implementation` → `:awake:core:host`; `implementation` → `:awake:core:input`; `api` → `:awake:core:logging`; `api` → `:awake:scene:document`; `api` → `:awake:scene:binding`; `api` → `:awake:scene:scene-core`; `api` → `:awake:scene:world`; `api` → `:awake:scene:scene3d`; `api` → `:awake:core:math`; `api` → `:awake:core:audio`; `api` → `:awake:scene:audio`; `api` → `:awake:ecs`; `api` → `:awake:engine:platform`; `api` → `:awake:engine:compose`; `api` → `:awake:core:text`; `api` → `:awake:compose:ui`; `api` → `:awake:engine:render:contract`; `api` → `:awake:compose:runtime` |
| `com.awakekt.awake.scene:scene-core` | `:awake:scene:scene-core` | `core` | `api` → `:awake:core:math`; `api` → `:awake:ecs`; `api` → `:awake:scene:document`; `api` → `:awake:scene:binding` |
| `com.awakekt.awake.scene:scene3d` | `:awake:scene:scene3d` | `core` | `implementation` → `:awake:core:graphics2d`; `implementation` → `:awake:core:math`; `api` → `:awake:core:color`; `implementation` → `:awake:core:animation`; `api` → `:awake:scene:scene-core`; `api` → `:awake:scene:world`; `api` → `:awake:scene:document`; `api` → `:awake:scene:binding`; `api` → `:awake:engine:render:contract`; `api` → `:awake:engine:render:passes`; `api` → `:awake:asset:terrain`; `api` → `:awake:asset:shader-pack` |
| `com.awakekt.awake.scene:world` | `:awake:scene:world` | `core` | `api` → `:awake:scene:scene-core` |
| `com.awakekt.awake:tailwind` | `:awake:tailwind` | `core` | `implementation` → `:awake:core:math2d`; `implementation` → `:awake:core:math`; `implementation` → `:awake:core:color`; `api` → `:awake:compose:foundation` |
| `com.awakekt.awake.ui:builder` | `:awake:ui:builder` | `core` | `implementation` → `:awake:core:graphics2d`; `implementation` → `:awake:core:math2d`; `implementation` → `:awake:core:math`; `implementation` → `:awake:core:color`; `implementation` → `:awake:core:input`; `implementation` → `:awake:tailwind`; `implementation` → `:awake:heroicons`; `implementation` → `:awake:compose:foundation`; `implementation` → `:awake:ui:shadcn` |
| `com.awakekt.awake.ui:shadcn` | `:awake:ui:shadcn` | `core` | `implementation` → `:awake:core:graphics2d`; `implementation` → `:awake:core:math2d`; `implementation` → `:awake:core:math`; `implementation` → `:awake:core:color`; `implementation` → `:awake:core:input`; `api` → `:awake:tailwind`; `api` → `:awake:heroicons`; `api` → `:awake:compose:foundation` |
