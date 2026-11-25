/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.diagnostics

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileCrashStorageTest {
    @Test
    fun readWriteOverwriteAndRemoveRoundTripUtf8() {
        val root = Files.createTempDirectory("awake-crash-storage-test")
        try {
            val nested = root.resolve("nested/storage")
            val storage = FileCrashStorage(nested)

            // Reading a missing key returns null without creating directories.
            assertNull(storage.read("awake.crash.session"))
            assertFalse(Files.exists(nested))

            val payload = "{\"town\":\"Harbor Town \uD83C\uDF0A\",\"lines\":\"a\\nb\",\"jp\":\"\u6E2F\"}"
            storage.write("awake.crash.session", payload)
            assertTrue(Files.isDirectory(nested))
            assertEquals(payload, storage.read("awake.crash.session"))

            // Overwriting replaces content cleanly with no stray temp files left behind.
            storage.write("awake.crash.session", "updated-payload")
            assertEquals("updated-payload", storage.read("awake.crash.session"))
            val files = Files.list(nested).use { stream -> stream.toList() }
            assertEquals(1, files.size)
            assertFalse(files.first().fileName.toString().endsWith(".tmp"))

            // File constructor and Path constructor view the exact same persisted entry.
            val viaFile = FileCrashStorage(nested.toFile())
            assertEquals("updated-payload", viaFile.read("awake.crash.session"))

            // Remove deletes the file; removing again is a no-op.
            storage.remove("awake.crash.session")
            assertNull(storage.read("awake.crash.session"))
            storage.remove("awake.crash.session")
        } finally {
            deleteRecursively(root)
        }
    }

    @Test
    fun keysAreSanitizedAndCollisionFreeAcrossPathTraversalCaseAndReservedNames() {
        val root = Files.createTempDirectory("awake-crash-storage-keys")
        try {
            val storage = FileCrashStorage(root)
            val keys = listOf(
                "../escape",
                "../../etc/passwd",
                "a/b",
                "a\\b",
                "a:b",
                "CaseKey",
                "casekey",
                "CON",
                "NUL",
                "AUX",
                "",
                "x".repeat(400),
            )

            keys.forEachIndexed { index, key ->
                val resolved = storage.fileForKey(key)
                assertEquals(root.toAbsolutePath().normalize(), resolved.parent)
                assertTrue(resolved.fileName.toString().length < 200)
                storage.write(key, "value-$index")
            }

            // Every key reads back its own independent value.
            keys.forEachIndexed { index, key ->
                assertEquals("value-$index", storage.read(key), "Mismatch for key '$key'")
            }

            assertNotEquals(storage.fileForKey("CaseKey"), storage.fileForKey("casekey"))
            assertNotEquals(storage.fileForKey("a/b"), storage.fileForKey("a\\b"))
        } finally {
            deleteRecursively(root)
        }
    }

    @Test
    fun crashRecorderPersistsAndRecoversPendingSessionAcrossInstances() {
        val root = Files.createTempDirectory("awake-crash-recorder-file")
        try {
            var clock = 1_000L
            val meta = CrashSessionMeta(build = "1.0.0", commit = "abc123", platform = "desktop")

            val firstRun = CrashRecorder(
                storage = FileCrashStorage(root),
                now = { clock },
                newId = { "session-001" },
            )
            assertNull(firstRun.begin(meta))
            clock = 2_000L
            firstRun.record(CrashBreadcrumb(at = clock, scene = "Harbor Town", fps = 60))
            clock = 3_000L
            firstRun.error("renderer", "device lost")
            // Simulate abrupt termination: do NOT call firstRun.end().

            val secondRun = CrashRecorder(
                storage = FileCrashStorage(root),
                now = { clock },
                newId = { "session-002" },
            )
            clock = 4_000L
            val crashed = secondRun.begin(meta)
            assertNotNull(crashed)
            assertEquals("session-001", crashed.id)
            assertEquals(1, crashed.crumbs.size)
            assertEquals("Harbor Town", crashed.crumbs.first().scene)
            assertEquals(1, crashed.errors.size)
            assertNotNull(secondRun.pending())

            secondRun.markSent()
            assertNull(secondRun.pending())
            secondRun.end()
        } finally {
            deleteRecursively(root)
        }
    }

    private fun deleteRecursively(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }
}
