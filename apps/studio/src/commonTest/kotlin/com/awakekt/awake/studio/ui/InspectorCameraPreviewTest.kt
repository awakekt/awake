/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.studio.ui

import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.editor.scene.viewport.SceneCameraPreview
import com.awakekt.awake.render.renderer.RenderViewport
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.rendering.Camera
import com.awakekt.awake.studio.state.StudioStore
import com.awakekt.awake.studio.studioHostResources
import kotlin.test.Test
import kotlin.test.assertEquals

/** Renderer double with real [sceneViewport] storage -- the contract's own accessors ignore
 * writes (see `Renderer.sceneViewport`), so a plain [NoopRenderer] cannot observe the borrow. */
private class ViewportRecordingRenderer : NoopRenderer() {
    override var sceneViewport: RenderViewport? = null
}

private fun studioCamera() = Lens(
    eye = Vec3f(0f, 5f, 10f),
    center = Vec3f(0f, 0f, 0f),
    fovYRadians = 0.8f,
    near = 0.1f,
    far = 100f,
)

class InspectorCameraPreviewTest {

    /** The preview quad lives in the viewport's own corner overlay now (`StudioShell.kt`), not
     * the Inspector panel, and is always on once the scene has its own (non-primary) camera --
     * no selection required (see `StudioEditorCamera`'s own doc comment: forcing a click first
     * just to preview the only OTHER camera in the scene was the reported bug this replaced).
     * `drawStudioShellBody` is the smallest accessible entry point that still exercises the real
     * quad. `isPrimary = false` on [cameraEntity] mirrors the real app's shape -- the viewport's
     * own (primary) camera is a separate, persistent editor camera Studio creates itself, never
     * one of these test-authored entities. */
    @Test
    fun aNonPrimaryCameraEntityAlwaysGetsAPreviewQuadRegardlessOfSelection() {
        val world = World()
        val cameraEntity = world.create()
        world.add(cameraEntity, Name("camera"))
        world.add(cameraEntity, Camera(studioCamera(), isPrimary = false))
        val cubeEntity = world.create()
        world.add(cubeEntity, Name("Cube"))

        val preview = SceneCameraPreview()
        preview.render(ViewportRecordingRenderer(), studioCamera(), emptyList())
        val store = StudioStore()

        run {
            val host = StudioTestHost(world, ViewportRecordingRenderer())
            fun textureQuads(): Int = host.frame {
                StudioShell(
                    store,
                    backend = "Vulkan",
                    resources = studioHostResources(cameraPreview = preview),
                )
            }.primitives.count { it is UiDrawPrimitive.Texture }

            assertEquals(
                1,
                textureQuads(),
                "a non-primary camera entity must show its preview with nothing selected",
            )
            assertEquals(
                1,
                textureQuads(),
                "a second frame must not hide the preview",
            )
        }
    }

    /** The shell points `sceneViewport` at the viewport panel in window coordinates, and Vulkan
     * applies that rect to `renderToTexture` too -- leaving it cleared would silently confine the
     * next frame's main scene pass to the whole surface. */
    @Test
    fun thePreviewPassRestoresTheSceneViewportItBorrowed() {
        val renderer = ViewportRecordingRenderer()
        val panel = RenderViewport(x = 200f, y = 80f, width = 900f, height = 600f)
        renderer.sceneViewport = panel

        SceneCameraPreview().render(renderer, studioCamera(), emptyList())

        assertEquals(panel, renderer.sceneViewport)
    }
}
