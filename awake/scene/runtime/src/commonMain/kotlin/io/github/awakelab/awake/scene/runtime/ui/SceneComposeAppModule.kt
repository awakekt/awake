/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.runtime.ui

import io.github.awakelab.awake.compose.runtime.CompositionLocalProvider
import io.github.awakelab.awake.compose.runtime.provides
import io.github.awakelab.awake.core.text.font.UiFont
import io.github.awakelab.awake.core.text.font.UiFonts
import io.github.awakelab.awake.engine.compose.composeAppModule
import io.github.awakelab.awake.engine.platform.core.AppModule
import io.github.awakelab.awake.engine.platform.dsl.AppSpecBuilder
import io.github.awakelab.awake.engine.platform.dsl.requireService
import io.github.awakelab.awake.render.renderer.Renderer
import io.github.awakelab.awake.scene.runtime.LocalFrameStats
import io.github.awakelab.awake.scene.runtime.LocalRenderer
import io.github.awakelab.awake.scene.runtime.LocalWorld
import io.github.awakelab.awake.scene.runtime.SceneAppLifecycleRuntime
import io.github.awakelab.awake.scene.runtime.SceneContent
import io.github.awakelab.awake.scene.runtime.SceneFrameStats
import io.github.awakelab.awake.scene.runtime.frameStats
import io.github.awakelab.awake.scene.runtime.session.SceneSession

/**
 * Adapts scene locals to the application-level Compose host.
 *
 * Install this module before the scene module so UI primitives are staged before the scene render
 * pass presents the frame.
 */
fun sceneComposeAppModule(
    content: SceneContent,
    font: UiFont = UiFonts.default(),
): AppModule = SceneComposeAppModule(content, font)

private class SceneComposeAppModule(
    private val content: SceneContent,
    private val font: UiFont,
) : AppModule {
    override fun install(into: AppSpecBuilder) {
        val existingScene = into.service(SceneAppLifecycleRuntime::class)
        check(existingScene == null || existingScene.spec.ui == null) {
            "An application-level Compose module cannot be installed when a scene has declared legacy content { }."
        }
        val services = into.serviceLookup()
        lateinit var session: SceneSession
        lateinit var renderer: Renderer
        into.ready { activeRenderer ->
            session = services.requireService(SceneSession::class)
            renderer = activeRenderer
        }
        into.install(
            composeAppModule(
                content = {
                    val runtime = services.service(SceneAppLifecycleRuntime::class)
                    CompositionLocalProvider(
                        LocalWorld provides session.world,
                        LocalRenderer provides renderer,
                        LocalFrameStats provides (runtime?.frameStats() ?: SceneFrameStats(0f, 0f, 0, 0, 0)),
                    ) {
                        content()
                    }
                },
                font = font,
                presentWithoutScene = false,
            ),
        )
    }
}
