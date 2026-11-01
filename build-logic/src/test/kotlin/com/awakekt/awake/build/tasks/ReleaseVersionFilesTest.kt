/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.tasks

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ReleaseVersionFilesTest {
    private val dir: File = createTempDirectory("release-version").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun aVersionPropertyIsReadAndWrittenInPlace() {
        val properties = "org.gradle.jvmargs=-Xmx2g\ngame.version = 0.3.1 \nother=1\n"

        assertEquals("0.3.1", readVersionProperty(properties, "game.version"))
        assertNull(readVersionProperty(properties, "game.versionCode"))
        assertEquals(
            "org.gradle.jvmargs=-Xmx2g\ngame.version = 0.4.0 \nother=1\n",
            writeVersionProperty(properties, "game.version", "0.4.0"),
        )
        assertFailsWith<IllegalArgumentException> { writeVersionProperty(properties, "missing", "0.4.0") }
    }

    /** A cut from the property counts on exactly as it would from a tag of the same version. */
    @Test
    fun aCutCountsOnFromTheVersionProperty() {
        val current = readVersionProperty("game.version=0.3.1\n", "game.version")

        assertEquals("0.4.0", nextReleaseVersion("stable", bump = "minor") { "v$current" })
        assertEquals("0.3.2-rc.1", nextReleaseVersion("rc", bump = "patch") { "v$current" })
    }

    @Test
    fun theFirstJsonVersionMemberIsRewritten() {
        val json = "{\n  \"name\": \"Harbor Town\",\n  \"version\" : \"0.3.1\",\n" +
            "  \"engine\": { \"version\": \"9\" }\n}\n"

        assertEquals(json.replace("\"0.3.1\"", "\"0.4.0\""), writeVersionField(json, "0.4.0", "awake.project.json"))
    }

    @Test
    fun aVersionLineIsRewrittenQuotedOrNot() {
        assertEquals(
            "[app]\nversion = \"0.4.0\"\n",
            writeVersionField("[app]\nversion = \"0.3.1\"\n", "0.4.0", "a.toml"),
        )
        assertEquals("version=0.4.0\n", writeVersionField("version=0.3.1\n", "0.4.0", "a.properties"))
    }

    @Test
    fun listedFilesAreRewrittenAndAMissingOneOrFieldFailsTheCut() {
        File(dir, "awake.project.json").writeText("{ \"version\": \"0.3.1\" }")
        File(dir, "notes.txt").writeText("no version here")

        val written = writeVersionFiles(dir, " awake.project.json, ", "0.4.0")

        assertEquals(listOf("awake.project.json"), written.map(File::getName))
        assertEquals("{ \"version\": \"0.4.0\" }", File(dir, "awake.project.json").readText())
        assertContains(
            assertFailsWith<IllegalArgumentException> { writeVersionFiles(dir, "missing.json", "0.4.0") }
                .message.orEmpty(),
            "does not exist",
        )
        assertContains(
            assertFailsWith<IllegalArgumentException> { writeVersionFiles(dir, "notes.txt", "0.4.0") }
                .message.orEmpty(),
            "no \"version\" field",
        )
        assertEquals(emptyList(), writeVersionFiles(dir, "", "0.4.0"))
    }
}
