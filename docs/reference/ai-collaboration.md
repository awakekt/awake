# AI Collaboration

Awake keeps architecture and product tooling independently usable from agent tooling.

## Ownership

| Surface | Source of truth | Purpose |
|---|---|---|
| `docs/*` | This repository | Architecture, module boundaries, product workflows, and contributor rules |
| `scripts/`, `tools/`, Gradle, CI, hooks | This repository | Reproducible product builds, generators, checks, and evidence |
| [`awake-agent-skills`](https://github.com/awakekt/awake-agent-skills) | Public Core skills repository | Public `awake-*` guidance, technical personas, and agent workflow helpers |
| Pinned third-party bundles | Their upstream repositories | Unmodified vendor `kmp-*` skills and commands |
| `awake-studio-agent-skills` | Private Studio repository | `studio-*` scoring, commercial workflows, and creative/Studio personas |

`AGENTS.md`, `CLAUDE.md`, and `GEMINI.md` are short bootstrap entrypoints. They point to this
documentation and, when needed, to the pinned agent bundle; they are not a second architecture
manual.

## Installation and update policy

The public Awake checkout tracks only `.agents/skills.lock.toml`, never deployed skill content.
The lock records each source's kind, URL, tag, immutable commit, archive digest, license, exposed
names, and deployment targets. Bootstrap with the public installer:

```bash
git clone https://github.com/awakekt/awake-agent-skills .agents/vendor/awake-agent-skills-bootstrap
python3 .agents/vendor/awake-agent-skills-bootstrap/scripts/install_consumer.py --project .
```

The installer verifies origin, revision, and archive digest, caches each source under
`.agents/vendor/<source>@<sha>`, and deploys only lock-declared names into `.agents/skills` and
`.agents/commands`. Those copies are immutable. Upgrade a bundle by reviewing a lockfile change;
never patch an installed copy.

Public Awake never includes a `maintained-studio` source. A Studio checkout may add its own
private lock entry using `studio-*` names, which may extend but never replace public `awake-*`
guidance.

## Decision rule

- Put product behavior, validation, and long-lived technical design in this repository.
- Put how an agent should apply that design in the owning skills repository.
- Put an executable product dependency in `scripts/` or `tools/`; it must run on a clean checkout
  with no agent bundle installed.
- Put installer, catalog, schema, or agent-workflow support in a skills repository. Gradle, Awake
  CI, `scripts/awake`, hooks, and product tools must not invoke it.

For product gates, use [`scripts/awake verify`](../../scripts/awake) and the documented `tools/`
commands. Agent package validation and installer tests run in the repository that owns the bundle.

## Read order

1. [Architecture](../architecture.md) and the relevant reference document.
2. This collaboration boundary.
3. The public [Core agent catalog](https://github.com/awakekt/awake-agent-skills/blob/main/docs/agent-catalog.md), if agent routing is useful.
4. The matching installed public skill, if it is available.

Studio-only tasks require a Studio checkout and its private overlay; they are deliberately not
available through this public repository.
