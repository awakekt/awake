/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.renderer

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.render.capture.FramebufferAttachment
import com.awakekt.awake.render.capture.FramebufferAttachmentData
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.pipeline.GpuDevice
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.render.texture.TextureAsset

/**
 * Module restructuring slice 1 (see docs/mvp-plan.md): the one real cross-backend entry
 * point `RenderSystem3D` calls. `awake-vulkan`'s `expect class Renderer` implements this
 * (`expect class Renderer(...) : com.awakekt.awake.render.renderer.Renderer`) --
 * see [com.awakekt.awake.render.mesh.Mesh]'s doc comment for why this doesn't
 * change `VulkanApplication.kt`'s construction pattern.
 */
interface Renderer : GpuDevice {

    /**
     * Current drawable surface aspect ratio used to build the camera projection.
     *
     * Scene culling may use a wider conservative aspect, but the projection must use the actual
     * surface ratio or geometry is visibly stretched. The default keeps GPU-free renderers and
     * headless tests deterministic until a backend exposes its live surface extent.
     */
    val surfaceAspect: Float
        get() = 16f / 9f

    /** RGBA (each `0f..1f`) color the 3D render pass clears to before every frame's
     * [draw] call -- defaults to opaque black on every backend, so any app that never sets
     * this sees exactly what it always did. A game with real 3D content whose camera can see
     * past its scene geometry (e.g. a sky above a ground plane) sets this once it becomes
     * relevant, mirroring the "optional per-game override, set from an `overlay`/`onReady`
     * block" pattern the UI runtime's own theme/font overrides already establish -- a plain `var`, not a per-frame parameter of [draw] itself, since
     * one solid background color rarely needs to change every single frame. */
    var clearColor: Color

    /** When `true`, meshes drawn by the next [draw] call render as edges instead of filled
     * triangles, for whichever formats the active backend has a wireframe-capable pipeline
     * for (a format with none just keeps drawing filled -- see each backend's `Renderer` for
     * which formats it built one for). `false` by default so a game that never toggles this
     * sees exactly what it always did. A plain `var`, same "not a per-frame [draw] parameter"
     * pattern as [clearColor] -- a debug/dev toggle, not something that needs to vary call to
     * call. Vulkan and WebGPU implement this with genuinely different mechanisms
     * (`VK_POLYGON_MODE_LINE` vs. a line-index buffer derived from each mesh's triangle
     * indices, drawn with `LineList` topology) -- WebGPU has no polygon-mode equivalent, see
     * that backend's `Renderer`/`Mesh` for why the derived-index-buffer approach was chosen
     * over a barycentric-coordinate fragment shader. */
    var wireframe: Boolean

    /**
     * Executes rendering from pre-packed GPU pass input.
     *
     * The HAL receives raw matrices, generic sub-passes, pre-sorted draw commands,
     * and uniform floats. It never imports or inspects SceneLight, RenderDrawCommand,
     * Lens, EnvironmentUniforms, or ShadowCascadeUniforms.
     */
    fun draw(input: GpuPassInput) {
        error(
            "Renderer.draw(GpuPassInput) is not implemented by ${this::class.simpleName}; " +
                "a renderer must execute or explicitly reject the submitted GPU pass.",
        )
    }

    /**
     * Renders [input] into offscreen [target] instead of swapchain.
     */
    fun renderToTexture(target: RenderTarget, input: GpuPassInput) {
        error(
            "Renderer.renderToTexture(RenderTarget, GpuPassInput) is not implemented by " +
                "${this::class.simpleName}; a renderer must execute or explicitly reject the GPU pass.",
        )
    }

    /**
     * Presents a frame containing no scene geometry.
     */
    fun presentWithoutScene() = draw(GpuPassInput.EMPTY)

    /** Reads [target]'s color attachment back to the CPU as tightly-packed RGBA8 pixels (the
     * same layout [TextureAsset.data] already assumes) -- for golden-image/screenshot-diff
     * testing. `suspend` because WebGPU's `GPUBuffer.mapAsync` readback is genuinely
     * asynchronous (no synchronous CPU-visible readback exists in that API); Vulkan's
     * implementation satisfies this `suspend fun` synchronously under the hood (a fence
     * wait), which is a valid implementation, not a violation of the contract. Call this only
     * after a [renderToTexture] call into the same [target] -- reading before any render is
     * backend-defined (typically zeroed/undefined pixels, not an error). */
    suspend fun readPixels(target: RenderTarget): TextureAsset

