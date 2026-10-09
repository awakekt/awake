# Awake Engine Release Process & Branching Guidelines

This document serves as the canonical source of truth for repository branching, versioning,
changelogs, release operations, and GitHub milestone tracking across Awake Engine.

---

## 1. Branching Strategy & Release Flow

To maintain high development velocity while ensuring release stability:

1. **`main` Branch (Source of Truth)**:
    - Always compilable, tested, and passing all CI quality checks (`./gradlew check`).
    - All changes enter `main` via Pull Requests from topic branches.

2. **Feature & Topic Branches**:
    - Short-lived branches formatted as `feat/*`, `fix/*`, `refactor/*`, or `docs/*` (e.g.
      `feat/webgpu-swapchain`, `fix/vulkan-sync-hazard`).
    - Merged into `main` using **Squash and Merge** or linear rebase to keep history clean.

3. **Stacked Pull Requests for Dependent Work**:
    - Use a stack when a change has multiple independently reviewable layers that must land in
      order. The bottom branch targets `main`; each branch above it targets the branch directly
      below it.
    - Keep every layer focused and use the repository prefixes above. For example:
      `feat/physics-sockets -> main`, then `feat/common-tests-across-platforms -> feat/physics-sockets`.
    - Preserve linear ancestry between layers. Do not create a dependent branch by cherry-picking
      the lower layer onto a separate history; create it directly from the lower branch.
    - Review from the bottom upward. A middle PR must not be closed while PRs remain above it;
      update or dissolve the stack first.
    - When a lower layer changes before merging, rebase the branches above it and push with
      `--force-with-lease`.
    - **Merge by collapsing the stack.** `main` only accepts squash merges, so squashing the bottom
      PR first leaves its original commit in every branch above it, which then needs another
      rebase and CI run. Instead, once every layer is green, squash-merge each PR into the branch
      below it from the top down, then squash the bottom PR into `main` once. The bottom PR's CI
      re-runs on the collapsed branch; that run is the gate for the whole stack.

4. **Release Branches (Major & Minor Cuts)**:
    - Cut a dedicated branch when preparing major or minor releases (e.g. `release/v0.1.0` or
      `release/v0.2.0`).
    - Only bug fixes, documentation, and release polish land on the release branch.
    - Tags (`v0.1.0-rc.1`, `v0.1.0`) are created directly on the release branch, then merged back
      into `main`.

---

## 2. Versioning & Lifecycle Scheme

The shared Core train is derived dynamically from Git tags using `git describe` in `build.gradle.kts`:

| Phase                 | Tag Format       | Maven Version   | Description                                               |
|:----------------------|:-----------------|:----------------|:----------------------------------------------------------|
| **Development**       | `v0.1.0-dev.10`  | `0.1.0-dev.10`  | Regular development tags cut from `main`.                 |
| **Alpha**             | `v0.1.0-alpha.2` | `0.1.0-alpha.2` | Feature-complete milestone cuts (Milestones 1–3).         |
| **Beta**              | `v0.1.0-beta.1`  | `0.1.0-beta.1`  | Public API frozen; memory leak, performance, & doc focus. |
| **Release Candidate** | `v0.1.0-rc.1`    | `0.1.0-rc.1`    | Final sanity checks before production release.            |
| **Stable Release**    | `v0.1.0`         | `0.1.0`         | Production general availability release on Maven Central. |

> **SNAPSHOT Behavior:** A commit after a Core tag increments its trailing number and appends
`-SNAPSHOT`: after stable `v0.1.0`, main publishes `0.1.1-SNAPSHOT`; after `v0.1.0-alpha.2`, it
publishes `0.1.0-alpha.3-SNAPSHOT`. The exact tagged commit retains the release version and is
published by the tag workflow. A main run on that commit skips snapshot upload.

### Cutting a Core release

A release is cut when its milestone is empty, not whenever a fix is waiting. The milestone names
the version (`v0.1.0`, `v0.2.0`), and the release that closes it **is** that version: a stable cut,
never another `rc`.

