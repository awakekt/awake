/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderdocument

import com.awakekt.awake.asset.shaderdsl.AslBlockBuilder
import com.awakekt.awake.asset.shaderdsl.AslExpr
import com.awakekt.awake.asset.shaderdsl.AslFragmentBuilder
import com.awakekt.awake.asset.shaderdsl.AslLayoutHandles
import com.awakekt.awake.asset.shaderdsl.AslShaderBuilder
import com.awakekt.awake.asset.shaderdsl.AslShaderDefinition
import com.awakekt.awake.asset.shaderdsl.AslSwizzle
import com.awakekt.awake.asset.shaderdsl.AslVaryingRef
import com.awakekt.awake.asset.shaderdsl.AslVaryings
import com.awakekt.awake.asset.shaderdsl.AslVertexBuilder
import com.awakekt.awake.asset.shaderdsl.cross
import com.awakekt.awake.asset.shaderdsl.fieldsFrom
import com.awakekt.awake.asset.shaderdsl.fullScreenTriangleCorner
import com.awakekt.awake.asset.shaderdsl.inputsFrom
import com.awakekt.awake.asset.shaderdsl.lit
import com.awakekt.awake.asset.shaderdsl.minus
import com.awakekt.awake.asset.shaderdsl.ndcToUv
import com.awakekt.awake.asset.shaderdsl.normalize
import com.awakekt.awake.asset.shaderdsl.plus
import com.awakekt.awake.asset.shaderdsl.sampler
import com.awakekt.awake.asset.shaderdsl.shader
import com.awakekt.awake.asset.shaderdsl.texture2d
import com.awakekt.awake.asset.shaderdsl.times
import com.awakekt.awake.asset.shaderdsl.unprojectFarRay
import com.awakekt.awake.asset.shaderdsl.vec3
import com.awakekt.awake.asset.shaderdsl.vec4
import com.awakekt.awake.asset.shaderdsl.x
import com.awakekt.awake.asset.shaderdsl.xy
import com.awakekt.awake.asset.shaderdsl.xyz
import com.awakekt.awake.asset.shaderdsl.y
import com.awakekt.awake.asset.shaderdsl.zw
import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.geometry.VertexSemantic
import com.awakekt.awake.core.math.ClipSpace

/** The shader's name in the emitted WGSL. A constant, so no document string reaches the source. */
internal const val SHADER_NAME = "shader_document"

/**
 * Lowers a checked document into a whole ASL shader for one clip space: the uniform block, the
 * textures and sampler, the varyings the fragment stage reads, and both stages with their prologues.
 */
internal class DocumentLowering(private val checked: CheckedDocument, private val fields: DocumentUniformFields) {
    private val document = checked.document
    private val plane = document.surface == ShaderSurface.Plane

    fun lower(clipSpace: ClipSpace): AslShaderDefinition = shader(SHADER_NAME) {
        val uniforms = uniformBlock("Uniforms", MATERIAL_GROUP, UNIFORM_BINDING).fieldsFrom(fields.layout())
        val textures = document.textures.withIndex().associate { (i, texture) -> texture.name to textureSlot(i) }
        val sampler = if (textures.isEmpty()) null else samplerSlot()
        val varyings = declareVaryings(this)
        val names = NameSource()
        val stage = StageContext(uniforms, varyings, clipSpace, textures, sampler, names)
        vertex { if (plane) planeVertex(stage) else fullScreenVertex(varyings) }
        fragment { fragmentStage(stage) }
    }

    private fun declareVaryings(builder: AslShaderBuilder): Varyings {
        val reads = checked.fragmentUsage.inputs
        val out = builder.varyings("VertexOutput")
        val ndc = if (!plane && reads.any { it in NDC_INPUTS }) ndcVarying(out) else null
        val uv = if (plane && ShaderInput.Uv in reads) uvVarying(out) else null
        val world = if (plane && reads.any { it in WORLD_INPUTS }) worldVarying(out) else null
        return Varyings(out.position, ndc, uv, world)
    }

