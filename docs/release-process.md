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
published by the tag workflow. A main run on that commit skips snapshot upload. The tag run also
publishes one snapshot, the WebGPU release pin `<release>-SNAPSHOT` (see
[the WebGPU release pin](#webgpu-release-pin)), so a `main` or `release/**` run whose snapshot is
`<release>-SNAPSHOT` skips its upload once `v<release>` is tagged.

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
   **Publish** run stages the Core family, its alignment BOM and build-logic's plugins in one folder
   and uploads them as one Central deployment (`scripts/central-bundle.sh`,
   `scripts/central-upload.sh`), which publishes whole or not at all. Wait for it to succeed and for
   `repo1.maven.org/.../core/host/maven-metadata.xml` to list the version. Its **WebGPU release
   pin** job then publishes `com.awakekt.awake.backend:webgpu:<release>-SNAPSHOT` once Core is on
   repo1; wait for that job too.
3. **Vulkan:** a Vulkan release pins the Core release it was built with, and
   [the Core alignment BOM](#core-alignment-bom) lifts those Core edges to any newer Core in a
   consumer's graph. So a new Core release does not by itself need a new Vulkan release. It does
   when either of two scripts reports an impact since the last `vulkan-v*` tag:
   - `scripts/vulkan-publication-impact.sh <last-vulkan-tag> vX.Y.Z` prints `true`: Vulkan's own
     code changed under `awake/backend/vulkan`.
   - `scripts/vulkan-core-api-impact.sh <last-vulkan-tag> vX.Y.Z` prints a module: a Core module the
     Vulkan family depends on (transitively, tests included) lost or changed a signature in its
     `api/*.api` dump, so the old Vulkan binaries may no longer link against the lifted Core. Added
     signatures do not count. It can name a module whose break Vulkan never calls; such a release is
     merely unneeded, while a missing one leaves consumers unable to link, so any report counts.

   The tag's **Publish** run does this itself. Once its **Core alignment** job has found the release
   on Central (and checked it against the latest Vulkan release), the **Vulkan tag** job runs
   `scripts/next-vulkan-tag.sh`, which applies both scripts against the latest `vulkan-v*` tag, and
   pushes the next `vulkan-vA.B.C` (the latest one's patch, bumped) on the **same commit** with
   `RELEASE_TOKEN`. Its run summary says why, or that no Vulkan release was needed. Previews
   (`-rc`, `-beta`, `-alpha`) get none. That tag's own Publish run uploads the three Vulkan modules
   as one Central deployment and attaches the bundle to the GitHub Release, as a Core release does.
   For a release whose Publish run predates that job or skipped it, run the **Vulkan tag** workflow
   by hand with the Core tag (`core_tag: v0.28.0`): it makes the same decision and push
   (`scripts/push-vulkan-tag.sh`) once that release's BOM is on Central. Tag by hand only when either
   says so: `RELEASE_TOKEN` is not set, or the next version is taken.
4. **Downstream:** bump awake-studio to the new Core and Vulkan releases by hand. The three
   starter template repositories (awake-template, awake-project-template, awake-plugin-template)
   need no bump PR: their sources live in Core, under `templates/game`, `templates/project` and
   `templates/plugin`, and once this release and its WebGPU release pin both succeed, the
   **Publish templates** job stamps and pushes each of them on its own. See "Template publishing"
   below.
   Re-record moved baselines only after stating which ones should move and why.
5. Close the milestone.

**A failed publish is not re-run.** A tag whose Publish run failed may still have reached Central,
and Maven Central never accepts the same version twice. Fix the cause on `main` and cut the
next version; the failed number stays a gap on Central (as `rc.4`, `rc.5`, `rc.7` and `rc.10` did). A release
Central refused is still served by the fallback repository when its bundle was built
([releasing](reference/releasing.md#fallback-repository)). A run that is re-run anyway cannot
send a version twice: `scripts/central-upload.sh` asks Central whether the bundle's version is
already published and, if it is, uploads nothing and lets the GitHub release steps finish.

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
latest reachable Core release tag, and before upload `verifyPublishedArtifacts
-Pawake.verifyPinnedCoreVersion` checks that every Core edge of the family's Gradle module metadata,
platform modules included, names exactly that release. Nothing checks that the Core closure is on
Maven Central before the upload. Local publication may pass `-Pawake.coreVersion=<version>`
explicitly. Both the Maven POM and Gradle module metadata use that Core version for non-Vulkan
dependencies and the family version for Vulkan-family dependencies. Snapshot POMs may contain
snapshot dependencies. A release never does: `scripts/central-bundle.sh` refuses a staged
`-SNAPSHOT` artifact, Central's validation refuses a release POM with a SNAPSHOT dependency, and
nothing strips the suffix.

Core `v*` snapshots and releases publish only the Core family. Vulkan snapshots publish only when a
Vulkan-family source/build change occurs; `vulkan-v*` tags publish only the three Vulkan modules.
Consumers normally declare the modules they directly use and let Maven/Gradle resolve their
published dependency metadata; they do not need to list the full internal closure themselves.

The Consumer gate publishes Core locally for a Core-only change and uses the latest reachable
Vulkan release tag. Before building the templates it waits up to one hour for that release's
POMs, Gradle module metadata, and all declared platform files to be served by Maven Central.
A tag alone does not prove availability. Missing files and transient repository failures are
retried within that deadline; authentication and other permanent HTTP errors fail immediately.
When the job publishes Vulkan locally, this remote availability check is unnecessary.

Publication-family selection skips Central preparation and automatic-release registration as
well as uploads. An excluded stable module must not register an empty release bundle during a
Core snapshot run. A run with no selected publications succeeds without contacting Central;
errors from selected uploads still fail the run.

### Core alignment BOM

A Vulkan release pins the Core release it was built with, so on its own it keeps requesting that
release's modules whatever Core a consumer chooses. Pinning Core 0.12.0 while `vulkan-desktop:0.1.28`
asked for `shader-compiler-desktop:0.11.0` left the consumer on the pre-fix shader compiler, because
nothing else in its graph asked for a newer one (#811). `com.awakekt.awake:bom` closes that gap.

- **What it is.** A Gradle platform and Maven BOM (`awake/bom`), published in every Core release at the
  Core version, in the same Central deployment as the Core modules. It constrains every published
  Core coordinate, root and per-target, to that version. The Vulkan family and the WebGPU release
  pin are not listed: their versions are not the Core version.
- **How it aligns.** Every published Core module, in every variant of its Gradle module metadata,
  depends on the platform at its own version, and so does the Vulkan backend at the Core release it
  pins (the bindings and the Android bridge reach no Core module). When a consumer's graph holds two
  Core versions, Gradle resolves the platform to the newer and the constraints lift every Core
  module to it, a Vulkan release's own Core edges included. Nothing to declare. A consumer who wants
  to state the version adds `implementation(platform("com.awakekt.awake:bom:<version>"))`. The WebGPU
  release pin does not take the platform; it holds Core `strictly`, so a newer Core fails resolution
  there instead.
- **Maven.** Maven does not read Gradle module metadata, so a Maven build imports the BOM in
  `dependencyManagement` (`<type>pom</type>`, `<scope>import</scope>`), as `awake/bom/README.md` shows.
- **Old releases.** A Vulkan or Core release published before the first BOM carries no platform edge,
  but any newer Core in the graph brings its own, so an old Vulkan release is lifted too.
- **Derived, not listed.** The root `build.gradle.kts` adds the edge to each module with the publish
  convention and builds the constraints from the publications the build registers, the read
  `verifyPublishedArtifacts` uses. `verifyPublishedArtifacts -Pawake.verifyPublishedFamily=core`
  fails unless every dependency-declaring variant names the platform at the release, and the platform
  lists exactly the Core modules this build publishes and Maven Local holds. For the Vulkan family,
  `-Pawake.verifyPinnedCoreVersion` also fails a variant that reaches Core without the platform at the
  pin.
- **Checking a mixed graph.** The **Core alignment** job of a `v*` tag's Publish run
  (`tools/core-alignment-check/check.sh <core>`) waits for the BOM on repo1, resolves
  `core:math:<core>` with the latest Vulkan release from Central in a standalone consumer build, and
  fails unless every Core module, `shader-compiler-desktop` included, resolves to `<core>`. It runs
  against Central only, so it cannot undo a release and can be re-run alone. It needs a BOM:
  `check.sh 0.12.0 0.1.28` (Core 0.12.0 has none) fails, with the modules Vulkan reaches on 0.11.0.
  Pass `-Pcore=... -Pvulkan=... -PmavenLocal` to `./gradlew -p tools/core-alignment-check checkCoreAlignment`
  to try fake versions published to Maven Local.

### WebGPU release pin

A SNAPSHOT dependency is never stripped to a guessed stable version. The WebGPU backend builds on
upstream wgpu4k snapshots, which Central refuses in a release, so it is held out of releases (its
last Central release is `0.1.0-alpha.4`) and publishes to Central's snapshot repository on two lines:

- **The release pin**, `<release>-SNAPSHOT`, is what a consumer of a Core release pins. Each `v*`
  tag's **WebGPU release pin** job builds it from the tagged commit
  (`tools/publish_webgpu_pin.sh`, `-Pawake.publishFamily=webgpu`) and uploads it once Core is on
  repo1. Every Core module it depends on is at exactly the release, and its metadata holds every
  published Core coordinate `strictly` at the release, so pairing it with a newer Core fails
  resolution instead of mixing two Cores into a web build that fails to link. The weekly **WebGPU
  release pin** workflow republishes the pins of the newest three releases before Central's 90-day
  snapshot cleanup can remove them, and repairs one on request (`tag` input).
- **The main train**, `<next patch>-SNAPSHOT`, is published from `main` with every Core snapshot,
  for integrating with a Core snapshot only: its Core edges name that snapshot, which sorts above the
  release.

The pin gates the release as well. The tag run's **Consumer** job builds it into Maven Local from
the tagged commit, checks its metadata (`-Pawake.verifyPinnedCoreVersion`), and builds and links both
templates' web apps against it, and the release waits for that job: a pin that cannot be built or
does not hold Core at the release stops the release before anything reaches Central. Only the
upload is a job of its own after the release, so a failed upload leaves the release in place, and
since the pin is a snapshot that job can be re-run. Pull requests and `main` run the same local
build and metadata check (CI's **WebGPU release pin** job, on a throwaway tag), so a tag is not the
first run of it.

#### The wgpu4k pins are hosted

`io.ygdrasil` (wgpu4k and its type libraries) publishes only snapshots, and Sonatype removed the
builds the backend pins (`pinnedYgdrasilSnapshots` in `build.gradle.kts`, all from 2026-07-16)
while upstream has had no newer commit. Awake therefore hosts those exact files, which are MIT
licensed: the `mirror-io.ygdrasil-20260716` GitHub Release holds `io-ygdrasil-20260716-maven.zip`,
Sonatype's files and checksums at its paths (`io/ygdrasil/<module>/<version>-SNAPSHOT/<module>-<timestamped version>.*`,
plus `LICENSE-wgpu4k.txt`). The **Maven fallback** workflow unpacks every `mirror-io.ygdrasil-*`
release into `https://awakekt.github.io/awake/` on each deploy, so the pins survive the site's
regeneration, and runs `scripts/check-ygdrasil-mirror.sh` on the result: a deploy that would lose a
pinned file fails and the live site stays as it was.

The builds read the mirror for `io.ygdrasil` only, and `settings.gradle.kts` excludes that group from
the Sonatype snapshot repos. Without the exclusion, a Gradle cache that once resolved a pinned
module from Sonatype (a restored CI cache) looks for its missing files only there, whatever other
repositories are declared; that is how v0.13.0's **Verify what will be published** step failed.

A consumer of a WebGPU pin needs the mirror too: the pin's published metadata constrains
`io.ygdrasil` to the timestamped builds, and only the mirror holds them.

```kotlin
repositories {
    mavenCentral()
    maven("https://central.sonatype.com/repository/maven-snapshots/") { content { excludeGroup("io.ygdrasil") } }
    maven("https://awakekt.github.io/awake/") { content { includeGroup("io.ygdrasil") } }
}
```

To move a pin, for instance if upstream publishes again:

1. Resolve the new builds in a scratch Gradle home and copy `io.ygdrasil` from a repository that
   still serves them, keeping the files, checksums and paths unchanged.
2. Attach the result as `io-ygdrasil-<date>-maven.zip` to a new `mirror-io.ygdrasil-<date>` pre-release
   (keep the old one while released pins still name it), with the licence file in the archive.
3. Change `pinnedYgdrasilSnapshots`, `ygdrasilModules` and the module list in
   `scripts/check-ygdrasil-mirror.sh` together, and run the check against the unpacked archive.
4. Run **Maven fallback** by hand, then check `scripts/check-ygdrasil-mirror.sh <url>` against the live site.

Verify a change to any of this with an empty Gradle home (`GRADLE_USER_HOME=$(mktemp -d)`) and
`./gradlew :awake:backend:webgpu:wasmJsPackageJson :awake:backend:webgpu:compileKotlinDesktop`.

### Template publishing

The public `com.awakekt.awake.plugin.scene-components` marker and the JVM-only
`com.awakekt.awake.scene.binding:processor` ship on the Core version train. The processor uses the
shared publication convention and participates in the Core artifact inventory and staging checks;
the build-logic publication check includes the marker. Both the PR Templates workflow and the
release Consumer job publish the build plugins locally, then run
`tools/check_scene_components_consumer.sh` against a separate project. That project resolves the
published marker and processor without an included build, generates bindings and a registry in
commonMain, and tests JVM/Wasm persistence plus Android compilation (and native compilation on
macOS). Templates also runs a separate macOS consumer job before merge, publishing only this
toolchain's dependency closure to check both native targets. A consumer project's own version must
never choose the processor version.

The three starter templates (`templates/game`, `templates/project`, `templates/plugin`) live in
this repository and are tested from there: `.github/workflows/templates.yml` and
`obfuscated-templates.yml` build the in-tree copy through `tools/check_template_consumer.sh`, so a
PR that breaks one fails against the change that broke it, not a template repository's `main` that
may already be ahead or behind it.

Once a Core release's **release** and **webgpu-pin** jobs both succeed, `workflow_run` starts
**Publish templates** (`.github/workflows/publish-templates.yml`): it stamps that release's Core
and Vulkan versions, and `awake-webgpu = "<release>-SNAPSHOT"`, into each template
(`tools/stamp_template_versions.sh`), and pushes the result to its own repository's `main` as one
commit, `chore: Awake Core X.Y.Z` (`tools/publish_template.sh`). A later `vulkan-v*` tag on the same
commit triggers it again, re-stamping the newer Vulkan release though Core's own version is
unchanged. `workflow_dispatch` republishes one named template between releases, at whatever Core
and Vulkan releases are newest on Maven Central.

Starting after the triggering workflow run, rather than as one of its own jobs, is what keeps a
failed template push from affecting the release: the release has already fully finished by the time
this starts, and each template is its own matrix job (`fail-fast: false`), so one failing does not
stop the others. It authenticates with the `TEMPLATES_PUSH_TOKEN` repository secret, a fine-grained
personal access token with *Contents* read/write on the three template repositories; a missing
secret fails the run before anything is cloned or pushed, and a push that does happen is one atomic
commit per template, never a partial one. See `templates/README.md` for what each publish leaves
alone (the project template's own GitHub Pages web player and its webgpu-pin check script, issue
templates, and anything else only the repository carries).

### Plugins consumer

The **Plugins consumer** job in `ci.yml` builds the private awakekt/awake-plugins against each
pull request's Core, so a change that breaks the plugins or makes them allocate more fails before a
release, not after it (#1169 reached 0.28.0 and needed 0.28.1). Through
`tools/check_plugins_consumer.sh`, it publishes to Maven Local, at `0.0.0-plugins-consumer`, the Core
modules the plugins' version catalog names and every Core project they depend on (JVM and wasm only),
points the plugins' `awake` version at it, and runs their JVM tests, plain and obfuscated, and
`pluginScorecard`, whose allocation gate holds the HUD modules, rpg-hud-studio's install path among
them, to `config/scorecards/hud-baselines.json`. It runs only when the change reaches one of those
modules or the build that publishes them. It needs the `PLUGINS_READ_TOKEN` repository secret, a
fine-grained token with *Contents* read-only on awake-plugins; without it (forks, Dependabot, or
before the secret exists) the job skips with a notice and passes. It builds the plugins' `main`,
or the branch the `AWAKE_PLUGINS_REF` repository variable names. It is not a required check yet.

### Build runner

The **Build (JVM/Native)** job in `ci.yml` runs on a Blacksmith runner,
`blacksmith-8vcpu-ubuntu-2404`, as a measured pilot; every other job stays on GitHub-hosted
runners. Which runner a job uses follows one rule:

- long, CPU-heavy Linux PR jobs go to Blacksmith;
- short checks, macOS and iOS jobs, and release and publish runs stay on GitHub-hosted runners;
- the Blacksmith jobs together stay within the 3,000 free Blacksmith minutes a month.

Each moved job reads its runner from a repository variable of its own, defaulting to Blacksmith:
`runs-on: ${{ vars.CORE_BUILD_RUNNER || (github.repository_owner == 'awakekt' && 'blacksmith-8vcpu-ubuntu-2404') || 'ubuntu-latest' }}`
for Build today, and `CORE_VULKAN_RUNNER` (Vulkan (Linux)) and `CORE_WEB_RUNNER` (Web) when those
jobs move. The Blacksmith default applies only in the awakekt organisation, where the Blacksmith app is installed;
a fork's own CI falls back to `ubuntu-latest` instead of queueing for a runner it cannot get. A job
that has not moved keeps `runs-on: ubuntu-latest` and has no variable. Moving a job back needs no code
change: set its variable to `ubuntu-latest` under *Settings → Secrets and variables → Actions →
Variables*, and delete the variable to return to Blacksmith. The job keeps
`gradle/actions/setup-gradle`; on Blacksmith its `actions/cache` traffic goes to Blacksmith's cache
without any change to the workflow.

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

A missing `CHANGELOG.md` is created, and one without `## [Unreleased]` gets the section before its
first release. Optional properties, all off by default, serve a repository that keeps its version in
files or commits elsewhere:

- `-Prelease.versionKey=<key>` (with `-Prelease.versionFile=<file>`, default `gradle.properties`):
  count on from that entry (a release version such as `0.3.1`) instead of the newest tag, and write
  the new version back. An existing tag of the new version still fails the cut.
- `-Prelease.syncVersionFiles=<a,b>`: also rewrite each listed file's first JSON `"version"` member
  or `version =` line. A listed file that is missing, or has no such field, fails the cut.
- `-Prelease.tag=false`: commit without tagging. The Release workflow always passes it: its `Tag`
  job tags the squash commit after the PR merges, so a local tag would only be thrown away.
- `-Prelease.git=false`: only edit files (the changelog, the version files, the deleted fragments);
  stage, commit and tag nothing, for a caller that commits itself.

### From GitHub Actions

`main` only accepts pull requests, so the **Release** workflow splits the same steps in two:

1. Actions → **Release** → *Run workflow*, choose the channel and bump (tick *dry-run* to only print
   the next version and changelog). The `Prepare` job refuses while another `release-cut/*` branch
   or open `chore(release)` PR exists, runs `releaseCut`, pushes a `release-cut/<tag>` branch, and
   opens a `chore(release): cut <tag>` PR with the milestone titled like the tag.
2. Merge that PR once CI passes. The `Tag` job tags the squash commit, by its full SHA, and pushes
   the tag, which triggers **Publish** (Maven Central, then the Vulkan tag when one is needed) and
   **Docs**.

Core's `release.yml` only calls `.github/workflows/release-reusable.yml`, the same workflow every
other repository calls (next section), so Core is its first consumer and the two cannot drift.

The workflow needs a `RELEASE_TOKEN` repository secret: a fine-grained personal access token for
this repository with *Contents*, *Pull requests* and *Workflows* read and write. `GITHUB_TOKEN`
cannot be used: the PRs and tags it creates do not trigger other workflows, and a tag whose history
adds or changes a file under `.github/workflows` can only be pushed with the Workflows permission.
A missing, expired or under-scoped token fails the run with the exact fix; the token is never
printed.

### Releasing another repository with the same workflow

Any repository that builds with Gradle (Awake Studio, an editor plugin, a consumer game) cuts its
releases with the same fragments, plugin and workflow; nothing is copied but a 30-line caller.

1. **Apply the plugin at the root.** The `release-cut` plugin is one of Core's published build
   plugins, at the Core version:

   ```kotlin
   // settings.gradle.kts
   pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
   // build.gradle.kts (root)
   plugins { id("com.awakekt.awake.plugin.release-cut") version "0.31.0" }
   ```

   Versions come from the
   repository's own `v*` tags, as in Core. A repository with no release tags yet keeps its version
   in a properties file instead (see `version-key` below). A missing `CHANGELOG.md` is created by the
   first cut.
2. **Add the caller workflow.** Copy [`templates/release.yml`](templates/release.yml) to
   `.github/workflows/release.yml`. It pins `awakekt/awake/.github/workflows/release-reusable.yml`
   to a Core release tag, never `main`; Dependabot's `github-actions` updates move the pin. Its
   inputs:

   | Input | Default | Use |
   |:------|:--------|:----|
   | `channel`, `bump` | `dev`, `none` | As `-Prelease.channel` and `-Prelease.bump` |
   | `runs-on` | `["ubuntu-latest"]` | Runner labels as JSON, e.g. `["self-hosted","Linux","X64","docker"]` for a private repository; the runner needs bash, git, curl and `gh` |
   | `java-version` | `17` | The Temurin JDK that runs Gradle |
   | `version-key`, `version-file` | empty, `gradle.properties` | The key and properties file holding the current version (`release.versionKey`, `release.versionFile`): the cut counts on from it instead of the tags and writes the new version back |
   | `sync-version-files` | empty | Comma-separated files whose first `"version"` field (JSON) or `version =` line the cut rewrites, e.g. `awake.project.json` (`release.syncVersionFiles`) |
   | `gradle-args` | empty | Any other `releaseCut` arguments |
   | `milestone` | the one titled like the tag | The release PR's milestone |
   | `dry-run` | `false` | Print the next version and changelog; push and open nothing |

3. **Add `RELEASE_TOKEN`.** A fine-grained token for that repository with *Contents*, *Pull
   requests* and *Workflows*: read and write, stored with
   `gh secret set RELEASE_TOKEN --repo <owner>/<repo>`. The reasons are those above.
4. **Write fragments.** Each change adds `changelog/unreleased/<section>/<branch-name>.md`
   (`added`, `changed`, `deprecated`, `removed`, `fixed`, `security`), as in Core.
5. **Release in two steps.** Dispatch **Release** (a dry run first, if in doubt), then merge the
   `chore(release): cut <tag>` PR it opens; the push of that squash commit tags it automatically.
   Whatever the repository runs on a `v*` tag (publishing, a GitHub Release) starts from there.

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
