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

3. **Release Branches (Major & Minor Cuts)**:
    - Cut a dedicated branch when preparing major or minor releases (e.g. `release/v0.1.0` or
      `release/v0.2.0`).
    - Only bug fixes, documentation, and release polish land on the release branch.
    - Tags (`v0.1.0-rc.1`, `v0.1.0`) are created directly on the release branch, then merged back
      into `main`.

---

## 2. Versioning & Lifecycle Scheme

Version numbers are derived dynamically from Git tags using `git describe` in `build.gradle.kts`:

| Phase                 | Tag Format       | Maven Version   | Description                                               |
|:----------------------|:-----------------|:----------------|:----------------------------------------------------------|
| **Development**       | `v0.1.0-dev.10`  | `0.1.0-dev.10`  | Regular development tags cut from `main`.                 |
| **Alpha**             | `v0.1.0-alpha.2` | `0.1.0-alpha.2` | Feature-complete milestone cuts (Milestones 1–3).         |
| **Beta**              | `v0.1.0-beta.1`  | `0.1.0-beta.1`  | Public API frozen; memory leak, performance, & doc focus. |
| **Release Candidate** | `v0.1.0-rc.1`    | `0.1.0-rc.1`    | Final sanity checks before production release.            |
| **Stable Release**    | `v0.1.0`         | `0.1.0`         | Production general availability release on Maven Central. |

> **SNAPSHOT Behavior:** Any local or CI commit after a tag automatically appends `-SNAPSHOT` (e.g.,
`0.1.0-alpha.2-SNAPSHOT`), ensuring unreleased local builds never collide with published releases.

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
    - `[x]` Verify `awake-template` consumer build against published Maven Central artifacts.
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

- **Subsystem: Editor IDE (`app:studio`)**
    - `[ ]` Undo/Redo command stack (`UndoManager`).
    - `[ ]` Scene Prefabs instantiation (`.prefab.json`).
    - `[ ]` Asset drag-and-drop cooking & scale/rotate gizmos.

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

1. **Handwritten Prose under `[Unreleased]`**:
    - As work lands, write clean, human-readable entries under `## [Unreleased]` in `CHANGELOG.md`.
    - Group entries into `### Added`, `### Changed`, or `### Fixed`.

2. **Inclusion of Sample & Tooling Updates**:
    - Updates to official sample applications (`samples:engine-showcase`, `samples:ui-showcase`,
      `samples:compose-showcase`) and Studio IDE (`app:studio`) **belong** in `CHANGELOG.md`.
    - Omit internal chores (`chore:`), unit test tweaks (`test:`), and private code cleanups.

---

## 5. Automated Release Cutter

To cut a release from the current `[Unreleased]` batch, run:

```bash
./scripts/release.py cut [--channel dev|alpha|beta|rc|stable] [--bump patch|minor|major]
```

This command automatically:

1. Reads handwritten prose under `## [Unreleased]` in `CHANGELOG.md`.
2. Promotes `## [Unreleased]` to `## [vX.Y.Z-channel.N] - YYYY-MM-DD`.
3. Prepend a fresh empty `## [Unreleased]` section at the top.
4. Commits `CHANGELOG.md` alone (`chore(release): cut vX.Y.Z-channel.N`).
5. Creates an annotated Git tag `vX.Y.Z-channel.N`.

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
| ⏱️ Ephemeral task lists & assignment | 🤖 **AI Agent Rules & Skills (`.agents/skills/`)** |

### Guidelines for AI Agents and Contributors

1. **Zero Checkbox Churn in Git**: Do not commit scratch `.md` task checklist files into `docs/` or commit every individual checkbox check-off. Track operational progress via GitHub Issues assigned to the relevant GitHub Milestone.
2. **Atomic Commits & PR Squashing**: Feature work on topic branches must be squashed upon merging into `main`. Never push rapid micro-commits (`style: reword comment`, `fix typo`) directly to `main`.
3. **Milestone Association**: Every issue and pull request should be associated with an active GitHub Milestone (`https://github.com/awakekt/awake/milestones`). Closing an issue updates the milestone progress automatically without touching Git history.
