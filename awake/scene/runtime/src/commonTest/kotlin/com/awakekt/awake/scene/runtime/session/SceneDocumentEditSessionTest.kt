/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime.session

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.document.SceneTransform
import com.awakekt.awake.scene.document.SceneVec3
import com.awakekt.awake.scene.rendering.mesh.SceneMeshRenderer
import com.awakekt.awake.scene.runtime.DefaultSceneComponentResolvers
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SceneDocumentEditSessionTest {
    // Play snapshots through the global registry by default, which knows Core's components once installed.
    init {
        DefaultSceneComponentResolvers.install()
    }

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

    /** An editor loads each project into a scoped registry, so a project's own component must survive into Play. */
    @Test
    fun playKeepsAComponentOnlyTheSessionsScopedRegistryKnows() {
        val registry = SceneComponentRegistry.scoped(bindings = listOf(WaypointBinding))
        val authored = SceneDocument(nodes = listOf(SceneNode(name = "buoy", components = listOf(SceneWaypoint(order = 2)))))

        val play = SceneDocumentEditSession(authored, componentRegistry = registry).startPlay()

        val orders = mutableListOf<Int>()
        play.world.queryEach(Waypoint::class) { _, waypoint -> orders += waypoint.order }
        assertEquals(listOf(2), orders)
        assertFails("the global registry cannot snapshot a component only a scope registers") {
            SceneDocumentEditSession(authored).startPlay()
        }
    }

    @Serializable
    @SerialName("edit_session_waypoint")
    private data class SceneWaypoint(val order: Int) : SceneComponent

    private data class Waypoint(val order: Int)

    private object WaypointBinding : SceneComponentBinding<Waypoint, SceneWaypoint> {
        override val componentClass = Waypoint::class
        override val schemaClass = SceneWaypoint::class
        override val serializer = SceneWaypoint.serializer()

        override fun attachTyped(world: World, entity: Entity, component: SceneWaypoint, context: SceneResolutionContext) {
            world.add(entity, Waypoint(component.order))
        }

        override fun export(world: World, entity: Entity, component: Waypoint) = SceneWaypoint(component.order)
    }
}
