# Releases and compatibility

Awake is in the alpha stage. The public API and published module set may change between releases.
Use the [GitHub Releases](https://github.com/awakekt/awake/releases) page as the authoritative
source for release notes and published versions.

## Versioned documentation

Every published Awake release has a matching documentation version. Use the version selector in
the site header to switch between releases; the installation examples on each version use the
same library version as that release.

The current default release is:

```toml
[versions]
awake = "{{ awake_version }}"
```

When using another release, open that version of the documentation, keep all Awake modules on the
same version, and check that the target platform is listed in that release’s notes. Development
builds are published separately and are not promoted to the stable `latest` alias.

## Compatibility guidance

- Native desktop applications use the Vulkan backend.
- Browser applications use the WebGPU backend through WasmJs.
- Android and iOS support depends on the specific module and release.
- Internal repository plans, milestone checklists, and branch rules are not part of the public
  compatibility contract.
