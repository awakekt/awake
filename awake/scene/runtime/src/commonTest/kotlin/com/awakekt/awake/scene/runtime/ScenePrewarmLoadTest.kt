/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.mesh.Mesh
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** A mesh its resolver loads first ([SceneAssetResolver.loadMesh]) is built once the load finishes. */
class ScenePrewarmLoadTest : PrewarmFixture(listOf(LoadingModels())) {
    private val models = extraResolvers.single() as LoadingModels

    private val ship = SceneAssetReferences(renderables = setOf(SceneRenderableKey(MODEL, "lit")))

    @Test
    fun aMeshIsBuiltOnceItsResolverHasLoadedIt() {
        val prewarm = runtime.prewarm(ship)
        library.runPrewarms()
        val load = models.loads.single()
        assertSame(runtime.session.coroutineScope, load.scope, "it loads in the session's scope")
        assertFalse(prewarm.isReady)
        assertEquals(0, library.meshHolderCount(MODEL))

        load.done = true
        models.loaded = true
        library.runPrewarms()

        assertTrue(prewarm.isReady)
        assertEquals(1, library.meshHolderCount(MODEL), "the prewarm holds what it built")
        assertTrue(load.closed, "and stops waiting for a load it is done with")
        prewarm.close()
    }

    @Test
    fun aMeshWhoseLoadFailsIsNamedInFailed() {
        val prewarm = runtime.prewarm(ship)
        library.runPrewarms()

        models.loads.single().apply {
            failure = IllegalStateException("no such file")
            done = true
        }
        library.runPrewarms()

        assertTrue(prewarm.isReady)
        assertEquals(setOf("$MODEL + lit"), prewarm.failed.keys)
        assertEquals(0, library.meshHolderCount(MODEL))
    }

    @Test
    fun closingAPrewarmStopsWaitingForItsLoads() {
        val prewarm = runtime.prewarm(ship)
        library.runPrewarms()

        prewarm.close()

        assertTrue(models.loads.single().closed)
    }

    @Test
    fun tearingTheSceneDownStopsWaitingForItsLoads() = runTest {
        val prewarm = runtime.prewarm(ship)
        library.runPrewarms()

        library.dispose()

        assertTrue(models.loads.single().closed)
        assertFailsWith<CancellationException> { prewarm.await() }
    }

    /** Resolves [MODEL] once a test says it has [loaded], recording each load a prewarm starts. */
    class LoadingModels : SceneAssetResolver {
        val loads = mutableListOf<Load>()
        var loaded = false

        override fun canResolveMesh(name: String): Boolean = name == MODEL

        override fun createMesh(runtime: SceneAppLifecycleRuntime, name: String): Mesh? = if (loaded) SloopMesh else null

        override fun loadMesh(name: String, scope: CoroutineScope): SceneMeshLoad? = if (loaded) null else Load(scope).also { loads += it }
    }

    private object SloopMesh : Mesh {
        override val format = VertexFormat.PositionNormalColor
        override val sizeBytes = 100L
        override fun destroy() = Unit
    }

    class Load(val scope: CoroutineScope) : SceneMeshLoad {
        var done = false
        override var failure: Throwable? = null
        var closed = false

        override fun poll(): Boolean = done

        override fun close() {
            closed = true
        }
    }

    private companion object {
        const val MODEL = "ships/sloop.model"
    }
}
