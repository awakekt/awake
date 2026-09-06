/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.document

import java.io.File

actual fun writeSceneDocument(fileName: String, json: String): String {
    val outDir = File(System.getProperty("user.home"), "Awake Studio").apply { mkdirs() }
    val file = File(outDir, fileName)
    file.writeText(json)
    return file.absolutePath
}
