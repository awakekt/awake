# Agent Routing

This public repository routes only technical Awake engine work. The authoritative role details
are in the public [Core agent catalog](https://github.com/awakekt/awake-agent-skills/blob/main/docs/agent-catalog.md).

| Work | Public Core role |
|---|---|
| Core math, ECS, scene lifecycle, asset contracts, and portable engine capabilities | `awake-engine-core-engineer` |
| Vulkan/WebGPU backends, GPU lifetime, JNI, physics bridge, and renderer evidence | `awake-render-backend-engineer` |
| Awake UI runtime, recipe parity, web-source translation, icons, and visual verification | `awake-ui-engineer` |
| Application composition, frame lifecycle, sample integration, and state flow | `awake-game-runtime-engineer` |
| KMP targets, Gradle, CI, release process, and developer documentation | `awake-platform-release-engineer` |
| Module ownership, dependency boundaries, and framework-versus-game decisions | `awake-architecture-auditor` |
| Documentation, entrypoint, and public catalog consistency | `awake-docs-maintainer` |

For a web or shadcn source, begin with the installed public
[`awake-web-to-compose`](https://github.com/awakekt/awake-agent-skills/tree/main/skills/awake-web-to-compose)
guidance. Add the public Tailwind, shadcn, or UI-audit skill only when the source actually uses
that technology.

Game production, narrative, creative direction, commercial workflow, Studio authoring, and Pro
scoring are not public routing targets. They require a Studio checkout with the private
`studio-*` overlay. The public [`awake-framework-boundary`](https://github.com/awakekt/awake-agent-skills/tree/main/skills/awake-framework-boundary)
skill remains the neutral rule for deciding whether work should cross that boundary.
