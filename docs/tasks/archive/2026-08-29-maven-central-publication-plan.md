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

### 1. Freeze Coordinates — done

Recorded in [maven-coordinates](../reference/maven-coordinates.md): the namespace, the derivation
rule (`group` carries the parent path, `artifactId` is the module name), the tag-derived version
scheme, the 38-module closure of the four template artifacts, and the explicit not-published list.

Three decisions came out of it and belong to Phase 2 rather than here:

1. `:awake:backend:vulkan`'s `commonMain` depends on `:awake:engine:render:testing`, so the shipped
   closure currently contains a module named *testing*. `VulkanUiPreviewCapture` needs `FrameCapture`
   and `PixelMap`; moving those into `:awake:engine:render:contract` makes the dependency test-only
   and is the right shape regardless of publication.
2. `:awake:scene:navigation` and `:awake:compose:foundation` are not reachable from the four
   artifacts, so neither would publish and a consumer could use neither navigation nor foundation's
   layout API. Both should be added to the surface deliberately.
3. `:awake:backend:webgpu` depends on snapshot `wgpu4k` artifacts, which a stable release must not.

- ~~Confirm `io.github.awake-lab` as the Maven namespace.~~
- ~~Confirm artifact IDs and module-to-coordinate mapping.~~
- ~~Mark public API modules separately from internal implementation modules.~~
- ~~Keep the version derived from `v*` tags and use `-SNAPSHOT` for post-tag development builds.~~

### 2. Enable Publications — done

`awake.publish-convention` now applies to the 18 modules the coordinate freeze named, including
`:awake:scene:navigation` and `:awake:compose:foundation` (decision 2, taken: both publish).
`:awake:backend:vulkan:bindings:android-native` keeps its own `AndroidSingleVariantLibrary`
publication — it is a plain Android library, so the KMP convention cannot apply to it, and adding
it there fails the build outright.

Verified by publishing to Maven Local: `io.github.awake-lab.core:text` and
`io.github.awake-lab.scene:navigation` produce a POM with the right coordinates, licence, SCM and
developer metadata, plus sources, Dokka javadoc and Gradle module metadata. Every project still
configures.

`tools/verify_publication_closure.py` now fails when a published module depends on an unpublished
one. It found one, and it was the blocker for a real release:

```text
:awake:backend:vulkan -> :awake:engine:render:testing (not published)
```

Fixed by moving `FrameCapture` and `PixelMap` into `:awake:engine:render:contract` rather than by
publishing a module named *testing*: rendering to an offscreen target and reading its pixels back is
a renderer capability, and being needed by tests first is how it landed in the wrong module. The
closure is now clean at 40 published modules.

Still to verify in Phase 3: that each published KMP module emits the intended Android, JVM, iOS and
Wasm variants, and that the Vulkan native artifacts cover every supported consumer platform.

### 3. Verify Metadata and API Surface — mostly done

Everything here was verified by publishing the whole repository to Maven Local and inspecting what
came out, rather than by reading build files. `tools/verify_published_artifacts.py` is that
inspection, kept so it can be run again: it reads every generated POM and fails on missing Central
metadata, on a dependency pointing at a coordinate nobody published, or on a missing sources,
javadoc or module-metadata artifact.

Three real problems surfaced, two fixed here:

1. **19 newly published modules had no `name` or `description` in their POM**, which Central
   rejects outright. The shared convention supplies licence, SCM, URL and developers, but those two
   are per-module prose and nothing had written them. Each module now declares its own.
2. **Dokka could not generate `:awake:backend:vulkan`'s javadoc at all** — `Wrong AST Tree. Header
   does not contain expected content`. The convention builds a module doc from each README by
   demoting its headings, and Dokka's includes grammar reads `# Module <name>` / `# Package <name>`
   as *structure* at any heading level, so a README section called "Module layout" became a
   malformed module declaration. Five READMEs have such a heading. The convention now backtick-
   wraps the keyword: the grammar stops matching, the README is untouched, and the heading still
   reads as its author wrote it.
3. **API dumps were stale repo-wide**, because the package rename changed the mangled suffixes of
   every member touching a value class (`getEntity-bQ-FlKo` became `getEntity-QVrgMZQ`). Re-dumped
   for `:awake:ecs` and audited rather than trusted: 0 members disappeared, 128 of 131 changed
   lines are mangling, and the 3 additions are `componentTypes` and `entityById`, both added by
   earlier work in this repository.

Duplicate coordinates need no separate check: the root build fails at configuration time if two
projects ever share `group:name`.

**Not finished:** `apiDump`/`apiCheck` across the remaining modules, and a clean re-run of the
artifact verification, both of which need a compiling tree. `:awake:scene:rendering` currently does
not build in this working tree — in-flight rendering-package work has `Camera` and `lens`
unresolved in `RenderSystem` — so those two steps are blocked on that landing, not on publication.

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

