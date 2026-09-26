# Awake Engine: Agent Guide

Welcome to **Awake Engine**. This repository is the core Kotlin Multiplatform 3D/2D game engine runtime powered by Vulkan, WebGPU, and Compose Multiplatform.

## Three-Layer Architecture Boundary

Awake enforces strict boundaries across three distinct architectural layers:
1. **Layer 1: Awake Core Engine (`awaken`)** (Apache 2.0): Runtime engine libraries (`:awake:scene`, `:awake:physics`, `:awake:render`, `:awake:ui:shadcn`, `:awake:project`, etc.).
2. **Layer 2: Awake Core Editor (`awaken:awake:editor:contract`)** (Apache 2.0): Public, vendor-neutral editor contracts, provider extension points, and project plugin metadata published under `com.awakekt:awake-editor-contract`.
3. **Layer 3: Awake Studio Pro (`awake-pro`)** (Commercial): Desktop authoring application (`:app:studio`), visual inspectors, collaborative workflows, and the secure runtime loader (`StudioPluginPipeline`).

## Skill precedence and technology boundaries

Project-owned `awake-*` skills are authoritative for Awake engine code and the
Awake-owned UI/runtime framework. They take precedence over generic `kmp-*` skills.

- Use `awake-*` for ECS, rendering, physics, engine lifecycle, Awake UI, Awake styling,
  and Awake's Compose-like engine APIs.
- Use `kmp-compose-*` only when the target code has actual Jetpack Compose or Compose
  Multiplatform imports, source sets, or verified Compose dependencies.
- Use `kmp-shadcn-*` only when the target uses verified `Shadcn*` APIs or the real
  shadcn-compose dependency.
- Do not route by naming resemblance alone. An Awake-owned composable or shadcn-like
  component is not automatically Jetpack Compose or shadcn-compose.

When a task spans both systems, route the Awake-owned boundary first and explicitly
identify any generic KMP/Compose follow-up.

## Agent skill bundles

Awake architecture lives in `docs/*`; agent execution guidance is installed from immutable sources,
not tracked in this repository. Run the public bundle installer when the current environment has
not yet deployed `.agents/skills`:

```bash
git clone https://github.com/awakekt/awake-agent-skills .agents/vendor/awake-agent-skills-bootstrap
python3 .agents/vendor/awake-agent-skills-bootstrap/scripts/install_consumer.py --project .
```

`.agents/skills.lock.toml` pins the public Apache-2.0 `awake-*` bundle and unmodified `kmp-*`
vendor dependencies by tag, commit, and archive digest. Never edit deployed copies. Studio Pro
repositories may add a private `maintained-studio` source using `studio-*` names; public Awake
must not consume that overlay.

Before touching `Renderer.kt`, a render-contract type, or a backend renderer, activate the pinned
`awake-render-pipeline` guidance and read `docs/reference/render-hardware-interface.md`.

**Pull Request & Release Invariants**:
1. Every PR must be created with `--milestone "<milestone>"` (e.g. `gh pr create --milestone "v0.1.0-beta.1"`). Never create a PR without an assigned milestone.
2. Every `feat:` and `fix:` PR must add a bullet entry to `CHANGELOG.md` under `## [Unreleased]`. CI enforces this.
3. When all issues/PRs for an active milestone are merged, cut the release via `./gradlew releaseCut -Prelease.channel=<channel>`, push the tag, and close the milestone.

Use `feat/*`, `fix/*`, `refactor/*`, or `docs/*` for topic branches. For dependent layers,
create a linear stacked PR chain with each PR targeting the branch immediately below it. Review
bottom-up, then collapse: once every layer is green, squash-merge each PR into the branch below it
from the top down, and squash the bottom PR into `main` once. Follow `docs/release-process.md`
before creating or retargeting stacked PRs.
