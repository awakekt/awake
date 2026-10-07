/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.render.passes.uniforms.RenderDebugView
import com.awakekt.awake.render.renderer.Renderer
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.PI

/**
 * Renders the parity scenes, every debug view and the UI scenarios on headless Vulkan, one PNG
 * each, into the directory given as the first argument, plus `captions.tsv` saying what each shows.
 *
 * The PR evidence workflow runs this on a pull request's base and head and posts the two sets side
 * by side, so a rendering change arrives with pictures. File names are the comparison key: keep
 * them stable, and add a scenario here when a new one lands in this module.
 */
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "build/reports/render-evidence").apply { mkdirs() }
    val captions = StringBuilder()
    fun write(name: String, caption: String, size: Int, pixels: ByteArray) {
        writePng(out, name, size, pixels)
        captions.append(name).append('\t').append(caption).append('\n')
    }
    openHeadlessScene(HeadlessUiBackend.Vulkan).use { session ->
        val renderer = session.renderer
        write("scene-unlit-atlas", "Unlit pixel atlas: frames, mirroring, tint and transparent margins", EVIDENCE_SIZE, renderer.renderUnlitAtlasScene(EVIDENCE_SIZE))
        write("scene-shadow", "Lit ground and a hovering quad: where the shadow lands", EVIDENCE_SIZE, renderer.renderShadowScene(size = EVIDENCE_SIZE))
        write(
            "scene-shadow-textured-ground",
            "The same, on a textured ground: shadows on textured materials",
            EVIDENCE_SIZE,
            renderer.renderShadowScene(texturedGround = true, size = EVIDENCE_SIZE),
        )
        write("scene-textured-pbr", "Textured PBR plane: material sampling and lighting", EVIDENCE_SIZE, renderer.renderTexturedPbrScene(size = EVIDENCE_SIZE))
        write("scene-back-culled", "Plane seen from above with back-face culling", EVIDENCE_SIZE, renderer.renderBackCulledScene(size = EVIDENCE_SIZE))
        write(
            "scene-facing-sprite-side",
            "Camera-facing sprite seen from the side: its proportions and which way round it is",
            EVIDENCE_SIZE,
            renderer.renderFacingSpriteFromTheSide(size = EVIDENCE_SIZE),
        )
        write("scene-facing-sprite-spin-45", "The same sprite spun 45 degrees: the quad turns in its own plane", EVIDENCE_SIZE, renderer.renderFacingSpriteFromTheSide(size = EVIDENCE_SIZE, rotation = (PI / 4).toFloat()))
        writeSkinnedEvidence(renderer, ::write)
        writeShaderEffectEvidence(renderer, ::write)
        write("scene-terrain-cliff-self-shadow", "Clipmap plateau coarser than its heightmap, sun on its cliff: top, rim and face stay lit", EVIDENCE_SIZE, renderer.renderTerrainCliffScene(size = EVIDENCE_SIZE))
        STUDIO_YAWS.withIndex().filter { it.index % EVIDENCE_YAW_STEP == 0 }.forEach { (index, yaw) ->
            write(
                "scene-studio-cube-yaw$index",
                "Studio's default cube under cascaded shadows, sun yaw $index of 12",
                EVIDENCE_SIZE,
                renderer.renderStudioCubeScene(yaw, size = EVIDENCE_SIZE),
            )
        }
        RenderDebugView.entries.forEach { view ->
            write(
                "debug-${view.name.lowercase()}",
                "Debug view ${view.name}: red cube on a ground past the shadow distance",
                EVIDENCE_SIZE,
                renderer.renderDebugViewScene(view, size = EVIDENCE_SIZE),
            )
        }
    }
    withHeadlessUi(HeadlessUiBackend.Vulkan) { renderer ->
        UI_PARITY_SCENARIOS.forEach { scenario ->
            write("ui-${scenario.name}", "UI pipeline: ${scenario.name}", SCENARIO_SIZE, renderer.render(scenario))
        }
    }
    File(out, "captions.tsv").writeText(captions.toString())
}

/** Every third of the twelve studio yaws: enough angles to show a lighting change, few enough to scan. */
private const val EVIDENCE_YAW_STEP = 3

/** Large enough to read in a PR comment at its own size; the probes keep [SCENE_SIZE]. */
private const val EVIDENCE_SIZE = 256

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

/** A textured plane skinned to one joint at the identity pose, and the same at exposure 2. */
private fun writeSkinnedEvidence(renderer: Renderer, write: (String, String, Int, ByteArray) -> Unit) {
    write(
        "scene-skinned-textured",
        "Textured plane skinned to one joint at the identity pose",
        EVIDENCE_SIZE,
        renderer.renderTexturedSkinnedScene(Mat4().data, size = EVIDENCE_SIZE),
    )
    write(
        "scene-skinned-textured-exposure-2",
        "The same skinned plane at exposure 2: exposure before the tone curve",
        EVIDENCE_SIZE,
        renderer.renderTexturedSkinnedScene(Mat4().data, exposure = 2f, size = EVIDENCE_SIZE),
    )
}

/** A project's shader documents, as `shader_effect` draws them: its sky alone, and a plane over it. */
private fun writeShaderEffectEvidence(renderer: Renderer, write: (String, String, Int, ByteArray) -> Unit) {
    val sky = shaderEffectDocument("sky")
    write(
        "scene-shader-effect-sky",
        "A project's shader document drawn as a background: blue above the horizon, red below",
        EVIDENCE_SIZE,
        renderer.renderShaderEffectScene(listOf(sky), size = EVIDENCE_SIZE),
    )
    write(
        "scene-shader-effect-plane",
        "A project's plane document seen from above, over its sky document",
        EVIDENCE_SIZE,
        renderer.renderShaderEffectScene(listOf(sky, shaderEffectDocument("plane")), view = ShaderEffectView.Above, size = EVIDENCE_SIZE),
    )
}