| Situation | Command | Example |
|:----------|:--------|:--------|
| First stable release: the `v0.1.0` milestone is empty | `./gradlew releaseCut -Prelease.channel=stable` | `0.1.0-rc.13` → `0.1.0` |
| Fixes only since the last stable, no public API change | `./gradlew releaseCut -Prelease.channel=stable -Prelease.bump=patch` | `0.1.0` → `0.1.1` |
| New features, or any public API change, since the last stable | `./gradlew releaseCut -Prelease.channel=stable -Prelease.bump=minor` | `0.1.1` → `0.2.0` |
| The public API is declared stable | `./gradlew releaseCut -Prelease.channel=stable -Prelease.bump=major` | `0.9.0` → `1.0.0` |
| A preview someone asked for, before the milestone is done | `./gradlew releaseCut -Prelease.channel=rc -Prelease.bump=minor`, then `-Prelease.channel=rc` for each further preview | `0.1.0` → `0.2.0-rc.1` → `0.2.0-rc.2` |

- **Patch or minor:** the changelog fragments decide. Anything under `added`, `changed`,
  `deprecated` or `removed`, or a changed `api/` dump, is a minor; only `fixed` or `security` is a
  patch. Before `1.0`, a minor may break the public API, but only after the old form shipped
  deprecated in an earlier minor.
- **Previews are optional and on request.** `alpha`, `beta` and `rc` are previews of the next
  version, not a release counter. Cutting `rc` after `rc` instead of the stable release is how Core
  sat at `0.1.0-rc.13` with an empty `v0.1.0` milestone.
- **Dry-run first:** `-Prelease.dryRun=true` prints the version and changelog without tagging. Run
  the real cut with `--no-daemon`; a shared daemon can see no tags and propose the wrong version.

### Who cuts, and the steps after the tag

One person or agent owns a release from cut to downstream bump. Before cutting, check that no other
`release-cut/*` branch or open `chore(release)` PR exists; if one does, that cut owns the release.

1. Branch `release-cut/vX.Y.Z` from `main`, run the cut, push the branch, open
   `chore(release): cut vX.Y.Z` with the milestone, and squash-merge it.
2. Tag the squash commit (`git tag -a vX.Y.Z -m vX.Y.Z <sha>`) and push the tag. The tag's
   **Publish** run stages the Core family and build-logic's plugins in one folder and uploads them
   as one Central deployment (`scripts/central-bundle.sh`, `scripts/central-upload.sh`), which
   publishes whole or not at all. Wait for it to succeed and for
   `repo1.maven.org/.../core/host/maven-metadata.xml` to list the version.
3. **Vulkan:** if anything under `awake/backend/vulkan` changed since the last `vulkan-v*` tag
   (`scripts/vulkan-publication-impact.sh <last-vulkan-tag> vX.Y.Z` prints `true`), tag
   `vulkan-vA.B.C` on the **same commit** and push it once Core is on Central. A Vulkan release
   pins the Core release it was built with; pairing it with a newer Core can fail at runtime.
4. **Downstream:** bump awake-studio, awake-template, awake-project-template and
   awake-plugin-template to the new Core and Vulkan releases. WebGPU stays on the Core train's
   `<next patch>-SNAPSHOT` after a stable cut (for `0.1.0`, pin `0.1.1-SNAPSHOT` once its main
   Snapshot run succeeds). Do not append `-SNAPSHOT` to the stable version.
   Re-record moved baselines only after stating which ones should move and why.
5. Close the milestone.

