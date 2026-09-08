/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime.session

import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.document.SceneTransform
import com.awakekt.awake.scene.document.SceneVec3
import com.awakekt.awake.scene.rendering.mesh.SceneMeshRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SceneDocumentEditSessionTest {
    @Test
    fun playUsesAnIsolatedWorldAndStopDestroysOnlyThatWorld() {
        val authored = SceneDocument(
            nodes = listOf(
                SceneNode(
                    name = "crate",
                    transform = SceneTransform(position = SceneVec3(1f, 2f, 3f)),
                    components = listOf(SceneMeshRenderer("cube", "default")),
                ),
            ),
        )
        val session = SceneDocumentEditSession(authored)

        val play = session.startPlay()
        val root = play.roots.single().entity

        assertEquals(SceneDocumentSessionMode.Play, session.mode)
        assertNotNull(session.world)
        assertFailsWith<IllegalStateException> { session.applyAuthoredDocument(SceneDocument()) }

        session.stopPlay()

        assertEquals(SceneDocumentSessionMode.Edit, session.mode)
        assertNull(session.world)
        assertEquals(authored, session.authoredDocument)
        assertEquals(false, play.world.isAlive(root))
    }

    @Test
    fun authoredChangesAreExplicitAndTheNextPlayUsesTheNewDocument() {
        val session = SceneDocumentEditSession(SceneDocument(name = "before"))
        val replacement = SceneDocument(name = "after")

        session.applyAuthoredDocument(replacement)
        session.startPlay()

        assertEquals(replacement, session.authoredDocument)
        session.close()
        assertEquals(SceneDocumentSessionMode.Edit, session.mode)
    }

    @Test
    fun pauseResumeAndStepLifecycleTransitions() {
        val session = SceneDocumentEditSession(SceneDocument(name = "test"))
        session.startPlay()
        assertEquals(SceneDocumentSessionMode.Play, session.mode)
        assertEquals(false, session.isStepping)

        session.pausePlay()
        assertEquals(SceneDocumentSessionMode.Pause, session.mode)

        session.stepPlay()
        assertEquals(true, session.isStepping)
        session.clearStepping()
        assertEquals(false, session.isStepping)

        session.resumePlay()
        assertEquals(SceneDocumentSessionMode.Play, session.mode)

        session.stopPlay()
        assertEquals(SceneDocumentSessionMode.Edit, session.mode)
    }

    @Test
    fun stoppingFromPauseDestroysIsolatedWorldAndRestoresEditMode() {
        val session = SceneDocumentEditSession(SceneDocument(name = "paused-stop"))
        val play = session.startPlay()
        assertNotNull(session.world)

        session.pausePlay()
        assertEquals(SceneDocumentSessionMode.Pause, session.mode)

        session.stopPlay()
        assertEquals(SceneDocumentSessionMode.Edit, session.mode)
        assertNull(session.world)
        assertEquals(false, session.isStepping)
    }
}
