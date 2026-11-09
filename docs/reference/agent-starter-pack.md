# Agent Starter Pack

Use a plugin/repository split when adding agent guidance to another repository.

```text
consumer-repository/
├── docs/                          # product architecture and contributor rules
├── scripts/ and tools/            # product tooling; no agent plugin required
├── AGENTS.md / CLAUDE.md / GEMINI.md
└── .claude/
    └── settings.json              # extraKnownMarketplaces and enabledPlugins only

agent-skills-repository/
├── .claude-plugin/                # marketplace.json and plugin.json
├── skills/
├── agents/
├── commands/
├── docs/                          # package catalog and authoring rules
└── scripts/                       # package validation helpers
```

Keep skill bundles separate by ownership: register upstream vendor plugins unchanged, publish
maintained technical guidance in its own public plugin repository, and reserve private plugin
marketplaces for proprietary work. Never copy skill directories into a consumer repository; updates
happen in the owning plugin repository.

Put any script required for a build, release, CI job, reproducible generator, or evidence capture
in the consumer's `scripts/` or `tools/` tree. Put only package-validation, catalog, and
agent-workflow helpers in the skills repository. Product tooling must run on a clean checkout
without any agent plugin installed.

For Awake-specific examples, see [AI Collaboration](ai-collaboration.md) and the public
[Core skills repository](https://github.com/awakekt/awake-agent-skills).
