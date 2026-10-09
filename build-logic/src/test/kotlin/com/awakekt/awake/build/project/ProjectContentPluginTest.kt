/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.project

import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertTrue

class ProjectContentPluginTest {
    /** Kotlin's wasm setup applies the lifecycle plugin to the root of a project with a wasmJs module. */
    @Test
    fun aProjectRootStillConfiguresWhenTheLifecyclePluginComesAfterIt() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.awakekt.awake.plugin.project-content")

        project.pluginManager.apply(LifecycleBasePlugin::class.java)

        val check = project.tasks.getByName("check")
        val runs = check.taskDependencies.getDependencies(check).map { it.name }
        assertTrue("checkProjectContent" in runs, "check runs the content checks: $runs")
    }
}
