# Publishing Awake libraries

Awake publishes Kotlin Multiplatform modules to Maven Central. The current publication set and
internal dependency graph are listed in [Maven coordinates](maven-coordinates.md); the full process
and release-train policy are in [the release process](../release-process.md).

## Which dependency should I add?

Add the Awake artifact whose public API your project uses. Its published Gradle module metadata and
POM bring in the required transitive dependencies. Use `api` dependencies when your own public
surface exposes their types; use `implementation` for implementation-only dependencies. You do
not need to declare every dependency shown in the inventory manually.

For example, use the appropriate released versions for your project:

```kotlin
dependencies {
    implementation("com.awakekt.awake.backend:vulkan:<version>")
}
```

The Vulkan artifact transitively resolves its bindings, Android JNI bridge, renderer contracts,
Core libraries, and shader/runtime dependencies. Add other top-level modules directly only when
your code imports or otherwise uses them.

## Version trains

- **Core** modules use the shared version derived from root `v*` tags.
- **Vulkan** renderer, raw bindings, and Android JNI bridge use one independent version derived from
  `vulkan-v*` tags. Snapshot POMs pin the exact Core snapshot used for integration; releases pin an
  exact published non-snapshot Core version in Maven and Gradle metadata.
- **Alignment across the trains.** Every Core release publishes `com.awakekt.awake:bom`, a platform
  that constrains all Core modules to the release. Each Core module, and the Vulkan backend at the Core
  it pins, depends on it, so a Gradle build holding two Core versions (a newer Core you declare, the
  older one a Vulkan release was built with) resolves every Core module to the newest. Nothing to
  declare; `implementation(platform("com.awakekt.awake:bom:<version>"))` states the version
  explicitly, and a Maven build imports the BOM in `dependencyManagement`. See
  [the Core alignment BOM](../release-process.md#core-alignment-bom).
- **WebGPU** stays out of Central releases while it builds on wgpu4k snapshots. Each Core release
  publishes its release pin, `com.awakekt.awake.backend:webgpu:<release>-SNAPSHOT`, to Central's
  snapshot repository: built from the release's tag, with every Core module at exactly that release
  and held there by `strictly` constraints. Pair a Core release with that pin, never with the newer
  `-SNAPSHOT` that `main` publishes, whose Core is a snapshot above the release. The pin also holds
  the wgpu4k builds it was tested with, which Sonatype no longer serves: add
  `maven("https://awakekt.github.io/awake/") { content { includeGroup("io.ygdrasil") } }`
  (see [the wgpu4k pins](../release-process.md#the-wgpu4k-pins-are-hosted)).
- A `-SNAPSHOT` is a mutable integration build, not a stable release. Central refuses a release POM
  with a snapshot dependency, a release bundle refuses a staged snapshot, and nothing guesses a
  stable replacement by deleting the suffix.

To build a consumer against an unreleased checkout, publish it to the local Maven repository under a
version no release uses, and point the consumer at it:

```bash
./gradlew publishToMavenLocal -Pawake.version=0.1.0-local
./gradlew -p build-logic publishToMavenLocal -Pawake.version=0.1.0-local
```

The second command publishes the Gradle plugins (`com.awakekt.awake.plugin.*`) that consumers apply.

Awake Studio and the games resolve Awake from Maven Central, or from `mavenLocal()` when built with
the same `-Pawake.version`. Neither includes a Core checkout as a source build. Repository publishing
and consumer verification resolve published Core and Vulkan coordinates from Maven Central. See the [Awake Pro publishing guide](https://github.com/awakekt/awake-pro/blob/main/docs/library-publishing.md)
for private Pro packages and credentials.

## Fallback repository

Each Core release and each Vulkan release also attaches its bundle, the signed files staged for
Central, to its GitHub Release, and the **Maven fallback** workflow serves the newest two of each
family from GitHub Pages. A build that
needs a release Central refused under its publishing limits adds the repository after Maven Central;
the coordinates are the same, so nothing else changes:

```kotlin
repositories {
    mavenCentral()
    maven("https://awakekt.github.io/awake/")
}
```

For Gradle plugins, add the same line to `pluginManagement.repositories`. The fallback holds the Core
family, build-logic's plugins and the Vulkan family.
