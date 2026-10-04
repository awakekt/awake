# D35: the editor boundary between Core and Studio

Status: accepted (2026-10-03)

## Decision

Awake Core owns everything a shipped game runs and every interface an editor plugin implements.
Awake Studio owns the editor library, the editor app, and all hosting logic. Code moves between the
repositories only when it changes sides of that line.

| Belongs in Core (`awakekt/awake`, Apache-2.0) | Belongs in Studio (`awakekt/awake-studio`) |
|---|---|
| Runtime modules, including the runtime that reads data a Studio tool emits | The editor library (`awake/editor/*`) and the app (`app/studio`) |
| `:awake:editor:contract`: `EditorPlugin`, `PluginManifest`, every provider interface a plugin implements, and the data those interfaces carry | How providers are hosted: dock and workspace layout, the keymap and its conflicts, template insertion, the floating-card deck, the shell |
| Neutral types those interfaces need (`Composer`, `Modifier`, `World`, `Key`) | Plugin discovery, signatures, entitlement, the marketplace |

A test for each change: if a third-party plugin has to implement it or read it, it is contract and
lives in Core. If only the host calls it, it is Studio.

## Why

The editor moved back and forth without a decision. It started in Core (D32, 2026-08-31, "the
editor is a library"). It left for Studio inside the v0.1.0-dev.11 release cut (2026-09-07) with
no stated reason, while D32 still said the opposite. Then pieces returned one at a time: the plugin
contract (#50), runtime that had become commercial (#145), and the panel and plugin hooks (#291 and
this change). Each move re-derived the line. This record fixes it, and replaces D32.

A plugin that must link Studio's editor library to draw a panel or add a toolbar control is tied to
Studio's AGPL-or-commercial license. Keeping every plugin-facing interface in the Apache contract
keeps third-party plugins license-free. The hosting stays Studio's: interfaces expose method shapes,
never how Studio lays out, orders or resolves what plugins contribute.

## Consequences

- Studio's own contribution types extend the Core interfaces instead of redefining them.
- A new extension point is added to `:awake:editor:contract` first, with a test, then hosted in
  Studio.
- Scene-bound hooks (viewport tools, scene systems, component inspectors) follow the same rule; their
  neutral scene types are decision [D36](D36-scene-bound-editor-hooks.md).
