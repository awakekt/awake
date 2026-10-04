/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.render.passes.uniforms.RenderDebugView
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Renders the parity scenes, every debug view and the UI scenarios on headless Vulkan, one PNG
 * each, into the directory given as the first argument.
 *
 * The PR evidence workflow runs this on a pull request's base and head and posts the two sets side
 * by side, so a rendering change arrives with pictures. File names are the comparison key: keep
 * them stable, and add a scenario here when a new one lands in this module.
 */
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "build/reports/render-evidence").apply { mkdirs() }
    openHeadlessScene(HeadlessUiBackend.Vulkan).use { session ->
        val renderer = session.renderer
        writePng(out, "scene-shadow", SCENE_SIZE, renderer.renderShadowScene())
        writePng(out, "scene-shadow-textured-ground", SCENE_SIZE, renderer.renderShadowScene(texturedGround = true))
        writePng(out, "scene-textured-pbr", SCENE_SIZE, renderer.renderTexturedPbrScene())
        writePng(out, "scene-back-culled", SCENE_SIZE, renderer.renderBackCulledScene())
        writePng(out, "scene-facing-sprite-side", SCENE_SIZE, renderer.renderFacingSpriteFromTheSide())
        writePng(out, "scene-skinned-textured", SCENE_SIZE, renderer.renderTexturedSkinnedScene(Mat4().data))
        writePng(out, "scene-skinned-textured-exposure-2", SCENE_SIZE, renderer.renderTexturedSkinnedScene(Mat4().data, exposure = 2f))
        STUDIO_YAWS.withIndex().filter { it.index % EVIDENCE_YAW_STEP == 0 }.forEach { (index, yaw) ->
            writePng(out, "scene-studio-cube-yaw$index", SCENE_SIZE, renderer.renderStudioCubeScene(yaw))
        }
        RenderDebugView.entries.forEach { view ->
            writePng(out, "debug-${view.name.lowercase()}", SCENE_SIZE, renderer.renderDebugViewScene(view))
        }
    }
    withHeadlessUi(HeadlessUiBackend.Vulkan) { renderer ->
        UI_PARITY_SCENARIOS.forEach { scenario ->
            writePng(out, "ui-${scenario.name}", SCENARIO_SIZE, renderer.render(scenario))
        }
    }
}

/** Every third of the twelve studio yaws: enough angles to show a lighting change, few enough to scan. */
private const val EVIDENCE_YAW_STEP = 3

/** Mid grey behind translucent pixels, so a light or a dark UI glyph both stay visible. */
private const val BACKDROP = 128

/** Writes [size]-square RGBA [pixels] as an opaque PNG, composited over [BACKDROP]. */
private fun writePng(dir: File, name: String, size: Int, pixels: ByteArray) {
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
    for (y in 0 until size) {
        for (x in 0 until size) {
            val offset = (y * size + x) * 4
            fun channel(index: Int) = pixels[offset + index].toInt() and 0xFF
            val alpha = channel(3)
            fun over(index: Int) = (channel(index) * alpha + BACKDROP * (MAX_CHANNEL - alpha)) / MAX_CHANNEL
            image.setRGB(x, y, (over(0) shl 16) or (over(1) shl 8) or over(2))
        }
    }
    ImageIO.write(image, "png", File(dir, "$name.png"))
}

private const val MAX_CHANNEL = 255
