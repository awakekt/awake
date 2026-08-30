/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.shadercompiler

/** JNI surface of the Rust `awake_naga` cdylib -- symbol names are load-bearing, matched by
 * the `#[no_mangle]` exports in `rust-native/src/lib.rs`. */
internal object NagaJni {
    external fun wgslToSpirv(source: String): ByteArray

    external fun validate(source: String): String?
}
