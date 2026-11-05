/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.InterpolatedSystem
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.engine.platform.dsl.AppSpecBuilder
import com.awakekt.awake.engine.platform.error.AppError
import com.awakekt.awake.engine.platform.error.AppErrorHandler
import com.awakekt.awake.engine.platform.error.AppErrorSource
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.core.transform.TransformSystem
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Verifies [SceneSystemPhase.PreAnimation] ordering, world-matrix propagation, and error guarding
 * inside [com.awakekt.awake.scene.runtime.schedule.SceneSchedule].
 */
class ScenePreAnimationScheduleTest {
    @Test
    fun singleFrameRunsFixedInterpolatePreAnimationTransformFrameAndInfrastructureInOrder() = runTest {
        val events = mutableListOf<String>()
        val actorTransform = Transform()
        val frameMovedTransform = Transform()
        var m03InPreAnimation = -1f
        var m03InFrame = -1f
        var frameMovedM03InRender = -1f

        val app = startScene(
            populate = {
                world.add(world.create(), actorTransform)
                world.add(world.create(), frameMovedTransform)
            },
            systems = listOf(
                registration("fixed", SceneSystemPhase.Fixed) { fixedProbe(actorTransform, events) },
                registration("pre-animation", SceneSystemPhase.PreAnimation) {
                    system { _, _ ->
                        m03InPreAnimation = actorTransform.worldMatrix.m03
                        actorTransform.position.x = 3f
                        events += "pre-animation"
                    }
                },
                registration("frame", SceneSystemPhase.Frame) {
                    system { _, _ ->
                        m03InFrame = actorTransform.worldMatrix.m03
                        frameMovedTransform.position.x = 9f
                        events += "frame"
                    }
                },
            ),
            infrastructure = listOf(
                TransformSystem(),
                system { _, _ ->
                    frameMovedM03InRender = frameMovedTransform.worldMatrix.m03
                    events += "infrastructure"
                },
            ),
        )
        events.clear()

        app.update(STEP, WIDTH, HEIGHT)

        assertEquals(listOf("fixed", "interpolate", "pre-animation", "frame", "infrastructure"), events)
        assertEquals(0f, m03InPreAnimation, "world matrices have not been rebuilt yet when pre-animation runs")
        assertEquals(3f, m03InFrame, "TransformSystem rebuilds pre-animation changes before frame systems run")
        assertEquals(9f, frameMovedM03InRender, "changes made during frame systems still propagate before render")
    }

    @Test
    fun preAnimationParentMovePropagatesParentAndChildWorldMatricesOnceBeforeFrame() = runTest {
        lateinit var parentTransform: Transform
        lateinit var childTransform: Transform
        val seenParent = Vec3f()
        val seenChild = Vec3f()
        var armed = false

        val app = startScene(
            populate = {
                val parent = world.create()
                parentTransform = Transform(position = Vec3f(0f, 0f, 0f))
                world.add(parent, parentTransform)
                val child = world.create()
                childTransform = Transform(position = Vec3f(0f, 2f, 0f), parent = parent)
                world.add(child, childTransform)
            },
            systems = listOf(
                registration("pose", SceneSystemPhase.PreAnimation) {
                    system { _, _ -> if (armed) parentTransform.position.set(4f, 1f, -3f) }
                },
                registration("observer", SceneSystemPhase.Frame) {
                    system { _, _ ->
                        seenParent.readTranslation(parentTransform)
                        seenChild.readTranslation(childTransform)
                    }
                },
            ),
            infrastructure = listOf(TransformSystem()),
        )
        val parentVersionBefore = parentTransform.worldVersion
        val childVersionBefore = childTransform.worldVersion
        armed = true

        app.update(STEP, WIDTH, HEIGHT)

        assertEquals(Vec3f(4f, 1f, -3f), seenParent)
        assertEquals(Vec3f(4f, 3f, -3f), seenChild)
        assertEquals(parentVersionBefore + 1, parentTransform.worldVersion, "parent matrix rebuilds once, not twice")
        assertEquals(childVersionBefore + 1, childTransform.worldVersion, "child matrix rebuilds once, not twice")
    }

