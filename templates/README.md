# Starter templates

Awake's three starter templates live here and are published to their own repositories, so "Use
this template" keeps working (awakekt/awake#721):

| Folder | Published to | What it is |
|---|---|---|
| `templates/game` | [awakekt/awake-template](https://github.com/awakekt/awake-template) | A Kotlin Multiplatform game starter |
| `templates/project` | [awakekt/awake-project-template](https://github.com/awakekt/awake-project-template) | An app that plays an Awake Studio project; Studio's app export is built on it |
| `templates/plugin` | [awakekt/awake-plugin-template](https://github.com/awakekt/awake-plugin-template) | A starter editor plugin |

**Those repositories are published copies.** Make a change by editing `templates/<name>` in a Core
pull request, not the repository directly: `.github/workflows/publish-templates.yml` overwrites
what it tracks there on every Core release, so a direct edit is lost on the next publish.

`.github/workflows/templates.yml` and `obfuscated-templates.yml` build these folders against this
pull request's own Core and Vulkan, the way "Use this template" would, so a change that breaks a
template fails here, against the change that broke it.

## Versions

Each template's `gradle/libs.versions.toml` pins whatever Awake release it was last published
against. Two things stamp a fresh set of versions into a scratch copy of that file, never into
this tree:

- `tools/check_template_consumer.sh`, with this build's own Core, Vulkan and WebGPU versions, so
  Core's CI tests the template against the change that might break it, not the stale pin.
- `tools/publish_template.sh`, with a release's versions, driven by `publish-templates.yml`.

Both call `tools/stamp_template_versions.sh`. It only rewrites a catalog entry that is already
there, so it safely does nothing on the plugin template's catalog, which names neither Vulkan nor
WebGPU.

## Publishing

On each Core release tag (`v*`, never `vulkan-v*`) that finishes publishing -- the release itself
and its WebGPU release pin both succeeded -- `publish-templates.yml` stamps that release's versions
into all three templates and pushes each as one commit, `chore: Awake Core X.Y.Z`, to its
repository's `main`. `workflow_dispatch` republishes one named template between releases.

`tools/publish_template.sh` copies only the files `templates/<name>` tracks onto the repository: it
overwrites a matching path and adds a new one, but never deletes a path the repository carries that
`templates/<name>` does not. That is how a few things that live only in the template repository, not
here, stay there untouched:

- The project template's own GitHub Pages web player and its `.github/scripts/check-webgpu-pin.sh`,
  which deploy to that repository's own Pages environment and release list and were left out of
  this import.
- Issue templates and other `.github/ISSUE_TEMPLATE` configuration, since issues are filed against
  the template repository, not Core.

It does not propagate a deletion made inside `templates/<name>` -- removing a path that is no
longer wanted needs a one-off commit directly in the template repository as well.
