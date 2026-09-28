/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.blueprint

import com.awakekt.awake.core.animation.AnimationChannel
import com.awakekt.awake.core.animation.AnimationClip
import com.awakekt.awake.core.animation.AnimationLibrary
import com.awakekt.awake.core.animation.AnimationPlayer
import com.awakekt.awake.core.animation.AnimationProperty
import com.awakekt.awake.core.animation.AnimationSampler
import com.awakekt.awake.core.animation.Bone
import com.awakekt.awake.core.animation.Skeleton
import com.awakekt.awake.core.animation.Skin
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.nodegraph.NodeGraph
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.rendering.animation.Animator
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlueprintSystemTest {
    private val world = World()
    private val graphs = HashMap<String, NodeGraph>()
    private val system = BlueprintSystem(graphs = { graphs.getValue(it) })

    private fun blueprint(path: String, vararg variables: Pair<String, JsonPrimitive>): Entity =
        world.create().also { world.add(it, BlueprintComponent(path, variables.toMap())) }

    private fun step(times: Int = 1) = repeat(times) { system.update(world, STEP) }

    private fun variable(entity: Entity, name: String): Any? = world.get<BlueprintComponent>(entity)?.instance?.variable(name)

    @Test
    fun onStartFiresOnceAfterTheVariableOverridesAreSet() {
        graphs["count"] = graph {
            node("start", "event.start")
            node("n", "var.get.float", "name" to "n")
            node("add", "math.add", "b" to 1f)
            node("set", "var.set.float", "name" to "n")
            wire("start.then", "set.exec")
            wire("n.value", "add.a")
            wire("add.sum", "set.value")
        }
        val plain = blueprint("count")
        val overridden = blueprint("count", "n" to JsonPrimitive(10))

        step(5)

        assertEquals(1f, variable(plain, "n"))
        assertEquals(11f, variable(overridden, "n"))
    }

    @Test
    fun anUnwiredTargetIsTheBlueprintsOwnEntity() {
        graphs["vanish"] = graph {
            node("start", "event.start")
            node("destroy", "entity.destroy")
            wire("start.then", "destroy.exec")
        }
        val self = blueprint("vanish")
        val bystander = world.create()

        step()

        assertFalse(world.isAlive(self))
        assertTrue(world.isAlive(bystander))
    }

    @Test
    fun findByNameAndSelfNameTheEntitiesToActOn() {
        graphs["clear"] = graph {
            node("start", "event.start")
            node("find", "entity.find-by-name", "name" to "crate")
            node("self", "entity.self")
            node("destroyCrate", "entity.destroy")
            node("destroySelf", "entity.destroy")
            wire("start.then", "destroyCrate.exec")
            wire("destroyCrate.then", "destroySelf.exec")
            wire("find.entity", "destroyCrate.target")
            wire("self.entity", "destroySelf.target")
        }
        val crate = world.create().also { world.add(it, Name("crate")) }
        val barrel = world.create().also { world.add(it, Name("barrel")) }
        val self = blueprint("clear")

        step()

        assertFalse(world.isAlive(crate))
        assertFalse(world.isAlive(self))
        assertTrue(world.isAlive(barrel))
    }

    @Test
    fun aDelayCountsFixedSteps() {
        graphs["timer"] = graph {
            node("start", "event.start")
            node("wait", "flow.delay", "seconds" to 1f)
            node("destroy", "entity.destroy")
            wire("start.then", "wait.exec")
            wire("wait.then", "destroy.exec")
        }
        val timer = blueprint("timer")

        step(59)
        assertTrue(world.isAlive(timer), "still waiting after 58 polls")
        step(3)
        assertFalse(world.isAlive(timer))
    }

    @Test
    fun playAnimationPlaysTheClipOnceAndSkipsAClipItDoesNotHave() {
        graphs["wave"] = graph {
            node("start", "event.start")
            node("missing", "animation.play", "clip" to "missing")
            node("wave", "animation.play", "clip" to "wave")
            wire("start.then", "missing.exec")
            wire("missing.then", "wave.exec")
        }
        val entity = blueprint("wave")
        val player = animated(entity, "wave", "idle")
        val bare = blueprint("wave")

        step()

        assertEquals("wave", player.activeClipId)
        assertTrue(world.isAlive(bare), "no animator is skipped, not an error")
        player.update(1f)
        assertTrue(player.isFinished, "played once, not looped")
    }

    @Test
    fun reloadSwapsTheGraphKeepingVariablesWithoutRestarting() {
        graphs["door"] = graph {
            node("start", "event.start")
            node("set", "var.set.float", "name" to "hp", "value" to 5f)
            node("wait", "flow.delay", "seconds" to 1f)
            node("destroy", "entity.destroy")
            wire("start.then", "set.exec")
            wire("set.then", "wait.exec")
            wire("wait.then", "destroy.exec")
        }
        val door = blueprint("door")
        step()

        system.reload(
            "door",
            graph {
                node("start", "event.start")
                node("hp", "var.get.float", "name" to "hp")
                node("destroy", "entity.destroy")
                wire("start.then", "destroy.exec")
            },
        )
        step(120)

        assertTrue(world.isAlive(door), "the wait was cancelled and On Start did not fire again")
        assertEquals(5f, variable(door, "hp"))
    }

    @Test
    fun anOverrideForAVariableTheGraphLacksNamesTheGraph() {
        graphs["empty"] = graph { node("start", "event.start") }
        blueprint("empty", "speed" to JsonPrimitive(2))

        val error = assertFailsWith<IllegalArgumentException> { step() }

        assertContains(error.message.orEmpty(), "'empty'")
        assertContains(error.message.orEmpty(), "speed")
    }

    private fun animated(entity: Entity, vararg clips: String): AnimationPlayer {
        val skeleton = Skeleton(bones = listOf(Bone(Vec3f.ZERO, Quat.IDENTITY, Vec3f(1f, 1f, 1f), null, emptyList())), roots = listOf(0))
        val library = AnimationLibrary(skeleton, clips.associateWith { clip(it) })
        val player = AnimationPlayer(library)
        world.add(entity, Animator(player, Skin(joints = listOf(0), inverseBindMatrices = listOf(Mat4()))))
        return player
    }

    private fun clip(name: String) = AnimationClip(
        name = name,
        channels = listOf(
            AnimationChannel(
                targetBone = 0,
                property = AnimationProperty.Translation,
                sampler = AnimationSampler(times = floatArrayOf(0f, 0.5f), values = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f), componentsPerKeyframe = 3),
            ),
        ),
    )
}
