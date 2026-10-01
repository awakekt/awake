/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.physics.ragdoll

/** Which limb each index in [humanoidRagdoll]'s output refers to. */
enum class HumanoidLimb {
    /** Root pelvis rigid body. */
    Pelvis,

    /** Main torso / upper torso body segment. */
    Torso,

    /** Head segment above the neck. */
    Head,

    /** Left arm segment between shoulder and elbow. */
    LeftUpperArm,

    /** Left forearm segment between elbow and wrist. */
    LeftLowerArm,

    /** Right arm segment between shoulder and elbow. */
    RightUpperArm,

    /** Right forearm segment between elbow and wrist. */
    RightLowerArm,

    /** Left leg upper segment between hip and knee. */
    LeftThigh,

    /** Left calf/shin segment between knee and ankle. */
    LeftShin,

    /** Right leg upper segment between hip and knee. */
    RightThigh,

    /** Right calf/shin segment between knee and ankle. */
    RightShin,
    ;

    /** Its index in the limb list, which is this enum's own order. */
    val index: Int get() = ordinal
}
