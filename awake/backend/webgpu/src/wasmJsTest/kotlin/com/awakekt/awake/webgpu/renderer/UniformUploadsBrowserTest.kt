/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.webgpu.device.createCanvasGpuContext
import kotlinx.coroutines.test.runTest
import web.dom.document
import web.html.HTMLCanvasElement
import kotlin.test.Test

/** The staged uniform path in a browser, where it writes from wasm linear memory; see [assertStagedUniformsMatchDirectWrites]. */
class UniformUploadsBrowserTest {
    @Test
    fun stagedBlocksWriteTheBytesDirectWritesDid() = runTest {
        val context = createCanvasGpuContext(document.createElement("canvas") as HTMLCanvasElement, 1, 1)
        try {
            context.device.assertStagedUniformsMatchDirectWrites()
        } finally {
            context.close()
        }
    }
}
