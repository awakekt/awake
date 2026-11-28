/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.scene2d

import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.motion.GroundContact
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.core.transform.TransformSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpriteLocomotionTest {
    private val spriteSchema = SceneSprite("hero.png", columns = 8, rows = 2)
    private val clipsSchema = SceneSpriteClips(
        clips = linkedMapOf(
            "idle" to SceneSpriteClip(firstFrame = 0, frameCount = 4, framesPerSecond = 6f),
            "walk" to SceneSpriteClip(firstFrame = 4, frameCount = 4, framesPerSecond = 10f),
            "run" to SceneSpriteClip(firstFrame = 8, frameCount = 4, framesPerSecond = 14f),
            "jump" to SceneSpriteClip(firstFrame = 12, frameCount = 2, framesPerSecond = 8f, loop = false),
            "fall" to SceneSpriteClip(firstFrame = 14, frameCount = 2, framesPerSecond = 8f, loop = false),
            "attack" to SceneSpriteClip(firstFrame = 12, frameCount = 2, framesPerSecond = 4f, loop = false),
        ),
        clip = "idle",
    )
    private val locomotionSchema = SceneSpriteLocomotion(
        idle = "idle",
        walk = "walk",
        run = "run",
        jump = "jump",
        fall = "fall",
        walkAbove = 0.2f,
        runAbove = 5f,
    )

    @Test
    fun selectsIdleWalkRunJumpFallAndFlipsFacingOnChildSprite() {
        val world = World()
        val transforms = TransformSystem()
        val locomotion = SpriteLocomotionSystem()
        val clipSystem = SpriteClipSystem()

        val parent = world.create()
        val parentTransform = Transform()
        val ground = GroundContact(grounded = true)
        world.add(parent, parentTransform)
        world.add(parent, ground)

        val child = world.create()
        val childTransform = Transform().apply { this.parent = parent }
        val sprite = Sprite(spriteSchema)
        val clips = SpriteClips(clipsSchema)
        val driver = SpriteLocomotion(locomotionSchema)
        world.add(child, childTransform)
        world.add(child, sprite)
        world.add(child, clips)
        world.add(child, driver)

        fun step(dx: Float, dy: Float, delta: Float = 0.05f) {
            parentTransform.position.x += dx
            parentTransform.position.y += dy
            transforms.update(world, delta)
            locomotion.update(world, delta)
            clipSystem.update(world, delta)
        }

        // First observation establishes initial position and idle clip.
        step(0f, 0f)
        assertEquals("idle", clips.activeClipId)
        assertFalse(sprite.flipX)

        // Walk right (+X at 2 units/sec).
        repeat(6) { step(0.1f, 0f) }
        assertEquals("walk", clips.activeClipId)
        assertFalse(sprite.flipX)

        // Sprint left (-X at 8 units/sec) flips sprite left and switches to run.
        repeat(8) { step(-0.4f, 0f) }
        assertEquals("run", clips.activeClipId)
        assertTrue(sprite.flipX)

        // Jump upward while off ground keeps left facing and plays jump.
        ground.grounded = false
        step(0f, 0.4f)
        assertEquals("jump", clips.activeClipId)
        assertTrue(sprite.flipX)

        // Descend while off ground switches to fall.
        step(0f, -0.4f)
        assertEquals("fall", clips.activeClipId)

        // Land and stop returns to idle while preserving last facing direction.
        ground.grounded = true
        repeat(4) { step(0f, 0f) }
        assertEquals("idle", clips.activeClipId)
        assertTrue(sprite.flipX)
    }

    @Test
    fun yieldsToUnmanagedOneShotClipUntilFinished() {
        val world = World()
        val transforms = TransformSystem()
        val locomotion = SpriteLocomotionSystem()
        val clipSystem = SpriteClipSystem()

        val entity = world.create()
        val transform = Transform()
        val sprite = Sprite(spriteSchema)
        val clips = SpriteClips(clipsSchema)
        world.add(entity, transform)
        world.add(entity, sprite)
        world.add(entity, clips)
        world.add(entity, SpriteLocomotion(locomotionSchema))

        transforms.update(world, 0.05f)
        locomotion.update(world, 0.05f)
        clipSystem.update(world, 0.05f)

        // Trigger an unmanaged 0.5s attack clip (2 frames @ 4 fps).
        clips.play("attack", restart = true)
        transform.position.x += 0.2f
        transforms.update(world, 0.1f)
        locomotion.update(world, 0.1f)
        clipSystem.update(world, 0.1f)
        assertEquals("attack", clips.activeClipId, "locomotion yields while an unmanaged clip plays")

        // Once the attack finishes, locomotion resumes cleanly.
        repeat(6) {
            transform.position.x += 0.2f
            transforms.update(world, 0.1f)
            locomotion.update(world, 0.1f)
            clipSystem.update(world, 0.1f)
        }
        assertEquals("walk", clips.activeClipId)
    }

    @Test
    fun validationRequiresSpriteAndSpriteClipsAndFiniteThresholds() {
        assertTrue(locomotionSchema.validate("hero", listOf(locomotionSchema)).any { "requires a sprite" in it.message })
        assertTrue(locomotionSchema.validate("hero", listOf(locomotionSchema, spriteSchema)).any { "requires sprite_clips" in it.message })
        assertEquals(emptyList(), locomotionSchema.validate("hero", listOf(locomotionSchema, spriteSchema, clipsSchema)))
        assertTrue(locomotionSchema.copy(walkAbove = 5f, runAbove = 2f).validate("hero").isNotEmpty())
        assertTrue(locomotionSchema.copy(airborneAbove = 0f).validate("hero").isNotEmpty())
    }
}
