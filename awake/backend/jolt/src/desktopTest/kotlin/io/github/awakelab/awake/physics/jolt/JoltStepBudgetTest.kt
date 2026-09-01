/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.physics.jolt

import io.github.awakelab.awake.core.math.Quat
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.physics.BoxShape
import io.github.awakelab.awake.physics.HeightFieldShape
import io.github.awakelab.awake.physics.MotionType
import io.github.awakelab.awake.physics.PhysicsWorld
import io.github.awakelab.awake.physics.SphereShape
import kotlinx.coroutines.test.runTest
import kotlin.math.sin
import kotlin.system.measureNanoTime
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What one fixed step of a populated world actually costs.
 *
 * This exists to answer a design question with a number rather than a guess. Async stepping --
 * simulating step N while rendering N−1 -- is the standard next move once fixed-step and
 * interpolation are in place, and it is also a large change with a hard platform split: Kotlin/Wasm
 * has no worker threads here, so whatever it buys, one of the four targets cannot have it. Worth
 * doing only if stepping is actually near the frame budget, and that had never been measured.
 *
 * Desktop only, deliberately. A number from the iOS simulator or from headless Chrome describes the
 * host more than the engine, and this is not a cross-backend claim -- it is one machine's answer to
 * "is this close to sixteen milliseconds".
 *
 * The assertion is deliberately loose. A tight budget on a shared CI machine measures scheduling
 * noise; this catches something becoming an order of magnitude slower, which is the regression that
 * would change the answer above.
 */
class JoltStepBudgetTest {

    /** Terrain plus a crowd on top of it: the shape of an open-world slice, not a microbenchmark. */
    private suspend fun populatedWorld(): PhysicsWorld {
        val world = createJoltPhysicsWorld()
        val samples = FloatArray(SAMPLES * SAMPLES) { index ->
            val x = index % SAMPLES
            val z = index / SAMPLES
            sin(x * 0.2f) * 0.5f + sin(z * 0.15f) * 0.5f
        }
        world.createBody(
            HeightFieldShape(samples, SAMPLES, Vec3f(1f, 1f, 1f)),
            Vec3f(0f, 0f, 0f),
            Quat.IDENTITY,
            MotionType.STATIC,
        )
        repeat(BODY_COUNT) { index ->
            val shape = if (index % 2 == 0) SphereShape(0.4f) else BoxShape(Vec3f(0.4f, 0.4f, 0.4f))
            world.createBody(
                shape,
                Vec3f(
                    (index % 12) * 1.1f + 4f,
                    6f + (index / 12) * 1.2f,
                    ((index / 12) % 12) * 1.1f + 4f,
                ),
                Quat.IDENTITY,
                MotionType.DYNAMIC,
            )
        }
        return world
    }

    @Test
    fun aFixedStepOfAPopulatedWorldFitsWellInsideAFrame() = runTest {
        val world = populatedWorld()
        try {
            // Warm up first: the first steps pay for broadphase build and JIT, and averaging them
            // in would describe start-up rather than steady state.
            repeat(WARMUP_STEPS) { world.step(STEP) }

            val elapsed = measureNanoTime { repeat(MEASURED_STEPS) { world.step(STEP) } }
            val perStepMillis = elapsed / MEASURED_STEPS / 1_000_000.0

            println(
                "JoltStepBudget: $BODY_COUNT dynamic bodies on a ${SAMPLES}x$SAMPLES heightfield " +
                    "cost ${(perStepMillis * 1000).toInt() / 1000.0} ms per fixed step",
            )
            assertTrue(
                perStepMillis < BUDGET_MILLIS,
                "a fixed step took $perStepMillis ms, past the $BUDGET_MILLIS ms this guards",
            )
        } finally {
            world.destroy()
        }
    }

    private companion object {
        const val SAMPLES = 65
        const val BODY_COUNT = 144
        const val STEP = 1f / 60f
        const val WARMUP_STEPS = 60
        const val MEASURED_STEPS = 240

        /**
         * Half a frame at sixty, which is loose on purpose.
         *
         * A budget tight enough to be interesting is tight enough to fail on a busy machine. This
         * one catches an order-of-magnitude regression -- the kind that would make async stepping
         * worth its platform split -- and nothing subtler.
         */
        const val BUDGET_MILLIS = 8.0
    }
}
