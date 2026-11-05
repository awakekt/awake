/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.animation

import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Locomotion is usable with only core animation/math, with no world or scene on its classpath. */
class LocomotionPlayerTest {
    @Test
    fun standaloneLocomotionSelectsIdleWalkRunAndStopsWithoutWalkingInPlace() {
        val mover = LocomotionTestDriver()
        assertNull(mover.motion.playing, "the first position is only an observation")
        assertEquals("idle", mover.stand())
        assertEquals("walk", mover.move(2f))
        assertEquals("run", mover.move(6f))
        val stopping = List(30) { mover.step(0f) }
        assertEquals(listOf("run", "idle"), stopping.filterIndexed { i, value -> i == 0 || value != stopping[i - 1] })
        assertEquals(0f, mover.motion.groundSpeed)
    }

    @Test
    fun extractedDampingMatchesThePreviousExponentialForVariableFrameSteps() {
        val mover = LocomotionTestDriver()
        var expected = 0f
        val rates = floatArrayOf(1f / 30f, 1f / 60f, 1f / 120f, 1f / 240f, 0.267f)
        repeat(100) { step ->
            val dt = rates[step % rates.size]
            val speed = if (step % 3 == 0) 6f else 2.57f
            val oldX = mover.x
            mover.step(speed, dt = dt)
            // Use actual measured displacement, just as the previous scene implementation did.
            val dx = mover.x - oldX
            val measured = sqrt(dx * dx) / dt
            expected += (measured - expected) * (1f - exp(-dt / 0.1f))
            assertEquals(expected, mover.motion.groundSpeed, 2e-5f, "step $step")
        }
    }

    @Test
    fun speedSmoothingIsIndependentOfFrameRate() {
        val results = listOf(30, 60, 120, 240).map { fps ->
            val mover = LocomotionTestDriver()
            repeat(fps / 2) { mover.step(6f, dt = 1f / fps) }
            assertEquals("run", mover.motion.playing)
            mover.motion.groundSpeed
        }
        results.forEach { assertEquals(results.first(), it, 5e-5f) }
    }

    @Test
    fun aTeleportPreservesTheWalkAndReferenceSpeedPlayback() {
        val mover = LocomotionTestDriver(CONFIG.copy(walkReferenceSpeed = 2f, runReferenceSpeed = 6f))
        mover.move(2f)
        val speed = mover.motion.groundSpeed
        mover.x += 50f
        mover.motion.update(mover.player, mover.x, 0f, 0f, DT)
        assertEquals("walk", mover.motion.playing)
        assertEquals(speed, mover.motion.groundSpeed)
        assertEquals(speed / 2f, mover.player.speed)
        assertEquals("walk", mover.step(2f))
    }

    @Test
    fun suppliedGroundContactDrivesPhasesAndLandingCanBeInterruptedByMovement() {
        val mover = LocomotionTestDriver(CONFIG.copy(takeOff = "takeoff", fall = "fall", land = "land"))
        mover.stand()
        assertEquals("takeoff", mover.step(0f, dy = 0.1f, grounded = false))
        assertTrue(mover.motion.playingOnce)
        repeat(20) { mover.step(0f, dy = 0.1f, grounded = false) }
        assertEquals("jump", mover.motion.playing)
        assertEquals("fall", mover.step(0f, dy = -0.1f, grounded = false))
        assertEquals("land", mover.step(0f, dy = -0.001f, grounded = true))
        assertTrue(mover.motion.playingOnce)
        assertEquals("walk", List(4) { mover.step(2f, grounded = true) }.last())
        assertFalse(mover.motion.playingOnce)
    }

    @Test
    fun unknownGroundContactInfersTheJumpFromVerticalMotion() {
        val mover = LocomotionTestDriver()
        mover.stand()
        assertEquals("jump", mover.step(0f, dy = 0.1f))
        repeat(30) { mover.step(0f) }
        assertEquals("idle", mover.motion.playing)
    }

    @Test
    fun missingClipsPreservePlaybackAndInvalidStepsDoNotChangeMotionState() {
        val mover = LocomotionTestDriver(CONFIG.copy(run = "missing"))
        mover.move(2f)
        assertEquals("walk", mover.move(6f))
        val speed = mover.motion.groundSpeed
        mover.motion.update(mover.player, 100f, 100f, 100f, 0f)
        mover.motion.update(mover.player, 100f, 100f, 100f, -DT)
        assertEquals(speed, mover.motion.groundSpeed)
        assertEquals("walk", mover.motion.playing)
    }

