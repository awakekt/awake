# Shader compilation

<p class="awake-lede">Check WGSL for errors and compile it to SPIR-V while the app runs, with the naga compiler built into AwakeKt Engine.</p>

<div class="awake-badges" markdown>
<span class="awake-badge">module: <code>awake:asset:shader-compiler</code></span>
<span class="awake-badge awake-badge--ok">Vulkan</span>
<span class="awake-badge">Desktop · Android · iOS</span>
</div>

The Vulkan backend already compiles every shader in the render plan this way, so most apps never call
the compiler. Use it directly for shaders made at run time: an editor, a live preview, or WGSL your
app generates. Shader compilation is Kotlin only.

## Validate and compile

`NagaShaderCompiler.validate` returns naga's diagnostic, or `null` when the WGSL is valid.
`wgslToSpirv` returns a SPIR-V module holding every entry point in the source:

```kotlin title="Kotlin"
--8<-- "awake/asset/shader-compiler/src/desktopTest/kotlin/com/awakekt/awake/asset/shadercompiler/ShaderCompilationDocsSampleTest.kt:compile"
```

Here `wgsl` is the output of an ASL shader's `emitWgsl()`; any WGSL string works.

## Handle a bad shader

`wgslToSpirv` throws `NagaException` when the source does not parse or validate. Its message is
naga's diagnostic, pointing at the WGSL line:

```kotlin title="Kotlin"
--8<-- "awake/asset/shader-compiler/src/desktopTest/kotlin/com/awakekt/awake/asset/shadercompiler/ShaderCompilationDocsSampleTest.kt:error"
```

## Platforms

| Platform | How it runs |
| --- | --- |
| Desktop (JVM) | JNI into the native `awake_naga` library. |
| Android | JNI into `libawake_naga.so`, loaded with `System.loadLibrary`. |
| iOS | Kotlin/Native interop with the static library. |
| Web (wasmJs) | Not supported: both functions throw `UnsupportedOperationException`. WebGPU takes WGSL directly, so there is nothing to compile. |

`NagaShaderCompiler` implements the `RuntimeShaderCompiler` interface (`wgslToSpirv`, `validate`),
so code that compiles shaders can take the interface and be tested with a fake.

## How it works

On desktop, the first call loads the native library, trying in order:

1. the file named by the JVM system property `awake.naga.library`;
2. `awake_naga` on `java.library.path`;
3. the copy bundled in the jar under `natives/<os>-<arch>/`, extracted to
   `~/.awake/natives/<platform>/<hash>/` (or the temp directory when there is no home directory);
4. `~/.awake/natives/<platform>/`.

If none is found, it fails with "Native Naga library 'awake_naga' not found", naming the platform.

!!! tip "Building from source"
    In a checkout of the engine, `./gradlew :awake:asset:shader-compiler:buildNagaDesktop` builds the
    desktop library with Cargo. The module's own desktop tests build it first and point
    `awake.naga.library` at it.

!!! warning "Validate before you compile a user's shader"
    A shader typed by a user, or generated, can be invalid. Call `validate` and show its message, or
    catch `NagaException`, rather than letting the exception reach the frame loop.

## See also

- [Render plans and shaders](shaders.md) for shader sets and the shader DSL.
- [Vulkan backend](vulkan.md) for how the backend compiles a plan's shaders.
