# Shader DSL

The Awake Shader DSL (ASL) lets applications describe procedural shader expressions in Kotlin and
emit WGSL text. The emitted source enters the normal shader validation and backend pipeline.

## Boundary

ASL is a source-generation layer. It does not own the render contract, GPU device, or backend
resource management. Applications can keep generated WGSL alongside their other shader assets and
select the appropriate backend representation at build time.

Use the [shader pipeline guide](../rendering/shaders.md) for shader assets and
[shader compilation](../rendering/shader-compiler.md) for runtime WGSL validation.
