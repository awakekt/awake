# Awake Engine: Agent Guide

Welcome to **Awake Engine**. This repository is the core Kotlin Multiplatform 3D/2D game engine runtime powered by Vulkan, WebGPU, and Compose Multiplatform.

## Three-Layer Architecture Boundary

Awake enforces strict boundaries across three distinct architectural layers:
1. **Layer 1: Awake Core Engine (`awaken`)** (Apache 2.0): Runtime engine libraries (`:awake:scene`, `:awake:physics`, `:awake:render`, `:awake:ui:shadcn`, `:awake:project`, etc.).
2. **Layer 2: Awake Core Editor (`awaken:awake:editor:contract`)** (Apache 2.0): Public, vendor-neutral editor contracts and provider extension points.
3. **Layer 3: Awake Studio Pro (`awake-pro`)** (Commercial): Desktop authoring application, visual inspectors, and commercial workflows.

## Skill precedence and technology boundaries

Project-owned `awake-*` skills are authoritative for Awake engine code and the Awake-owned UI/runtime framework. They take precedence over generic `kmp-*` skills. Use `kmp-compose-*` or `kmp-shadcn-*` only when verified dependencies and imports require them.

## Agent skill bundles

Awake architecture lives in `docs/*`; agent execution guidance is installed from immutable sources, not tracked in this repository. Bootstrap the public bundle with:

```bash
git clone https://github.com/awakekt/awake-agent-skills .agents/vendor/awake-agent-skills-bootstrap
python3 .agents/vendor/awake-agent-skills-bootstrap/scripts/install_consumer.py --project .
```

`.agents/skills.lock.toml` pins public `awake-*` skills and unmodified `kmp-*` vendor dependencies. Never edit deployed copies; Studio-only `studio-*` skills belong to a private overlay and are not permitted in this public engine checkout.

Before touching `Renderer.kt`, a render-contract type, or a backend renderer, activate the pinned `awake-render-pipeline` guidance and read `docs/reference/render-hardware-interface.md`.

**Pull Request & Release Invariants**:
1. Every PR must be created with `--milestone "<milestone>"`.
2. Every `feat:` and `fix:` PR must add a bullet entry to `CHANGELOG.md` under `## [Unreleased]`.
3. When an active milestone is complete, cut the release with `./scripts/release.py cut`.
