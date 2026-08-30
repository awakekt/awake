# Maven Central Publication Plan

**Date:** 2026-08-29  
**Status:** `Active`  
**Consumer:** [awake-template](https://github.com/awake-lab/awake-template)

**Prerequisite:** [Kotlin package namespace migration](2026-08-29-kotlin-package-namespace-migration-plan.md)

Stable Maven publication must not begin until the package migration and its external-consumer
validation gate are complete.

## Objective

Publish the Awake modules required by external Kotlin Multiplatform applications to Maven
Central. A clean checkout of `awake-template` must resolve Awake from Maven without a sibling
`../awaken` checkout or composite-build substitution.

## Current State

Awake already has the main publication infrastructure:

- `com.vanniktech.maven.publish`
- Maven Central publication and signing configuration
- POM metadata for license, source repository, issues, and developers
- Sources and Dokka JAR configuration
- A GitHub Actions publication workflow

The current workflow publishes Vulkan binding artifacts only:

```text
:awake:backend:vulkan:bindings
:awake:backend:vulkan:bindings:android-native
```

That is not enough for the public template, which directly consumes:

```text
io.github.awake-lab.engine:bootstrap
io.github.awake-lab.asset:shaders
io.github.awake-lab.backend:vulkan
io.github.awake-lab.backend:webgpu
```

Every transitive `api` dependency of those artifacts must also be published and resolvable.

## Artifact Surface

### Direct template dependencies

| Coordinate | Source module | Current publication state |
|---|---|---|
| `io.github.awake-lab.engine:bootstrap` | `:awake:engine:bootstrap` | Must enable |
| `io.github.awake-lab.asset:shaders` | `:awake:asset:shaders` | Verify and publish |
| `io.github.awake-lab.backend:vulkan` | `:awake:backend:vulkan` | Must enable |
| `io.github.awake-lab.backend:webgpu` | `:awake:backend:webgpu` | Must enable |

### Required dependency closure

Inventory every `api(project(...))` edge reachable from the four direct dependencies. At minimum,
verify the following families and their platform variants:

- Engine platform, render contract, render passes, and 2D passes
- Core math, color, graphics, host, image, input, and related runtime modules
- Asset shader-pack and generated shader artifacts
- Vulkan bindings and Android native artifacts
- WebGPU runtime dependencies and Wasm variants

Do not publish an artifact whose generated POM contains an internal project dependency that an
external consumer cannot resolve.

## Implementation Phases

### 1. Freeze Coordinates

- Confirm `io.github.awake-lab` as the Maven namespace.
- Confirm artifact IDs and module-to-coordinate mapping.
- Mark public API modules separately from internal implementation modules.
- Keep the version derived from `v*` tags and use `-SNAPSHOT` for post-tag development builds.

### 2. Enable Publications

- Apply `awake.publish-convention` to each public engine, asset, and backend module.
- Ensure each published KMP module emits the intended Android, JVM, iOS, and Wasm variants.
- Ensure native Vulkan artifacts are published for every supported consumer platform.
- Verify publication task names and generated module metadata.

### 3. Verify Metadata and API Surface

- Generate and inspect POM files for all public modules.
- Confirm Apache-2.0, SCM, issue tracker, project URL, and developer metadata.
- Generate sources and Dokka JARs.
- Run API compatibility checks for every public module.
- Confirm no duplicate Maven coordinates exist.

### 4. Configure Central and Signing

- Register and verify the `io.github.awake-lab` namespace in Sonatype Central Portal.
- Create or select a GPG signing key and publish its public key.
- Configure GitHub Actions secrets:

```text
CI_MAVEN_USERNAME
CI_MAVEN_PASSWORD
CI_SIGNING_KEY_ID
CI_SIGNING_KEY
CI_SIGNING_PASSWORD
```

- Keep credentials and private signing material out of the repository.

### 5. Separate Snapshot and Release CI

- Publish snapshots from `main` to the configured snapshot repository.
- Publish stable artifacts only from `v*` tags.
- Require build, API, metadata, and dependency-closure checks before publication.
- Keep the release job explicit so a normal branch push cannot create a stable release.

### 6. Validate as an External Consumer

- Clone `awake-template` into a clean directory.
- Disable or remove its optional `../awaken` composite build.
- Resolve only from Maven Central and the snapshot repository when testing snapshots.
- Run the desktop, Wasm/WebGPU, Android, and iOS integration paths available on the CI host.
- Confirm the template no longer requires local Awake source.

### 7. Publish the First Release

- Create a release tag such as `v0.1.0`.
- Publish and wait for Maven Central synchronization.
- Update `awake-template/gradle/libs.versions.toml` to the stable version.
- Re-run the clean external-consumer validation.
- Document the released coordinates and supported targets.

## Acceptance Criteria

- A clean `awake-template` checkout resolves all Awake dependencies without `../awaken`.
- All four direct template dependencies are available from Maven Central.
- All transitive KMP metadata and platform artifacts resolve correctly.
- Published artifacts contain valid POM metadata, sources, Dokka documentation, and signatures.
- Stable publication runs only from a version tag.
- Snapshot publication and stable publication use separate, documented paths.
- The first release can be consumed using only the version catalog aliases in the template.

## Risks and Decisions

- **Native distribution:** Vulkan consumers need platform-native binaries, not only Kotlin metadata.
  The publication format and supported host matrix must be verified before calling Vulkan stable.
- **WebGPU snapshots:** The current WebGPU backend depends on snapshot `wgpu4k` artifacts. Those
  artifacts must be available from a repository reachable by both CI and external consumers.
- **Namespace ownership:** Central Portal namespace verification must complete before a release
  can be accepted.
- **Pre-1.0 API:** Public modules should use API validation and document that compatibility is not
  yet guaranteed across minor development releases.