    @Test
    fun theDocumentedStandaloneLoopRunsWithoutAScene() {
        val playback = player()
        val mover = LocomotionPlaybackExample(playback)
        mover.frame(0f, 0f, 0f, DT, true)
        repeat(60) { frame -> mover.frame((frame + 1) * 2f * DT, 0f, 0f, DT, true) }
        assertEquals("walk", playback.activeClipId)
        assertEquals(1f, playback.speed, 1e-3f)
    }

    @Test
    fun invalidConfigurationFailsAtConstructionInsteadOfDuringPlayback() {
        val invalid = listOf<() -> LocomotionConfig>(
            { LocomotionConfig(walkAbove = -1f) },
            { LocomotionConfig(runAbove = 0f, walkAbove = 1f) },
            { LocomotionConfig(airborneAbove = 0f) },
            { LocomotionConfig(crossFade = -1f) },
            { LocomotionConfig(walkReferenceSpeed = 0f) },
            { LocomotionConfig(runReferenceSpeed = -1f) },
        )
        invalid.forEach { create -> assertFailsWith<IllegalArgumentException> { create() } }
    }

    @Test
    fun nonFiniteConfigurationAndInvalidCopiesAreRejected() {
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            val invalid = listOf<() -> LocomotionConfig>(
                { LocomotionConfig(walkAbove = value) },
                { LocomotionConfig(runAbove = value) },
                { LocomotionConfig(airborneAbove = value) },
                { LocomotionConfig(crossFade = value) },
                { LocomotionConfig(walkReferenceSpeed = value) },
                { LocomotionConfig(runReferenceSpeed = value) },
            )
            invalid.forEach { create -> assertFailsWith<IllegalArgumentException> { create() } }
        }
        assertFailsWith<IllegalArgumentException> { CONFIG.copy(walkReferenceSpeed = 0f) }
        // Inclusive threshold/fade boundaries and absent reference speeds remain valid.
        LocomotionConfig(walkAbove = 0f, runAbove = 0f, crossFade = 0f)
    }

    @Test
    fun externalOneShotClipPlaysUninterruptedAtItsOwnSpeedAndResumesStandOnTheNextUpdate() {
        val config = CONFIG.copy(idle = "stand", walkReferenceSpeed = 2f, runReferenceSpeed = 6f)
        for (startRunning in listOf(false, true)) {
            val playback = player(mapOf("stand" to 1f, "attack" to 0.5f))
            val motion = LocomotionPlayer(config)
            var x = 0f
            motion.update(playback, x, 0f, 0f, DT, grounded = true)
            val warmupFrames = if (startRunning) 60 else 30
            val warmupSpeed = if (startRunning) 6f else 0f
            repeat(warmupFrames) {
                x += warmupSpeed * DT
                motion.update(playback, x, 0f, 0f, DT, grounded = true)
                playback.advance(DT)
            }
            assertEquals(if (startRunning) "run" else "stand", playback.activeClipId)

            playback.play("attack", AnimationPlayback.Once)
            playback.speed = 1.25f

            // A 0.5s one-shot at 1.25x finishes in 0.4s (24 steps at 60 Hz) while standing still.
            repeat(24) { step ->
                motion.update(playback, x, 0f, 0f, DT, grounded = true)
                assertEquals("attack", playback.activeClipId, "step $step (startRunning=$startRunning)")
                assertEquals(1.25f, playback.speed, "step $step (startRunning=$startRunning)")
                assertFalse(playback.isFinished, "before advance on step $step (startRunning=$startRunning)")
                playback.advance(DT)
            }
            assertTrue(playback.isFinished, "completed at 0.4s (startRunning=$startRunning)")

            // On the very next update, locomotion resumes "stand" at rate 1f without waiting STILL_AFTER.
            motion.update(playback, x, 0f, 0f, DT, grounded = true)
            assertEquals("stand", playback.activeClipId, "resumed stand immediately (startRunning=$startRunning)")
            assertEquals("stand", motion.playing, "resynced playing (startRunning=$startRunning)")
            assertEquals(1f, playback.speed, "stand rate is 1f (startRunning=$startRunning)")
            assertTrue(playback.isPlaying, "stand is looping (startRunning=$startRunning)")
        }
    }

    @Test
    fun movingDuringExternalOneShotClipTracksSpeedAndResumesSpeedMatchedRun() {
        val runReference = 6f
        val config = CONFIG.copy(idle = "stand", walkReferenceSpeed = 2f, runReferenceSpeed = runReference)
        val playback = player(mapOf("stand" to 1f, "attack" to 0.5f))
        val motion = LocomotionPlayer(config)
        var x = 0f
        motion.update(playback, x, 0f, 0f, DT, grounded = true)
        repeat(30) {
            motion.update(playback, x, 0f, 0f, DT, grounded = true)
            playback.advance(DT)
        }
        assertEquals("stand", playback.activeClipId)

        playback.play("attack", AnimationPlayback.Once)
        playback.speed = 1.25f

        val travelSpeed = 7.5f
        repeat(24) { step ->
            x += travelSpeed * DT
            motion.update(playback, x, 0f, 0f, DT, grounded = true)
            assertEquals("attack", playback.activeClipId, "step $step")
            assertEquals(1.25f, playback.speed, "step $step")
            playback.advance(DT)
        }
        assertTrue(playback.isFinished)
        assertTrue(motion.groundSpeed > config.runAbove)

        x += travelSpeed * DT
        motion.update(playback, x, 0f, 0f, DT, grounded = true)
        assertEquals("run", playback.activeClipId)
        assertEquals("run", motion.playing)
        assertEquals(motion.groundSpeed / runReference, playback.speed, 1e-5f)
    }

    @Test
    fun externalLoopingClipStaysUntouchedAndManagedHandBackPreservesPlaybackTime() {
        val config = CONFIG.copy(idle = "stand", crossFade = 0f, walkReferenceSpeed = 2f, runReferenceSpeed = 6f)
        val playback = player(mapOf("stand" to 1f, "emote" to 1f))
        val motion = LocomotionPlayer(config)
        var x = 0f
        motion.update(playback, x, 0f, 0f, DT, grounded = true)
        repeat(30) {
            motion.update(playback, x, 0f, 0f, DT, grounded = true)
            playback.advance(DT)
        }
        assertEquals("stand", playback.activeClipId)

        playback.play("emote", AnimationPlayback.Loop)
        playback.speed = 0.75f

        // Across standing, running, and stopping, the external looping clip and its custom speed stay intact.
        repeat(120) { step ->
            if (step in 30 until 90) x += 6f * DT
            motion.update(playback, x, 0f, 0f, DT, grounded = true)
            assertEquals("emote", playback.activeClipId, "step $step")
            assertEquals(0.75f, playback.speed, "step $step")
            assertNull(motion.playing, "step $step")
            playback.advance(DT)
        }

        // Handing back via a managed looping clip adopts it without resetting its clock even at crossFade == 0f.
        playback.play("stand", AnimationPlayback.Loop)
        playback.speed = 1f
        playback.advance(0.3f)
        val standTimeBefore = playback.time
        motion.update(playback, x, 0f, 0f, DT, grounded = true)
        assertEquals("stand", playback.activeClipId)
        assertEquals("stand", motion.playing)
        assertEquals(standTimeBefore, playback.time, 1e-6f)
        assertEquals(1f, playback.speed)
    }

    @Test
    fun externalLoopingClipHandsBackCleanlyAfterFinishingExternalOneShot() {
        val config = CONFIG.copy(idle = "stand", walkReferenceSpeed = 2f, runReferenceSpeed = 6f)
        val playback = player(mapOf("stand" to 1f, "emote" to 1f, "recover" to 0.125f))
        val motion = LocomotionPlayer(config)
        motion.update(playback, 0f, 0f, 0f, DT, grounded = true)
        repeat(30) {
            motion.update(playback, 0f, 0f, 0f, DT, grounded = true)
            playback.advance(DT)
        }

        playback.play("emote", AnimationPlayback.Loop)
        playback.speed = 0.5f
        motion.update(playback, 0f, 0f, 0f, DT, grounded = true)
        assertEquals("emote", playback.activeClipId)

        playback.play("recover", AnimationPlayback.Once)
        playback.speed = 1f
        repeat(8) {
            motion.update(playback, 0f, 0f, 0f, DT, grounded = true)
            playback.advance(DT)
        }
        assertTrue(playback.isFinished)

        motion.update(playback, 0f, 0f, 0f, DT, grounded = true)
        assertEquals("stand", playback.activeClipId)
        assertEquals("stand", motion.playing)
        assertEquals(1f, playback.speed)
    }

    @Test
    fun referenceSpeedUsesActiveClipIdBeforeLocomotionChoosesClipAndWhenWalkEqualsRun() {
        val walkOnlyActive = player()
        walkOnlyActive.play("walk", AnimationPlayback.Loop)
        val motion = LocomotionPlayer(
            LocomotionConfig(idle = null, walk = "walk", run = "run", walkAbove = 3f, runAbove = 5f, walkReferenceSpeed = 2f, runReferenceSpeed = 6f),
        )
        var x = 0f
        motion.update(walkOnlyActive, x, 0f, 0f, DT, grounded = true)
        assertNull(motion.playing)
        assertEquals(0f, walkOnlyActive.speed, 1e-6f)

        repeat(30) {
            x += 1.5f * DT
            motion.update(walkOnlyActive, x, 0f, 0f, DT, grounded = true)
            walkOnlyActive.advance(DT)
        }
        assertNull(motion.playing, "speed stays below walkAbove while idle is unconfigured")
        assertEquals("walk", walkOnlyActive.activeClipId)
        assertEquals(motion.groundSpeed / 2f, walkOnlyActive.speed, 1e-5f)

        // When walk and run share a clip name and only runReferenceSpeed is set, that clip uses runReferenceSpeed.
        val sharedClipPlayer = player(mapOf("stride" to 0.5f))
        val sharedMotion = LocomotionPlayer(
            LocomotionConfig(idle = "idle", walk = "stride", run = "stride", walkAbove = 0.5f, runAbove = 4f, runReferenceSpeed = 5f),
        )
        var sx = 0f
        sharedMotion.update(sharedClipPlayer, sx, 0f, 0f, DT, grounded = true)
        repeat(30) {
            sx += 5f * DT
            sharedMotion.update(sharedClipPlayer, sx, 0f, 0f, DT, grounded = true)
            sharedClipPlayer.advance(DT)
        }
        assertEquals("stride", sharedClipPlayer.activeClipId)
        assertEquals(sharedMotion.groundSpeed / 5f, sharedClipPlayer.speed, 1e-5f)
    }

    private class LocomotionTestDriver(config: LocomotionConfig = CONFIG) {
        val player = player()
        val motion = LocomotionPlayer(config)
        var x = 0f
        private var y = 0f

        init {
            motion.update(player, x, y, 0f, DT)
        }

        fun step(speed: Float, dy: Float = 0f, dt: Float = DT, grounded: Boolean? = null): String? {
            x += speed * dt
            y += dy
            motion.update(player, x, y, 0f, dt, grounded)
            player.advance(dt)
            return motion.playing
        }

        fun move(speed: Float): String? {
            repeat(60) { step(speed) }
            return motion.playing
        }

        fun stand(): String? {
            repeat(30) { step(0f) }
            return motion.playing
        }
    }

    private companion object {
        const val DT = 1f / 60f
        val CONFIG = LocomotionConfig(idle = "idle", walk = "walk", run = "run", jump = "jump", walkAbove = 0.5f)

        fun player(extraDurations: Map<String, Float> = emptyMap()): AnimationPlayer {
            val skeleton = Skeleton(listOf(Bone(Vec3f(), Quat(), Vec3f(1f, 1f, 1f), null, emptyList())), listOf(0))
            val durations = listOf("idle", "walk", "run", "jump", "takeoff", "fall", "land").associateWith { 0.25f } + extraDurations
            val clips = durations.mapValues { (name, seconds) ->
                AnimationClip(name, listOf(AnimationChannel(0, AnimationProperty.Translation, AnimationSampler(floatArrayOf(0f, seconds), FloatArray(6), 3))))
            }
            return AnimationPlayer(AnimationLibrary(skeleton, clips))
        }
    }
}
