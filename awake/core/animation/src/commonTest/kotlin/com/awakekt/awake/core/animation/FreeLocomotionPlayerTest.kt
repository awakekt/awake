/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.animation

import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import kotlin.math.exp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 3D free-motion configuration, clip selection, speed matching, and mode-transition tests for [LocomotionPlayer]. */
class FreeLocomotionPlayerTest {
    @Test
    fun freeLocomotionConfigurationValidatesThresholdsFadeAndReferenceSpeeds() {
        val invalid = listOf<() -> FreeLocomotionConfig>(
            { FreeLocomotionConfig(moveAbove = -0.1f) },
            { FreeLocomotionConfig(moveAbove = 2f, fastAbove = 1f) },
            { FreeLocomotionConfig(verticalAbove = 0f) },
            { FreeLocomotionConfig(verticalAbove = -0.5f) },
            { FreeLocomotionConfig(crossFade = -0.01f) },
            { FreeLocomotionConfig(moveReferenceSpeed = 0f) },
            { FreeLocomotionConfig(moveReferenceSpeed = -1f) },
            { FreeLocomotionConfig(fastReferenceSpeed = 0f) },
            { FreeLocomotionConfig(fastReferenceSpeed = -2f) },
        )
        invalid.forEach { create -> assertFailsWith<IllegalArgumentException> { create() } }

        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            val nonFinite = listOf<() -> FreeLocomotionConfig>(
                { FreeLocomotionConfig(moveAbove = value) },
                { FreeLocomotionConfig(fastAbove = value) },
                { FreeLocomotionConfig(verticalAbove = value) },
                { FreeLocomotionConfig(crossFade = value) },
                { FreeLocomotionConfig(moveReferenceSpeed = value) },
                { FreeLocomotionConfig(fastReferenceSpeed = value) },
            )
            nonFinite.forEach { create -> assertFailsWith<IllegalArgumentException> { create() } }
        }

        val valid = FreeLocomotionConfig(moveAbove = 0f, fastAbove = 0f, verticalAbove = 0.01f, crossFade = 0f)
        assertFailsWith<IllegalArgumentException> { valid.copy(fastReferenceSpeed = 0f) }

