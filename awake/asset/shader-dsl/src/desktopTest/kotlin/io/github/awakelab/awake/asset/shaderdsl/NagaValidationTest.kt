/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.shaderdsl

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** Emitted WGSL through the real naga validator -- the same binary
 * `validateAwakeShaders` runs. Skips silently when naga is not on PATH (CI without the
 * toolchain); the golden-text tests still hold the emitter's exact output. */
class NagaValidationTest {

    @Test
    fun emittedShadersPassNagaValidation() {
        if (!nagaAvailable()) return
        listOf(TriangleShader, CheckerShader).forEach { definition ->
            val file = File.createTempFile("asl-${definition.name}", ".wgsl")
            try {
                file.writeText(definition.emitWgsl())
                val process = ProcessBuilder("naga", file.absolutePath)
                    .redirectErrorStream(true)
                    .start()
                val output = process.inputStream.bufferedReader().readText()
                assertEquals(0, process.waitFor(), "naga rejected '${definition.name}':\n$output")
            } finally {
                file.delete()
            }
        }
    }

    private fun nagaAvailable(): Boolean = runCatching {
        ProcessBuilder("naga", "--version").start().waitFor() == 0
    }.getOrDefault(false)
}
