# Awake Shader Document

Shaders as project data. A published project ships a shader document, a JSON file, and Core checks
it, compiles it to WGSL and draws it as a content feature, with no Kotlin and no change to Core or a
backend.

A document is a restricted subset of ASL (`:awake:asset:shader-dsl`). It is limited so that
untrusted input stays safe to run on other people's GPUs.

## What a document can say

- **A surface:** `background` (the whole screen, behind the scene), `overlay` (the whole screen,
  alpha-blended over it) or `plane` (a rectangle in the scene, placed by its entity, with its own
  vertex stage that can displace it).
- **Parameters** a scene sets by name, each with a type and a default, and **textures** it supplies
  by name.
- **Expressions** over scalars, vectors and conditions: arithmetic, comparisons, `and`/`or`/`not`,
  swizzles, vector construction, a fixed set of WGSL builtins, texture samples, and engine inputs
  (`uv`, `screenUv`, `worldPosition`, `normal`, `viewDirection`, `cameraPosition`, `sunDirection`,
  `time`, `deltaTime`, `resolution`).
- **Statements:** `let`, `var`, `set`, `if`, `for` with literal bounds, and `discard_if`.

It cannot say: functions, storage buffers, compute, raw WGSL, binding numbers, or a loop whose bound
is computed.

```json
{"formatVersion":1,"name":"Gradient sky","surface":"background",
 "parameters":[{"name":"top","type":"color","default":[0.1,0.3,0.8,1]},
               {"name":"bottom","type":"color","default":[0.9,0.5,0.3,1]}],
 "fragment":{"color":{"op":"call","fn":"mix","args":[
   {"op":"param","name":"bottom"},{"op":"param","name":"top"},
   {"op":"call","fn":"saturate","args":[{"op":"swizzle","value":{"op":"input","input":"viewDirection"},"components":"y"}]}]}}}
```

## Using one

```kotlin
val compiled = ShaderDocuments.compile(text)   // throws ShaderDocumentException with every issue
val inputs = compiled.newInputs()
compiled.packParameters(mapOf("top" to listOf(0f, 0.2f, 0.9f, 1f)), inputs)
host.attachContentFeature(compiled.contentFeature(inputs))
// each frame, from whatever drives the effect:
inputs.timeSeconds += delta
```

`awake:scene:shader` (#477) binds this into scenes: a `shader_effect` component, the system that
attaches and animates it, and the project loader.

## Why it is safe

- **Checked before anything compiles.** `ShaderDocuments.compile` rejects a document beyond
  `ShaderDocumentLimits`: size, JSON nesting (checked before parsing), expression depth, node count,
  weighted cost, loop iterations and nesting, weighted texture samples, parameters, textures and
  locals. Every issue names its path in the document and the parameter, texture or local involved.
- **Typed by WGSL's rules, not ASL's.** ASL checks shapes; the document checker also rejects what WGSL
  would, such as comparing vectors or taking `.x` of a scalar.
- **No constant left for the compiler to fail on.** WGSL evaluates an expression of constants when it
  creates the shader, and a NaN or an infinity there is an error. Every such expression is folded on
  the CPU first: one that is not finite is reported where it is, and the rest are emitted as literals.
- **No string from the file reaches the source.** Every name is replaced by a generated identifier,
  and every compound value gets its own `let`, so operator precedence never matters in the output.
- **Valid by construction, and tested that way.** WebGPU does not report a shader it rejects until the
  frame that draws it. `ShaderDocumentCompileTest` in `:awake:asset:shader-compiler` validates the
  WGSL of hand-written documents and 1,200 seeded random ones with naga, in both clip spaces.

The default limits are provisional: they bound the cost but have not been measured on a slow device.

## Dependencies

`awake:asset:shader-dsl`, `awake:asset:shaders`, `awake:engine:render:contract` and `passes`,
`awake:core:geometry` and `awake:core:math`, and kotlinx-serialization. No `awake:scene` module: this
is the capability, and `awake:scene:shader` is its scene wrapper.
