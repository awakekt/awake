/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.shadercompiler

/** A WGSL source failed to parse or validate at runtime. [message] is naga's own rendered
 * diagnostic, pointing at the WGSL line. */
class NagaException(message: String) : RuntimeException(message)

/**
 * Runtime WGSL compilation -- the on-device tier of the ASL plan, backed by the same naga
 * library the build pipeline's `naga-cli` wraps (versions pinned together; see
 * `rust-native/Cargo.toml`). The build-time committed-`.spv` path stays the default for
 * everything shipped; this exists for runtime-generated shaders on Vulkan targets, where the
 * driver cannot take WGSL text the way WebGPU's `createShaderModule` can.
 */
interface RuntimeShaderCompiler {
    /** Compiles [wgsl] to a SPIR-V module (little-endian words, all entry points -- the same
     * multi-entry-point shape `VkShaderModule` accepts). Throws [NagaException] with naga's
     * diagnostic on invalid source. */
    fun wgslToSpirv(wgsl: String): ByteArray

    /** naga's diagnostic for [wgsl], or null when it parses and validates clean. */
    fun validate(wgsl: String): String?
}

/** The platform's naga binding: JNI over the Rust cdylib on desktop/Android, cinterop over
 * the static library on iOS. wasmJs throws -- a browser's WebGPU takes WGSL directly, so a
 * SPIR-V compiler has nothing to do there. */
expect object NagaShaderCompiler : RuntimeShaderCompiler {
    /** Compiles [wgsl] to a SPIR-V module. */
    override fun wgslToSpirv(wgsl: String): ByteArray
    /** Validates [wgsl] and returns a diagnostic or null. */
    override fun validate(wgsl: String): String?
}
