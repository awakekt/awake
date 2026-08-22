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
 * Files still importing the above, tracked as debt.
 *
 * Every one is in `renderer/`, and every one is rewritten by the draw-preparation phase of
 * `docs/tasks/2026-08-23-rhi-gpudevice-plan.md` -- that phase is finished exactly when this
 * list is empty. Shrink it; never grow it without a plan entry saying why.
 */
val exemptBackendFiles = when (project.path) {
    ":awake:backend:vulkan" -> listOf(
        "renderer/Renderer.kt",
        "renderer/RendererDraw3D.kt",
        "renderer/RendererFrameContext.kt",
        "renderer/RendererOffscreen.kt",
    )
    ":awake:backend:webgpu" -> listOf(
        "renderer/Renderer.kt",
        "renderer/RendererDraw3D.kt",
        "renderer/RendererOffscreen.kt",
        "renderer/RendererOpaqueDraws.kt",
        "renderer/WebGpuFrameContext.kt",
    )
    else -> emptyList()
}

val verifyBackendLayering = tasks.register<VerifyBackendLayeringTask>("verifyBackendLayering") {
    group = "verification"
    description = "Reject render-runtime vocabulary (DrawCall/SceneLight/Lens) inside a GPU backend."
    modulePath.set(project.path)
    sourceFiles.from(
        fileTree("src") {
            include("**/*Main/**/*.kt")
            exclude("**/*Test/**/*.kt")
        },
    )
    forbiddenImports.set(forbiddenBackendImports)
    exemptFiles.set(exemptBackendFiles)
}

tasks.named("check").configure {
    dependsOn(verifyBackendLayering)
}
