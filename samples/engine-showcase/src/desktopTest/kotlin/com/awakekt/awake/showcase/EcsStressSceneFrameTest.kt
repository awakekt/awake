/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase

import com.awakekt.awake.engine.platform.HeadlessSurface
import com.awakekt.awake.engine.platform.core.FrameStats
import com.awakekt.awake.render.capture.PixelMap
import com.awakekt.awake.render.testing.writePng
import com.awakekt.awake.scene.rendering.debug.RenderDiagnostics
import com.awakekt.awake.showcase.app.EngineShowcaseRenderPlan
import com.awakekt.awake.showcase.app.engineShowcaseApp
import com.awakekt.awake.showcase.examples.EcsStressExampleDriver
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * The ECS stress showcase on the real render plan, without a window and without Studio.
 *
 * What it asserts is structural, because a timing bound would fail on a busy machine rather than
 * on a regression: every entity is either drawn or culled, and they reach the GPU as a handful of
 * instanced draws rather than one per entity. The frame times are printed for a person comparing
 * two builds on the same machine.
 */
class EcsStressSceneFrameTest {
    @Test
    fun tenThousandEntitiesDrawInAFewInstancedCalls() = runBlocking {
        val app = engineShowcaseApp(SHOWCASE_ID, ShowcaseLaunchOptions(stressEntities = ENTITIES))
        val renderer = HeadlessPlanEngine(app, EngineShowcaseRenderPlan).boot(HeadlessSurface(WIDTH, HEIGHT))
        try {
            app.ready(renderer)
            repeat(WARMUP_FRAMES) { app.update(FRAME, WIDTH.toFloat(), HEIGHT.toFloat()) }
            val times = FrameStats(percentileWindowSize = MEASURED_FRAMES)
            repeat(MEASURED_FRAMES) {
                val start = TimeSource.Monotonic.markNow()
                app.update(FRAME, WIDTH.toFloat(), HEIGHT.toFloat())
                times.update(start.elapsedNow().inWholeNanoseconds / NANOS_PER_SECOND)
            }
            val recorded = assertNotNull(renderer.frameStats, "Vulkan counts the draws it records")
            val visible = RenderDiagnostics.submittedDrawCalls
            println(
                "ecs-stress headless: entities=$ENTITIES visible=$visible culled=${RenderDiagnostics.frustumCulled} " +
                    "p50=${times.p50FrameTimeMs}ms p99=${times.p99FrameTimeMs}ms max=${times.maxFrameTimeMs}ms " +
                    "recorded draws=${recorded.drawCalls} instances=${recorded.instances} tris=${recorded.triangles} " +
                    "gpu=${recorded.gpuTimeMs ?: "not timed"}",
            )

            val accounted = RenderDiagnostics.submittedInstances + RenderDiagnostics.frustumCulled + RenderDiagnostics.occluded
            assertTrue(accounted >= ENTITIES, "Only $accounted of $ENTITIES entities were drawn or culled.")
            // Extraction emits one draw per visible entity; instancing has to fold them before the
            // backend records. A batch is one colour's mesh, up to 4,096 copies, and each pass --
            // shadow cascades, scene depth, main -- draws every batch once. The bound grows with
            // batches, not with entities.
            val batches = EcsStressExampleDriver.paletteSize + visible / INSTANCES_PER_BATCH
            val bound = (batches + OTHER_DRAWS_PER_PASS) * PASSES + UI_DRAWS
            assertTrue(recorded.drawCalls <= bound, "$visible visible entities took ${recorded.drawCalls} recorded draws; expected at most $bound.")
            assertTrue(recorded.instances >= visible, "The backend recorded ${recorded.instances} instances for $visible visible entities.")

            val presented = renderer.readPresentedPixels()
            PixelMap(WIDTH, HEIGHT, presented.data.copyOf()).writePng(File(CAPTURE_PATH))
            assertTrue(presented.data.count { (it.toInt() and 0xff) > BACKGROUND } > MINIMUM_LIT_BYTES, "The stress field did not reach the frame.")
        } finally {
            app.dispose()
            renderer.destroy()
        }
    }

    private companion object {
        const val SHOWCASE_ID = "ecs-stress"
        const val ENTITIES = 10_000
        const val INSTANCES_PER_BATCH = 4_096
        const val OTHER_DRAWS_PER_PASS = 1
        const val PASSES = 6
        const val UI_DRAWS = 40
        const val WIDTH = 960
        const val HEIGHT = 540
        const val FRAME = 1f / 60f
        const val WARMUP_FRAMES = 60
        const val MEASURED_FRAMES = 240
        const val NANOS_PER_SECOND = 1_000_000_000f
        const val BACKGROUND = 20
        const val MINIMUM_LIT_BYTES = 10_000
        const val CAPTURE_PATH = "build/reports/render-captures/ecs-stress.png"
    }
}
