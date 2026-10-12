/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.scene.canvas.CanvasElement
import com.awakekt.awake.scene.canvas.CanvasViews
import com.awakekt.awake.scene.rendering.Camera

/**
 * Each View element's picture: a target the size of its box, kept while the size holds. `SceneCanvas`
 * asks for them as it paints; [render] then draws every one asked for, before the UI that samples
 * them, and frees the rest.
 */
internal class SceneCanvasViews(
    private val world: World,
    private val renderer: Renderer,
    private val plan: (World, Camera, Int, Int) -> GpuPassInput,
) : CanvasViews {
    private class View(val target: RenderTarget, var camera: Camera, renderer: Renderer) {
        val material: Material = renderer.createMaterial(renderTarget = target)
        var shown = true

        fun destroy() {
            material.destroy()
            target.destroy()
        }
    }

    private val views = HashMap<CanvasElement, View>()

    override fun texture(element: CanvasElement, camera: Entity, width: Int, height: Int): Any? {
        val lens = world.get<Camera>(camera)
        if (lens == null || width < 1 || height < 1) return null
        val view = views[element]?.takeIf { it.target.width == width && it.target.height == height }
            ?: View(renderer.createRenderTarget(width, height), lens, renderer).also { views.put(element, it)?.destroy() }
        view.camera = lens
        view.shown = true
        return view.material
    }

    /** Renders every view asked for since the last call and frees the others. */
    fun render() {
        val iterator = views.values.iterator()
        while (iterator.hasNext()) {
            val view = iterator.next()
            if (view.shown) {
                // Not waited for: the frame that samples the target is submitted after it.
                renderer.submitToTexture(view.target, plan(world, view.camera, view.target.width, view.target.height))
                view.shown = false
            } else {
                view.destroy()
                iterator.remove()
            }
        }
    }

    fun dispose() {
        views.values.forEach(View::destroy)
        views.clear()
    }
}
