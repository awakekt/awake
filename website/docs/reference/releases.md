# Releases and compatibility

<p class="awake-lede">Which versions go together, which targets each module runs on, and what a build needs. AwakeKt Engine is alpha: the API and the published module set can change between releases.</p>

Release notes are on [GitHub Releases](https://github.com/awakekt/awake/releases).

## Versions on this page

| Release train | Version | Tags | Modules |
| --- | --- | --- | --- |
| Core | `{{ awake_version }}` (this documentation version) | `v*` | Every published module except the Vulkan family. All share one version. |
| Vulkan | `{{ awake_vulkan_version }}`, built against Core `{{ awake_vulkan_core_version }}` | `vulkan-v*` | `com.awakekt.awake.backend:vulkan`, `com.awakekt.awake:vulkan-kmp`, `com.awakekt.awake:vulkan-kmp-android-native` |
| WebGPU | a `-SNAPSHOT` version | none | `com.awakekt.awake.backend:webgpu` |

The install snippets pair the Vulkan release with the Core release it was built against:

```toml title="gradle/libs.versions.toml"
[versions]
awake = "{{ awake_vulkan_core_version }}"
awake-vulkan = "{{ awake_vulkan_version }}"
```

## Core and Vulkan pairing

| Rule | Detail |
| --- | --- |
| Each Vulkan release names the Core release it was built against | Its POM and Gradle module metadata depend on that exact Core version: the newest stable Core `v*` tag when the Vulkan release was cut. |
| The newest of each is not always a pair | A Core release can be newer than every Vulkan release. Core `{{ awake_version }}` pairs with Vulkan only once a Vulkan release names it. |
| Check a pair | Read the Core dependency versions in the Vulkan release's POM on Maven Central. |
| Core releases do not move Vulkan | A Core release publishes no Vulkan module and changes no Vulkan version. |
| Core never depends on Vulkan | No published Core module has a main-source dependency on the Vulkan family. |
| Keep Core modules on one version | Every Core module you declare uses the same `awake` version. |

## WebGPU

| Item | Value |
| --- | --- |
| Published as | Snapshot only. It builds on a wgpu4k snapshot, and Maven Central releases cannot depend on a snapshot. |
| Repository | `https://central.sonatype.com/repository/maven-snapshots/` |
| Pinned dependencies | The published module constrains the wgpu4k and webgpu-ktypes snapshots to the builds it was tested with. |

## Targets

| Modules | Android | Desktop JVM | iOS (`iosArm64`, `iosSimulatorArm64`) | Web (`wasmJs`) |
| --- | --- | --- | --- | --- |
| Every Core module not listed below | Yes | Yes | Yes | Yes |
| `backend:jolt` | Yes | Yes | Yes | Yes, through the `jolt-physics` npm module. Shape casts with a hull, mesh or heightfield throw `PhysicsCapabilityException`. |
| `backend:webgpu` | — | Yes, over wgpu-native | — | Yes |
| `backend:vulkan`, `vulkan-kmp` | Yes | Yes | Yes, through MoltenVK | — |
| `vulkan-kmp-android-native` | Yes | — | — | — |

| Platform | Rendering backend |
| --- | --- |
| Desktop (JVM) | Vulkan |
| Android | Vulkan |
| iOS | Vulkan through MoltenVK |
| Web (`wasmJs`) | WebGPU |

## Build requirements

Values the engine is built with.

| Item | Value | Source |
| --- | --- | --- |
| Kotlin | `2.4.10` | `gradle/libs.versions.toml` |
| JVM toolchain | 17 (`backend:webgpu`: 25) | `jvmToolchain(...)` |
| Android `minSdk` | 33 | `gradle.properties` |
| Android `compileSdk` | 35 | `gradle.properties` |

## Documentation versions

| Item | Behavior |
| --- | --- |
| One site per release | Each Core release publishes a matching documentation version. Pick it in the header's version selector. |
| `latest` | The newest Core release. |
| `dev` | Development builds (`-dev.*` versions). Never promoted to `latest`. |

## See also

- [Modules](modules.md)
- [Installation](../get-started/installation.md)
