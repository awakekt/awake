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
    "RenderDrawCommand",
    "SceneLight",
    "Lens",
    "PointLight",
    "ShadowCascadeUniforms",
    "DirectionalShadowBox",
    "ScenePassDescriptor",
    "SkyboxUniforms",
    "ParticleUniforms",
    "LightComponent",
    "RenderSystem3D",
    // Authored environment presets belong to render-pipeline lowering, never backend recording.
    "EnvironmentUniforms",
    // Scene defaults are authored inputs; a backend may consume the already-packed pass payload.
    "DEFAULT_SCENE_LIGHT",
    "DEFAULT_HORIZON_COLOR",
    "DEFAULT_ZENITH_COLOR",
    "DEFAULT_FOG_COLOR",
)

/**
 * Qualified references must be checked separately because Kotlin permits a source file to avoid
 * an import through either `typealias Local = package.Type` or direct use of the qualified name.
 * Keep this list aligned with [forbiddenBackendImports]; it is deliberately exact so comments
 * and harmless names remain legal.
 */
val forbiddenBackendQualifiedReferences = listOf(
    "com.awakekt.awake.render.passes.RenderDrawCommand",
    "com.awakekt.awake.render.renderer.DrawCall",
    "com.awakekt.awake.render.renderer.SceneLight",
    "com.awakekt.awake.render.passes.uniforms.ShadowCascadeUniforms",
    "com.awakekt.awake.render.passes.uniforms.DirectionalShadowBox",
    "com.awakekt.awake.render.passes.ScenePassDescriptor",
    "com.awakekt.awake.core.math.Lens",
    "com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms",
    "com.awakekt.awake.render.passes.uniforms.DEFAULT_SCENE_LIGHT",
    "com.awakekt.awake.render.passes.uniforms.DEFAULT_HORIZON_COLOR",
    "com.awakekt.awake.render.passes.uniforms.DEFAULT_ZENITH_COLOR",
    "com.awakekt.awake.render.passes.uniforms.DEFAULT_FOG_COLOR",
)

/** Package prefixes that are forbidden even when a backend uses a wildcard import or a fully
 * qualified reference instead of one of the named types above. */
val forbiddenBackendQualifiedPrefixes = listOf(
    "com.awakekt.awake.scene.",
    "com.awakekt.awake.ecs.",
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
 * Tracked import exemptions. The list is intentionally empty: source lowering now hands backends
 * the contract-owned `GpuDrawRequest`, and backends consume only generic preparation/resolved
 * packets.
 * Keep it empty; a new exemption would reintroduce the architecture debt this task removed.
 */
val exemptBackendFiles = emptyList<String>()

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
    forbiddenQualifiedReferences.set(forbiddenBackendQualifiedReferences)
    forbiddenQualifiedPrefixes.set(forbiddenBackendQualifiedPrefixes)
    exemptFiles.set(exemptBackendFiles)
    forbiddenContentVocabulary.set(backendContentVocabulary)
    contentExemptFiles.set(contentExemptBackendFiles)
}

tasks.named("check").configure {
    dependsOn(verifyBackendLayering)
}
