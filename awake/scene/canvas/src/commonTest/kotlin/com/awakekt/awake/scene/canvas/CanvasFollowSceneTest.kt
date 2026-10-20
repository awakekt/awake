/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.FrameOutput
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.core.math2d.Vec2
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.canvas.CanvasElementBinding.toComponent
import com.awakekt.awake.scene.canvas.CanvasElementBinding.toSceneComponent
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.document.SceneValidator
import com.awakekt.awake.ui.canvas.CanvasCurve
import com.awakekt.awake.ui.canvas.CanvasCurvePoint
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasFollowLimit
import com.awakekt.awake.ui.canvas.CanvasFollowRank
import com.awakekt.awake.ui.canvas.CanvasFollowRules
import com.awakekt.awake.ui.canvas.CanvasProjection
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Follow rules, limits and the follower pool, as a scene saves and draws them. */
class CanvasFollowSceneTest {

    private val world = World()

    /** A node named [name] at x [x]. */
    private fun node(name: String, x: Float): Entity = world.create().also {
        world.add(it, Name(name))
        world.add(it, Transform().apply { worldMatrix = Mat4().translate(x, 0f, 0f) })
    }

    /** Puts world x on the screen as is, at a distance of x. */
    private val projector = object : CanvasProjector {
        override fun project(x: Float, y: Float, z: Float): Vec2 = Vec2(x, 100f)

        override fun bounds(entity: Entity): Rectangle? = null

        override fun projectFollow(x: Float, y: Float, z: Float): CanvasProjection = CanvasProjection(x, 100f, depth = x, distance = x)
    }

    private fun frame(): FrameOutput = ComposeHost().frame(FrameInput(800, 600)) { SceneCanvas(world, projector = projector) }

    private fun FrameOutput.drawn(entity: Entity): Boolean = semantics.findTag("canvas-element-${entity.id}") != null

    @Test
    fun aScenesFollowLimitThinsItsFollowers() {
        world.add(world.create(), CanvasFollowLimit(max = 2, rankBy = CanvasFollowRank.Distance))
        val pool = CanvasFollowerPool(world, SceneCanvasElement(width = 10f, height = 10f, offsetX = 0f, offsetY = 0f))
        val followers = (1..4).map { index -> node("N$index", index * 100f).let { pool.acquire("N$index") } }

        val out = frame()

        assertEquals(listOf(true, true, false, false), followers.map { out.drawn(it) }, "the two nearest")
    }

    @Test
    fun anOccludedFollowerIsAskedAboutTheNodeItFollows() {
        val asked = ArrayList<Entity>()
        val walled = node("Walled", 100f)
        val open = node("Open", 200f)
        val occluding = object : CanvasProjector by projector {
            override fun occluded(target: Entity, x: Float, y: Float, z: Float): Boolean {
                asked += target
                return target == walled
            }
        }
        val rules = SceneCanvasElement(width = 10f, height = 10f, followRules = CanvasFollowRules(occlusion = true))
        val pool = CanvasFollowerPool(world, rules)
        val behindWall = pool.acquire("Walled")
        val inSight = pool.acquire("Open")
        val unasked = CanvasFollowerPool(world, SceneCanvasElement(width = 10f, height = 10f)).acquire("Walled")

        val out = ComposeHost().frame(FrameInput(800, 600)) { SceneCanvas(world, projector = occluding) }

        assertFalse(out.drawn(behindWall), "hidden behind what stands in front of its node")
        assertTrue(out.drawn(inSight))
        assertTrue(out.drawn(unasked), "a follower whose rules do not ask is never hidden")
        assertEquals(listOf(walled, open), asked, "asked once for each follower that asks, with the node it follows")
    }

    @Test
    fun followOcclusionRoundTripsAndIsChecked() {
        val settings = CanvasFollowOcclusion(raysPerFrame = 16, reuseFrames = 3, layer = 0)
        val entity = world.create()
        val context = object : SceneResolutionContext {
            override val world: World = this@CanvasFollowSceneTest.world

            override fun deferNodeLink(targetNodeName: String, onResolved: (target: Entity) -> Unit) = Unit

            override fun recordRequest(request: Any) = Unit
        }
        assertNull(world.canvasFollowOcclusion())

        CanvasFollowOcclusionBinding.attachTyped(world, entity, CanvasFollowOcclusionBinding.export(world, entity, settings), context)

        assertEquals(settings, world.canvasFollowOcclusion())
        assertEquals(3, SceneCanvasFollowOcclusion(raysPerFrame = 0, reuseFrames = 0, layer = -1).validate("node").size)
        assertTrue(SceneCanvasFollowOcclusion().validate("node").isEmpty())
    }

    @Test
    fun aFollowLimitRoundTripsAndIsChecked() {
        val limit = CanvasFollowLimit(group = "plates", max = 12, rankBy = CanvasFollowRank.Priority)
        val entity = world.create()

        val context = object : SceneResolutionContext {
            override val world: World = this@CanvasFollowSceneTest.world

            override fun deferNodeLink(targetNodeName: String, onResolved: (target: Entity) -> Unit) = Unit

            override fun recordRequest(request: Any) = Unit
        }
        CanvasFollowLimitBinding.attachTyped(world, entity, CanvasFollowLimitBinding.export(world, entity, limit), context)

        assertEquals(listOf(limit), world.canvasFollowLimits())
        assertEquals(1, SceneCanvasFollowLimit(max = -1).validate("node").size)
    }

