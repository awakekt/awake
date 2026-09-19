---
name: awake-core-editor
description: Author, extend, and verify vendor-neutral editor plugins and contracts in Awake Core (:awake:editor:contract). Use before adding editor extension points, writing community plugins, or defining editor provider contracts.
license: Apache-2.0
metadata:
  author: awake
  last-updated: '2026-09-19'
  keywords: Awake, Awake Core Editor, EditorPlugin, PluginManifest, EditorProvider, AssetConverterPlugin, 3-layer architecture
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

## Plugin Contract Components

### 1. Plugin Manifest (`PluginManifest`)
Every editor plugin describes its metadata and capabilities through `PluginManifest`:
- `id`: Reverse-domain format (e.g. `com.example.terrain-tools`).
- `name`: Human-readable display name.
- `version`: Semantic version string (`x.y.z`).
- `apiVersion`: Target Awake Editor Contract version.
- `kind`: `PluginKind` (`Tool`, `Asset`, `Workspace`, `RuntimeBridge`).
- `requiredPermissions`: Explicit sandbox permissions declared by the plugin.
- `author`, `description`, `homepage`: Metadata fields.

### 2. Plugin Lifecycle (`EditorPlugin`)
```kotlin
interface EditorPlugin {
    val manifest: PluginManifest
    fun activate(context: EditorPluginContext)
    fun deactivate(context: EditorPluginContext)
}
```
- `activate(context)`: Registers providers, tools, and listeners with `context.registry`.
- `deactivate(context)`: Cleanly unregisters providers and releases allocated resources.

### 3. Editor Providers (`EditorProvider` & `EditorProviderKind`)
Plugins extend editor capabilities by registering `EditorProvider` implementations:
- `Tool`: Action or interactive workflow (e.g. terrain sculpting, UV unwrapping).
- `Inspector`: Custom component or entity inspector panels.
- `FloatingCard`: Transient contextual tool overlays.
- `BottomPanel`: Docked bottom drawers (e.g. animation timeline, console, profiler).
- `ViewportOverlay`: Viewport HUD controls and gizmo overlays.
- `Build`: Project build steps and export pipelines.
- `Environment`: Sky, lighting, and ambient preview controllers.
- `EntityTemplate`: Custom entity templates and archetypes.
- `AssetConverter`: Asset conversion and cooking extensions (`AssetConverterPlugin`).

### 4. Asset Converters (`AssetConverterPlugin`)
Pure byte-to-engine asset converters:
```kotlin
interface AssetConverterPlugin : EditorPlugin {
    val sourceExtension: String
    val targetExtension: String
    fun convert(sourceBytes: ByteArray): ByteArray
}
```

---

## Project Plugin Reference

Projects declare their active plugins in `awake.project.json` using canonical `AwakeProjectPluginReference` in `:awake:project`:
```kotlin
data class AwakeProjectPluginReference(
    val id: String,
    val path: String,
    val version: String = "",
    val sha256: String? = null,
    val entrypointClass: String? = null,
    val required: Boolean = false,
)
```
- `sha256`: Optional content integrity pin to prevent tampered or mismatched plugin binaries.
- `entrypointClass`: The fully qualified name of the class implementing `EditorPlugin`.
- `path`: Project-relative path to the plugin bundle (or local directory).

---

## Verification & Architecture Checklist

When adding or modifying editor contracts in Awake Core:
1. **Public API Tracking**: Run `./gradlew :awake:editor:contract:desktopApiDump` and `./gradlew :awake:editor:contract:desktopApiCheck`.
2. **Detekt & Code Smells**: Verify zero violations with `./gradlew :awake:editor:contract:detekt`.
3. **No Deprecated Shims**: Ensure old names (`EditorDock`, `EntityPreset`, etc.) are not re-introduced.
4. **License & Provenance**: All files in `:awake:editor:contract` must have the Apache-2.0 header and Ron June Valdoz copyright notice.
