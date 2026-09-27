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
awake-vulkan = "{{ awake_vulkan_version }}"
```

Core modules share one version. The Vulkan backend (`backend:vulkan` and its bindings) is released
separately; each Vulkan release depends on one exact Core release, listed in its release notes.
When using another release, open that version of the documentation, keep all Core modules on the
same version, and check that the target platform is listed in that release’s notes. Development
builds are published separately and are not promoted to the stable `latest` alias.

## Compatibility guidance

- Native desktop applications use the Vulkan backend.
- Browser applications use the WebGPU backend through WasmJs. It is published as a snapshot only
  until its wgpu4k dependency has a stable release.
- Android uses the Vulkan backend; iOS uses Vulkan through MoltenVK. Support for other modules
  depends on the specific module and release.
- Internal repository plans, milestone checklists, and branch rules are not part of the public
  compatibility contract.
