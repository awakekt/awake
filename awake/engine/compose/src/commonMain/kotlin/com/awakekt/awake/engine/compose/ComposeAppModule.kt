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
import com.awakekt.awake.core.input.CursorImage
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.input.PointerCursor
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.engine.platform.core.AppModule
import com.awakekt.awake.engine.platform.dsl.AppServiceLookup
import com.awakekt.awake.engine.platform.dsl.AppSpecBuilder
import com.awakekt.awake.engine.platform.error.AppCoroutineScope
import com.awakekt.awake.engine.platform.lifecycle.AppFrame
import com.awakekt.awake.render.renderer.Renderer

/** Composable function signature for top-level application UI content. */
typealias ComposeAppContent = context(Composer)
() -> Unit

/**
 * Installs the one Compose host for an application.
 *
 * Install this module before a scene module when its output must be staged before that scene's
 * render pass. Set [presentWithoutScene] to false when another module presents the frame.
 *
 * @param content The root composable UI content hierarchy.
 * @param font The default font to use for UI text rendering.
 * @param presentWithoutScene Whether the runtime should present the rendered frame when no scene runs.
 * @return The configured [AppModule] ready for installation.
 */
fun composeAppModule(
    content: ComposeAppContent,
    font: UiFont = UiFonts.default(),
    presentWithoutScene: Boolean = true,
): AppModule = ComposeAppModule(content, font, presentWithoutScene)

/**
 * Runtime state holder managing the Compose UI host, input dispatch, and frame rendering.
 */
class ComposeAppRuntime internal constructor(
    content: ComposeAppContent,
    private val font: UiFont,
    private val input: Input,
    private val presentWithoutScene: Boolean,
    services: AppServiceLookup,
) {
    // Looked up when first composed, so the app may register its scope after installing this module.
    private val appScope: AppCoroutineScope? by lazy { services.service(AppCoroutineScope::class) }

    // Wrapped once, not per frame.
    private val content: ComposeAppContent = { ProvideAppCoroutineScope(appScope, content) }

    /** The core retained Compose UI host instance. */
    val host = ComposeHost()
    private val graphicsLayers = GraphicsLayerCompositor()

    /** The active hardware renderer used for drawing UI primitives. */
    lateinit var renderer: Renderer
        private set

    /** Cached output from the most recently completed UI frame, or `null` before the first frame. */
    var lastFrame: FrameOutput? = null
        private set

    /** Input ownership flags indicating which pointer and keyboard interactions were consumed by the UI. */
    var inputOwnership: InputOwnership = InputOwnership()
        private set

    /** Active pointer cursor style requested by the topmost hovered UI element. */
    var cursor: PointerCursor = PointerCursor.Default
        private set

    /**
     * A custom cursor image the application asks the host to show, overriding the system [cursor]
     * shape while non-null. The application's to set; the UI layer does not drive it. A hovered
     * node's own image takes precedence; see [effectiveCursorImage].
     */
    var cursorImage: CursorImage? = null

    /** The custom cursor image the innermost hovered node asked for in the last frame, or `null`. */
    var hoveredCursorImage: CursorImage? = null
        private set

    /**
     * The cursor image a host shows: the [hoveredCursorImage] a hovered node asked for, else the
     * application's [cursorImage], else `null`, which leaves the system [cursor] shape in effect.
     */
    val effectiveCursorImage: CursorImage?
        get() = hoveredCursorImage ?: cursorImage

    internal fun ready(renderer: Renderer) {
        this.renderer = renderer
    }

    internal fun render(frame: AppFrame) {
        host.density = frame.density
        host.applyInsets(frame)
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
        input.textInputPassword = output.effects.passwordKeyboard
        output.effects.clipboardText?.let { input.clipboardWrite = it }
        inputOwnership = output.ownership
        cursor = output.effects.cursor
        hoveredCursorImage = output.effects.cursorImage
        lastFrame = output
    }

    internal fun dispose() {
        // The UI first, while the renderer is still alive: what it remembered is told it was forgotten.
        host.dispose()
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
        val runtime = ComposeAppRuntime(content, font, into.input, presentWithoutScene, into.serviceLookup())
        into.service(ComposeAppRuntime::class, runtime)
        into.service(ComposeHost::class, runtime.host)
        into.ready(runtime::ready)
        into.render(runtime::render)
        into.dispose(runtime::dispose)
    }
}
