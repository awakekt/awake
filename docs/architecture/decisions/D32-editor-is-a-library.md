# D32 — The editor is a library, not Studio's UI (2026-08-31)

**Status: Accepted.** `awake:editor` and its adapters are a published library whose consumers are
other applications. Code in them is measured against that contract, never against how much of it
Studio happens to call.

## The contradiction this resolves

The build classifies the editor as an application:

| Signal | Reading |
|---|---|
| No `awake.publish-convention` in `editor`, `editor:scene`, `editor:physics` | Nothing outside this repo can consume them |
| `"editor"` in `apiValidation.ignoredProjects` | Its public API is not tracked at all |
| The company it keeps in that list — `ui-showcase`, `engine-showcase`, `studio`, `server`, `benchmark`, `generator` | Every other entry is a sample or a build tool |

The code is written as a library:

- `awake:editor` does not depend on `awake:ecs`. `EditorEntityId` is a string, and
  `EditorFieldScope`/`EditorInspector<T>` name no entity, world or component.
- `EditorProviders` has stable ids, ordered registration, atomic batch install, and disposal.
- `EditorPlugin` carries an id, a version, and an `EditorPluginApiVersion` compatibility check.
- The generic/adapter split (`editor` → `editor:scene` → `editor:physics`) exists so a consumer
  can take one layer without the ones below it.

Nobody builds version negotiation for code only they call.

## What the contradiction cost

Twice in one day, capability was recommended for deletion on a caller count:

- **`EditorPlugin`** — "85 lines of ids and version checks for zero implementations". It was a
  considered design waiting for its first user. `PhysicsEditorPlugin` is now that user and the
  registry is load-bearing.
- **The Stage 4 capability panels** — `EditorAssetPanel`, `EditorAnimationPanel`,
  `EditorBuildPanel`, `EditorEnvironmentPanel`, ~632 lines, called "dead". They were built
  deliberately in `781f40c29` with tests that still pass, and the plan marks the stage complete.
  They are **unwired, not dead** — nothing superseded them, and the audit lists the asset browser
  as a *missing* feature while the panel that provides it sits in the repo.

Both mistakes have one cause: a caller count measures what is *connected*, and for a library that
is not a measure of anything. In the library reading an unreferenced public component is
inventory. In the app reading it is unfinished work. Nobody had decided which, so the same
question was re-litigated from first principles each time it came up.

## The decision

**Library.** The stated goal — an editor that installs plugins from a marketplace and supports
consumer customization — has no other reading. A marketplace requires third parties compiling
against a stable surface, and there is no third party if nothing is published.

## What follows, and is not optional

1. **Publish all three editor modules.** Add `awake.publish-convention`. Until then the capability
   panels are inventory nobody can buy, and `EditorPluginApiVersion` negotiates compatibility for
   an artifact that does not exist.
2. **Remove `"editor"` from `apiValidation.ignoredProjects` and dump its API.** A library whose
   public surface is untracked has no ABI. Note the current state is already inconsistent:
   `editor:scene` and `editor:physics` have API dumps and only the core — the module that matters
   most — does not.
3. **Judge editor code by its contract, not by callers.** An unreferenced public component needs a
   documented usage example, not deletion. Deleting it needs a reason of its own: superseded,
   unsound, or a feature no longer wanted.

Sequenced after the current release: (1) and (2) both change what gets published, and that is
mid-flight.

## What this does not decide

**`AwakePluggableWorkbench` still has to be judged on its merits**, and being a library does not
save it. It is an *alternative* to `AwakeEditorShell`, which Studio uses — two ways to compose an
editor, one of them exercised. The scene-editor audit's criticisms are about the code rather than
its caller count: raw string tab ids, plain `var` rather than snapshot state, and tabs that appear
and vanish on list emptiness. That is a real "pick one" decision and this record does not make it.

Likewise this says nothing about whether every Stage 4 panel is wanted. It says only that
"nothing calls it" is not the argument.

## Related

- [Module architecture](../reference/module-architecture.md) — why `editor:physics` is a module
- [Scene editor audit](../audits/2026-08-30-scene-editor-production-readiness-audit.md) — gap #16
  is the asset browser this record reframes
- [`awake:editor:scene` README](../../awake/editor/scene/README.md) — the consumer-facing guide
