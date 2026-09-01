/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.showcase.examples

import io.github.awakelab.awake.core.math.Mat4
import io.github.awakelab.awake.core.math.Quat
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.physics.BodyHandle
import io.github.awakelab.awake.physics.BoxShape
import io.github.awakelab.awake.physics.CapsuleShape
import io.github.awakelab.awake.physics.MotionType
import io.github.awakelab.awake.scene.physics.ragdoll.Ragdoll
import io.github.awakelab.awake.scene.physics.ragdoll.humanoidRagdoll
import io.github.awakelab.awake.scene.rendering.mesh.InstancedMeshRenderer
import io.github.awakelab.awake.scene.runtime.Scene
import io.github.awakelab.awake.scene.runtime.SceneAppLifecycleRuntime

/**
 * A jointed figure collapsing under gravity, and what its joint limits are for.
 *
 * The one demonstration where the interesting part is what *cannot* happen: every limb is held by
 * a ball-and-socket joint with a swing cone and a twist range, or by a hinge where the real joint
 * is one. Without those a ragdoll still falls and still holds together -- it just folds its
 * shoulders flat against its back and bends its knees the wrong way on the way down, which reads as
 * broken rather than as limp.
 *
 * Drawn as boxes rather than as a skinned character. Driving a real character's bones is what
 * `RagdollSkeleton` is for, and it needs a ragdoll built from that character's own bind pose to
 * look right; this shows the simulation itself, which is the part the joints decide.
 */
internal object RagdollExampleDriver {
    private var ragdoll: Ragdoll? = null
    private var ground: BodyHandle? = null
    private var restSeconds = 0f

    /** One matrix per limb, rewritten in place each frame rather than rebuilt. */
    private val transforms = mutableListOf<Mat4>()

    /** Each limb's box dimensions, from the capsule it stands for. */
    private val extents = mutableListOf<Vec3f>()

    fun attach(instance: Scene, runtime: SceneAppLifecycleRuntime) {
        val node = instance.roots.find { it.name == "ragdoll" } ?: return
        val physics = ShowcasePhysics.world ?: return
        ground = physics.createBody(
            BoxShape(Vec3f(GROUND_HALF_EXTENT, GROUND_THICKNESS, GROUND_HALF_EXTENT)),
            Vec3f(0f, -GROUND_THICKNESS, 0f),
            Quat.IDENTITY,
            MotionType.STATIC,
        )
        spawn()
        runtime.world.add(
            node.entity,
            InstancedMeshRenderer(runtime.requireMesh("cube"), runtime.requireMaterial("lit-shadow"), transforms),
        )
    }

    /**
     * Rewrites each limb's matrix from the simulation.
     *
     * A frame driver, not a fixed one: this only reads what the fixed physics step already wrote,
     * and reading twice in a frame that stepped twice would draw the same thing twice.
     */
    fun advance(delta: Float) {
        val doll = ragdoll ?: return
        doll.forEachLimb { index, position, rotation ->
            transforms[index].setTrs(position, rotation, extents[index])
        }
        // Collapsed and still: drop a fresh one, so the demonstration is the fall rather than the
        // pile it leaves. Held for a moment first, because the settled pose is the thing the joint
        // limits produced and is worth looking at.
        restSeconds = if (doll.isAtRest) restSeconds + delta else 0f
        if (restSeconds > REST_SECONDS_BEFORE_RESPAWN) spawn()
    }

    fun detach() {
        val physics = ShowcasePhysics.world
        ragdoll?.dispose()
        ragdoll = null
        ground?.let { physics?.destroyBody(it) }
        ground = null
        transforms.clear()
        extents.clear()
        restSeconds = 0f
    }

    /** Builds a figure above the floor, replacing whatever was there. */
    private fun spawn() {
        val physics = ShowcasePhysics.world ?: return
        ragdoll?.dispose()
        restSeconds = 0f
        val (limbs, joints) = humanoidRagdoll(height = FIGURE_HEIGHT, origin = Vec3f(0f, DROP_HEIGHT, 0f))
        ragdoll = Ragdoll(physics, limbs, joints)
        // Cleared and refilled, never reassigned: the instanced renderer was handed this exact list
        // at activation and never asks for it again, so a fresh list would leave it drawing the
        // figure that has already fallen. A respawn happens every few seconds, so rebuilding the
        // matrices it holds costs nothing worth guarding against.
        transforms.clear()
        extents.clear()
        limbs.forEach { limb ->
            val capsule = limb.shape as CapsuleShape
            transforms += Mat4()
            extents += Vec3f(
                capsule.radius * 2f,
                (capsule.halfHeight + capsule.radius) * 2f,
                capsule.radius * 2f,
            )
        }
    }

    /** Drops a figure without a scene, so a test can exercise the simulation half on its own. */
    internal fun spawnForTest() = spawn()

    /** The matrices handed to the instanced renderer, for the same reason. */
    internal fun drawnTransforms(): List<Mat4> = transforms

    private const val FIGURE_HEIGHT = 1.8f
    private const val DROP_HEIGHT = 2.5f
    private const val GROUND_HALF_EXTENT = 8f
    private const val GROUND_THICKNESS = 0.5f
    private const val REST_SECONDS_BEFORE_RESPAWN = 2f
}
