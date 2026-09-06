/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.builder.model

import kotlinx.serialization.Serializable

/**
 * Visual layout and style properties for a [UiNode].
 */
@Serializable
data class UiStyleSpec(
    val width: String = "auto",
    val height: String = "auto",
    val padding: String = "8.dp",
    val gap: String = "8.dp",
    val arrangement: String = "Start",
    val alignment: String = "CenterVertically",
    val backgroundColorToken: String? = null,
)
