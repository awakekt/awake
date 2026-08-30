# Awake Shader Compiler

Runtime WGSL→SPIR-V for [Awake](../../../README.md): naga — the same translation library the
build pipeline's `naga-cli` wraps — compiled as a native library and bridged into KMP. The
on-device tier of the [ASL plan](../../../docs/tasks/2026-08-23-asl-procedural-shader-plan.md);
build-time committed `.spv` stays the default for everything shipped.

```kotlin
val spirv: ByteArray = NagaShaderCompiler.wgslToSpirv(wgslText)   // all entry points
val error: String? = NagaShaderCompiler.validate(wgslText)        // null when clean
```

| Target | Binding | Native build task |
|---|---|---|
| desktop JVM | JNI (Rust `jni` crate, no C glue) | `buildNagaDesktop` |
| Android | same JNI symbols, per-ABI `.so` | `buildNagaAndroid` (cargo-ndk) |
| iOS | cinterop over `include/awake_naga.h` | `buildNagaIosArm64` / `...SimulatorArm64` |
| wasmJs | throws — browsers take WGSL directly | — |

The shim is `rust-native/` (~150 lines over `naga`). Native builds are manual/on-demand, same
policy as jolt's `buildJoltC*`; a Rust toolchain (`rustup`, plus `cargo-ndk` and the
iOS/Android targets) is required to build them and is NOT part of the default `check`.

**Version pinning:** `rust-native/Cargo.toml` pins the `naga` crate; keep it in step with the
`naga-cli` the shader-pipeline convention shells out to, or runtime compilation accepts/emits
differently than the committed-`.spv` path.

Verification honesty: desktop runs real JNI round-trip tests; Android is compile+`.so`-build
verified (no instrumented run yet); iOS is cinterop-compile and framework-link verified (no
device run).