**A failed publish is not re-run.** A tag whose Publish run failed may still have reached Central,
and Maven Central never accepts the same version twice. Fix the cause on `main` and cut the
next version; the failed number stays a gap on Central (as `rc.4`, `rc.5`, `rc.7` and `rc.10` did). A release
Central refused is still served by the fallback repository when its bundle was built
([releasing](reference/releasing.md#fallback-repository)).

### Independent Vulkan family

The Vulkan renderer, raw bindings, and Android JNI bridge are one release family of three published
modules. No published Core module depends on them, so they use a separate version without splitting
the repository. The other 59 published modules remain on the shared Core train.

| Git state | Vulkan version | Behavior |
|---|---|---|
| No `vulkan-v*` tag | `0.1.0-SNAPSHOT` | Initial family snapshot |
| HEAD on `vulkan-v0.1.0` | `0.1.0` | Immutable Vulkan-family release |
| Commits after `vulkan-v0.1.0` | `0.1.1-SNAPSHOT` | Next patch snapshot |

Vulkan snapshots pin `awake.coreVersion` to the exact Core snapshot they integrate with. Vulkan
releases must pin it to an exact published, non-snapshot Core release; the workflow selects the
latest reachable Core release tag and verifies its complete dependency closure exists on Maven
Central before upload. Local publication may pass `-Pawake.coreVersion=<version>` explicitly. Both
the Maven POM and Gradle module metadata use that Core version for non-Vulkan dependencies and the
family version for Vulkan-family dependencies. Snapshot POMs may contain snapshot dependencies;
release verification rejects them and does not strip the suffix.

Core `v*` snapshots and releases publish only the Core family. Vulkan snapshots publish only when a
Vulkan-family source/build change occurs; `vulkan-v*` tags publish only the three Vulkan modules.
Consumers normally declare the modules they directly use and let Maven/Gradle resolve their
published dependency metadata; they do not need to list the full internal closure themselves.

The Consumer gate publishes Core locally for a Core-only change and uses the latest reachable
Vulkan release tag. Before building either template it waits up to one hour for that release's
POMs, Gradle module metadata, and all declared platform files to be served by Maven Central.
A tag alone does not prove availability. Missing files and transient repository failures are
retried within that deadline; authentication and other permanent HTTP errors fail immediately.
When the job publishes Vulkan locally, this remote availability check is unnecessary.

Publication-family selection skips Central preparation and automatic-release registration as
well as uploads. An excluded stable module must not register an empty release bundle during a
Core snapshot run. A run with no selected publications succeeds without contacting Central;
errors from selected uploads still fail the run.

Release verification rejects SNAPSHOT dependencies instead of stripping the suffix and guessing a
stable version. The WebGPU backend builds on upstream wgpu4k snapshots, so it is held out of
releases and publishes snapshots only (its last Central release is `0.1.0-alpha.4`); consumers pin
it to `<next patch>-SNAPSHOT` after a stable Core release, from Central's snapshot repository.
Verify that snapshot's successful upload before updating downstream pins. Core releases are not
blocked by it.

---

## 3. GitHub Milestones & Subsystem Roadmap

Active milestones on GitHub represent concrete version boundaries organized by subsystem capability:

### **Milestone 0: `v0.1.0-dev` — Engine Foundations & Infrastructure** *(COMPLETED)*

- **Subsystem: Core Engine & Math (`awake:core:math`, `awake:ecs`)**
    - `[x]` `Vec3f`, `Mat4`, `Quat`, `Bounds`, `Ray`, `AABB` geometry primitives.
    - `[x]` Multiplatform ECS World, Systems, queries, and family pooling.
- **Subsystem: Compose Engine & UI (`awake:compose:*`, `awake:ui:shadcn`)**
    - `[x]` Retained Compose layout engine, local theme/density providers, 23+ Shadcn Compose
      components.
    - `[x]` `app:studio` IDE shell, dock panels, and inspector integration.
- **Subsystem: Namespace & Repository Transfer**
    - `[x]` `com.awakekt.awake` package migration and `awakekt/awake` transfer.
    - `[x]` Pre-release dev cuts (`v0.1.0-dev.1` through `v0.1.0-dev.10`).

---

### **Milestone 1: [v0.1.0-alpha.1](https://github.com/awakekt/awake/milestone/1)** — *First Public Maven Release* *(COMPLETED)*

- **Subsystem: Maven Central Distribution (`com.awakekt`)**
    - `[x]` Publish `com.awakekt.awake:*` libraries to Maven Central via `build-and-publish.yml`.
    - `[x]` Verify the `awake-template` and `awake-project-template` consumer builds against published Maven Central artifacts.
- **Subsystem: Multi-OS Desktop Vulkan (`awake:backend:vulkan`)**
    - `[x]` Multi-OS Vulkan native binaries (macOS ARM64, macOS x86_64, Linux x86_64).
    - `[x]` Out-of-the-box Desktop JVM sample verification (`samples:engine-showcase`).

---

### **Milestone 2: [v0.1.0-alpha.2](https://github.com/awakekt/awake/milestone/2)** — *WebGPU Backend & Web Demos Preview* *(COMPLETED)*

- **Subsystem: Rendering Engine (`awake:backend:webgpu`)**
    - `[x]` WebGPU WasmJs browser runtime stability in Chrome/Edge.
    - `[x]` Omnidirectional Point-Light Shadows (Cube Maps) parity with Vulkan.
    - `[x]` Naga SPIR-V → WGSL shader compilation pipeline.
- **Subsystem: Web Hosting & Preview**
    - `[x]` Automated Cloudflare Pages deployment for `demo.awakekt.com` (Engine & UI Showcases).
    - `[x]` Automated documentation deployment for `docs.awakekt.com` (MkDocs).

---

### **Milestone 3: [v0.1.0-alpha.3](https://github.com/awakekt/awake/milestone/3)** — *Physics & Character Controller Maturity* *(COMPLETED)*

- **Subsystem: Physics Simulation (`awake:backend:jolt`, `awake:physics:api`)**
    - `[x]` Heightfield terrain colliders, raycasting, character controller.
- **Subsystem: Asset Pipeline (`awake:asset:gltf`)**
    - `[x]` glTF 2.0 skinned skeletal mesh animations and socket attachments.

---

### **Milestone 4: [v0.1.0-beta.1](https://github.com/awakekt/awake/milestone/4)** — *Studio IDE Maturity & Prefabs System* *(IN PROGRESS)*

- Studio IDE work moved to the Studio repository; Core tracks engine releases only.

---

### **Milestone 5: [v0.1.0-rc.1](https://github.com/awakekt/awake/milestone/5)** — *Release Candidate & Performance Ratchets*

- **Subsystem: Quality & Performance Gates**
    - `[ ]` Zero memory leaks in long-running headless test sessions.
    - `[ ]` Frame timing and rasterization baseline ratchets.
    - `[ ]` Automated cross-backend pixel parity verification.

---

### **Milestone 6: [v0.1.0](https://github.com/awakekt/awake/milestone/6)** — *Production Stable Engine General Availability*

- **Subsystem: API Stability & Ecosystem**
    - `[ ]` Full platform matrix (JVM, Android, iOS MoltenVK, Web Wasm).
    - `[ ]` Dokka API reference documentation on `docs.awakekt.com`.
    - `[ ]` 100% automated headless render parity gate.

---

### **Milestone 7: [v0.2.0](https://github.com/awakekt/awake/milestone/7)** — *Multiplayer Synchronization & Open-World Ecosystem*

- **Subsystem: Networking (`awake:net:api`, `samples:server`)**
    - `[ ]` Client prediction, entity delta serialization, authoritative server harness.
- **Subsystem: Spatial Audio & Terrain (`awake:core:audio`, `awake:asset:terrain`, `awake:navigation`)**
    - `[ ]` 3D spatial positional audio, attenuation curves, audio bus mixing.
    - `[ ]` Concentric geometry clipmap LODs, 4-weight terrain splatting, 3D NavMesh baking.

---

## 4. Changelog Rules & Sample Inclusion Policy

1. **One fragment per change**:
    - As work lands, each PR writes its clean, human-readable entry to its own file,
      `changelog/unreleased/<section>/<branch-name>.md`, where `<section>` is `added`, `changed`,
      `deprecated`, `removed`, `fixed` or `security` (see `changelog/unreleased/README.md`).
    - Never edit `CHANGELOG.md` in a PR: every PR inserting at the same line under `## [Unreleased]`
      conflicted with every other open one, and a PR merged after a cut filed its entry under a
      release that did not ship it. CI rejects `CHANGELOG.md` edits outside `release-cut/*`
      branches and asks every `feat:`/`fix:` PR for a fragment.

2. **Inclusion of Sample & Tooling Updates**:
    - Updates to official sample applications (`samples:engine-showcase`, `samples:ui-showcase`,
      `samples:compose-showcase`) **belong** in `CHANGELOG.md`. Studio changes go in the Studio
      repository's changelog.
    - Omit internal chores (`chore:`), unit test tweaks (`test:`), and private code cleanups.

---

## 5. Automated Release Cutter

To cut a release from the current `[Unreleased]` batch, run:

```bash
./gradlew releaseCut -Prelease.channel=dev -Prelease.bump=patch
# preview without editing CHANGELOG.md, committing, or tagging
./gradlew releaseCut -Prelease.channel=dev -Prelease.dryRun=true
```

This command automatically:

1. Reads the fragments under `changelog/unreleased/`, plus any entries still handwritten under
   `## [Unreleased]` in `CHANGELOG.md` (those come first in their section).
2. Writes them as `## [vX.Y.Z-channel.N] - YYYY-MM-DD`, sections in Keep a Changelog order,
   below a fresh empty `## [Unreleased]`.
3. Deletes the fragments and commits them with `CHANGELOG.md` (`chore(release): cut vX.Y.Z-channel.N`).
4. Creates an annotated Git tag `vX.Y.Z-channel.N`. A misnamed section directory fails the cut
   rather than dropping its entries.

### From GitHub Actions

`main` only accepts pull requests, so the **Release** workflow splits the same steps in two:

1. Actions → **Release** → *Run workflow*, choose the channel and bump. The `Prepare` job runs
   `releaseCut`, pushes a `release-cut/<tag>` branch, and opens a `chore(release): cut <tag>` PR.
2. Merge that PR once CI passes. The `Tag` job tags the merge commit and pushes the tag, which
   triggers **Publish** (Maven Central) and **Docs**.

The workflow needs a `RELEASE_TOKEN` repository secret: a fine-grained personal access token for
this repository with *Contents* and *Pull requests* read/write. `GITHUB_TOKEN` cannot be used
because the PRs and tags it creates do not trigger other workflows.

Pushing that tag also publishes the matching version of the public MkDocs site. The documentation
workflow keeps the full Maven version in the URL and in installation examples, moves the `latest`
alias only for non-development releases, and exposes older releases through the site version
selector.

---

## 6. Repository Hygiene: GitHub Milestones vs. In-Repo Docs

To maintain a clean, readable Git history and prevent commit bloat:

### The Rule of Thumb

| Use **GitHub Milestones & Issues** for: | Keep **In-Repo Docs (`docs/`)** for: |
| :--- | :--- |
| 🎯 Release targets (`v0.1.0-alpha.1`, `v0.2.0`) | 🏛️ **Architecture Decision Records (ADRs)** |
| 📋 To-do items, progress checklists, & burndown | 📐 **Hardware Abstraction Layer (HAL) & Render contracts** |
| 🐛 Bug reports, triage, & fixes | 📖 **API Guides, tutorials, & setup references** |
| 💬 Design discussions before code lands | 📜 **Official release `CHANGELOG.md`** |
| ⏱️ Ephemeral task lists & assignment | 🤖 **Pinned AI agent skill releases** |

### Guidelines for AI Agents and Contributors

1. **Zero Checkbox Churn in Git**: Do not commit scratch `.md` task checklist files into `docs/` or commit every individual checkbox check-off. Track operational progress via GitHub Issues assigned to the relevant GitHub Milestone.
2. **Atomic Commits & PR Squashing**: Feature work on topic branches must be squashed upon merging into `main`. Never push rapid micro-commits (`style: reword comment`, `fix typo`) directly to `main`.
3. **Milestone Association**: Every issue and pull request should be associated with an active GitHub Milestone (`https://github.com/awakekt/awake/milestones`). Closing an issue updates the milestone progress automatically without touching Git history.
