/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.editor.core.asset

import com.awakekt.awake.editor.core.plugin.EditorPlugin
import com.awakekt.awake.editor.core.plugin.EditorProvider

/**
 * An [EditorPlugin] that contributes one or more [AssetConverter] implementations to Awake Studio.
 */
interface AssetConverterPlugin : EditorPlugin {
    val converters: List<AssetConverter>

    override fun createProviders(): List<EditorProvider> = emptyList()
}