    private fun AslVertexBuilder.fullScreenVertex(varyings: Varyings) {
        val corner = fullScreenTriangleCorner()
        // A background sits on the far plane, as a sky does; neither surface tests depth.
        val depth = if (document.surface == ShaderSurface.Background) 1f else 0f
        varyings.position set vec4(corner, depth.lit, 1f.lit)
        varyings.ndc?.let { it set corner }
    }

    private fun AslVertexBuilder.planeVertex(stage: StageContext) {
        val vertexInputs = inputsFrom(VertexFormat.PositionUv)
        val position = vertexInputs.input(VertexSemantic.Position)
        val uv = vertexInputs.input(VertexSemantic.Uv)
        val model = stage.uniforms.value("model")
        val inputs = { input: ShaderInput ->
            when (input) {
                ShaderInput.Uv -> uv
                ShaderInput.WorldPosition -> AslSwizzle(model * vec4(position, 1f.lit), "xyz")
                else -> uniformInput(input, stage.uniforms)
            }
        }
        val lowering = ExpressionLowering(stage.names, prologue(this, checked.vertexUsage, inputs, stage, "vertex"))
        val scope = Scope<LoweredLocal>()
        val vertex = document.vertex
        lowering.lowerBlock(this, vertex?.statements.orEmpty(), scope, discard = null)
        val displaced = vertex?.displacement?.let { d ->
            let("displaced", position + vec3(0f.lit, lowering.lower(this, d, scope), 0f.lit))
        } ?: position
        val world = let("worldPoint", model * vec4(displaced, 1f.lit))
        stage.varyings.position set stage.uniforms.value("viewProjection") * world
        stage.varyings.uv?.let { it set uv }
        stage.varyings.world?.let { it set world.xyz }
    }

    private fun AslFragmentBuilder.fragmentStage(stage: StageContext) {
        val inputs = { input: ShaderInput -> fragmentInput(input, stage) }
        val lowering = ExpressionLowering(stage.names, prologue(this, checked.fragmentUsage, inputs, stage, "fragment"))
        if (!plane) {
            // A full-screen vertex stage reads no uniform, so a fragment stage that reads none either
            // would leave the block out of the pipeline's bindings, and WebGPU rejects a content
            // feature whose group has no uniform buffer. Reading the clock keeps it in.
            let("frameClock", stage.uniforms.value("clock"))
        }
        val scope = Scope<LoweredLocal>()
        lowering.lowerBlock(this, document.fragment.statements, scope) { condition -> discardIf(condition) }
        colorOutput(lowering.lower(this, document.fragment.color, scope))
    }

    private fun AslFragmentBuilder.fragmentInput(input: ShaderInput, stage: StageContext): AslExpr {
        val uniforms = stage.uniforms
        val cameraPosition = uniforms.value("cameraPosition")
        return when {
            plane -> when (input) {
                ShaderInput.Uv -> requireNotNull(stage.varyings.uv)
                ShaderInput.WorldPosition -> requireNotNull(stage.varyings.world)
                ShaderInput.ViewDirection -> normalize(requireNotNull(stage.varyings.world) - cameraPosition.xyz)
                ShaderInput.ScreenUv -> position().xy * uniforms.value("viewport").zw
                else -> uniformInput(input, uniforms)
            }
            input == ShaderInput.Uv || input == ShaderInput.ScreenUv -> ndcToUv(requireNotNull(stage.varyings.ndc), stage.clipSpace)
            input == ShaderInput.ViewDirection ->
                unprojectFarRay(uniforms.value("inverseViewProjection"), cameraPosition, requireNotNull(stage.varyings.ndc))
            else -> uniformInput(input, uniforms)
        }
    }

    /**
     * Binds, at the top of a stage, each parameter and input the stage reads, so the document's
     * expressions read them as names. Parameters and inputs come in declaration order, so the
     * output does not depend on the order they were first read in.
     */
    private fun prologue(
        block: AslBlockBuilder,
        usage: StageUsage,
        inputValue: (ShaderInput) -> AslExpr,
        stage: StageContext,
        stageName: String,
    ): StageValues {
        val parameters = document.parameters.withIndex()
            .filter { (i, _) -> i in usage.parameters }
            .associate { (i, parameter) ->
                // One parameter is a plain vec4f field: ASL declares an array only for a count above 1.
                val element = if (document.parameters.size == 1) stage.uniforms.value("params") else stage.uniforms.array("params")[i]
                val value = if (parameter.type.shape == ValueShape.Vec4) element else AslSwizzle(element, "xyzw".take(parameter.type.shape.components))
                parameter.name to block.let(stage.names.next("p"), value)
            }
        val inputs = ShaderInput.entries.filter { it in usage.inputs }
            .associateWith { input -> block.let("${stageName}_${input.serialName}", inputValue(input)) }
        return StageValues(parameters, inputs, stage.textures, stage.sampler)
    }
}

