---
name: awake-core-editor
description: Author, extend, and verify vendor-neutral editor plugins and contracts in Awake Core (:awake:editor:contract). Use before adding editor extension points, writing community plugins, or defining editor provider contracts.
license: Apache-2.0
metadata:
  author: awake
  last-updated: '2026-09-19'
  keywords: Awake, Awake Core Editor, EditorPlugin, PluginManifest, EditorProvider, PluginId, PluginRegistry, ProviderRegistry, 3-layer architecture
---

# Awake Core Editor Architecture & Plugin Contracts

The Awake Engine ecosystem is structured into three strictly decoupled architectural layers:

1. **Layer 1: Awake Core Engine (`awaken`)** (Apache 2.0):
   Runtime libraries required to compile, execute, and ship games on Desktop, iOS, Android, and WASM (`:awake:scene`, `:awake:physics`, `:awake:render`, `:awake:ui:shadcn`, `:awake:project`, etc.).
2. **Layer 2: Awake Core Editor (`awaken:awake:editor:contract`)** (Apache 2.0):
   Public, vendor-neutral editor contracts, provider extension points, and project plugin metadata published under `com.awakekt:awake-editor-contract`. Allows third-party developers, community creators, and commercial tools to write plugins against an open-source standard.
3. **Layer 3: Awake Studio Pro (`awake-pro`)** (Commercial):
   Commercial desktop authoring application (`:app:studio`), visual inspectors, collaborative workflows, and the secure runtime loader (`StudioPluginPipeline`) that verifies signatures, checks permissions, and hosts plugins.

---

## The Core Editor Contract Boundary

All public plugin interfaces live in `:awake:editor:contract`. They are vendor-neutral and intentionally decoupled from Awake Studio internals:

- **Package**: `com.awakekt.awake.editor.core.plugin`
- **License**: Apache 2.0
- **Publish Coordinates**: `com.awakekt:awake-editor-contract`

### Fundamental Rule: Zero Downward or Horizontal Leaks
- `:awake:editor:contract` **must never** depend on `:app:studio` or any commercial plugin in `awake-pro`.
- `:awake:editor:contract` only depends on Awake Core modules (`:awake:project`, `:awake:ecs`, etc.).
- Third-party plugins compile strictly against `com.awakekt:awake-editor-contract`.

---

## Canonical Naming Convention

**Do NOT use** the legacy `Editor*` prefixed names. They exist only as deprecated typealiases for migration.
Always use the **canonical names** below when writing new code:

### Plugin-level types (in `EditorPlugin.kt`)

| Canonical (use this) | Deprecated alias (do NOT introduce) |
|---|---|
| `PluginId` | ~~`EditorPluginId`~~ |
| `PluginApiVersion` | ~~`EditorPluginApiVersion`~~ |
| `PluginApi` | ~~`EditorPluginApi`~~ |
| `PluginMetadata` | ~~`EditorPluginMetadata`~~ |
| `PluginLifecycle` | ~~`EditorPluginLifecycle`~~ |
| `PluginRegistry` | ~~`EditorPluginRegistry`~~ |

The interface `EditorPlugin` **keeps** its `Editor` prefix since it is the named extension point that hosts reference — this name is stable and intentional.

### Provider-level types (in `EditorProviders.kt`)

| Canonical (use this) | Deprecated alias (do NOT introduce) |
|---|---|
| `ProviderId` | ~~`EditorProviderId`~~ |
| `ProviderMetadata` | ~~`EditorProviderMetadata`~~ |
| `ProviderConfiguration` | ~~`EditorProviderConfiguration`~~ |
| `ProviderCodec` | ~~`EditorProviderCodec`~~ |
| `ValidationSeverity` | ~~`EditorValidationSeverity`~~ |
| `ValidationMessage` | ~~`EditorValidationMessage`~~ |
| `ProviderRegistry` | ~~`EditorProviders`~~ |
| `ComponentProvider` | ~~`EditorComponentProvider`~~ |
| `AssetProvider` | ~~`EditorAssetProvider`~~ |
| `EnvironmentProvider` | ~~`EditorEnvironmentProvider`~~ |
| `AnimationProvider` | ~~`EditorAnimationProvider`~~ |
| `BuildProvider` | ~~`EditorBuildProvider`~~ |

The interfaces `EditorProvider` and `EditorProviderKind` **keep** their `Editor` prefix — they are the named extension point interfaces that hosts reference.

---

## Plugin Contract Components

### 1. Plugin Manifest (`PluginManifest`)

Serialized descriptor for marketplace and `.awakeplugin` archives:

