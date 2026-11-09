# Agent Catalog Boundary

Awake does not keep an agent roster or personas as tracked repository content. The public
technical catalog, naming rules, and validation live in
[`awakekt/awake-agent-skills`](https://github.com/awakekt/awake-agent-skills/blob/main/docs/agent-catalog.md).

The public bundle provides only technical `awake-*` roles and skills. Studio Pro scoring,
commercial operations, Studio/editor workflows, and creative personas belong to the private
`awakekt/awake-studio-agent-skills` overlay under the `studio-*` namespace.

The public Awake `.claude/settings.json` must never register `awake-studio-agent-skills`. See
[AI Collaboration](ai-collaboration.md) for plugin registration and product-tooling boundary
rules.
