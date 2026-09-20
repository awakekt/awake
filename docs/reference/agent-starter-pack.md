# Agent Starter Pack

Use a source/deployment split when adding agent guidance to another repository.

```text
consumer-repository/
├── docs/                         # product architecture and contributor rules
├── scripts/ and tools/            # product tooling; no agent install required
├── AGENTS.md / CLAUDE.md / GEMINI.md
└── .agents/
    └── skills.lock.toml           # reviewed immutable source pins only

agent-skills-repository/
├── skills/
├── commands/
├── docs/                          # package catalog and authoring rules
└── scripts/                       # package validation and installer workflow
```

Keep source bundles separate by ownership: use an upstream pinned vendor bundle unchanged,
publish maintained technical guidance independently, and reserve private overlays for proprietary
work. Give each source an immutable tag, full commit, archive digest, license, exposed names, and
deployment targets in the consumer lockfile.

The installer should verify the source origin and immutable archive before materializing ignored
copies below `.agents/vendor/` and deploying only declared entries. Treat deployed copies as
read-only; updates happen through a reviewed lockfile bump.

Put any script required for a build, release, CI job, reproducible generator, or evidence capture
in the consumer's `scripts/` or `tools/` tree. Put only installer, package-validation, catalog,
and agent-workflow helpers in the skills repository. Product tooling must run on a clean checkout
without `.agents` installed.

For Awake-specific examples, see [AI Collaboration](ai-collaboration.md) and the public
[Core skills repository](https://github.com/awakekt/awake-agent-skills).
