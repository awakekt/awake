/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.io.Sha256
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.project.IndexedAssetSource
import com.awakekt.awake.project.LayeredAssetSource
import com.awakekt.awake.project.ProjectIndex
import com.awakekt.awake.project.ProjectIndexEntry
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** A project whose entry scene is gated starts on a placeholder and plays its entry scene once the gate opens. */
class EntryGateTest {
    private val publicFiles = mapOf(MANIFEST_PATH to MANIFEST, TITLE to scene("title"))
    private val protectedFiles = mapOf(HARBOR to scene("harbor"))

    @Test
    fun aGatedEntrySceneStartsOnThePlaceholderAndPlaysOnceTheGateOpens() = runTest {
        val files = LayeredAssetSource(indexed(publicFiles))
        val project = loadProject(files, EntryGate(TITLE))
        assertEquals("title", project.scene.name)
        assertEquals(HARBOR, project.pendingEntryScene)

        project.sceneLoads = Dispatchers.Unconfined
        val app = app { scene("play") { runProject(project) } }
        app.ready(TestRenderer())
        val status: SceneSwitchStatus = app.requireService()
        val frames = { count: Int -> repeat(count) { app.update(DELTA, WIDTH, HEIGHT) } }
        frames(FRAMES)

        assertEquals(SceneSwitchState.WaitingForContent(HARBOR), status.state.value)
        assertEquals("title", project.scene.name, "the placeholder plays while the entry scene waits")
        assertNull(project.pendingEntryScene, "the runner has asked for it")

        files.attachProtected(indexed(protectedFiles))
        frames(FRAMES)

        assertEquals(SceneSwitchState.Ready(HARBOR), status.state.value)
        assertEquals("harbor", project.scene.name)
        project.close()
    }

    @Test
    fun withNoPlaceholderAnEmptySceneWaits() = runTest {
        val files = LayeredAssetSource(indexed(publicFiles))
        val project = loadProject(files, EntryGate(), componentRegistry = SceneComponentRegistry.scoped(), hasRenderer = false)

        assertEquals(EntryGate.EMPTY_PLACEHOLDER.removeSuffix(".scene.json"), project.scene.name)
        assertEquals(emptyList(), project.scene.nodes)
        assertEquals(HARBOR, project.pendingEntryScene)

        files.attachProtected(indexed(protectedFiles))
        project.switchTo(checkNotNull(project.pendingEntryScene))
        assertEquals("harbor", project.scene.name, "a host without runProject switches to it itself")
        project.close()
    }

    @Test
    fun anEntrySceneThatIsNotGatedLoadsAsUsual() = runTest {
        val files = LayeredAssetSource(indexed(publicFiles))
        files.attachProtected(indexed(protectedFiles))

        val project = loadProject(files, EntryGate(TITLE), componentRegistry = SceneComponentRegistry.scoped(), hasRenderer = false)

        assertEquals("harbor", project.scene.name)
        assertNull(project.pendingEntryScene)
        project.close()
    }

    @Test
    fun aPlaceholderPathMustStayInTheProject() {
        assertFailsWith<IllegalArgumentException> { EntryGate("../outside.scene.json") }
    }

    private fun indexed(files: Map<String, String>): IndexedAssetSource {
        val entries = files.map { (path, text) ->
            val bytes = text.encodeToByteArray()
            ProjectIndexEntry(path = path, sizeBytes = bytes.size.toLong(), sha256 = Sha256.digestHex(bytes), url = path)
        }
        return IndexedAssetSource(ProjectIndex(files = entries), { url -> files.getValue(url).encodeToByteArray() })
    }

    private class TestRenderer :
        NoopRenderer(),
        GpuDrawPreparationSource {
        override val gpuDrawPreparer = GpuDrawPreparer { _, _, _ -> null }
    }

    private companion object {
        const val DELTA = 1f / 60f
        const val WIDTH = 320f
        const val HEIGHT = 240f
        const val FRAMES = 3
        const val MANIFEST_PATH = "awake.project.json"
        const val TITLE = "scenes/title.scene.json"
        const val HARBOR = "scenes/harbor.scene.json"
        const val MANIFEST = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"$HARBOR"}"""

        fun scene(name: String) =
            """{ "version": 1, "name": "$name", "nodes": [ { "name": "Camera", "components": [ { "component": "camera" } ] } ] }"""
    }
}