```kotlin
data class PluginManifest(
    val id: String,                        // reverse-domain (e.g. "com.example.terrain")
    val name: String,                      // human-readable display name
    val version: String,                   // SemVer (e.g. "1.2.0")
    val author: String = "Community",
    val description: String = "",
    val entrypointClass: String = "",      // FQN implementing EditorPlugin; if non-blank, archive MUST have payload bytes
    val requiredApiVersion: Int = 1,
    val minEngineVersion: String? = null,
    val supportedPlatforms: List<String> = emptyList(),
    val targetJvmVersion: Int? = null,
    val dependencies: List<PluginDependency> = emptyList(),
    val isPro: Boolean = false,
    val requiredLicense: String? = null,
    val category: String = "Tools",
    val tags: List<String> = emptyList(),
    val documentationUrl: String = "",
)
```

> **⚠️ Important**: `contributesDockTab` and `dockTabTitle` were retired. Bottom panel contributions are
> now declared by the plugin's `createProviders()` returning providers with `EditorProviderKind.BottomPanel`.
> Never re-add these manifest fields.

### 2. Plugin Lifecycle (`EditorPlugin` + `PluginLifecycle`)

```kotlin
// The named extension point interface (keeps "Editor" prefix)
interface EditorPlugin {
    val metadata: PluginMetadata               // use PluginMetadata, NOT EditorPluginMetadata

    fun createProviders(): List<EditorProvider> // return providers; no direct registry mutation
}

// Optional: for plugins that own resources beyond their providers
interface PluginLifecycle {
    fun dispose()                              // called after providers are unregistered
}
```

### 3. Editor Providers (`EditorProvider` & `EditorProviderKind`)

Plugins extend editor capabilities by returning typed `EditorProvider` implementations from `createProviders()`:

| `EditorProviderKind` | Purpose | Typed sub-interface |
|---|---|---|
| `Component` | Custom component panels | `ComponentProvider` |
| `Asset` | Asset import/processing | `AssetProvider` |
| `Environment` | Sky, lighting, ambient | `EnvironmentProvider` |
| `Animation` | Animation curves/clips | `AnimationProvider` |
| `Build` | Build steps & export | `BuildProvider` |
| `BottomPanel` | Docked bottom tray panels | (raw `EditorProvider`) |
| `Toolbar` | Toolbar actions & controls | (raw `EditorProvider`) |
| `Sidebar` | Left sidebar tabs | (raw `EditorProvider`) |
| `InspectorPanel` | Right inspector tabs | (raw `EditorProvider`) |
| `Keybinding` | Keyboard shortcut maps | (raw `EditorProvider`) |
| `Workspace` | Central canvas (viewport, visual scripting) | (raw `EditorProvider`) |
| `EntityTemplate` | Insertable entity archetypes | (raw `EditorProvider`) |
| `SceneSystems` | ECS systems injected into the scene loop | (raw `EditorProvider`) |
| `ViewportTool` | Interactive viewport tools | (raw `EditorProvider`) |
| `FloatingCard` | Floating HUD cards over the 3D viewport | (raw `EditorProvider`) |

### 4. `PluginRegistry` & `ProviderRegistry`

The host wires `PluginRegistry` → `ProviderRegistry`:

```kotlin
val providerRegistry = ProviderRegistry()        // was EditorProviders
val pluginRegistry = PluginRegistry(providerRegistry) // was EditorPluginRegistry
pluginRegistry.install(myPlugin)                 // calls myPlugin.createProviders() atomically
```

### 5. Asset Converters (`AssetConverterPlugin`)

```kotlin
interface AssetConverterPlugin : EditorPlugin {
    fun getConverters(): List<AssetConverter>
    override fun createProviders(): List<EditorProvider> = emptyList() // default no-op
}
```

---

## Project Plugin Reference

Projects declare their active plugins in `awake.project.json` using `AwakeProjectPluginReference` in `:awake:project`:

```kotlin
data class AwakeProjectPluginReference(
    val id: String,
    val path: String,               // project-relative path to the plugin bundle
    val version: String = "",
    val sha256: String? = null,     // optional content integrity pin
    val entrypointClass: String? = null,
    val required: Boolean = false,
)
```

- `sha256`: Prevents tampered or mismatched plugin binaries.
- `entrypointClass`: The fully qualified name implementing `EditorPlugin`.

---

## Verification & Architecture Checklist

When adding or modifying editor contracts in Awake Core:

1. **API Tracking**: Run `./gradlew :awake:editor:contract:desktopApiDump` then `desktopApiCheck`.
2. **Tests**: `./gradlew :awake:editor:contract:desktopTest`.
3. **Detekt**: Verify zero violations with `./gradlew :awake:editor:contract:detekt`.
4. **No deprecated shims**: Do not introduce `EditorDock`, `EntityPreset`, `V1` suffixes, `contributesDockTab`, or `dockTabTitle`.
5. **No re-introduction of old prefixes**: Use canonical names from the table above.
6. **Payload check**: If `PluginManifest.entrypointClass` is non-blank, the `.awakeplugin` archive must have non-empty `payloadBytes` — enforced by `PluginPreflightChecker` in the Pro pipeline.
7. **License & Provenance**: All files in `:awake:editor:contract` must have the Apache-2.0 header and `Ron June Valdoz` copyright notice.
