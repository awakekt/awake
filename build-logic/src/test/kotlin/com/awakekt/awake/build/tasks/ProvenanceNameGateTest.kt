/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.tasks

import java.io.File
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A shader that carries a third-party name needs provenance that names it, or a pending review (#1791). */
class ProvenanceNameGateTest {
    private val root: File = createTempDirectory("provenance-names").toFile()
    private val shader = "awake/effects/src/commonMain/kotlin/Fxaa.kt"

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    private fun write(relative: String, text: String) = File(root, relative).apply { parentFile.mkdirs() }.writeText(text)

    private fun names(pending: String = "") = write(
        "docs/reference/third-party-names.json",
        """{"version": 1, "names": [{"name": "FXAA", "pattern": "FXAA"}], "pending": [$pending]}""",
    )

    private fun provenance(vararg entries: Pair<String, String>) = write(
        "docs/reference/source-provenance.json",
        """{"version": 1, "entries": [${entries.joinToString { (path, reason) -> """{"path": "$path", "reason": "$reason"}""" }}]}""",
    )

    private fun failures(): List<String> = ProvenanceNameGate.failures(Path.of(root.path))

    @Test
    fun aShaderCarryingANameWithNoProvenanceFails() {
        names()
        provenance()
        write(shader, "// FXAA edge filter\nfun build() = shader(\"fxaa\") { }")

        assertEquals(1, failures().size)
        assertTrue(failures().single().startsWith(shader), failures().single())
    }

    @Test
    fun anEntryForThePathPassesOnlyWhenItsReasonNamesTheAlgorithm() {
        names()
        write(shader, "// FXAA edge filter\nfun build() = shader(\"fxaa\") { }")

        provenance(shader to "a tone curve")
        assertEquals(1, failures().size, "an entry about something else in the same file")

        provenance(shader to "FXAA-style edge anti-aliasing, no recorded source")
        assertEquals(emptyList(), failures())
    }

    @Test
    fun aPendingReviewHoldsItUntilTheNameIsGone() {
        names("""{"path": "$shader", "names": ["FXAA"], "tracker": "#1791"}""")
        provenance()
        write(shader, "// FXAA edge filter\nfun build() = shader(\"fxaa\") { }")
        assertEquals(emptyList(), failures())

        write(shader, "fun build() = shader(\"edge\") { }")
        assertEquals(1, failures().size, "a pending line for a file that no longer carries the name")
    }

    @Test
    fun onlyMainShaderSourcesAreChecked() {
        names()
        provenance()
        write("awake/effects/src/commonMain/kotlin/Settings.kt", "enum class Mode { Off, FXAA }")
        write("awake/effects/src/commonTest/kotlin/FxaaTest.kt", "// FXAA\nfun build() = shader(\"fxaa\") { }")
        write("awake/effects/build/generated/Fxaa.kt", "// FXAA\nfun build() = shader(\"fxaa\") { }")

        assertEquals(emptyList(), failures())
    }

    @Test
    fun thisRepositoryPasses() {
        val repository = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "docs/reference/third-party-names.json").isFile }
        assertEquals(emptyList(), ProvenanceNameGate.failures(repository.toPath()))
    }
}
