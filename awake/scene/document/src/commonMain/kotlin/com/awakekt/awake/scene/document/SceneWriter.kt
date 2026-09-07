/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.document

/** Platform-specific writer saving serialized scene JSON [json] to disk at [fileName]. */
expect fun writeSceneDocument(fileName: String, json: String): String