    /**
     * Reads the pixels of the most recently presented frame back to the CPU as tightly-packed
     * RGBA8 pixels for headless verification and test harnesses.
     *
     * In headless environments, this reads the presented swapchain image or active offscreen
     * target without requiring callers to cast to backend types.
     */
    suspend fun readPresentedPixels(): TextureAsset {
        error(
            "Renderer.readPresentedPixels() is not supported by ${this::class.simpleName}; " +
                "this renderer does not expose presented frame readback.",
        )
    }

    /**
     * Reads a generic framebuffer attachment for diagnostics. The result reports unsupported or
     * non-retained attachments explicitly; a backend must never return cleared pixels for an
     * attachment it did not actually preserve.
     */
    suspend fun readFramebufferAttachment(
        target: RenderTarget,
        attachment: FramebufferAttachment,
    ): FramebufferAttachmentData = FramebufferAttachmentData.unavailable(
        attachment,
        "${this::class.simpleName} does not expose framebuffer attachment readback.",
    )

    /** Draws this frame's UI overlay on top of whatever [draw] already wrote -- a separate
     * method (not folded into [draw]) so the 3D `Lens`+draw command contract stays untouched.
     * Each backend composites this as a second render pass with `loadOp = LOAD`, after the
     * 3D pass, in the same frame. [font] is only needed the first time a caller draws glyph
     * primitives -- both the (colored-quad) UI pipeline and the glyph pipeline are built
     * lazily, on the first call that needs them, not unconditionally at `Renderer`
     * construction time, so a game that never calls this never pays for either pipeline. */
    fun drawUi(primitives: List<UiDrawPrimitive>, font: UiFont? = null)

    /**
     * Blocks until this frame's slot of per-frame GPU resources is free for the CPU to rewrite.
     *
     * A backend that keeps frames in flight has to do this before overwriting a host-visible
     * buffer the GPU may still be reading, and [drawUi] does it internally. It is also exposed
     * here so a caller can *time* it apart from the staging it precedes: the wait is not UI work,
     * it is however far behind the GPU is. While it was only ever inside the UI staging phase, a
     * GPU-bound frame reported the whole stall as UI cost -- 40.7 ms of a 43.9 ms Studio frame,
     * against a UI that actually costs about one.
     *
     * Idempotent, and free once the slot is already free, so calling it first does not add work;
     * it moves the cost onto a timer that names it correctly. A backend with nothing in flight
     * leaves this a no-op.
     */
    fun awaitFrameResources() = Unit

    /** Renders UI into an offscreen target for compositing as one texture. */
    fun drawUiToTexture(
        target: RenderTarget,
        primitives: List<UiDrawPrimitive>,
        font: UiFont? = null,
    ) {
        error("This renderer does not support UI rendering to a RenderTarget.")
    }

    /**
     * Samples [source] and the already-painted [destination] into the distinct [output] target.
     *
     * [UiTargetCompositeMode.Screen] and [UiTargetCompositeMode.Overlay] read destination
     * colour and therefore cannot be represented by a fixed-function blend state. All three
     * targets must have the same dimensions, and [output] must not alias either sampled target.
     */
    fun compositeUiTargets(
        destination: RenderTarget,
        source: RenderTarget,
        output: RenderTarget,
        mode: UiTargetCompositeMode,
    ) {
        error("This renderer does not support sampled UI target compositing.")
    }

    /** Draws world-space debug lines (e.g. a frustum wireframe) -- unlike [drawUi], these
     * are transformed by [draw]'s own view-projection matrix and drawn *inside* the main 3D
     * render pass (not a separate pass), so they get real depth-testing against scene
     * geometry. Stages the lines for the next [draw] call, same "stage now, consume on next
     * draw" pattern [drawUi] already uses -- call before [draw] each frame. */
    fun drawDebugLines(lines: List<LineSegment>)
}

/**
 * Equations for whole-target UI composites. Unlike primitive blend modes, every entry here is
 * evaluated in a shader against a sampled destination target.
 */
enum class UiTargetCompositeMode {
    SourceOver,
    Screen,
    Overlay,
}
