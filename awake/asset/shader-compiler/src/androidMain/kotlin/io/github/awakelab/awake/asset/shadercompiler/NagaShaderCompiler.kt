/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.shadercompiler

/** Android JNI actual -- the per-ABI `libawake_naga.so` is built by `buildNagaAndroid`
 * (cargo-ndk) into the jniLibs tree, so `loadLibrary` resolves it from the APK. */
actual object NagaShaderCompiler : RuntimeShaderCompiler {
    init {
        System.loadLibrary("awake_naga")
    }

    actual override fun wgslToSpirv(wgsl: String): ByteArray = NagaJni.wgslToSpirv(wgsl)

    actual override fun validate(wgsl: String): String? = NagaJni.validate(wgsl)
}