/** An input every stage reads straight from the uniform block. */
private fun uniformInput(input: ShaderInput, uniforms: AslLayoutHandles): AslExpr = when (input) {
    ShaderInput.CameraPosition -> uniforms.value("cameraPosition").xyz
    ShaderInput.SunDirection -> uniforms.value("sunDirection").xyz
    ShaderInput.Time -> uniforms.value("clock").x
    ShaderInput.DeltaTime -> uniforms.value("clock").y
    ShaderInput.Resolution -> uniforms.value("viewport").xy
    ShaderInput.Normal -> planeNormal(uniforms.value("model"))
    else -> error("input '${input.serialName}' is not read from the uniform block")
}

/** The plane's world-space normal: the cross product of its transformed Z and X axes, which is +Y before any transform. */
private fun planeNormal(model: AslExpr): AslExpr {
    val z = AslSwizzle(model * vec4(0f.lit, 0f.lit, 1f.lit, 0f.lit), "xyz")
    val x = AslSwizzle(model * vec4(1f.lit, 0f.lit, 0f.lit, 0f.lit), "xyz")
    return normalize(cross(z, x))
}

/** What both stages of one lowering share. */
private class StageContext(
    val uniforms: AslLayoutHandles,
    val varyings: Varyings,
    val clipSpace: ClipSpace,
    val textures: Map<String, AslExpr>,
    val sampler: AslExpr?,
    val names: NameSource,
)

/** The varyings one lowering declared: only those its fragment stage reads, since ASL rejects one that is written and never read. */
private class Varyings(val position: AslVaryingRef, val ndc: AslVaryingRef?, val uv: AslVaryingRef?, val world: AslVaryingRef?)

/** Inputs a full-screen fragment stage reads through the normalized device coordinate. */
private val NDC_INPUTS = setOf(ShaderInput.Uv, ShaderInput.ScreenUv, ShaderInput.ViewDirection)

/** Inputs a plane's fragment stage reads through the world position. */
private val WORLD_INPUTS = setOf(ShaderInput.WorldPosition, ShaderInput.ViewDirection)

// Fixed-name slots. ASL names a texture, a sampler or a varying after the Kotlin property it is
// delegated to, so each slot is one property; the names are this module's, never a document's.

private fun AslShaderBuilder.textureSlot(index: Int): AslExpr = when (index) {
    0 -> {
        val t0 by texture2d(MATERIAL_GROUP, FIRST_TEXTURE_BINDING)
        t0
    }
    1 -> {
        val t1 by texture2d(MATERIAL_GROUP, FIRST_TEXTURE_BINDING + 1)
        t1
    }
    2 -> {
        val t2 by texture2d(MATERIAL_GROUP, FIRST_TEXTURE_BINDING + 2)
        t2
    }
    3 -> {
        val t3 by texture2d(MATERIAL_GROUP, FIRST_TEXTURE_BINDING + 3)
        t3
    }
    else -> error("a document has at most $TEXTURE_SLOTS textures")
}

private fun AslShaderBuilder.samplerSlot(): AslExpr {
    val textureSampler by sampler(MATERIAL_GROUP, SAMPLER_BINDING)
    return textureSampler
}

private fun ndcVarying(out: AslVaryings): AslVaryingRef {
    val ndc by out.varying(GpuDataShape.Vec2, location = 0)
    return ndc
}

private fun uvVarying(out: AslVaryings): AslVaryingRef {
    val uv by out.varying(GpuDataShape.Vec2, location = 0)
    return uv
}

private fun worldVarying(out: AslVaryings): AslVaryingRef {
    val worldPosition by out.varying(GpuDataShape.Vec3, location = 1)
    return worldPosition
}
