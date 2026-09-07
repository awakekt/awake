/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import com.awakekt.awake.build.extension.*
import com.awakekt.awake.build.tasks.*
/**
 * Keeps GPU backends free of render-runtime vocabulary.
 *
 * Applied to `awake:backend:vulkan` and `awake:backend:webgpu`. See
 * `docs/reference/render-hardware-interface.md` for the boundary and
 * `docs/reference/decision-log.md` D26 for why two hand-written backends exist at all.
 */

/** Scene-level types a backend must not depend on. Each describes what to draw, not what the
 * hardware can do. */
val forbiddenBackendImports = listOf(
    "DrawCall",
    "SceneLight",
    "Lens",
)

/**
 * Content words no backend may declare.
 *
 * The rule: **a graphics backend knows hardware only.** Each word below answers "what is being
 * drawn", which is the app's question, not the driver's. A backend that declares
 * `SkyboxRenderPipeline` cannot gain a fourth content feature without being edited -- and it has
 * to be edited twice, once per backend, which is how the two drifted apart before.
 *
 * Deliberately not listed: `Line`, `Ui`, `Mesh`, `Material`. Those are capabilities -- a draw
 * primitive the app supplies content to -- not content. See `docs/reference/render-extensibility.md`
 * for the content-versus-capability test.
 */
val backendContentVocabulary = listOf(
    "Skybox",
    "Shadow",
    "Particle",
    "Fog",
    "Terrain",
    "Water",
    "Decal",
    "Billboard",
    "Occlusion",
)

/**
 * Files still importing the above, tracked as debt.
 *
 * Every one is in `renderer/`, and every one is rewritten by the draw-preparation phase of
 * `docs/tasks/2026-08-23-rhi-gpudevice-plan.md` -- that phase is finished exactly when this
 * list is empty. Shrink it; never grow it without a plan entry saying why.
 */
val exemptBackendFiles = when (project.path) {
    ":awake:backend:vulkan" -> listOf(
        "renderer/RendererCommandRecording.kt",
        "renderer/RendererDraw3D.kt",
        "renderer/RendererFrameContext.kt",
        "renderer/RendererOffscreen.kt",
        "renderer/Renderer.kt",
    )
    ":awake:backend:webgpu" -> listOf(
        "renderer/RendererDraw3D.kt",
        "renderer/RendererOffscreen.kt",
        "renderer/RendererOpaqueDraws.kt",
        "renderer/WebGpuFrameContext.kt",
        "renderer/Renderer.kt",
    )
    else -> emptyList()
}

/**
 * Files still declaring [backendContentVocabulary], tracked as debt. **Empty since 2026-08-24**
 * -- it ran 14 -> 0 across phases 1-4 of
 * `docs/tasks/2026-08-23-backend-content-split-plan.md`. Never grow it without a plan entry.
 *
 * Empty does NOT mean the backends carry no content, and nobody should read it that way. This
 * check only matches [DECLARATION] -- `class`/`interface`/`object`/`fun` followed by a name --
 * against the vocabulary. Content in a call, a local, a property or a well-chosen function name
 * is invisible to it. `RendererDraw3D.kt` is the live example: it mentions shadow ~20 times and
 * declares `lightViewProjection`, which builds a directional shadow box. Real content, no
 * forbidden word in any declared name, so this list never saw it.
 *
 * That gap is tracked in the content-split plan rather than papered over here, because widening
 * what this reads (line-level vocabulary, not just declarations) would flag every doc comment
 * that merely explains what a consumer uses a capability for -- which is exactly the prose that
 * makes a hardware type comprehensible.
 */
val contentExemptBackendFiles = emptyList<String>()

val verifyBackendLayering = tasks.register<VerifyBackendLayeringTask>("verifyBackendLayering") {
    group = "verification"
    description = "Reject scene vocabulary and content concepts (skybox, shadow, ...) inside a GPU backend."
    modulePath.set(project.path)
    sourceFiles.from(
        fileTree("src") {
            include("**/*Main/**/*.kt")
            exclude("**/*Test/**/*.kt")
        },
    )
    forbiddenImports.set(forbiddenBackendImports)
    exemptFiles.set(exemptBackendFiles)
    forbiddenContentVocabulary.set(backendContentVocabulary)
    contentExemptFiles.set(contentExemptBackendFiles)
}

tasks.named("check").configure {
    dependsOn(verifyBackendLayering)
}
