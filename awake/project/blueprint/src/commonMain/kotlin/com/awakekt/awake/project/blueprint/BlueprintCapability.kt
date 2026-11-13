/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.blueprint

import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.project.runtime.SceneCapability
import com.awakekt.awake.project.runtime.SceneContent
import com.awakekt.awake.project.runtime.SceneSystemPlan
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.document.SceneDocument

/**
 * [BlueprintFeature] under its name before the rename of features to features, kept for one release.
 * A manifest whose `capabilityClass` names this class still loads; it plans exactly what
 * [BlueprintFeature] does.
 */
@Suppress("DEPRECATION")
@Deprecated("Renamed to BlueprintFeature", ReplaceWith("BlueprintFeature", "com.awakekt.awake.project.blueprint.BlueprintFeature"))
object BlueprintCapability : SceneCapability {
    /** The id a manifest's `plugins` entry names this feature by. */
    const val ID: String = BlueprintFeature.ID

    override val id: String = BlueprintFeature.ID

    override val components: List<SceneComponentBinding<*, *>> = BlueprintFeature.components

    override val systemNames: List<String> = BlueprintFeature.systemNames

    override suspend fun load(scene: SceneDocument, files: AssetSource, content: SceneContent.Builder) =
        BlueprintFeature.load(scene, files, content)

    override fun plan(scene: SceneDocument, plan: SceneSystemPlan) = BlueprintFeature.plan(scene, plan)
}