### 5. Separate Snapshot and Release CI — done

`build-and-publish.yml` is now four jobs: `natives`, `verify`, `snapshot` and `release`. The split
is structural rather than a convention someone remembers — `snapshot` runs only for
`refs/heads/main` and `release` only for `refs/tags/v*`, so no branch push can reach the release
path however the workflow is triggered, and each additionally refuses the other's ref.

Both paths wait on `verify`, which runs the publication-closure check, `apiCheck`, and the
documented-coordinate check. A snapshot nobody can resolve wastes the same afternoon a broken
release does.

The release path carries what only matters once something is permanent, because Central will not
let a version be replaced:

- The derived version must not be a snapshot, and must match the tag that triggered the run. A tag
  build that derives `-SNAPSHOT` means `git describe` never saw the tag, which is how a release
  silently becomes nothing.
- Every supported platform must have a native library, the gate that already existed.
- The whole repository publishes to Maven Local first and `verify_published_artifacts.py` reads
  what came out. The same inspection a consumer's resolution performs, run while it is still free
  to fail.

Both paths now publish everything that opted into publication rather than the two Vulkan binding
artifacts the old single job uploaded.

**What enabling `apiCheck` exposed.** It had never run in CI — it appears nowhere in the previous
workflows — so the checked-in dumps had drifted for months. Refreshing nine of them removed 86
declarations that no longer exist in source: `ShaderSource` moved from `:awake:asset:shaders` to
`:awake:engine:render:pipeline`, `MutableState` and `StateKt` left `:awake:compose:runtime`,
`FrameInput` gained fields, `draggable` changed shape. These are real API changes that already
happened; the baseline simply never recorded them.

Re-recording is safe here for one reason worth stating rather than assuming: **none of these
modules has ever been published**, so no consumer has resolved the API being replaced. The only
artifacts on Central are `vulkan-kmp` and `vulkan-kmp-android-native`, whose dumps are unchanged.
After the first release the same diff would be a breaking change and would need a version decision
instead of an `apiDump`.

### 6. Validate as an External Consumer — partly done, two findings

Run against a copy of `awake-template` extracted to a directory with no sibling engine, so its
`if (file("../awaken").isDirectory)` substitution is inactive and every Awake coordinate has to
resolve from a repository. Maven Local stands in for the snapshot repository until Phase 7 puts
something on Central; the resolution path is otherwise identical.

**Resolution works.** `:app:shared`, `:app:desktopApp`, `:app:webApp` and `:core` all compile
against the published artifacts with no `Could not resolve` anywhere — the closure is complete and
the POMs are usable. That is the question this whole plan exists to answer, and the answer is yes.

Two things it found that nothing inside this repository could:

1. **The template's source still imports `io.github.ronjunevaldoz.*`.** The rename landed in the
   engine and the template was never swept, so it fails to compile against its own dependency.
   Confirmed by rewriting the imports in the throwaway copy, after which every target compiles.
   The fix belongs to the template repository.
2. **`io.github.awake-lab.backend:vulkan-android` and `io.github.awake-lab:vulkan-kmp-android`
   both declared the Android namespace `io.github.awakelab.awake.vulkan`.** AGP refuses to merge
   manifests for two libraries sharing a namespace, so *every* Android consumer of the Vulkan
   backend would fail with an error naming the namespace and neither owner. Inside the composite
   build they are projects and it never surfaces. `:awake:backend:vulkan` now uses
   `io.github.awakelab.awake.vulkan.backend`; the already-published `vulkan-kmp` keeps its
   namespace, so no existing consumer is affected.

**Now a CI gate rather than a thing someone remembers to do.** `tools/check_template_consumer.sh`
performs the whole exercise -- copy the template where no engine sits beside it, point its version
catalog at the version this tree produces, build every target it declares -- and the workflow's
`consumer` job runs it on macOS before either publish path. Both `snapshot` and `release` need it.

Run locally the same way:

```bash
./gradlew publishToMavenLocal -PisMainHost=true
tools/check_template_consumer.sh ../awake-template
```

**iOS is consumable, and now proven.** `linkDebugFrameworkIosSimulatorArm64` builds
`Shared.framework` against the published `iosSimulatorArm64` klibs. That had never been tested
anywhere: `ci.yml` has no iOS coverage at all, so compiling was the only evidence and linking is
where a Kotlin/Native consumer actually finds out.

**Not yet done:** the Android path still has to pass end to end. The Android one is
blocked behind rebuilding the Vulkan artifact, which does not currently compile in this working
tree — in-flight shadow-cascade work has `RendererDraw3D` passing a `Mat4` where
`ShadowCascadeUniforms` is expected. Re-run once that lands.

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
