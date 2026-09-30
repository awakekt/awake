# Scene document schema

<p class="awake-lede">Every key a scene document (<code>*.scene.json</code>) can hold, with its type and default. Taken from <code>com.awakekt.awake.scene.document</code> in <code>awake:scene:document</code>.</p>

For what each component holds, see [Scene document components](scene-document-components.md).

## Document

`SceneDocument`, the top level of the file.

| Key | Type | Default | What it holds |
| --- | --- | --- | --- |
| `version` | integer | `1` (`SCENE_SCHEMA_VERSION`) | Schema version the document was written for. |
| `name` | string | none | Scene title. |
| `nodes` | array of [node](#node) | `[]` | Top-level nodes. |
| `extensions` | array of [extension record](#extension-record) | `[]` | Data owned by extensions, kept as written. |

## Node

`SceneNode`. Each node becomes one ECS entity.

| Key | Type | Default | What it holds |
| --- | --- | --- | --- |
| `name` | string | none | Node name. Becomes the entity's `Name`. Must be unique across the whole document. Other components refer to nodes by this name. |
| `transform` | [transform](#transform) | identity | Local transform, relative to the parent node. Becomes the entity's `Transform`. |
| `components` | array of [component](#component) | `[]` | Components attached to the entity. |
| `children` | array of node | `[]` | Child nodes. Their `Transform` is parented to this node. |
| `prefabGuid` | string | none | GUID of the prefab this node came from. Kept in the document; the scene runtime does not read it. |
| `overrides` | array of [override](#prefab-override) | `[]` | Property overrides over the linked prefab. Kept in the document; the scene runtime does not read them. |

## Transform

`SceneTransform`.

| Key | Type | Default | What it holds |
| --- | --- | --- | --- |
| `position` | [vector](#vector) | `{x: 0, y: 0, z: 0}` | Local translation. |
| `rotation` | [vector](#vector) | `{x: 0, y: 0, z: 0}` | Local Euler angles in radians, applied X, then Y, then Z. |
| `scale` | [vector](#vector) | `{x: 1, y: 1, z: 1}` | Local scale. |

## Component

One entry of a node's `components` array.

| Key | Type | Default | What it holds |
| --- | --- | --- | --- |
| `component` | string | required | The component id, such as `light` or `mesh_renderer`. Picks which fields follow. |
| *other keys* | per component | per component | The component's fields, listed in [Scene document components](scene-document-components.md). |

## Extension record

`SceneExtensionRecord`.

| Key | Type | Default | What it holds |
| --- | --- | --- | --- |
| `id` | string | required | Extension id. Must not be blank. |
| `version` | integer | required | Extension data version. Must be 1 or more. |
| `payload` | any JSON | required | The extension's data. |

A record whose extension is not installed loads with a warning and keeps its data.

## Prefab override

`ScenePropertyOverride`.

| Key | Type | Default | What it holds |
| --- | --- | --- | --- |
| `targetPath` | string | required | Property path, for example `components[0].color`. |
| `value` | string | required | Serialized value. |

## Value types

### Vector

`SceneVec3`.

| Key | Type | Default |
| --- | --- | --- |
| `x` | number | `0` |
| `y` | number | `0` |
| `z` | number | `0` |

### Color

`SceneColor`. Any of these forms is accepted.

| Form | Example | Notes |
| --- | --- | --- |
| Hex string | `"#FFCC88"` | `#RGB`, `#RGBA`, `#RRGGBB` or `#RRGGBBAA`. |
| Object | `{"r": 1.0, "g": 0.8, "b": 0.5}` | Channels from 0 to 1. `a` defaults to `1.0`. |
| Array | `[1.0, 0.8, 0.5]` | Three or four channels from 0 to 1. |

Colors are written back as an object; `a` is left out when it is `1.0`.

## Versioning

| Item | Value | Where |
| --- | --- | --- |
| Current schema version | `SCENE_SCHEMA_VERSION = 1` | `SceneDocument.kt` |
| Document newer than the engine | `SceneLoader.decode` throws `SceneSchemaVersionException`, carrying `documentVersion` | `SceneLoader.kt` |
| Document at or below the current version | Loads | `SceneLoader.kt` |

## JSON settings

`DefaultSceneJson`, used by `SceneLoader` unless you pass your own.

| Setting | Value | Effect |
| --- | --- | --- |
| `ignoreUnknownKeys` | `true` | Unknown keys are skipped, not rejected. |
| `encodeDefaults` | `true` | Saved documents write every field, defaults included. |
| `explicitNulls` | `false` | Absent optional fields are left out, not written as `null`. |
| Component discriminator | `component` | The key that names a component id. |

## Legacy component ids

`SceneLoader.decode` rewrites these older camelCase ids before decoding. Write the canonical id in
new documents.

| Legacy id | Canonical id |
| --- | --- |
| `meshRenderer` | `mesh_renderer` |
| `spinControl` | `spin_control` |
| `pbrMaterial` | `pbr_material` |
| `prefabLink` | `prefab_link` |

The rewrite matches `"component": "<id>"` and `"component":"<id>"`, with no other spacing.

Some fields also accept a second spelling:

| Component | Field | Also accepted |
| --- | --- | --- |
| `mesh_renderer` | `cullMode` | `cull_mode` |
| `prefab_link` | `prefabGuid` | `prefab_guid` |
| `prefab_link` | `isRoot` | `is_root` |

## Validation

`SceneValidator` runs before instantiation (`SceneLoader.instantiate`) and throws
`SceneValidationException` listing every issue.

| Rule | Message |
| --- | --- |
| Node names are unique across the document | `duplicate node name '<name>' already used at <path>` |
| At most one `camera` and one `terrain` per node | `node declares <n> <type> instances, expected at most 1` |
| Each component's own rules | See [Scene document components](scene-document-components.md) |
| Extension errors (with an extension registry) | The extension's message |

Issue paths use node names, or `#<index>` for an unnamed node, joined with `/`.

## See also

- [Scene document components](scene-document-components.md)
- [Component map](component-map.md)
- [Scene documents](../guides/scene-documents.md)
