/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai

import kotlinx.serialization.json.Json

/** The JSON node states and parameters are read and written with: strict, so a typo is refused. */
internal object AiJson {
    val data: Json = Json { encodeDefaults = true }
}
