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
- A `-SNAPSHOT` is a mutable integration build, not a stable release. Release verification rejects
  snapshot dependencies and never guesses a stable replacement by deleting the suffix.

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

Each Core release also attaches its bundle, the signed files staged for Central, to its GitHub
Release, and the **Maven fallback** workflow serves the newest two from GitHub Pages. A build that
needs a release Central refused under its publishing limits adds the repository after Maven Central;
the coordinates are the same, so nothing else changes:

```kotlin
repositories {
    mavenCentral()
    maven("https://awakekt.github.io/awake/")
}
```

For Gradle plugins, add the same line to `pluginManagement.repositories`. The fallback holds the Core
family and build-logic's plugins; the Vulkan family is published to Central only.
