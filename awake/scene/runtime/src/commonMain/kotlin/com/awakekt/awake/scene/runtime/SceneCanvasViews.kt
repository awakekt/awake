/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.scene.canvas.CanvasViews
import com.awakekt.awake.scene.rendering.Camera
import com.awakekt.awake.ui.canvas.CanvasElement

/**
 * Each View element's picture, whichever world it is in: a target the size of its box, kept while the
 * size holds. `SceneCanvas` asks for them through [forWorld] as it paints; [render] then draws every
 * one asked for, from its element's world, before the UI that samples them, and frees the rest, so a
 * world nothing draws any more is let go with them.
 */
internal class SceneCanvasViews(
    private val renderer: Renderer,
    private val plan: (World, Camera, Int, Int) -> GpuPassInput,
) {
    private class View(val target: RenderTarget, var world: World, var camera: Camera, renderer: Renderer) {
        val material: Material = renderer.createMaterial(renderTarget = target)
        var shown = true
        var transparent = false

        fun destroy() {
            material.destroy()
            target.destroy()
        }
    }

    // By element: an element is in one world, so two worlds' Views never share an entry.
    private val views = HashMap<CanvasElement, View>()

    /** The pictures of [world]'s View elements, which look their camera node up in [world]. */
    fun forWorld(world: World): CanvasViews = CanvasViews { element, camera, width, height -> texture(world, element, camera, width, height) }

    private fun texture(world: World, element: CanvasElement, camera: Entity, width: Int, height: Int): Any? {
        val lens = world.get<Camera>(camera)
        if (lens == null || width < 1 || height < 1) return null
        val view = views[element]?.takeIf { it.target.width == width && it.target.height == height }
            ?: View(renderer.createRenderTarget(width, height), world, lens, renderer).also { views.put(element, it)?.destroy() }
        view.world = world
        view.camera = lens
        view.shown = true
        view.transparent = element.view?.transparent == true
        return view.material
    }

    /** Renders every view asked for since the last call and frees the others. */
    fun render() {
        val iterator = views.values.iterator()
        while (iterator.hasNext()) {
            val view = iterator.next()
            if (view.shown) {
                // Not waited for: the frame that samples the target is submitted after it.
                val input = plan(view.world, view.camera, view.target.width, view.target.height)
                val picture = if (view.transparent) input.copy(environment = input.environment.copy(clearColor = Color.Transparent, showSky = false)) else input
                renderer.submitToTexture(view.target, picture)
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
