# AI Collaboration

Awake keeps architecture and product tooling independently usable from agent tooling.

## Ownership

| Surface | Source of truth | Purpose |
|---|---|---|
| `docs/*` | This repository | Architecture, module boundaries, product workflows, and contributor rules |
| `scripts/`, `tools/`, Gradle, CI, hooks | This repository | Reproducible product builds, generators, checks, and evidence |
| [`awake-agent-skills`](https://github.com/awakekt/awake-agent-skills) | Public Core skills plugin | Public `awake-*` Core maintainer guidance, technical personas, and workflow helpers |
| [`awake-game-agent-skills`](https://github.com/awakekt/awake-game-agent-skills) | Public game skills plugin | Public `awake-*` ECS, scene, runtime, asset, and UI authoring guidance |
| [`kmp-agent-skills`](https://github.com/ronjunevaldoz/kmp-agent-skills) | Public KMP skills plugin | Unmodified vendor `kmp-*` Kotlin Multiplatform skills |
| `awake-studio-agent-skills` | Private Studio plugin | `studio-*` scoring, commercial workflows, and creative/Studio personas |

`AGENTS.md`, `CLAUDE.md`, and `GEMINI.md` are short bootstrap entrypoints. They point to this
documentation and to the registered agent plugins; they are not a second architecture manual.

## Installation and update policy

The public Awake checkout tracks only `.claude/settings.json` plugin marketplace declarations, never
copied skill content. Register and install the three public bundles in Claude Code:

```bash
claude plugin marketplace add awakekt/awake-agent-skills
claude plugin install awake-agent-skills@awake-agent-skills
```

Do the same for `awakekt/awake-game-agent-skills` and `ronjunevaldoz/kmp-agent-skills`. Changes to a
skill happen in its owning skills repository, never in a local copy inside this checkout.

Public Awake never registers `awake-studio-agent-skills`. A Studio checkout adds that private plugin
under `studio-*` names, which may extend but never replace public `awake-*` guidance.

## Decision rule

- Put product behavior, validation, and long-lived technical design in this repository.
- Put how an agent should apply that design in the owning skills repository.
- Put an executable product dependency in `scripts/` or `tools/`; it must run on a clean checkout
  with no agent plugin installed.
- Put catalog, schema, or agent-workflow support in a skills repository. Gradle, Awake CI,
  `scripts/`, hooks, and product tools must not invoke it.

For product gates, use `./gradlew awakeVerify` and the documented `tools/`
commands. Agent package validation runs in the repository that owns the bundle.

## Read order

1. [Architecture](../architecture.md) and the relevant reference document.
2. This collaboration boundary.
3. The public [Core agent catalog](https://github.com/awakekt/awake-agent-skills/blob/main/docs/agent-catalog.md), if agent routing is useful.
4. The matching installed public skill, if it is available.

Studio-only tasks require a Studio checkout and its private overlay; they are deliberately not
available through this public repository.