    @Test
    fun headlessScheduleWithoutInfrastructureTransformStillPropagatesPreAnimationTransforms() = runTest {
        lateinit var parentTransform: Transform
        lateinit var childTransform: Transform
        val seenChild = Vec3f()

        val app = startScene(
            populate = {
                val parent = world.create()
                parentTransform = Transform()
                world.add(parent, parentTransform)
                val child = world.create()
                childTransform = Transform(position = Vec3f(1f, 1.5f, 0f), parent = parent)
                world.add(child, childTransform)
            },
            systems = listOf(
                registration("pose", SceneSystemPhase.PreAnimation) {
                    system { _, delta -> if (delta > 0f) parentTransform.position.set(5f, 0f, 2f) }
                },
                registration("observer", SceneSystemPhase.Frame) {
                    system { _, _ -> seenChild.readTranslation(childTransform) }
                },
            ),
            infrastructure = emptyList(),
        )

        app.update(STEP, WIDTH, HEIGHT)

        assertEquals(Vec3f(6f, 1.5f, 2f), seenChild)
    }

    @Test
    fun preAnimationFailureRoutesToAppErrorHandlerOnceWithoutAbortingTransformOrFrameSystems() = runTest {
        val received = mutableListOf<AppError>()
        val boom = IllegalStateException("bad snapshot")
        val actorTransform = Transform()
        var brokenCalls = 0
        var siblingCalls = 0
        val frameSeenX = mutableListOf<Float>()

        val app = startScene(
            handler = AppErrorHandler { received += it },
            populate = { world.add(world.create(), actorTransform) },
            systems = listOf(
                registration("broken", SceneSystemPhase.PreAnimation) {
                    system { _, delta ->
                        if (delta > 0f) {
                            brokenCalls++
                            throw boom
                        }
                    }
                },
                registration("mover", SceneSystemPhase.PreAnimation) {
                    system { _, delta ->
                        if (delta > 0f) {
                            siblingCalls++
                            actorTransform.position.x = siblingCalls * 2f
                        }
                    }
                },
                registration("observer", SceneSystemPhase.Frame) {
                    system { _, delta -> if (delta > 0f) frameSeenX += actorTransform.worldMatrix.m03 }
                },
            ),
            infrastructure = listOf(TransformSystem()),
        )

        repeat(3) { app.update(STEP, WIDTH, HEIGHT) }

        val error = received.single()
        assertEquals(AppErrorSource.System, error.source)
        assertSame(boom, error.cause)
        assertNull(error.retry)
        assertEquals(1, brokenCalls, "the failing pre-animation system is skipped on later frames")
        assertEquals(3, siblingCalls)
        assertEquals(listOf(2f, 4f, 6f), frameSeenX)
    }

    private fun fixedProbe(actorTransform: Transform, events: MutableList<String>): InterpolatedSystem =
        object : InterpolatedSystem {
            override fun update(world: World, delta: Float) {
                actorTransform.position.x = 1f
                events += "fixed"
            }

            override fun interpolate(world: World, alpha: Float) {
                actorTransform.position.x = 2f
                events += "interpolate"
            }
        }

    private fun system(block: (World, Float) -> Unit): System = object : System {
        override fun update(world: World, delta: Float) = block(world, delta)
    }

    private fun Vec3f.readTranslation(transform: Transform) {
        set(transform.worldMatrix.m03, transform.worldMatrix.m13, transform.worldMatrix.m23)
    }

    private fun registration(
        name: String,
        phase: SceneSystemPhase,
        factory: SceneAppLifecycleRuntime.() -> System,
    ): SceneSystemRegistration = SceneSystemRegistration(SceneSystemHandle(name), phase, factory)

    private suspend fun startScene(
        handler: AppErrorHandler? = null,
        populate: SceneAppLifecycleRuntime.() -> Unit = {},
        systems: List<SceneSystemRegistration>,
        infrastructure: List<System>,
    ): AwakeAppLifecycle {
        val builder = AppSpecBuilder()
        handler?.let { builder.service(AppErrorHandler::class, it) }
        SceneAppSpec(
            sceneName = "pre-animation",
            systems = systems,
            scenePopulationBlock = populate,
            renderableFactory = { error("unused") },
            assetLibraryFactory = null,
            updateBlock = { _, _ -> },
            ui = null,
            onReadyBlock = {},
            onDisposeBlock = {},
            serviceRegistrations = emptyList(),
            infrastructureSystemsFactory = { infrastructure },
        ).installInto(builder)
        return builder.build().createLifecycle().also { it.ready(NoopRenderer()) }
    }

    private companion object {
        const val STEP = 1f / 60f
        const val WIDTH = 320f
        const val HEIGHT = 240f
    }
}
