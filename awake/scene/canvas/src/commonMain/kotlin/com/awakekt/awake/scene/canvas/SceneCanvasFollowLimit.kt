/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.core.schema.PropertyRange
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneBinding
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import com.awakekt.awake.ui.canvas.CanvasFollowLimit
import com.awakekt.awake.ui.canvas.CanvasFollowRank
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The saved form of a [CanvasFollowLimit]: the most followers of one group the scene's canvas draws
 * at once, keeping the most important. A scene has one for each group it limits, each on any node.
 *
 * @property group The `followRules.group` it limits; empty limits the followers that name none.
 * @property max The most of them drawn at once; 0 for no limit.
 * @property rankBy Which are kept: `Distance`, the nearest; `Priority`, the highest `followRules.priority`
 * and then the nearest; or `Score`, the highest score game code sets and then the nearest.
 */
@Serializable
@SerialName("canvas_follow_limit")
@SceneBinding(CanvasFollowLimit::class)
data class SceneCanvasFollowLimit(
    val group: String = "",
    @PropertyRange(min = 0.0) val max: Int = 0,
    val rankBy: CanvasFollowRank = CanvasFollowRank.Distance,
) : SceneComponent {
    override val allowsMultiplePerNode: Boolean get() = false

    override fun validate(path: String): List<SceneValidationIssue> =
        CanvasFollowLimit(group, max, rankBy).problems().map { SceneValidationIssue(path, "canvas_follow_limit.$it") }
}

/** Every [CanvasFollowLimit] on this world's nodes, in the order the world stores them. */
fun World.canvasFollowLimits(): List<CanvasFollowLimit> {
    val limits = family<CanvasFollowLimit>()
    if (limits.size == 0) return emptyList()
    return ArrayList<CanvasFollowLimit>(limits.size).also { all -> limits.forEachComponent { all += it } }
}
