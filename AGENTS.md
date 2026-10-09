# Awake Engine: Agent Guide

Welcome to **Awake Engine**. This repository is the core Kotlin Multiplatform 3D/2D game engine runtime powered by Vulkan, WebGPU, and Compose Multiplatform.

## Three-Layer Architecture Boundary

Awake enforces strict boundaries across three distinct architectural layers:
1. **Layer 1: Awake Core Engine (`awakekt/awake`)** (Apache 2.0): Runtime engine libraries (`:awake:scene`, `:awake:physics`, `:awake:render`, `:awake:ui:shadcn`, `:awake:project`, etc.).
2. **Layer 2: Awake Core Editor (`:awake:editor:contract` in `awakekt/awake`)** (Apache 2.0): Public, vendor-neutral editor contracts, provider extension points, and project plugin metadata published under `com.awakekt.awake.editor:contract`.
3. **Layer 3: Awake Studio Pro (`awakekt/awake-studio`)** (Commercial): Desktop authoring application (`:app:studio`), visual inspectors, collaborative workflows, and the secure runtime loader (`StudioPluginPipeline`).

## No third-party game brands

Never name a specific commercial game, its publisher, or its characters, places, items, or
proprietary file formats anywhere in this repository or its GitHub activity: code, tests,
fixtures, docs, commit messages, branch names, PRs, issues, and review comments. Call a consumer
game "a private consumer pack", describe its formats generically ("a legacy tiled terrain
format"), and use neutral fixture names ("Harbor Town"). Check the diff and any PR or issue text
for such names before committing or posting.

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

## Scene is the wrapper, not the home for capabilities

Awake is a library first. `awake:scene:*` binds capabilities into the ECS scene graph and the scene
document: a scene schema, its binding, the mapping to the capability's types, and the system that runs
the capability over a `World`. The capability itself, an algorithm or behaviour with an API of its own
(a simulation, sampling, culling, a clock), lives in a module outside `awake/scene/` and depends on no
`awake:scene` module; `./gradlew verifyCapabilityLayering` enforces it. Before you add to or change
anything under `awake/scene/`, read "What belongs in a scene module" in `awake/scene/README.md` and
activate `awake-framework-boundary`. A capability that still lives in a scene module is separated
first, not extended in place.

A played project needs no display: `awake:project` and `awake:project:runtime` reach no GPU backend
or window module, because the host passes its backends in, and a system that draws checks
`hasRenderer`. `./gradlew verifyHeadlessRuntime` enforces the dependency half.

## Agent skill bundles

Awake architecture lives in `docs/*`; agent execution guidance is installed from immutable sources,
not tracked in this repository. Run the public bundle installer when the current environment has
not yet deployed `.agents/skills`:

```bash
git clone https://github.com/awakekt/awake-agent-skills .agents/vendor/awake-agent-skills-bootstrap
python3 .agents/vendor/awake-agent-skills-bootstrap/scripts/install_consumer.py --project .
```

It needs Python 3.11 or newer; on Windows, where `python3` is often the Microsoft Store placeholder,
run it with `py -3`.

`.agents/skills.lock.toml` pins, by tag, commit and archive digest: the Core maintainer bundle
[awake-agent-skills](https://github.com/awakekt/awake-agent-skills), the game-authoring bundle
[awake-game-agent-skills](https://github.com/awakekt/awake-game-agent-skills) (both Apache-2.0,
`awake-*`), and unmodified `kmp-*` vendor skills. Never edit deployed copies. Studio repositories
add a private `maintained-studio` source using `studio-*` names; public Awake must not consume
that overlay. The awake-agent-skills README maps every Awake repository and which bundles each
pins; new or changed skills follow its `docs/skill-authoring.md`.

Keeping them current is automatic once the repository hooks are on
(`git config core.hooksPath .githooks`): `hooks/sync-agent-skills.sh` reinstalls when a pull or branch
switch moves the lockfile, and Claude Code runs it at session start (`.claude/settings.json`). It
syncs the main checkout, whose skills the app's worktrees under `.claude/worktrees/` read. The weekly
**Agent skills lock** workflow opens a PR moving every pin to its newest release.

Before touching `Renderer.kt`, a render-contract type, or a backend renderer, activate the pinned
`awake-render-pipeline` guidance and read `docs/reference/render-hardware-interface.md`.

**Pull Request & Release Invariants**:
1. Every PR must be created with `--milestone "<milestone>"` (e.g. `gh pr create --milestone "v0.1.0-beta.1"`). Never create a PR without an assigned milestone.
2. Every `feat:` and `fix:` PR must add a changelog fragment, `changelog/unreleased/<section>/<branch-name>.md` (section: `added`, `changed`, `deprecated`, `removed`, `fixed`, `security`), holding its bullet. Never edit `CHANGELOG.md` directly: `releaseCut` files fragments under the release. CI enforces both.
3. When a milestone is empty, cut the version it names as a **stable** release, never another `rc`: `-Prelease.channel=stable` for `0.1.0`, then `-Prelease.bump=patch` (fixes only) or `minor` (features or API change). `rc`/`beta` are previews only when asked for. Follow "Cutting a Core release" in `docs/release-process.md` end to end: one owner, tag the squash commit, the Vulkan tag when Vulkan changed, the downstream bumps, and a failed publish is never re-run.

Use `feat/*`, `fix/*`, `refactor/*`, or `docs/*` for topic branches. For dependent layers,
create a linear stacked PR chain with each PR targeting the branch immediately below it. Review
bottom-up, then collapse: once every layer is green, squash-merge each PR into the branch below it
from the top down, and squash the bottom PR into `main` once. Follow `docs/release-process.md`
before creating or retargeting stacked PRs.
