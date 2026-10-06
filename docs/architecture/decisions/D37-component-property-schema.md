# D37: component property schema

Status: proposed (2026-10-06)

## Decision

Describe every registered scene component once, in Core, with its `SerialDescriptor` plus a few
`@SerialInfo` annotations, and let editors, the reference docs and tools read that description
instead of hard-coding fields. Four questions in awakekt/awake#442 had to be answered first.

| Question | Answer |
|---|---|
| **Edit path** | The schema describes the **document** component (`SceneComponent`). A generated inspector edits by export, patch, attach (`SceneComponentBinding.export`, a JSON round trip, `attachTyped`) **only for a component whose live form is a faithful copy of its document**. A component whose live form is derived from the document by a system keeps its hand-written inspector in the first release. A binding hook for derived components comes later. |
| **Constraints and hints** | Two annotations. `@PropertyRange(min, max)` is a **constraint**: a generic validator in the schema module enforces it, and the JSON Schema exports it as `minimum` and `maximum`. `@PropertySlider(softMin, softMax, step)` is a **hint** and is never enforced. Annotate only a range that the component's `validate()` already enforces, so adding an annotation never makes a valid scene invalid. Relational rules stay in `validate()`. |
| **Module** | `awake:core:schema`: the annotations, `PropertySchema`, the descriptor walker, the defaults derivation and the JSON Schema exporter. It depends on `kotlinx-serialization-json` and on no `awake:scene` and no `awake:ecs` module (the capability rule in `awake/scene/README.md`; `core:io` already depends on the same library). `awake:scene:document` supplies the catalog of registered components. `:awake:editor:contract` maps a schema onto `InspectorFieldScope` and depends on the schema module, not the reverse. |
| **Plugin and `custom` components** | A plugin registers a typed `@Serializable` `SceneComponent` with `SceneSerializers.register`, and its descriptor is in the catalog with no further work: that is how all 29 components are found today. `custom` stays opaque (its data is kept, it has no schema). A per-type schema for `custom` is left out until a plugin needs it. |

## Why: what the probes showed

Throwaway probes ran against the real bindings on 2026-10-06; none is committed.

**The edit path is not universal.**

- `spin_control`: export, patch `speed`, attach is lossless. The live `SpinControl` is replaced by a new instance and keeps its runtime `radians` (1.234 in the probe), because export reads it from the live component.
- `particle_emitter`: the same steps change the document (`spawnRate` 50) and leave the running emitter untouched (`spawnRate` still 10, same instance, 10 live particles). `ParticleContentSystem` builds the live `ParticleEmitter` only when the entity has none. Removing it to force a rebuild gives the new rate and **zero live particles**: the effect restarts.

So a generic write path works for a component like `spin_control` and silently does nothing, or resets state, for one like `particle_emitter`. Only two of the 29 were probed. The binding names `BlueprintComponent`, `TerrainComponent`, `PhysicsBody` and `MeshRenderer` (whose assets resolve after attach) suggest more derived components, **unchecked**. Classifying all 29 is the first task of awakekt/awake#448.

**The JSON round trip is exact and cheap enough to drag with.** Unchanged, top-level patch, clearing a nullable field and patching a nested field all reproduce the expected value exactly, including the custom-serialized `SceneColor` (`{"r","g","b","a"}`). One tick costs about 15 microseconds and 13.6 KB of allocation for a 30-field component: under 1 ms of CPU and under 1 MB per second at 60 ticks. Undo is cheap, because the document component is an immutable data class and a snapshot is a reference. Coalescing a drag into one undo step is the host's rule already (`InspectorFieldScope.scalar`).

**Most rules are not single-field ranges.** The validators hold 77 messages of the single-line form. By wording, about half are single-field numeric limits ("must not be negative", "must be > 0", "must be between 0 and 180"). The rest are relational and cannot be a per-field annotation: `camera_rig.distance` between `minDistance` and `maxDistance`, `locomotion_animation` needs `0 <= walkAbove <= runAbove`, keyframes in time order within the duration, a duplicate node name, a mesh-shaped `physics_body` that cannot be a sensor. (Keyword counts, and the categories overlap.)

**Defaults can come from decoding.** 22 of the 29 components decode from `{"component":"<id>"}` alone, so their default document exists. The other seven need a value: `blueprint` (graph), `keyframe_animation` (duration), `mesh_renderer` (mesh, material), `particle_emitter` (texture), `prefab_link` (path), `terrain` (width, depth, samples) and `custom`. Almost all are asset references or sizes, so "Add Component" has to ask for them.

**Scalars cover most of it.** The 29 components have 190 top-level fields: 95 float, 16 boolean, 23 string (the asset references among them), 13 int, 10 enum, 9 colour and 7 `SceneVec3`. That is 173, or 91%. The other 17 are lists (8), maps (2), polymorphic (2) and nested objects (5). A first release that handles scalars, colour, vector and nested objects covers nearly everything, and falls back to a hand-written inspector for the rest.

**Two traps for the implementation.**

- The `component` discriminator comes from `@JsonClassDiscriminator` on the interface and is honoured only when a component is decoded through a property that carries it, such as `SceneNode.components`. A hand-built `PolymorphicSerializer(SceneComponent::class)` fails every component with "Class discriminator was missing". The catalog must not offer a decode-one-component helper built that way.
- `SceneSerializers` is a global mutable registry. The catalog reflects what is registered when it is asked, so a tool must ask after plugins are installed.

## Left out until needed

| Left out | Add it when |
|---|---|
| Edits to derived components through the schema (`particle_emitter` first) | A binding hook such as `applyEdit(world, entity, edited)` exists, defaulting to `attachTyped`, with derived bindings patching live state |
| A schema for `custom` components | A plugin needs one |
| Lists, maps and polymorphic fields in a generated inspector | A component needs them (12 of 190 fields today) |
| Cross-field constraints as annotations | A relational rule appears often enough to name |
| A generic validator that adds restrictions `validate()` does not enforce | A separate, visible change with its own changelog line |

## What this changes in the work

- awakekt/awake#443: build `PropertySchema` from the descriptor; recognise `SceneVec3` and `SceneColor` by serial name; derive defaults by decoding; flag the seven required-value components `required`.
- awakekt/awake#444: the catalog, with the discriminator and registry notes above.
- awakekt/awake#446: annotate only enforced ranges; mark runtime state such as `SceneSpinControl.radians` hidden; test that each `@PropertyRange` rejects an out-of-range value.
- awakekt/awake#448: classify the 29 as faithful or derived; the first generated inspector covers the faithful ones.
- awakekt/awake#441: the "edit path proven" readiness item stays open until derived components have their hook.
