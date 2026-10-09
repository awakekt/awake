/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.asset.shaders.ContentFeatureHost
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.shader.SceneShaderEffect
import com.awakekt.awake.scene.shader.ShaderEffectAssets
import com.awakekt.awake.scene.shader.ShaderEffectSystem
import com.awakekt.awake.scene.shader.loadShaderEffects

/**
 * A scene's `shader_effect`s: the shader documents the project ships, drawn through the renderer when
 * it takes content features. A document that does not check out is logged and not drawn.
 */
internal object ShaderEffectsCapability : SceneCapability {
    override val id = "com.awakekt.awake.shader-effects"

    /** The scene's shader documents and their images, as [loadShaderEffects] reads them. */
    val Effects = SceneContentKey<ShaderEffectAssets>("shader effects")

    override suspend fun load(scene: SceneDocument, files: AssetSource, content: SceneContent.Builder) {
        if (scene.uses(SceneShaderEffect::class)) content[Effects] = loadShaderEffects(scene, files)
    }

    override fun plan(scene: SceneDocument, plan: SceneSystemPlan) {
        if (!scene.uses(SceneShaderEffect::class) || !plan.hasRenderer) return
        plan.frame("shader-effects") {
            ShaderEffectSystem(it.renderer as? ContentFeatureHost, it.content[Effects] ?: ShaderEffectAssets.Empty)
        }
    }
}
