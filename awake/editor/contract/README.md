# `:awake:editor:contract`

The Apache-2.0 contract between an Awake editor host, such as Awake Studio, and the plugins it loads.
A plugin compiles against this module only (`com.awakekt.awake.editor:contract`), never against a
host's own editor library. The host owns layout and behaviour; the contract only fixes what a plugin
hands over. The boundary is decision
[D35](../../../docs/architecture/decisions/D35-editor-boundary.md).

## A plugin

```kotlin
class WeatherPlugin : EditorPlugin {
    override val metadata = PluginMetadata(PluginId("com.example.weather"), "Weather", "1.0.0", PluginApi.currentVersion)

    override fun createProviders(): List<EditorProvider> = listOf(WeatherPanel(), WeatherKeys())
}

class WeatherPanel : PanelProvider {
    override val metadata = ProviderMetadata(ProviderId("com.example.weather.panel"), "Weather")
    override val kind = EditorProviderKind.BottomPanel

    context(_: Composer)
    override fun content() {
        ShadcnButton("Make it rain", onClick = { /* change data the game runtime reads */ })
    }
}
```

The host installs it with `PluginRegistry.install(plugin)`, which registers every provider from
`createProviders()` atomically and disposes them in reverse order on uninstall. Provider IDs are
global, so prefix them with the plugin ID.

## Providers

Implement the interface for the slot you want. Each one fixes its own `kind` and needs no codec unless
it stores configuration (`NoProviderConfiguration` is the default).

| Kind | Implement | Use it for | The host keeps |
|---|---|---|---|
| `BottomPanel`, `Sidebar`, `InspectorPanel` | `PanelProvider` (set `kind`) | A panel of your own UI: a log, a palette, a property page | The tab, labelled with `metadata.displayName` |
| `Toolbar` | `ToolbarProvider` | A button or small control in the toolbar | Placement; `displayName` is the tooltip |
| `Workspace` | `WorkspaceProvider` | A whole central canvas: a node graph, a map editor | The workspace switcher and the canvas size |
| `FloatingCard` | `FloatingCardProvider` | A small card over the viewport, opened from a tool or another card | Where cards dock and their order (`FloatingCardDeck`) |
| `Keybinding` | `KeybindingProvider` | Keyboard shortcuts for your plugin's actions | The keymap, user rebinding and conflicts |
| `EntityTemplate` | `EntityTemplateProvider` | An insertable entity that arrives with your scene components | The insert menu and the insertion |

Asset import is not a provider kind: implement `AssetConverterPlugin` and register an
`AssetConverter` per file extension.

### Reserved kinds

These kinds exist so persisted data stays stable, but the contract gives them no behaviour yet. A
plugin that uses one is tied to whichever host interprets it.

| Kind | Waiting on |
|---|---|
| `Component` (`ComponentProvider`) | Inspector fields for a component type; needs neutral scene types (D35) |
| `SceneSystems`, `ViewportTool` | Neutral scene and viewport types (D35) |
| `Asset` (`AssetProvider`), `Environment`, `Animation`, `Build` (`BuildProvider`) | A defined use; until then use a panel, a workspace, or `AssetConverterPlugin` |

## Packaging

A plugin ships as a `.awakeplugin` archive with a `plugin.json` `PluginManifest`. A non-blank
`entrypointClass` needs a bytecode payload. A plugin is free unless `requiredLicense` names an
entitlement; each host maps that identifier to its own tiers.

## Changing this module

New extension points start here with a test, then get hosted. Run
`./gradlew :awake:editor:contract:desktopApiDump :awake:editor:contract:desktopTest :awake:editor:contract:detekt`
and review the API dump: a removal is a breaking change for every published plugin.
