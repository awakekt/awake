# Agent Catalog Boundary

Awake does not keep an agent roster or personas as tracked repository content. The public
technical catalog, naming rules, and validation live in
[`awakekt/awake-agent-skills`](https://github.com/awakekt/awake-agent-skills/blob/main/docs/agent-catalog.md).

The public bundle provides only technical `awake-*` roles and skills. Studio Pro scoring,
commercial operations, Studio/editor workflows, and creative personas belong to the private
`awakekt/awake-studio-agent-skills` overlay under the `studio-*` namespace.

The public Awake lockfile must never include a `maintained-studio` source. See
[AI Collaboration](ai-collaboration.md) for installation, update, and product-tooling boundary
rules.
