/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.platform

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.engine.platform.dsl.AppSpecBuilder
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.renderer.LineSegment
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.render.texture.TextureAsset
import kotlin.test.Test
import kotlin.test.assertEquals

class GraphicsEngineSurfaceTest {

    @Test
    fun losingTheSurfaceKeepsTheRendererAndTheApp() {
        val events = mutableListOf<String>()
        val app = AppSpecBuilder().apply {
            ready { events += "ready" }
            pause { events += "pause" }
            resume { events += "resume" }
            dispose { events += "dispose" }
        }.build().createLifecycle()
        val engine = RecordingEngine(app, events)

        engine.create("surface-1")
        engine.releaseSurface()
        engine.releaseSurface()
        engine.restoreSurface("surface-2")
        engine.releaseSurface()
        engine.dispose()

        assertEquals(
            listOf(
                "backend surface-1", "ready",
                "pause", "release",
                "restore surface-2", "resume",
                "pause", "release",
                "dispose", "destroy",
            ),
            events,
        )
    }

    @Test
    fun aHostDensityReachesTheAppsFrames() {
        val densities = mutableListOf<Float>()
        val app = AppSpecBuilder().apply { render { densities += it.density } }.build().createLifecycle()
        val engine = RecordingEngine(app, mutableListOf())
        engine.create("surface")

        engine.update(0f)
        engine.setDensity(2.625f)
        engine.update(0f)

        assertEquals(listOf(1f, 2.625f), densities)
    }

    private class RecordingEngine(
        app: com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle,
        private val events: MutableList<String>,
    ) : GraphicsEngine(app) {
        override suspend fun createBackendResources(window: Any): BackendResources {
            events += "backend $window"
            return BackendResources(renderer = NoopRenderer, viewportSize = { 1f to 1f })
        }

        override fun releaseBackendSurface() {
            events += "release"
        }

        override fun restoreBackendSurface(window: Any) {
            events += "restore $window"
        }

        override fun destroyBackend() {
            events += "destroy"
        }
    }

    private object NoopRenderer : Renderer {
        override val clipSpace: ClipSpace = ClipSpace.WebGpu
        override var clearColor: Color = Color.Black
        override var wireframe: Boolean = false

        override fun createMesh(geometry: MeshGeometry): Mesh = object : Mesh {
            override val format: VertexFormat = geometry.format
            override val sizeBytes: Long = 0
            override fun destroy() = Unit
        }

        override fun createMaterial(
            texture: TextureAsset?,
            renderTarget: RenderTarget?,
            uniformFloatCount: Int,
            pbrTextures: PbrTextureSet?,
        ): Material = object : Material {
            override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
            override fun destroy() = Unit
        }

        override fun createRenderTarget(width: Int, height: Int): RenderTarget = object : RenderTarget {
            override val width: Int = width
            override val height: Int = height
            override fun destroy() = Unit
        }

        override suspend fun readPixels(target: RenderTarget): TextureAsset = TextureAsset(ByteArray(0), 0, 0)
        override fun drawUi(primitives: List<UiDrawPrimitive>, font: UiFont?) = Unit
        override fun drawDebugLines(lines: List<LineSegment>) = Unit
        override fun destroy() = Unit
    }
}
