/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.examples.ragdoll

/** Which limb each index in [humanoidRagdoll]'s output refers to. */
enum class HumanoidLimb {
    Pelvis,
    Torso,
    Head,
    LeftUpperArm,
    LeftLowerArm,
    RightUpperArm,
    RightLowerArm,
    LeftThigh,
    LeftShin,
    RightThigh,
    RightShin,
    ;

    /** Its index in the limb list, which is this enum's own order. */
    val index: Int get() = ordinal
}
