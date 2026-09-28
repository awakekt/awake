/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase

import com.awakekt.awake.asset.shaderpack.PackShaderSets
import com.awakekt.awake.asset.shaderpack.litShadowShader
import com.awakekt.awake.asset.shaders.aslShaderSet
import com.awakekt.awake.asset.shaders.program
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.logging.Logger
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.pipeline.ShaderProgram
import com.awakekt.awake.render.pipeline.ShaderReplacement
import com.awakekt.awake.render.pipeline.ShaderReplacementException
import com.awakekt.awake.render.pipeline.ShaderSource
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.scene.authoring.SceneAppDsl
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.runtime.SceneSystemHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Replaces the lit shader every mesh here draws with, while the showcase runs:
 * - [TOGGLE_KEY] swaps between the shipped shader and a brighter-ambient variant built at runtime;
 * - [BROKEN_KEY] tries a variant that does not compile, which is refused and logged while the
 *   current shader keeps drawing.
 *
 * Vulkan only: on a backend without [ShaderReplacement] the keys do nothing.
 */
internal class ShowcaseShaderSwap(
    private val renderer: () -> Renderer,
    private val input: () -> GameplayInput,
) : System {
    private val log = Logger("showcase-shaders")

    // Unconfined, so a replacement runs here, in this frame system, between frames.
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val shipped = PackShaderSets.LitShadow.vulkan.program()
    private val brighter by lazy {
        aslShaderSet { litShadowShader(it, ambientStrength = BRIGHT_AMBIENT) }.vulkan.program()
    }
    private val broken by lazy {
        val wgsl = (shipped.vertex as ShaderSource.InlineText).sourceCode + "\nfn broken( {\n"
        shipped.copy(vertex = ShaderSource.InlineText(wgsl, "vertexMain"), fragment = ShaderSource.InlineText(wgsl, "fragmentMain"))
    }
    private var running = shipped

    override fun update(world: World, delta: Float) {
        val keys = input()
        val next = when {
            keys.wasPressed(TOGGLE_KEY) -> if (running == shipped) brighter else shipped
            keys.wasPressed(BROKEN_KEY) -> broken
            else -> return
        }
        val replacement = renderer().capability(ShaderReplacement) ?: return
        scope.launch { replace(replacement, next) }
    }

    private suspend fun replace(replacement: ShaderReplacement, next: ShaderProgram) {
        try {
            val pipelines = replacement.replace(running, next)
            running = next
            log.info { "Replaced the lit shader in $pipelines pipeline(s)." }
        } catch (e: ShaderReplacementException) {
            log.warn { "Kept the current lit shader: ${e.message}" }
        }
    }

    companion object {
        val TOGGLE_KEY = Key.L
        val BROKEN_KEY = Key.K
        private const val BRIGHT_AMBIENT = 0.6f
    }
}

/** L swaps the lit shader for a variant built at runtime; K tries a broken one. */
internal fun SceneAppDsl.shaderSwapSystem(): SceneSystemHandle<ShowcaseShaderSwap> = frameSystem("shader-swap") {
    ShowcaseShaderSwap(
        renderer = { renderer },
        input = { GameplayInput(requireService(Input::class).currentSnapshot, uiOwnership) },
    )
}
