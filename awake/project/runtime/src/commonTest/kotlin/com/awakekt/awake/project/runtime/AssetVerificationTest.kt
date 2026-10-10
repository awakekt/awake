/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.core.io.Sha256
import com.awakekt.awake.project.AssetMismatch
import com.awakekt.awake.project.AssetMismatchException
import com.awakekt.awake.project.AwakeAssetLockEntry
import com.awakekt.awake.project.AwakeAssetsLock
import com.awakekt.awake.project.AwakeProjectValidator
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** A project loads through its own asset lock, each pinned file checked as it's read. */
class AssetVerificationTest {
    private val mismatches = mutableListOf<AssetMismatch>()

    @Test
    fun aProjectThatMatchesItsLockLoadsWithNoMismatch() = runTest {
        val project = loadProject(files(SCENE).verifiedByAssetsLock { mismatches += it })

        assertEquals("harbor", project.scene.name)
        assertTrue(mismatches.isEmpty(), "got $mismatches")
        project.close()
    }

    @Test
    fun anEditedSceneIsReportedAndStillLoadsByDefault() = runTest {
        val project = loadProject(files(EDITED_SCENE).verifiedByAssetsLock { mismatches += it })

        assertEquals("harbour", project.scene.name, "the edited scene loads")
        assertEquals(listOf(ENTRY_SCENE), mismatches.map(AssetMismatch::path))
        project.close()
    }

    @Test
    fun refusingMismatchesStopsAnEditedSceneLoading() = runTest {
        val files = files(EDITED_SCENE).verifiedByAssetsLock(refuseMismatches = true) { mismatches += it }

        val error = assertFailsWith<IllegalArgumentException> { loadProject(files) }

        val refusal = assertIs<AssetMismatchException>(error.cause, "the load fails because the scene was refused: $error")
        assertEquals(ENTRY_SCENE, refusal.mismatch.path)
    }

    @Test
    fun aProjectWithNoLockLoadsUnchecked() = runTest {
        val files = files(SCENE, lock = null)

        assertSame(files, files.verifiedByAssetsLock(), "there is nothing to check against")
    }

    @Test
    fun aLockThatIsNotALockIsAnError() = runTest {
        val error = assertFailsWith<IllegalStateException> { files(SCENE, lock = "[1, 2]").verifiedByAssetsLock() }

        assertTrue(error.message.orEmpty().contains(PROJECT_ASSETS_LOCK), "names the lock: ${error.message}")
    }

    /** A project whose [lock] pins its manifest and the original [SCENE], with [scene] as its entry scene. */
    private fun files(scene: String, lock: String? = LOCK): AssetSource {
        val sources = mapOf(PROJECT_MANIFEST to MANIFEST, ENTRY_SCENE to scene) + listOfNotNull(lock?.let { PROJECT_ASSETS_LOCK to it })
        return AssetSource { path -> runCatching { sources.getValue(path.value).encodeToByteArray() } }
    }

    private companion object {
        const val ENTRY_SCENE = "scenes/main.scene.json"
        const val MANIFEST = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"$ENTRY_SCENE"}"""
        const val SCENE = """{ "version": 1, "name": "harbor", "nodes": [ { "name": "Crate" } ] }"""
        const val EDITED_SCENE = """{ "version": 1, "name": "harbour", "nodes": [ { "name": "Crate" } ] }"""
        val LOCK = AwakeProjectValidator.encodeAssetsLock(
            AwakeAssetsLock(assets = listOf(PROJECT_MANIFEST to MANIFEST, ENTRY_SCENE to SCENE).associate { (path, text) -> path to pin(text) }),
        )

        fun pin(text: String) = text.encodeToByteArray().let { AwakeAssetLockEntry(Sha256.digestHex(it), it.size.toLong()) }
    }
}
