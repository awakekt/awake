/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.shaders

import io.github.awakelab.awake.render.pipeline.UiShaderSet

/**
 * The shaders the engine's own capability passes draw with -- the four UI pipelines and debug
 * lines. Not content: nothing here answers "what is being drawn", only "how does a quad, a glyph
 * or a line reach the screen".
 *
 * Declared once because both engines used to spell them out independently, as twelve
 * `const val ..._SHADER_RESOURCE_PATH` strings -- Vulkan naming its `.vert.spv`/`.frag.spv` pair
 * and WebGPU naming its `.wgsl`, with nothing tying the two halves of one shader together. Each
 * backend deciding which shader its own UI pass uses is a duplicated decision, which is the
 * shape that shipped WebGPU without an alpha-blended pipeline for as long as Vulkan had one.
 *
 * A backend reads only its own half; see [ShaderSet].
 */
object EngineShaderSets {
    val UiQuad = aslShaderSet(UiQuadShader)
    val UiGlyph = aslShaderSet(UiGlyphShader)
    val UiTexture = aslShaderSet(UiTextureShader)
    val UiRoundedQuad = aslShaderSet(UiRoundedQuadShader)
    val UiTargetComposite = aslShaderSet(UiTargetCompositeShader)
    val DebugLine = aslShaderSet(DebugLineShader)
}

/**
 * The UI pass's four shaders, each loaded by [load] into whatever a backend holds them as.
 *
 * *Which* four is one decision, made here; *how* one loads is the backend's, because the payloads
 * genuinely differ -- Vulkan wants a SPIR-V vertex/fragment pair, WebGPU one WGSL module. Both
 * used to spell out the same four slots, which is the duplicated decision that shipped WebGPU
 * without an alpha-blended pipeline for as long as Vulkan had one.
 *
 * A fifth UI shader is added to [EngineShaderSets] and to `UiShaderSet` -- never to one backend.
 */
suspend fun <T> uiShaderSet(load: suspend (ShaderSet) -> T): UiShaderSet<T> = UiShaderSet(
    quad = load(EngineShaderSets.UiQuad),
    glyph = load(EngineShaderSets.UiGlyph),
    texture = load(EngineShaderSets.UiTexture),
    roundedQuad = load(EngineShaderSets.UiRoundedQuad),
    targetComposite = load(EngineShaderSets.UiTargetComposite),
)
