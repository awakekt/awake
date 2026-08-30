/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.vulkan

import io.github.awakelab.awake.asset.shadercompiler.NagaShaderCompiler
import io.github.awakelab.awake.asset.shaderpack.PackShaderSets
import io.github.awakelab.awake.asset.shaders.EngineShaderSets
import io.github.awakelab.awake.asset.shaders.ShaderSet
import io.github.awakelab.awake.asset.shaders.ShaderStage
import io.github.awakelab.awake.asset.shaders.resolveBytes
import io.github.awakelab.awake.core.host.readResourceBytes
import io.github.awakelab.awake.vulkan.pipeline.UiShaderPairs
import io.github.awakelab.awake.vulkan.pipeline.ShaderPair

/**
 * Every shipped shader, by the name a test asks for.
 *
 * All of them carry their WGSL inline now, so a name lookup reaches the set rather than a file.
 * The remaining `wgslShaderPair` path exists for this module's own test probes, which are still
 * `.wgsl` under `desktopTest/resources`.
 */
private val SHIPPED_SETS = mapOf(
    "ui_quad" to EngineShaderSets.UiQuad,
    "ui_glyph" to EngineShaderSets.UiGlyph,
    "ui_texture" to EngineShaderSets.UiTexture,
    "ui_rounded_quad" to EngineShaderSets.UiRoundedQuad,
    "ui_target_composite" to EngineShaderSets.UiTargetComposite,
    "debug_line" to EngineShaderSets.DebugLine,
    "triangle" to PackShaderSets.Triangle,
    "lit_shadow" to PackShaderSets.LitShadow,
    "shadow_depth" to PackShaderSets.ShadowDepth,
    "textured" to PackShaderSets.Textured,
    "instanced" to PackShaderSets.Instanced,
    "skinned" to PackShaderSets.Skinned,
    "skinned_textured" to PackShaderSets.SkinnedTextured,
    "skinned_instanced" to PackShaderSets.SkinnedInstanced,
    "skybox" to PackShaderSets.Skybox,
    "particle" to PackShaderSets.Particle,
)

/**
 * Loads the shader called [name] and compiles it for a headless test, the way
 * `VulkanShaderResolver` does at runtime.
 *
 * Shipped shaders are WGSL on both backends now, so a test cannot read `.spv` bytes straight off
 * the classpath any more. One module carries both entry points, so the pair holds it twice and
 * the entry point names pick the stage.
 *
 * Shared rather than copied: fourteen test files had grown their own identical loader, and each
 * would otherwise need this same change.
 */
suspend fun packShaderPair(name: String): ShaderPair =
    SHIPPED_SETS[name]?.let { packShaderPair(it) }
        ?: wgslShaderPair("assets/shader/vulkan/$name.wgsl")

/** [packShaderPair] for a [ShaderSet], whose Vulkan half may be inline text or a resource. */
suspend fun packShaderPair(shaderSet: ShaderSet): ShaderPair {
    val source = checkNotNull(shaderSet.vulkan[ShaderStage.VERTEX]) {
        "Shader set declares no Vulkan vertex stage."
    }
    return spirvPair(source.resolveBytes().decodeToString())
}

/** [packShaderPair] for a shader addressed by full resource path. */
suspend fun wgslShaderPair(path: String): ShaderPair =
    spirvPair(readResourceBytes(path).decodeToString())

private fun spirvPair(wgsl: String): ShaderPair =
    NagaShaderCompiler.wgslToSpirv(wgsl).let { ShaderPair(it, it) }

/**
 * The four UI shaders every headless fixture passes to `Renderer` and none of them exercises --
 * the constructor requires them, the UI pass never runs in a 3D pixel test.
 */
suspend fun defaultUiShaderPairs(): UiShaderPairs = UiShaderPairs(
    quad = packShaderPair("ui_quad"),
    glyph = packShaderPair("ui_glyph"),
    texture = packShaderPair("ui_texture"),
    roundedQuad = packShaderPair("ui_rounded_quad"),
)
