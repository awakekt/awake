# `awake:core:schema`

Reads a `kotlinx.serialization` descriptor into a `PropertySchema`: for each property its name, kind
(float, int, boolean, text, enum, list, map, object, polymorphic), nullability, whether it is
required, its default, a numeric constraint, and hints for an editor. An inspector, a reference
page or a JSON Schema exporter reads that, instead of hard-coding a type's fields.

It knows nothing about scenes, ECS or editors, and depends on no `awake:scene` module. It works on
any `@Serializable` type.

## Annotating a type

Annotations are `@SerialInfo`, read through the descriptor, so they need no reflection and no code
generation. They go on the properties of a `@Serializable` class:

```kotlin
@Serializable
data class Emitter(
    @AssetReference("texture") val texture: String,
    @PropertyRange(min = 0.0, exclusiveMin = true) @PropertyUnit("s") val lifetime: Float = 1f,
    @PropertySlider(softMin = 0.0, softMax = 1.0, step = 0.05) val alpha: Float = 1f,
    @PropertyHidden val radians: Float = 0f,
)
```

| Annotation | Means | Enforced |
|---|---|---|
| `@PropertyRange` | The value must lie in a range; exclusive ends are supported | Yes: it is a constraint |
| `@PropertySlider` | A slider range for an editor | No: a hint |
| `@PropertyStep` | The increment an editor steps by | No: a hint |
| `@PropertyUnit` | A unit label | No: a hint |
| `@PropertyHint` | A one-sentence tooltip | No: a hint |
| `@PropertyHidden` | An editor must not show it | No: a hint |
| `@PropertyReadOnly` | An editor shows it but must not change it | No: a hint |
| `@AssetReference` | A text property is an asset path of a kind | No: a hint |

Annotate a `@PropertyRange` only where the type's own validation already enforces the limit, so the
annotation never makes a valid document invalid. A misapplied annotation (a range on text, a minimum
above the maximum, an `@AssetReference` on a number) is rejected when the schema is read, with a
message that names the property.

## Reading a schema

```kotlin
val schema = propertySchemaOf(Emitter.serializer(), json)
schema.children.map { it.name }          // declaration order
schema.children.single { it.name == "texture" }.required   // true: no default
```

`propertySchemaOf` also learns defaults. A descriptor says whether a property has a default but not
its value, so `deriveDefaults` decodes an empty object and encodes the result with every default
written. When the type has a required property, each required one is seeded with a neutral
placeholder first (zero, false, empty text, the first enum entry, an empty list or map); a required
property keeps no default, so a placeholder never shows. If the type rejects the placeholders too, no
default is known.

`SchemaOptions.semanticTypes` maps a type's serial name to a richer kind, such as
`PropertyKind.Vector3` or `PropertyKind.Color`. This module names no such type; the caller does.

Reading is deterministic: the same descriptor and options give an equal schema, with properties in
declaration order. `SchemaOptions.maxDepth` (12 by default) bounds nesting, which also stops a type
that contains itself.

## Limits

- Only the type's own `@Serializable` shape is read. A polymorphic property has no children.
- A required nested object has no default for its own properties.
- Custom serializers are read through their descriptor, so a type whose descriptor differs from its
  fields is described by the descriptor.
