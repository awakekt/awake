/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.animation

/**
 * Rebinds this clip, authored against [from], onto [to] by joint name: each channel moves to the
 * bone of [to] that has the same name as its bone in [from]. A channel whose bone is unnamed, or
 * has no namesake in [to], is dropped, so the clip leaves that bone at its bind pose. Keyframe
 * values are copied unchanged; the two skeletons are expected to share proportions, since a
 * translation is not rescaled between differently sized rigs.
 *
 * @param from The skeleton this clip's channel bone indices address.
 * @param to The skeleton the returned clip targets.
 * @return A clip with the same name and keyframes whose channels address [to].
 */
fun AnimationClip.retarget(from: Skeleton, to: Skeleton): AnimationClip {
    if (from === to) return this
    val targetByName = HashMap<String, Int>(to.bones.size)
    to.bones.forEachIndexed { index, bone -> bone.name?.let { targetByName.getOrPut(it) { index } } }
    val retargeted = channels.mapNotNull { channel ->
        val name = from.bones.getOrNull(channel.targetBone)?.name ?: return@mapNotNull null
        val target = targetByName[name] ?: return@mapNotNull null
        if (target == channel.targetBone) channel else channel.copy(targetBone = target)
    }
    return AnimationClip(name, retargeted)
}
