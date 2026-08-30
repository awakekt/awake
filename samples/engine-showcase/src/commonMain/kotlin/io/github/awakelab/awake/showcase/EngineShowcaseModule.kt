/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.showcase

import io.github.awakelab.awake.asset.shaderpack.LitShadowUniformLayout
import io.github.awakelab.awake.core.geometry.generate.generate
import io.github.awakelab.awake.ecs.System
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.engine.bootstrap.dsl.appModule
import io.github.awakelab.awake.engine.platform.core.AppModule
import io.github.awakelab.awake.scene.authoring.scene
import io.github.awakelab.awake.scene.rendering.systems.AnimationSystem
import io.github.awakelab.awake.scene.rendering.systems.ParticleSystem
import io.github.awakelab.awake.scene.runtime.defaultInfrastructureSystems
import io.github.awakelab.awake.showcase.ui.ShowcaseSwitcher

/** The independent engine-demo host. [initialShowcaseId] is injectable for focused smoke tests. */
internal fun engineShowcaseModule(initialShowcaseId: String = DEFAULT_SHOWCASE_ID): AppModule {
    require(EngineShowcases.any { it.id == initialShowcaseId }) { "Unknown showcase '$initialShowcaseId'." }
    val loader = EngineShowcaseLoader()
    val selection = ShowcaseSelection(initialShowcaseId)

    return appModule {
        scene("engine-showcase") {
            assets {
                mesh("cube") { renderer.createMesh(generate { cube(size = 1f, colored = true) }) }
                mesh("ground") { renderer.createMesh(generate { plane(size = 10f, colored = false) }) }
                material("lit-shadow") { renderer.createMaterial(uniformFloatCount = LitShadowUniformLayout.total) }
                registerEngineShowcaseAssets()
            }
            frameSystem("showcase-driver") {
                val runtime = this
                object : System {
                    override fun update(world: World, delta: Float) {
                        // Before advancing, not after: a switch requested by the UI last frame
                        // replaces the world the driver is about to read.
                        selection.consumeRequest()?.let { loader.activate(it, runtime) }
                        loader.advance(selection.current, runtime, delta)
                    }
                }
            }
            frameSystem("animation") { AnimationSystem() }
            frameSystem("particles") { ParticleSystem() }
            infrastructureSystems { defaultInfrastructureSystems() }
            onReady {
                loader.preload()
                loader.activate(selection.current, this)
            }
            content { ShowcaseSwitcher(selection, EngineShowcases) }
        }
    }
}

internal const val DEFAULT_SHOWCASE_ID = "point-lights"
