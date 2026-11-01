/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.ktx.loader

/**
 * This platform's [BasisTranscoder], or null where none ships: [LibKtxBasisTranscoder] on desktop and
 * [BasisWasmTranscoder.load] in a browser. Android and iOS have none, and load the ASTC or ETC2 file the
 * cook wrote for them instead. Throws when the platform's transcoder can't be loaded.
 */
expect suspend fun platformBasisTranscoder(): BasisTranscoder?
