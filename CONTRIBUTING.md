# Contributing to Awake Engine

Awake Engine is an early-alpha Kotlin Multiplatform project. Contributions are welcome, but
changes should preserve the shared runtime boundary and keep the supported targets buildable.

## Before you start

- Search existing [issues](https://github.com/awakekt/awake/issues) and [pull requests](https://github.com/awakekt/awake/pulls).
- For a new feature or a non-trivial refactor, open an issue before implementation so the scope and
  acceptance criteria are clear.
- Do not include credentials, private keys, generated build output, local configuration, or files
  copied from another project.
- Report security vulnerabilities privately; see [SECURITY.md](SECURITY.md).

## Development setup

Clone the repository with its native submodules:

```bash
git clone --recurse-submodules https://github.com/awakekt/awake.git
cd awake
```

Awake targets Desktop JVM, Android, iOS, and WebAssembly/browser. The exact native prerequisites
depend on the target being changed. The engine showcase README documents runnable examples and
the repository's [module guide](awake/README.md) describes the module layout.

Run the smallest relevant verification first, then the broader checks when practical:

```bash
./gradlew detekt
./gradlew compileTestKotlinDesktop
./gradlew compileKotlinWasmJs compileTestKotlinWasmJs
./gradlew check
```

For rendering, native, or platform-specific work, run the matching module task and state which
targets were verified in the pull request. A desktop-only result is not evidence for iOS or WebGPU.

## Branches and commits

Start from the latest `origin/main` and use a short-lived topic branch:

```bash
git fetch origin
git switch main
git pull --ff-only origin main
git switch -c feat/short-description
```

Use one of these prefixes:

- `feat/*` — a new capability
- `fix/*` — a bug fix
- `refactor/*` — an internal structural change
- `docs/*` — documentation or repository guidance

Do not use `codex/*` for project branches. Keep commits focused and use Conventional Commit
messages such as `feat(scene): add ...`, `fix(webgpu): correct ...`, or `docs: update ...`.

## Where code belongs

Keep generic runtime contracts, platform adapters, codecs, math, ECS, rendering, and asset
capabilities in Awake Core. Keep commercial Studio policy, editor UI, plugins, cloud workflows,
marketplace behavior, and licensing in Awake Pro. Core must not gain Studio-only authentication,
scene/game authoring persistence, or backend product policy.

Prefer an existing Core capability over adding a second reader, parser, platform adapter, or
serialization path. If a capability is reusable across consumers, document its ownership and
boundary before adding a Pro-specific copy.

## Pull requests

Every pull request should:

1. Link an issue using `Fixes #123`, `Closes #123`, or `Related to #123`.
2. Describe the user-visible or engineering outcome and the affected modules/platforms.
3. Include tests or explain why testing is not applicable.
4. Include screenshots or recordings for UI and rendering changes when visual behavior changes.
5. Update public API dumps and documentation when the public surface changes.
6. Add a clear entry under `## [Unreleased]` in `CHANGELOG.md` for user-visible behavior,
   supported-platform, build, or public API changes.
7. Confirm that the branch contains no unrelated changes, secrets, or generated artifacts.

Use the repository pull request template. Topic branches target `main` unless the release process
explicitly calls for a stacked or release branch. Maintainers squash and merge after the required
checks pass. `main` does not require a branch to be up to date before merging, so a PR is not
re-run just because another PR landed first; CI on `main` after each merge is the backstop.

## License and provenance

By contributing, you agree that your contribution is provided under the repository's
[Apache License 2.0](LICENSE.md). Do not copy code, shaders, assets, or documentation without
recording compatible license and attribution information.
