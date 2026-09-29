# Unreleased changelog fragments

Each change adds its own file here instead of editing `CHANGELOG.md`, so parallel PRs never
conflict and a PR merged after a release cut is filed under the release that ships it.

- Path: `changelog/unreleased/<section>/<branch-name>.md`, where `<section>` is one of `added`,
  `changed`, `deprecated`, `removed`, `fixed`, `security`.
- Content: the entry as it should read in `CHANGELOG.md`, e.g.
  `- **Sideways scroll reaches the app on desktop.** GLFW's scroll callback kept only ...`

`./gradlew releaseCut` moves every fragment into the new version's section of `CHANGELOG.md`, in
the order above, and deletes the files. CI requires a fragment on every `feat:` and `fix:` PR and
rejects direct edits to `CHANGELOG.md` outside `release-cut/*` branches.
