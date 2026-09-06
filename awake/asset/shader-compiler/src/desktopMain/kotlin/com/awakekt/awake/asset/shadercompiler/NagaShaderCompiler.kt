/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shadercompiler

/** Desktop JNI actual. The dylib/so is built by `buildNagaDesktop` (cargo) into
 * `rust-native/target/release` and packaged into `/natives/<platform>/` in the desktop jar.
 * Loaded via explicit `awake.naga.library`, `java.library.path`, or automated classpath
 * extraction by [NagaNativeLoader]. */
actual object NagaShaderCompiler : RuntimeShaderCompiler {
    init {
        NagaNativeLoader.load()
    }

    actual override fun wgslToSpirv(wgsl: String): ByteArray = NagaJni.wgslToSpirv(wgsl)

    actual override fun validate(wgsl: String): String? = NagaJni.validate(wgsl)
}
