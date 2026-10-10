/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.animation.AnimationChannel
import com.awakekt.awake.core.animation.AnimationClip
import com.awakekt.awake.core.animation.AnimationLibrary
import com.awakekt.awake.core.animation.AnimationPlayer
import com.awakekt.awake.core.animation.AnimationPose
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
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.core.transform.TransformSystem
import com.awakekt.awake.scene.rendering.animation.Animator
import com.awakekt.awake.scene.rendering.animation.ModularCharacterComponent
import com.awakekt.awake.scene.rendering.animation.SkinnedPose
import com.awakekt.awake.scene.rendering.animation.SocketAttachmentComponent
import com.awakekt.awake.scene.rendering.animation.SocketAttachmentSystem
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SocketAttachmentSystemTest {

    @Test
    fun tracksJointTranslationInWorldSpace() {
        val world = World()
        val system = SocketAttachmentSystem()

        // Create character entity with Transform and SkinnedPose
        val character = world.create()
        val charTransform = Transform(position = Vec3f(10f, 0f, 5f))
        world.add(character, charTransform)

        // Joint palette with joint 0 translated by (1f, 2f, 3f)
        val palette = FloatArray(32)
        palette[0] = 1f
        palette[5] = 1f
        palette[10] = 1f
        palette[15] = 1f
        palette[12] = 1f
        palette[13] = 2f
        palette[14] = 3f

        // Joint 1 translated by (4f, 5f, 6f)
        palette[16] = 1f
        palette[21] = 1f
        palette[26] = 1f
        palette[31] = 1f
        palette[28] = 4f
        palette[29] = 5f
        palette[30] = 6f

        world.add(character, SkinnedPose(palette))

        val weapon = world.create()
        val weaponTransform = Transform(position = Vec3f(0f, 0f, 0f))
        world.add(weapon, weaponTransform)
        world.add(
            weapon,
            SocketAttachmentComponent(
                targetEntity = character,
                jointIndex = 0,
                offsetPosition = Vec3f(0.5f, 0f, 0f),
            ),
        )

        system.update(world, 0.016f)

        assertEquals(11.5f, weaponTransform.position.x)
        assertEquals(2.0f, weaponTransform.position.y)
        assertEquals(8.0f, weaponTransform.position.z)

        val attachment = world.get<SocketAttachmentComponent>(weapon)!!
        attachment.jointIndex = 1
        attachment.offsetPosition = Vec3f(0f, 0f, 0f)

        system.update(world, 0.016f)

        assertEquals(14.0f, weaponTransform.position.x)
        assertEquals(5.0f, weaponTransform.position.y)
        assertEquals(11.0f, weaponTransform.position.z)
    }

    @Test
    fun resolvesNamedSocketAndTracksWorldRotation() {
        val world = World()
        val system = SocketAttachmentSystem()
        val character = world.create()
        val charTransform = Transform(position = Vec3f(10f, 0f, 5f))
        world.add(character, charTransform)
        val skeleton = Skeleton(
            bones = listOf(
                Bone(Vec3f.ZERO, Quat.IDENTITY, Vec3f(1f, 1f, 1f), null, listOf(1), "root"),
                Bone(Vec3f(0f, 1f, 0f), Quat.IDENTITY, Vec3f(1f, 1f, 1f), null, emptyList(), "hand_r"),
            ),
            roots = listOf(0),
        )
        world.add(
            character,
            ModularCharacterComponent(
                skin = Skin(listOf(0, 1), listOf(Mat4(), Mat4())),
                skeleton = skeleton,
            ),
        )

        val palette = FloatArray(32) { 0f }
        palette[0] = 1f
        palette[5] = 1f
        palette[10] = 1f
        palette[15] = 1f
        palette[16] = 0f
        palette[17] = 1f
        palette[20] = -1f
        palette[21] = 0f
        palette[26] = 1f
        palette[31] = 1f
        palette[28] = 2f
        palette[29] = 3f
        palette[30] = 3f
        world.add(character, SkinnedPose(palette))

        val weapon = world.create()
        val weaponTransform = Transform()
        world.add(weapon, weaponTransform)
        world.add(
            weapon,
            SocketAttachmentComponent(targetEntity = character, jointName = "hand_r"),
        )

        system.update(world, 0.016f)

        assertEquals(12f, weaponTransform.position.x)
        assertEquals(3f, weaponTransform.position.y)
        assertEquals(8f, weaponTransform.position.z)
        assertEquals(PI.toFloat() / 2f, weaponTransform.rotation.z, 0.001f)
        assertEquals(1, world.get<SocketAttachmentComponent>(weapon)!!.jointIndex)
    }

    /**
     * The issue's case: a shield on the hand of a skinned character that has turned, raised its arm
     * and stands away from the origin. The attached entity's world transform is the hand's world
     * transform with the grip offset applied in the hand's space, within 1 mm.
     */
    @Test
    fun followsAJointOfASkinnedCharacterWithinAMillimetre() {
        val rig = SkinnedRig()
        val shield = rig.attach(SocketAttachmentComponent(rig.character, jointName = "hand", offsetPosition = GRIP, offsetRotation = GRIP_TURN))
        val bare = rig.attach(SocketAttachmentComponent(rig.character, jointIndex = HAND_JOINT))

        rig.raiseArm()
        rig.frame()

        assertMatrixNear(rig.handWorld(), rig.worldOf(bare), "with no offset it is the hand's own world transform")
        assertMatrixNear(rig.onHand(), rig.worldOf(shield), "the grip is in the hand's space")
        assertEquals(HAND_JOINT, rig.world.get<SocketAttachmentComponent>(shield)!!.jointIndex, "the name resolves to the skin's joint order")
    }

    /** The character moves in this frame, after the last transform pass; the attachment moves with it in the same frame. */
    @Test
    fun followsTheCharacterInTheFrameItMoves() {
        val rig = SkinnedRig()
        val shield = rig.attach(SocketAttachmentComponent(rig.character, jointName = "hand", offsetPosition = GRIP, offsetRotation = GRIP_TURN))
        rig.frame()

        rig.world.get<Transform>(rig.character)!!.position.set(-4f, 1f, 7f)
        rig.raiseArm()
        rig.frame()

        assertMatrixNear(rig.onHand(), rig.worldOf(shield))
    }

    /** An attachment parented in the hierarchy, here to the character itself, is given the local transform that puts it on the hand. */
    @Test
    fun aParentedAttachmentIsPutOnTheJointInTheWorld() {
        val rig = SkinnedRig()
        val shield = rig.attach(SocketAttachmentComponent(rig.character, jointName = "hand", offsetPosition = GRIP, offsetRotation = GRIP_TURN))
        rig.world.get<Transform>(shield)!!.parent = rig.character

        rig.raiseArm()
        rig.frame()

        assertMatrixNear(rig.onHand(), rig.worldOf(shield))
    }

    /** The positive control: a target with no pose is not followed, and the attached entity stays where it was put. */
    @Test
    fun aTargetWithNoPoseLeavesTheAttachmentWhereItIs() {
        val world = World()
        val target = world.create()
        world.add(target, Transform(position = Vec3f(3f, 0f, 0f)))
        val attached = world.create()
        val transform = Transform(position = Vec3f(1f, 2f, 3f))
        world.add(attached, transform)
        world.add(attached, SocketAttachmentComponent(target, jointIndex = 0))

        SocketAttachmentSystem().update(world, 0.016f)

        assertEquals(Vec3f(1f, 2f, 3f), transform.position)
    }

    private fun assertMatrixNear(expected: Mat4, actual: Mat4, message: String = "world transform") {
        val position = maxOf(abs(actual.m03 - expected.m03), abs(actual.m13 - expected.m13), abs(actual.m23 - expected.m23))
        assertTrue(position < MILLIMETRE, "$message: position off by $position m, expected below 1 mm\n$actual\nvs\n$expected")
        val basis = (0 until 12).maxOf { abs(actual.data[it] - expected.data[it]) }
        assertTrue(basis < 1e-4f, "$message: rotation and scale off by $basis\n$actual\nvs\n$expected")
    }

    /**
     * A character 10 m along x and 5 along z, turned a quarter about y, with its own [Animator]. Its
     * skeleton's bones are root, spine and hand; its skin lists them hand, root, spine, so a bone index
     * is not a palette index. Each inverse bind matrix undoes its joint's rest transform, as an
     * exporter writes it, so at rest every palette entry is identity.
     */
    private class SkinnedRig {
        val world = World()
        val character: Entity = world.create()
        private val skeleton = Skeleton(
            bones = listOf(
                Bone(Vec3f(0f, 0f, 0f), Quat.IDENTITY, Vec3f(1f, 1f, 1f), null, listOf(1), "root"),
                Bone(Vec3f(0f, 1f, 0f), Quat.IDENTITY, Vec3f(1f, 1f, 1f), null, listOf(2), "spine"),
                Bone(HAND_REST, Quat.IDENTITY, Vec3f(1f, 1f, 1f), null, emptyList(), "hand"),
            ),
            roots = listOf(0),
        )
        private val skin = Skin(
            joints = listOf(2, 0, 1),
            inverseBindMatrices = listOf(translation(-0.5f, -1.4f, 0f), Mat4(), translation(0f, -1f, 0f)),
        )
        private val pose = AnimationPose(skeleton)
        private val systems = listOf(SocketAttachmentSystem(), TransformSystem())

        init {
            world.add(character, Transform(position = Vec3f(10f, 0f, 5f), rotation = Vec3f(0f, QUARTER_TURN, 0f)))
            val still = AnimationSampler(floatArrayOf(0f), floatArrayOf(0f, 0f, 0f), 3)
            val clip = AnimationClip("still", listOf(AnimationChannel(0, AnimationProperty.Translation, still)))
            world.add(character, Animator(AnimationPlayer(AnimationLibrary(skeleton, mapOf("still" to clip))), skin))
            world.add(character, SkinnedPose(pose.jointPalette(skin)))
        }

        fun attach(attachment: SocketAttachmentComponent): Entity = world.create().also {
            world.add(it, Transform())
            world.add(it, attachment)
        }

        /** Bends the spine 60 degrees about z, swinging the hand up and inward, and poses the character as a clip would. */
        fun raiseArm() {
            pose.setBoneTransform(1, Vec3f(0f, 1f, 0f), SPINE_BEND)
            world.get<SkinnedPose>(character)!!.jointPalette = pose.jointPalette(skin)
        }

        /** One frame: the attachments, then the transform pass the renderer reads. */
        fun frame() = systems.forEach { it.update(world, 0.016f) }

        fun worldOf(entity: Entity): Mat4 = world.get<Transform>(entity)!!.worldMatrix

        /** The hand's world transform, from the character's transform and the bones, not from the palette. */
        fun handWorld(): Mat4 {
            val placed = world.get<Transform>(character)!!.localMatrix()
            val spine = Mat4.fromTrs(pose.boneTranslation(1), pose.boneRotation(1), Vec3f(1f, 1f, 1f))
            val hand = Mat4.fromTrs(HAND_REST, Quat.IDENTITY, Vec3f(1f, 1f, 1f))
            return Mat4.multiplyColumnMajor(Mat4.multiplyColumnMajor(placed, spine), hand)
        }

        /** Where the grip puts a shield on the hand: [GRIP] and [GRIP_TURN] in the hand's own space. */
        fun onHand(): Mat4 {
            val grip = Mat4().setEulerTRS(GRIP.x, GRIP.y, GRIP.z, GRIP_TURN.x, GRIP_TURN.y, GRIP_TURN.z, 1f, 1f, 1f)
            return Mat4.multiplyColumnMajor(handWorld(), grip)
        }
    }

    private companion object {
        const val HAND_JOINT = 0
        const val MILLIMETRE = 0.001f
        const val QUARTER_TURN = (PI / 2).toFloat()
        val HAND_REST = Vec3f(0.5f, 0.4f, 0f)
        val GRIP = Vec3f(0f, 0.2f, 0.05f)
        val GRIP_TURN = Vec3f(0f, 0f, QUARTER_TURN)
        val SPINE_BEND = Quat(0f, 0f, sin(PI.toFloat() / 6f), cos(PI.toFloat() / 6f))

        fun translation(x: Float, y: Float, z: Float) = Mat4().apply {
            m03 = x
            m13 = y
            m23 = z
        }
    }
}