    @Test
    fun followRulesRoundTripAndAreSavedOnlyWhenSet() {
        val rules = CanvasFollowRules(
            maxDistance = 80f,
            group = "plates",
            priority = 3f,
            scale = CanvasCurve(listOf(CanvasCurvePoint(0f, 1f), CanvasCurvePoint(50f, 0.5f))),
            pinToEdge = true,
            edgeMargin = 12f,
        )
        val element = CanvasElement().apply { follow = "Hero"; followRules = rules }
        val arrow = CanvasElement().apply { followArrow = true }

        assertEquals(rules, element.toSceneComponent().toComponent().followRules)
        assertTrue(arrow.toSceneComponent().toComponent().followArrow)
        val json = Json { encodeDefaults = false }
        assertFalse("followRules" in json.encodeToString(SceneCanvasElement.serializer(), CanvasElement().toSceneComponent()))
        assertNull(json.decodeFromString(SceneCanvasElement.serializer(), "{}").followRules, "a scene saved before them has none")
    }

    @Test
    fun followRulesOnAnElementThatFollowsNothingAreReported() {
        val issues = SceneCanvasElement(followRules = CanvasFollowRules()).validate("node")

        assertTrue(issues.any { "followRules" in it.message }, issues.toString())
        assertTrue(SceneCanvasElement(follow = "Hero", followRules = CanvasFollowRules(maxDistance = -1f)).validate("node").any { "maxDistance" in it.message })
    }

    @Test
    fun aPoolHandsOutWhatItTookBack() {
        val pool = CanvasFollowerPool(world, SceneCanvasElement(text = "plate", width = 40f))
        val first = pool.acquire("A") { text = "Guard" }
        val element = world.get<CanvasElement>(first)!!

        assertTrue(pool.release(first))
        assertFalse(element.visible)
        assertEquals("", element.follow)
        assertFalse(pool.release(first), "already back")

        val again = pool.acquire("B")
        assertEquals(first, again)
        assertSame(element, world.get<CanvasElement>(again), "the same element, not a new one")
        assertEquals("plate", element.text, "set back to the template")
        assertEquals("B", element.follow)
        assertTrue(element.visible)
        assertEquals(1, pool.size)
    }

    @Test
    fun churningThroughAPoolMakesNoNewEntities() {
        val pool = CanvasFollowerPool(world, SceneCanvasElement())
        repeat(3) {
            val handed = (1..50).map { pool.acquire("N$it") }
            handed.forEach(pool::release)
        }

        assertEquals(50, pool.size)
        assertEquals(0, pool.active)
        assertEquals(50, world.family<CanvasElement>().size)

        pool.clear()
        assertEquals(0, world.family<CanvasElement>().size)
    }

    @Test
    fun multipleFollowLimitsOnSameNodeAreFlaggedBySceneValidator() {
        val document = SceneDocument(
            nodes = listOf(
                SceneNode(
                    name = "limits",
                    components = listOf(
                        SceneCanvasFollowLimit(group = "plates", max = 150),
                        SceneCanvasFollowLimit(group = "quest", max = 8),
                    ),
                ),
            ),
        )
        val issues = SceneValidator.validate(document)
        assertTrue(issues.any { "node declares 2 SceneCanvasFollowLimit instances, expected at most 1" in it.message })
    }

    @Test
    fun multipleFollowOcclusionsOnSameNodeAreFlaggedBySceneValidator() {
        val document = SceneDocument(
            nodes = listOf(
                SceneNode(
                    name = "occlusion",
                    components = listOf(
                        SceneCanvasFollowOcclusion(raysPerFrame = 16),
                        SceneCanvasFollowOcclusion(raysPerFrame = 32),
                    ),
                ),
            ),
        )
        val issues = SceneValidator.validate(document)
        assertTrue(issues.any { "node declares 2 SceneCanvasFollowOcclusion instances, expected at most 1" in it.message })
    }

    @Test
    fun separateNodesWithFollowLimitsAreLoadedAndThinnedIndependently() {
        val plateLimit = CanvasFollowLimit(group = "plates", max = 2, rankBy = CanvasFollowRank.Distance)
        val questLimit = CanvasFollowLimit(group = "quest", max = 1, rankBy = CanvasFollowRank.Distance)
        val nodePlates = world.create()
        val nodeQuest = world.create()
        world.add(nodePlates, plateLimit)
        world.add(nodeQuest, questLimit)

        assertEquals(listOf(plateLimit, questLimit), world.canvasFollowLimits())

        val poolPlates = CanvasFollowerPool(world, SceneCanvasElement(width = 10f, height = 10f, followRules = CanvasFollowRules(group = "plates")))
        val poolQuest = CanvasFollowerPool(world, SceneCanvasElement(width = 10f, height = 10f, followRules = CanvasFollowRules(group = "quest")))

        val plate1 = node("P1", 100f).let { poolPlates.acquire("P1") }
        val plate2 = node("P2", 200f).let { poolPlates.acquire("P2") }
        val plate3 = node("P3", 300f).let { poolPlates.acquire("P3") }

        val quest1 = node("Q1", 150f).let { poolQuest.acquire("Q1") }
        val quest2 = node("Q2", 250f).let { poolQuest.acquire("Q2") }

        val out = frame()

        // Plate limit is 2 -> P1, P2 drawn; P3 dropped
        assertTrue(out.drawn(plate1))
        assertTrue(out.drawn(plate2))
        assertFalse(out.drawn(plate3))

        // Quest limit is 1 -> Q1 drawn; Q2 dropped
        assertTrue(out.drawn(quest1))
        assertFalse(out.drawn(quest2))
    }
}

private fun List<SemanticsNode>.findTag(tag: String): SemanticsNode? =
    firstNotNullOfOrNull { if (it.testTag == tag) it else it.children.findTag(tag) }
