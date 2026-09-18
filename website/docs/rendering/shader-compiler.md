# Shader compiler

Awake can validate WGSL and, on native targets, translate WGSL to SPIR-V through its Naga bridge.
Browser applications consume WGSL directly through WebGPU.

```kotlin
val spirv: ByteArray = NagaShaderCompiler.wgslToSpirv(wgslText)
val error: String? = NagaShaderCompiler.validate(wgslText)
```

Build-time, committed shader assets remain the simplest deployment path. Runtime compilation is
useful for tools, previews, and applications that generate shader source dynamically.
