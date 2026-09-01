/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

/**
 * Generates a Kotlin `ImageVector` file from the SVGs vendored beside it.
 *
 * The generator is the same script the icon skill documents, run once for the whole set rather than
 * per glyph -- 78 separate interpreter starts took 3.6s against 0.3s batched, which is what makes
 * running it from the build reasonable at all.
 *
 * Writes into `build/generated`, not `src/`: registering that with `kotlin.srcDir` lets Gradle infer
 * the compile dependency, which is the ~20 lines of `dependsOn`/`mustRunAfter` that
 * `awake.shader-pipeline-convention` needs precisely because it writes into `src/`.
 */
abstract class GenerateImageVectorsTask : DefaultTask() {

    /** The SVG tree, including the `manifest.json` that names the tiers. */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDirectory: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val generatorScript: RegularFileProperty

    /** Named so a host without `python3` on PATH can point at its own interpreter. */
    @get:Input
    abstract val pythonExecutable: Property<String>

    /** Package directories are created under here; the file is named after the manifest's interface. */
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun generate() {
        val manifestFile = sourceDirectory.file("manifest.json").get().asFile
        check(manifestFile.isFile) { "no manifest.json in ${sourceDirectory.get().asFile}" }
        val manifest = manifestFile.readText()

        val stdout = ByteArrayOutputStream()
        execOperations.exec {
            commandLine(
                pythonExecutable.get(),
                generatorScript.get().asFile.absolutePath,
                "--manifest",
                manifestFile.absolutePath,
            )
            standardOutput = stdout
        }

        // Reading these two out of the manifest rather than configuring them twice -- the generator
        // already decides both, and a second copy in Gradle is a second thing to keep in step.
        val packageName = manifest.stringField("package")
        val fileName = manifest.stringField("interface")
        val target = outputDirectory.get().asFile
            .resolve(packageName.replace('.', '/'))
            .also { it.mkdirs() }
            .resolve("$fileName.kt")
        target.writeText(stdout.toString(Charsets.UTF_8.name()))
        logger.lifecycle("Generated ${target.name} (${target.readLines().size} lines)")
    }

    /** Enough JSON for two flat string fields; a parser dependency in build-logic for this is not. */
    private fun String.stringField(name: String): String =
        Regex("\"$name\"\\s*:\\s*\"([^\"]+)\"").find(this)?.groupValues?.get(1)
            ?: error("manifest.json has no \"$name\" field")
}
