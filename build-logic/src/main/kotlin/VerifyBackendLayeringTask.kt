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
abstract class VerifyBackendLayeringTask : DefaultTask() {

    @get:Input
    abstract val modulePath: Property<String>

    /** Simple type names whose import is forbidden, e.g. `DrawCall`. */
    @get:Input
    abstract val forbiddenImports: ListProperty<String>

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

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @TaskAction
    fun verify() {
        val forbidden = forbiddenImports.get()
        val exempt = exemptFiles.get()
        val violations = mutableListOf<String>()

        sourceFiles.files.filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val path = file.invariantSeparatorsPath
            if (exempt.any { path.endsWith(it) }) return@forEach
            file.readLines().forEachIndexed { index, line ->
                val trimmed = line.trim()
                if (!trimmed.startsWith("import ")) return@forEachIndexed
                val imported = trimmed.removePrefix("import ").substringBefore(" as ").trim()
                val simpleName = imported.substringAfterLast('.')
                if (simpleName in forbidden) {
                    violations += "${file.name}:${index + 1}: imports $simpleName"
                }
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("${modulePath.get()} imports render-runtime vocabulary into a GPU backend:")
                    violations.forEach { appendLine("  $it") }
                    appendLine()
                    appendLine("A backend receives pipelines and recorded commands -- it should not know")
                    appendLine("what a scene light or camera is. Move the logic that needs these types into")
                    appendLine("the shared render runtime (awake:engine:render:passes), or if this is")
                    appendLine("tracked debt, add the file to exemptFiles with a reason.")
                    appendLine("See docs/reference/render-hardware-interface.md.")
                },
            )
        }
    }
}
