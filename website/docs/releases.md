# Releases and compatibility

Awake is in the alpha stage. The public API and published module set may change between releases.
Use the [GitHub Releases](https://github.com/awakekt/awake/releases) page as the authoritative
source for release notes and published versions.

## Current example version

The installation examples in this site currently use `0.1.0-alpha.4`:

```toml
[versions]
awake = "0.1.0-alpha.4"
```

When using another release, keep all Awake modules on the same version and check that the target
platform is listed in that release’s notes.

## Compatibility guidance

- Native desktop applications use the Vulkan backend.
- Browser applications use the WebGPU backend through WasmJs.
- Android and iOS support depends on the specific module and release.
- Internal repository plans, milestone checklists, and branch rules are not part of the public
  compatibility contract.