        val withFree = CONFIG.copy(free = FREE_CONFIG)
        assertEquals(FREE_CONFIG, withFree.copy(walkAbove = 0.25f).free)
    }

    @Test
    fun freeMotionSelectsHoverGlideAndBoostAcross3dTrajectoriesWithSpeedMatching() {
        val config = CONFIG.copy(free = FREE_CONFIG)
        val mover = FreeMotionTestDriver(config)
        assertNull(mover.motion.playing, "initial observation does not pick a clip")

        // Stationary settle selects "hover" at unscaled rate 1f.
        assertEquals("hover", mover.hover())
        assertEquals(0f, mover.motion.speed)
        assertEquals(1f, mover.player.speed)
        assertFalse(mover.motion.playingOnce)

        // Pure vertical ascent selects "glide" and scales playback by smoothed 3D speed / moveReferenceSpeed (3f).
        var expected3d = 0f
        repeat(60) {
            mover.step(vy = 2.4f)
            expected3d += (2.4f - expected3d) * (1f - exp(-DT / 0.1f))
        }
        assertEquals("glide", mover.motion.playing)
        assertEquals(expected3d, mover.motion.speed, 2e-4f)
        assertEquals(0f, mover.motion.groundSpeed, 1e-6f)
        assertEquals(mover.motion.speed / 3f, mover.player.speed, 1e-5f)
        assertFalse(mover.motion.playingOnce)

        // Pure vertical descent above fastAbove (4f) selects "boost" scaled by fastReferenceSpeed (8f).
        repeat(60) { mover.step(vy = -9f) }
        assertEquals("boost", mover.motion.playing)
        assertEquals(mover.motion.speed / 8f, mover.player.speed, 1e-5f)

        // Diagonal XYZ travel where planar speed (3f) is below fastAbove (4f) while 3D speed (5f) exceeds it.
        repeat(60) { mover.step(vx = 1.8f, vy = 4f, vz = 2.4f) }
        assertEquals("boost", mover.motion.playing)
        assertTrue(mover.motion.groundSpeed < FREE_CONFIG.fastAbove)
        assertTrue(mover.motion.speed > FREE_CONFIG.fastAbove)
        assertEquals(mover.motion.speed / 8f, mover.player.speed, 1e-5f)

        // Brief pause (< STILL_AFTER) holds "boost", then settling past STILL_AFTER returns to "hover" at 1f.
        val stopping = List(30) { mover.step() }
        assertEquals(listOf("boost", "hover"), stopping.filterIndexed { i, v -> i == 0 || v != stopping[i - 1] })
        assertEquals(0f, mover.motion.speed)
        assertEquals(1f, mover.player.speed)
    }

    @Test
    fun freeMotionSelectsDirectionalAscendAndDescendAndSharedMoveFastReferenceSpeed() {
        val directional = FreeLocomotionConfig(
            idle = "hover",
            move = "glide",
            fast = "boost",
            ascend = "rise",
            descend = "dive",
            moveAbove = 0.1f,
            fastAbove = 6f,
            verticalAbove = 0.5f,
            moveReferenceSpeed = 3f,
            fastReferenceSpeed = 8f,
        )
        val mover = FreeMotionTestDriver(CONFIG.copy(free = directional))
        mover.hover()

        // Pure vertical climb selects "rise" at authored rate 1f.
        repeat(45) { mover.step(vy = 2f) }
        assertEquals("rise", mover.motion.playing)
        assertEquals(1f, mover.player.speed)

        // Steep diagonal climb (abs(vy) >= groundSpeed) stays "rise".
        repeat(45) { mover.step(vx = 1.5f, vy = 2f) }
        assertEquals("rise", mover.motion.playing)

        // Pure and steep diagonal dive select "dive" at authored rate 1f.
        repeat(45) { mover.step(vx = 1f, vy = -2.2f, vz = 1f) }
        assertEquals("dive", mover.motion.playing)
        assertEquals(1f, mover.player.speed)

        // Shallow pitched horizontal glide (verticalSpeed > verticalAbove, yet groundSpeed > abs(verticalSpeed)) selects "glide".
        repeat(60) { mover.step(vx = 3f, vy = 1f) }
        assertEquals("glide", mover.motion.playing)
        assertEquals(mover.motion.speed / 3f, mover.player.speed, 1e-5f)

        // Fast vertical climb above fastAbove selects "boost" over "rise".
        repeat(60) { mover.step(vy = 8f) }
        assertEquals("boost", mover.motion.playing)
        assertEquals(mover.motion.speed / 8f, mover.player.speed, 1e-5f)

        // Shared move/fast clip with only fastReferenceSpeed set uses fastReferenceSpeed; null move falls back to idle.
        val sharedPlayer = player(mapOf("hover" to 0.5f, "swim" to 0.5f))
        val shared = LocomotionPlayer(
            LocomotionConfig(
                free = FreeLocomotionConfig(idle = "hover", move = "swim", fast = "swim", fastReferenceSpeed = 5f),
            ),
        )
        var z = 0f
        shared.update(sharedPlayer, 0f, 0f, z, DT, freeMotion = true)
        repeat(30) {
            z += 2f * DT
            shared.update(sharedPlayer, 0f, 0f, z, DT, freeMotion = true)
            sharedPlayer.advance(DT)
        }
        assertEquals("swim", shared.playing)
        assertEquals(shared.speed / 5f, sharedPlayer.speed, 1e-5f)

        val hoverOnly = FreeMotionTestDriver(LocomotionConfig(free = FreeLocomotionConfig(idle = "hover", move = null)))
        repeat(30) { hoverOnly.step(vx = 2f) }
        assertEquals("hover", hoverOnly.motion.playing)
    }

    @Test
    fun freeCrossFadeOverridesBaseCrossFadeAndNullFallsBackToBase() {
        fun sampleGlideTransitionX(freeCrossFade: Float?, baseCrossFade: Float): Float {
            val skeleton = Skeleton(listOf(Bone(Vec3f(), Quat(), Vec3f(1f, 1f, 1f), null, emptyList())), listOf(0))
            val hoverClip = AnimationClip(
                "hover",
                listOf(AnimationChannel(0, AnimationProperty.Translation, AnimationSampler(floatArrayOf(0f, 1f), floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f), 3))),
            )
            val glideClip = AnimationClip(
                "glide",
                listOf(AnimationChannel(0, AnimationProperty.Translation, AnimationSampler(floatArrayOf(0f, 1f), floatArrayOf(10f, 0f, 0f, 10f, 0f, 0f), 3))),
            )
            val animPlayer = AnimationPlayer(AnimationLibrary(skeleton, mapOf("hover" to hoverClip, "glide" to glideClip)))
            val motion = LocomotionPlayer(
                LocomotionConfig(
                    crossFade = baseCrossFade,
                    free = FreeLocomotionConfig(idle = "hover", move = "glide", crossFade = freeCrossFade),
                ),
            )
            motion.update(animPlayer, 0f, 0f, 0f, DT, freeMotion = true)
            repeat(10) {
                motion.update(animPlayer, 0f, 0f, 0f, DT, freeMotion = true)
                animPlayer.update(DT)
            }
            assertEquals("hover", animPlayer.activeClipId)
            motion.update(animPlayer, 2f * 0.1f, 0f, 0f, 0.1f, freeMotion = true)
            return animPlayer.update(0.1f).boneTranslation(0).x
        }

        // free.crossFade = 0f overrides base 0.4f -> immediate jump to 10f.
        assertEquals(10f, sampleGlideTransitionX(freeCrossFade = 0f, baseCrossFade = 0.4f), 1e-4f)
        // free.crossFade = 0.4f overrides base 0f -> 0.1s / 0.4s = 25% of 10f = 2.5f.
        assertEquals(2.5f, sampleGlideTransitionX(freeCrossFade = 0.4f, baseCrossFade = 0f), 1e-4f)
        // free.crossFade = null falls back to base 0.4f -> 2.5f.
        assertEquals(2.5f, sampleGlideTransitionX(freeCrossFade = null, baseCrossFade = 0.4f), 1e-4f)
    }

    @Test
    fun teleportSpikesAlongYOrDiagonalPreserveFreeGlideAndPlaybackRate() {
        val mover = FreeMotionTestDriver(CONFIG.copy(free = FREE_CONFIG.copy(ascend = "rise")))
        repeat(60) { mover.step(vx = 2.4f) }
        assertEquals("glide", mover.motion.playing)
        val steadySpeed = mover.motion.speed

        // Pure-Y warp in a single frame neither switches to "boost"/"rise" nor spikes speed.
        mover.y += 50f
        mover.motion.update(mover.player, mover.x, mover.y, mover.z, DT, grounded = false, freeMotion = true)
        assertEquals("glide", mover.motion.playing)
        assertEquals(steadySpeed, mover.motion.speed, 1e-6f)
        assertEquals(steadySpeed / 3f, mover.player.speed, 1e-6f)

        // Diagonal XYZ warp also leaves smoothed 3D speed, groundSpeed, and active clip untouched.
        val groundBeforeDiagonalWarp = mover.motion.groundSpeed
        mover.x += 30f
        mover.y -= 30f
        mover.z += 30f
        mover.motion.update(mover.player, mover.x, mover.y, mover.z, DT, grounded = false, freeMotion = true)
        assertEquals("glide", mover.motion.playing)
        assertEquals(steadySpeed, mover.motion.speed, 1e-6f)
        assertEquals(groundBeforeDiagonalWarp, mover.motion.groundSpeed, 1e-6f)
        assertEquals(steadySpeed / 3f, mover.player.speed, 1e-6f)

        // High fastAbove raises warp threshold so legitimate high-speed flight above MIN_WARP_SPEED (30f) is tracked.
        val highSpeedConfig = LocomotionConfig(
            free = FreeLocomotionConfig(idle = "hover", move = "glide", fast = "boost", fastAbove = 12f, fastReferenceSpeed = 20f),
        )
        val fastMover = FreeMotionTestDriver(highSpeedConfig)
        repeat(60) { fastMover.step(vx = 36f) }
        assertEquals("boost", fastMover.motion.playing)
        assertTrue(fastMover.motion.speed > 30f)
        assertEquals(fastMover.motion.speed / 20f, fastMover.player.speed, 1e-5f)
    }

    @Test
    fun externalClipYieldsAndResumesCleanlyDuringFreeMotion() {
        val config = CONFIG.copy(free = FREE_CONFIG)
        val playback = player(mapOf("hover" to 1f, "glide" to 0.5f, "boost" to 0.5f, "attack" to 0.5f))
        val motion = LocomotionPlayer(config)
        var x = 0f
        var y = 0f
        motion.update(playback, x, y, 0f, DT, grounded = false, freeMotion = true)
        repeat(30) {
            motion.update(playback, x, y, 0f, DT, grounded = false, freeMotion = true)
            playback.advance(DT)
        }
        assertEquals("hover", playback.activeClipId)

        playback.play("attack", AnimationPlayback.Once)
        playback.speed = 1.25f

        // Accelerate diagonally in 3D during the 24-frame one-shot attack.
        repeat(24) { step ->
            x += 5f * DT
            y += 5f * DT
            motion.update(playback, x, y, 0f, DT, grounded = false, freeMotion = true)
            assertEquals("attack", playback.activeClipId, "step $step")
            assertEquals(1.25f, playback.speed, "step $step")
            playback.advance(DT)
        }
        assertTrue(playback.isFinished)
        assertTrue(motion.speed > FREE_CONFIG.fastAbove)

        // Resumes speed-matched "boost" on the next update.
        x += 5f * DT
        y += 5f * DT
        motion.update(playback, x, y, 0f, DT, grounded = false, freeMotion = true)
        assertEquals("boost", playback.activeClipId)
        assertEquals("boost", motion.playing)
        assertEquals(motion.speed / FREE_CONFIG.fastReferenceSpeed!!, playback.speed, 1e-5f)

        // Returning to a free-motion clip while stationary also resets playback speed to 1f after an external one-shot.
        repeat(30) {
            motion.update(playback, x, y, 0f, DT, grounded = false, freeMotion = true)
            playback.advance(DT)
        }
        assertEquals("hover", playback.activeClipId)
        playback.play("attack", AnimationPlayback.Once)
        playback.speed = 1.25f
        repeat(24) {
            motion.update(playback, x, y, 0f, DT, grounded = false, freeMotion = true)
            playback.advance(DT)
        }
        assertTrue(playback.isFinished)
        motion.update(playback, x, y, 0f, DT, grounded = false, freeMotion = true)
        assertEquals("hover", playback.activeClipId)
        assertEquals(1f, playback.speed)
    }

    @Test
    fun switchingBetweenGroundedAndFreeModesNeverMisfiresTakeOffOrLand() {
        val config = CONFIG.copy(
            takeOff = "takeoff",
            fall = "fall",
            land = "land",
            free = FREE_CONFIG,
        )
        val playback = player(mapOf("hover" to 0.5f, "glide" to 0.5f, "boost" to 0.5f))
        val motion = LocomotionPlayer(config)
        var x = 0f
        var y = 0f

        // 1. Standing on ground -> toggle freeMotion = true transitions immediately to "hover" on that frame.
        motion.update(playback, x, y, 0f, DT, grounded = true, freeMotion = false)
        repeat(15) {
            motion.update(playback, x, y, 0f, DT, grounded = true, freeMotion = false)
            playback.advance(DT)
        }
        assertEquals("idle", motion.playing)

        motion.update(playback, x, y, 0f, DT, grounded = true, freeMotion = true)
        assertEquals("hover", motion.playing)
        assertFalse(motion.playingOnce)

        // Toggle back to grounded on ground -> immediately "idle", no "land" one-shot.
        motion.update(playback, x, y, 0f, DT, grounded = true, freeMotion = false)
        assertEquals("idle", motion.playing)
        assertFalse(motion.playingOnce)

        // 2. Start jumping ("takeoff" one-shot) -> toggle freeMotion = true cancels takeOff immediately.
        y += 0.1f
        motion.update(playback, x, y, 0f, DT, grounded = false, freeMotion = false)
        assertEquals("takeoff", motion.playing)
        assertTrue(motion.playingOnce)

        motion.update(playback, x, y, 0f, DT, grounded = false, freeMotion = true)
        assertFalse(motion.playingOnce)
        assertEquals("glide", motion.playing)

        // 3. Hovering in mid-air in free mode -> toggle freeMotion = false while grounded = false:
        // enters "jump" immediately WITHOUT firing "takeoff", nor on subsequent airborne frames, then lands cleanly.
        repeat(20) {
            motion.update(playback, x, y, 0f, DT, grounded = false, freeMotion = true)
            playback.advance(DT)
        }
        assertEquals("hover", motion.playing)

        motion.update(playback, x, y, 0f, DT, grounded = false, freeMotion = false)
        assertEquals("jump", motion.playing)
        assertTrue(motion.playingOnce)

        y -= 0.1f
        motion.update(playback, x, y, 0f, DT, grounded = false, freeMotion = false)
        assertEquals("fall", motion.playing)

        y -= 0.001f
        motion.update(playback, x, y, 0f, DT, grounded = true, freeMotion = false)
        assertEquals("land", motion.playing)

        // 4. Leaving free mode resets player.speed to 1f when only free had reference speeds configured,
        // including when toggled on an intermediate sub-step where position did not change yet.
        x += 2.4f * DT
        repeat(30) {
            x += 2.4f * DT
            motion.update(playback, x, y, 0f, DT, grounded = true, freeMotion = true)
            playback.advance(DT)
        }
        assertEquals("glide", motion.playing)
        assertTrue(playback.speed < 1f && playback.speed > 0.5f)

        motion.update(playback, x, y, 0f, DT * 0.5f, grounded = true, freeMotion = false)
        assertEquals("walk", motion.playing)
        assertEquals(1f, playback.speed)
    }

    @Test
    fun freeMotionWithoutFreeConfigFallsBackToIdleWalkRunBy3dSpeedWithoutAirborneClips() {
        val config = CONFIG.copy(
            takeOff = "takeoff",
            fall = "fall",
            land = "land",
            walkReferenceSpeed = 2f,
            runReferenceSpeed = 6f,
            free = null,
        )
        val mover = FreeMotionTestDriver(config)
        assertEquals("idle", mover.hover())
        assertEquals(1f, mover.player.speed)

        // Pure vertical climb at 2.5f selects "walk" (not "takeoff"/"jump") and scales by 3D speed / walkReferenceSpeed.
        repeat(60) { mover.step(vy = 2.5f) }
        assertEquals("walk", mover.motion.playing)
        assertFalse(mover.motion.playingOnce)
        assertEquals(mover.motion.speed / 2f, mover.player.speed, 1e-5f)

        // Pure vertical descent at 7f selects "run" (not "fall") and scales by 3D speed / runReferenceSpeed.
        repeat(60) { mover.step(vy = -7f) }
        assertEquals("run", mover.motion.playing)
        assertFalse(mover.motion.playingOnce)
        assertEquals(mover.motion.speed / 6f, mover.player.speed, 1e-5f)
    }

    @Test
    fun nonFiniteCoordinatesAndDeltaStepsLeaveStateAndPlaybackUntouched() {
        val config = CONFIG.copy(
            takeOff = "takeoff",
            walkReferenceSpeed = 2f,
            runReferenceSpeed = 6f,
            free = FREE_CONFIG,
        )
        val playback = player(mapOf("hover" to 0.5f, "glide" to 0.5f, "boost" to 0.5f))
        val motion = LocomotionPlayer(config)
        var x = 0f
        var y = 0f
        motion.update(playback, x, y, 0f, DT, grounded = true)
        repeat(30) {
            x += 2f * DT
            motion.update(playback, x, y, 0f, DT, grounded = true)
            playback.advance(DT)
        }
        val expectedGround = motion.groundSpeed
        val expectedSpeed = playback.speed
        assertEquals("walk", motion.playing)

        for (bad in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            motion.update(playback, bad, y, 0f, DT, grounded = true)
            motion.update(playback, x, bad, 0f, DT, grounded = true)
            motion.update(playback, x, y, bad, DT, grounded = true)
            motion.update(playback, x, y, 0f, bad, grounded = true)
            motion.update(playback, bad, bad, bad, bad, grounded = false, freeMotion = true)
        }
        assertEquals("walk", motion.playing)
        assertEquals(expectedGround, motion.groundSpeed, 1e-6f)
        assertEquals(expectedSpeed, playback.speed, 1e-6f)

        // Subsequent valid airborne take-off still counts down onceLeft cleanly without NaN lockup.
        y += 0.1f
        motion.update(playback, x, y, 0f, DT, grounded = false)
        assertEquals("takeoff", motion.playing)
        motion.update(playback, x, y, 0f, Float.NaN, grounded = false)
        repeat(20) {
            y += 0.1f
            motion.update(playback, x, y, 0f, DT, grounded = false)
            playback.advance(DT)
        }
        assertEquals("jump", motion.playing)
    }

    @Test
    fun sharedClipsBetweenAirborneOnceAndFreeLoopFlipPlaybackModeAndRestartWhenFinished() {
        val config = LocomotionConfig(
            idle = "idle",
            walk = "walk",
            run = "run",
            jump = "float",
            fall = "dive",
            crossFade = 0.2f,
            free = FreeLocomotionConfig(
                idle = "float",
                move = "glide",
                descend = "dive",
                crossFade = 0.2f,
            ),
        )
        val playback = player(mapOf("float" to 0.2f, "glide" to 0.5f, "dive" to 0.2f))
        val motion = LocomotionPlayer(config)
        var y = 0f

        motion.update(playback, 0f, y, 0f, DT, grounded = false, freeMotion = false)
        y += 0.1f
        motion.update(playback, 0f, y, 0f, DT, grounded = false, freeMotion = false)
        assertEquals("float", motion.playing)
        assertTrue(motion.playingOnce)

        // Advance stationary airborne frames past clip duration so "float" finishes its single-shot hold.
        repeat(20) {
            motion.update(playback, 0f, y, 0f, DT, grounded = false, freeMotion = false)
            playback.advance(DT)
        }
        assertTrue(playback.isFinished)

        // Switching to freeMotion = true while stationary selects free.idle = "float" as Loop and restarts it even with crossFade > 0f.
        motion.update(playback, 0f, y, 0f, DT, grounded = false, freeMotion = true)
        assertEquals("float", motion.playing)
        assertFalse(motion.playingOnce)
        assertFalse(playback.isFinished)
        repeat(20) {
            motion.update(playback, 0f, y, 0f, DT, grounded = false, freeMotion = true)
            playback.advance(DT)
        }
        assertFalse(playback.isFinished, "free.idle loops continuously past clip duration")

        // Descend in free mode to engage looping "dive", then exit free mode into airborne fall ("dive" as Once).
        repeat(30) {
            y -= 2f * DT
            motion.update(playback, 0f, y, 0f, DT, grounded = false, freeMotion = true)
            playback.advance(DT)
        }
        assertEquals("dive", motion.playing)
        assertFalse(motion.playingOnce)

        y -= 2f * DT
        motion.update(playback, 0f, y, 0f, DT, grounded = false, freeMotion = false)
        assertEquals("dive", motion.playing)
        assertTrue(motion.playingOnce)
        repeat(20) { playback.advance(DT) }
        assertTrue(playback.isFinished, "airborne fall holds its final pose once finished")
    }

    @Test
    fun exitingFreeDescentWithHeightInferredGroundingLandsImmediatelyOnTouchdown() {
        val config = CONFIG.copy(fall = "fall", land = "land", free = FREE_CONFIG)
        val playback = player(mapOf("hover" to 0.5f, "glide" to 0.5f, "boost" to 0.5f))
        val motion = LocomotionPlayer(config)
        var y = 10f

        motion.update(playback, 0f, y, 0f, DT, grounded = null, freeMotion = true)
        repeat(20) {
            y -= 5f * DT
            motion.update(playback, 0f, y, 0f, DT, grounded = null, freeMotion = true)
            playback.advance(DT)
        }
        assertEquals("boost", motion.playing)

        // Exit freeMotion while still descending faster than airborneAbove (grounded == null).
        y -= 5f * DT
        motion.update(playback, 0f, y, 0f, DT, grounded = null, freeMotion = false)
        assertEquals("fall", motion.playing)

        // Stopping vertical descent past STILL_AFTER immediately triggers "land" without waiting LANDED_AFTER (0.25s).
        repeat(7) {
            motion.update(playback, 0f, y, 0f, DT, grounded = null, freeMotion = false)
            playback.advance(DT)
        }
        assertEquals("land", motion.playing)
        assertTrue(motion.playingOnce)
    }

    private class FreeMotionTestDriver(config: LocomotionConfig) {
        val player = player(
            mapOf(
                "hover" to 0.5f,
                "glide" to 0.5f,
                "boost" to 0.5f,
                "rise" to 0.5f,
                "dive" to 0.5f,
            ),
        )
        val motion = LocomotionPlayer(config)
        var x = 0f
        var y = 0f
        var z = 0f

        init {
            motion.update(player, x, y, z, DT, grounded = false, freeMotion = true)
        }

        fun step(vx: Float = 0f, vy: Float = 0f, vz: Float = 0f, dt: Float = DT): String? {
            x += vx * dt
            y += vy * dt
            z += vz * dt
            motion.update(player, x, y, z, dt, grounded = false, freeMotion = true)
            player.advance(dt)
            return motion.playing
        }

        fun hover(): String? {
            repeat(30) { step() }
            return motion.playing
        }
    }

    private companion object {
        const val DT = 1f / 60f
        val CONFIG = LocomotionConfig(idle = "idle", walk = "walk", run = "run", jump = "jump", walkAbove = 0.5f)
        val FREE_CONFIG = FreeLocomotionConfig(
            idle = "hover",
            move = "glide",
            fast = "boost",
            moveReferenceSpeed = 3f,
            fastReferenceSpeed = 8f,
        )

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
