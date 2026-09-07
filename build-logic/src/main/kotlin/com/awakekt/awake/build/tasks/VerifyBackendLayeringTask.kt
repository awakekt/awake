/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.tasks
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Rejects render-runtime vocabulary inside a GPU backend.
 *
 * A backend answers "what can this hardware do" -- pipelines, buffers, textures, command
 * recording. It has no business knowing what a scene light or a camera is; it should receive
 * pipelines and recorded commands. When both backends import the same scene-level type, each
 * one re-interprets it independently, and that is exactly how the two drifted far enough for
 * WebGPU to ship without an alpha-blended pipeline that Vulkan had.
 *
 * See `docs/reference/render-hardware-interface.md` for the boundary this enforces.
 *
 * Import-based on purpose: a doc comment naming `MeshRenderer` to explain why a pipeline exists
 * is useful and stays legal. What this bans is a compile-time dependency.
 */
@DisableCachingByDefault(because = "Verification task with no outputs")
abstract class VerifyBackendLayeringTask : DefaultTask() {

    @get:Input
    abstract val modulePath: Property<String>

    /** Simple type names whose import is forbidden, e.g. `DrawCall`. */
    @get:Input
    abstract val forbiddenImports: ListProperty<String>

    /**
     * Content words no backend declaration may contain, e.g. `Skybox`.
     *
     * The import check above catches a backend *depending on* scene vocabulary. This catches the
     * other direction, which is the more common one: a backend *inventing* it. `SkyboxRenderPipeline`
     * imports nothing forbidden and still means the driver layer knows what a sky is -- so a fourth
     * content feature cannot be added without editing a backend.
     *
     * Matched against declared names only (`class`/`interface`/`object`/`fun`), so a doc comment or
     * a shader path string naming a skybox stays legal. Naming the thing to explain it is fine;
     * declaring it here is not.
     */
    @get:Input
    abstract val forbiddenContentVocabulary: ListProperty<String>

    /**
     * Path suffixes (invariant separators) still allowed to import [forbiddenImports] -- a
     * tracked-debt ledger, not an opt-out.
     *
     * Shrink this list, never grow it. It reaches empty when the draw-preparation phase of
     * `docs/tasks/2026-08-23-rhi-gpudevice-plan.md` lands, which is that phase's completion
     * test.
     */
    @get:Input
    abstract val exemptFiles: ListProperty<String>

    /**
     * Path suffixes still allowed to declare [forbiddenContentVocabulary] -- the same kind of
     * tracked-debt ledger as [exemptFiles], for the same reason: the rule arrived after the code.
     *
     * Shrink it, never grow it. Empty is the completion test for phase 4b of
     * `docs/tasks/2026-08-23-rhi-gpudevice-plan.md`.
     */
    @get:Input
    abstract val contentExemptFiles: ListProperty<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @TaskAction
    fun verify() {
        val forbidden = forbiddenImports.get()
        val exempt = exemptFiles.get()
        val vocabulary = forbiddenContentVocabulary.get()
        val contentExempt = contentExemptFiles.get()
        val importViolations = mutableListOf<String>()
        val contentViolations = mutableListOf<String>()

        sourceFiles.files.filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val path = file.invariantSeparatorsPath
            val importExempt = exempt.any { path.endsWith(it) }
            val vocabularyExempt = contentExempt.any { path.endsWith(it) }
            if (importExempt && vocabularyExempt) return@forEach
            file.readLines().forEachIndexed { index, line ->
                val trimmed = line.trim()
                if (!importExempt && trimmed.startsWith("import ")) {
                    val imported = trimmed.removePrefix("import ").substringBefore(" as ").trim()
                    if (imported.substringAfterLast('.') in forbidden) {
                        importViolations += "${file.name}:${index + 1}: imports ${imported.substringAfterLast('.')}"
                    }
                }
                if (vocabularyExempt) return@forEachIndexed
                val declared = DECLARATION.find(trimmed)?.groupValues?.get(2) ?: return@forEachIndexed
                vocabulary.firstOrNull { declared.contains(it, ignoreCase = true) }?.let { word ->
                    contentViolations += "${file.name}:${index + 1}: declares $declared ($word)"
                }
            }
        }

        if (importViolations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("${modulePath.get()} imports render-runtime vocabulary into a GPU backend:")
                    importViolations.forEach { appendLine("  $it") }
                    appendLine()
                    appendLine("A backend receives pipelines and recorded commands -- it should not know")
                    appendLine("what a scene light or camera is. Move the logic that needs these types into")
                    appendLine("the shared render runtime (awake:engine:render:passes), or if this is")
                    appendLine("tracked debt, add the file to exemptFiles with a reason.")
                    appendLine("See docs/reference/render-hardware-interface.md.")
                },
            )
        }

        if (contentViolations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("${modulePath.get()} declares content vocabulary inside a GPU backend:")
                    contentViolations.forEach { appendLine("  $it") }
                    appendLine()
                    appendLine("A graphics backend knows hardware only -- pipelines, buffers, textures,")
                    appendLine("command recording. It must not know what a skybox or a shadow IS. Content")
                    appendLine("is declared once in the shared layer (awake:engine:render:passes) and built")
                    appendLine("from a PipelineSpec the backend compiles without naming it.")
                    appendLine("See docs/reference/render-extensibility.md.")
                },
            )
        }
    }

    private companion object {
        /** `class Foo` / `fun bar` / `internal object Baz` -- captures the declared name in group 2. */
        val DECLARATION = Regex("""\b(class|interface|object|fun)\s+([A-Za-z_][A-Za-z0-9_]*)""")
    }
}
