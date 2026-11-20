/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * Fails when a test class of a gated module runs in no CI step (#1592).
 *
 * CI runs some modules' tests by name, and a class no step names only ever compiles: #1765's gates
 * ran nowhere, and a test #1781 broke stayed red on `main` unseen. A gated module passes when a
 * workflow runs its suite whole, or when every one of its test classes is named by some `--tests`.
 */
@DisableCachingByDefault(because = "Verification task with no outputs")
abstract class VerifyCiTestCoverageTask : DefaultTask() {
    /** The repository root, which [gatedModules]' directories are relative to. */
    @get:Input
    abstract val rootPath: Property<String>

    /** The CI workflow files. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val workflows: ConfigurableFileCollection

    /** Each gated module's test task path, such as `:awake:compose:ui:desktopTest`, to its directory. */
    @get:Input
    abstract val gatedModules: MapProperty<String, String>

    @TaskAction
    fun verify() {
        val root = File(rootPath.get())
        val texts = workflows.files.filter { it.isFile }.map { it.readText() }
        val failures = gatedModules.get().toSortedMap().flatMap { (task, dir) ->
            unrunTestClasses(CiTestRuns(texts, task), testClasses(File(root, dir))).map { "$task: $it" }
        }
        if (failures.isNotEmpty()) {
            throw GradleException(
                "Test classes that no CI step runs (${failures.size}); run the module's suite whole in a step, " +
                    "or name each class with --tests:\n" + failures.joinToString("\n") { "  $it" },
            )
        }
        logger.lifecycle("CI test coverage passed: ${gatedModules.get().size} gated module(s) run in CI")
    }

    private fun testClasses(moduleDir: File): List<String> =
        File(moduleDir, "src").listFiles().orEmpty()
            .filter { it.isDirectory && it.name.endsWith("Test") }
            .flatMap { sourceSet -> sourceSet.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
            .mapNotNull { testClassName(it.name, it.readText()) }
}
