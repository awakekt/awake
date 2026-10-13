/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.material.GpuMaterial
import com.awakekt.awake.render.mesh.GpuMesh
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.instancedUniformFloats
import com.awakekt.awake.render.passes.uniformFloats
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.passes.uniforms.ParticleExtraUniformLayout
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.passes.uniforms.SpriteUniformLayout
import com.awakekt.awake.render.passes.uniforms.pbrMaterialFloats
import com.awakekt.awake.render.passes.uniforms.sceneLightUniforms
import com.awakekt.awake.render.pipeline.InstancedDrawKind
import com.awakekt.awake.webgpu.device.drivePendingWork
import com.awakekt.awake.webgpu.fastArrayBufferOf
import com.awakekt.awake.webgpu.writeBufferData
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUMapMode
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.test.assertContentEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Asserts that [UniformUploads] writes each buffer exactly the bytes the direct per-draw write
 * gave it, for every kind of draw block the shared writers pack, over two frames on one stage.
 *
 * Every block is written twice: straight from its own array to one buffer, as draws were before
 * staging, and staged to another. Enough blocks are staged to grow the stage past its first size,
 * so the second frame shows it reused rather than reallocated.
 *
 * Positive control: starting each staged block one float late (`starts[block] + 1` in
 * `writeStaged`) fails the comparison at block 0. The non-constant check below keeps that true: a
 * block whose floats were all equal would read the same shifted by one.
 */
internal suspend fun GPUDevice.assertStagedUniformsMatchDirectWrites() {
    val blocks = List(REPEATS) { mixedDrawBlocks(it) }.flatten()
    assertTrue(blocks.all { block -> block.toList().zipWithNext().any { (a, b) -> a != b } }, "a constant block cannot catch a shifted write")
    val uploads = UniformUploads()
    repeat(FRAMES) { frame ->
        val frameBlocks = if (frame == 0) blocks else blocks.map { block -> FloatArray(block.size) { block[it] + frame } }
        val direct = frameBlocks.map { readableBuffer(it.size) }
        val staged = frameBlocks.map { readableBuffer(it.size) }
        try {
            frameBlocks.forEachIndexed { index, block ->
                queue.writeBufferData(direct[index], 0uL, fastArrayBufferOf(block))
                uploads.stage(staged[index], block)
            }
            val floats = uploads.floats
            val starts = uploads.starts
            uploads.flush(queue)
            queue.submit(emptyList())
            if (frame > 0) {
                assertSame(floats, uploads.floats, "a frame the stage already fits must not reallocate it")
                assertSame(starts, uploads.starts)
            }
            direct.indices.forEach { index ->
                assertContentEquals(readBack(direct[index]), readBack(staged[index]), "frame $frame block $index")
            }
        } finally {
            (direct + staged).forEach { it.close() }
        }
    }
}

/** One block per kind of draw [prepareGpuDraw] uploads, each packed by the shared writer it uses. */
private fun mixedDrawBlocks(seed: Int): List<FloatArray> {
    val eye = Vec3f(1f + seed, 4f, 6f)
    val light = sceneLightUniforms(SceneLight(direction = Vec3f(0.3f, 0.8f, 0.5f), color = Vec3f(1f, 0.9f, 0.7f)), eye).packed
    val viewProjection = Mat4().apply { data.indices.forEach { data[it] = it * 0.25f + seed } }
    fun draw(format: VertexFormat, extras: FloatArray = FloatArray(0)) = RenderDrawCommand(
        mesh = FakeMesh(format),
        material = FakeMaterial,
        model = Mat4().apply { data[12] = seed.toFloat(); data[13] = 2f; data[14] = -3f },
        extraUniformFloats = extras,
    )
    fun single(format: VertexFormat, floats: Int, extras: FloatArray = FloatArray(0)) = draw(format, extras).uniformFloats(
        materialUniformFloatCount = floats,
        viewProjection = viewProjection,
        cameraEye = eye,
        lightPayload = light,
        cameraForward = Vec3f(0f, 0f, -1f),
    )
    fun instanced(kind: InstancedDrawKind, format: VertexFormat, floats: Int, extras: FloatArray = FloatArray(0)) =
        draw(format, extras).instancedUniformFloats(
            kind = kind,
            viewProjection = viewProjection,
            lightPayload = light,
            cameraEye = eye,
            materialUniformFloatCount = floats,
            cameraForward = Vec3f(0f, 0f, -1f),
        )
    val pbr = pbrMaterialFloats(metallic = 0.2f, roughness = 0.7f, baseColorFactor = Color(0.9f, 0.5f, 0.3f, 1f), emissiveFactor = Color.Transparent)
    return listOf(
        single(VertexFormat.PositionNormalColor, MaterialUniformLayouts.LitShadow.total),
        single(VertexFormat.PositionNormalColorUv, MaterialUniformLayouts.PbrTextured.total, pbr),
        single(VertexFormat.PositionUv, SpriteUniformLayout.total, floatArrayOf(1f, 1f, 0.5f, 0f, 1f, 0.8f, 0.6f, 1f)),
        single(VertexFormat.PositionNormalColorSkin, 0),
        single(VertexFormat.PositionNormalColor, MaterialUniformLayouts.Lit.total),
        instanced(InstancedDrawKind.Plain, VertexFormat.PositionNormalColor, MaterialUniformLayouts.LitShadow.total),
        instanced(InstancedDrawKind.Particle, VertexFormat.PositionUv, 0, FloatArray(ParticleExtraUniformLayout.total) { it + 0.5f }),
    )
}

private fun GPUDevice.readableBuffer(floats: Int): GPUBuffer = createBuffer(
    BufferDescriptor(size = (floats * Float.SIZE_BYTES).toULong(), usage = GPUBufferUsage.CopyDst or GPUBufferUsage.MapRead),
)

private suspend fun GPUDevice.readBack(buffer: GPUBuffer): ByteArray = coroutineScope {
    val mapping = async { buffer.mapAsync(GPUMapMode.Read) }
    drivePendingWork { mapping.isCompleted }
    mapping.await().getOrThrow()
    buffer.getMappedRange().toByteArray().also { buffer.unmap() }
}

private class FakeMesh(override val format: VertexFormat) : GpuMesh

private object FakeMaterial : GpuMaterial {
    override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
    override fun destroy() = Unit
}

private const val REPEATS = 50
private const val FRAMES = 2
