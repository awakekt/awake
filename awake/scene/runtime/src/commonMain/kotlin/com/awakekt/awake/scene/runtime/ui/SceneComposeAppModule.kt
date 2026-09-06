/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime.ui

import com.awakekt.awake.compose.runtime.CompositionLocalProvider
import com.awakekt.awake.compose.runtime.provides
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.engine.compose.composeAppModule
import com.awakekt.awake.engine.platform.core.AppModule
import com.awakekt.awake.engine.platform.dsl.AppSpecBuilder
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.scene.runtime.LocalFrameStats
import com.awakekt.awake.scene.runtime.LocalRenderer
import com.awakekt.awake.scene.runtime.LocalWorld
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.scene.runtime.SceneContent
import com.awakekt.awake.scene.runtime.SceneFrameStats
import com.awakekt.awake.scene.runtime.frameStats
import com.awakekt.awake.scene.runtime.session.SceneSession

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
