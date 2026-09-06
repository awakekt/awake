/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.compose

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameOutput
import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.compose.ui.platform.toFrameInput
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.input.PointerCursor
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.engine.platform.core.AppModule
import com.awakekt.awake.engine.platform.dsl.AppSpecBuilder
import com.awakekt.awake.engine.platform.lifecycle.AppFrame
import com.awakekt.awake.render.renderer.Renderer

typealias ComposeAppContent = context(Composer)
() -> Unit

/**
 * Installs the one Compose host for an application.
 *
 * Install this module before a scene module when its output must be staged before that scene's
 * render pass. Set [presentWithoutScene] to false when another module presents the frame.
 */
fun composeAppModule(
    content: ComposeAppContent,
    font: UiFont = UiFonts.default(),
    presentWithoutScene: Boolean = true,
): AppModule = ComposeAppModule(content, font, presentWithoutScene)

class ComposeAppRuntime internal constructor(
    private val content: ComposeAppContent,
    private val font: UiFont,
    private val input: Input,
    private val presentWithoutScene: Boolean,
) {
    val host = ComposeHost()
    private val graphicsLayers = GraphicsLayerCompositor()

    lateinit var renderer: Renderer
        private set

    var lastFrame: FrameOutput? = null
        private set

    var inputOwnership: InputOwnership = InputOwnership()
        private set

    var cursor: PointerCursor = PointerCursor.Default
        private set

    internal fun ready(renderer: Renderer) {
        this.renderer = renderer
    }

    internal fun render(frame: AppFrame) {
        host.density = frame.density
        val output = host.frame(
            frame.input.toFrameInput(
                viewportWidth = frame.viewportWidth.toInt(),
                viewportHeight = frame.viewportHeight.toInt(),
                deltaSeconds = frame.delta,
            ),
            content,
        )
        renderer.drawUi(
            graphicsLayers.composite(
                renderer = renderer,
                primitives = output.primitives,
                layers = output.graphicsLayers,
                font = font,
                viewportWidth = frame.viewportWidth.toInt(),
                viewportHeight = frame.viewportHeight.toInt(),
            ),
            font,
        )
        if (presentWithoutScene) renderer.presentWithoutScene()

        input.textInputFocused = output.effects.requestKeyboard
        inputOwnership = output.ownership
        cursor = output.effects.cursor
        lastFrame = output
    }

    internal fun dispose() {
        graphicsLayers.dispose()
    }
}

private class ComposeAppModule(
    private val content: ComposeAppContent,
    private val font: UiFont,
    private val presentWithoutScene: Boolean,
) : AppModule {
    override fun install(into: AppSpecBuilder) {
        check(into.service(ComposeAppRuntime::class) == null) {
            "An application may install only one Compose app module."
        }
        val runtime = ComposeAppRuntime(content, font, into.input, presentWithoutScene)
        into.service(ComposeAppRuntime::class, runtime)
        into.service(ComposeHost::class, runtime.host)
        into.ready(runtime::ready)
        into.render(runtime::render)
        into.dispose(runtime::dispose)
    }
}
