/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.extension

import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopRuntimeDependencyConfigurationTest {
    @Test
    fun resolvesRuntimeConfigurationFromKmpDesktopCompilation() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.extensions.getByType(KotlinMultiplatformExtension::class.java).jvm("desktop")

        val configuration = project.desktopRuntimeDependencyConfiguration().get()

        assertEquals("desktopRuntimeClasspath", configuration.name)
        assertTrue(configuration.isCanBeResolved)
    }

    @Test
    fun reportsMissingDesktopCompilationClearly() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")

        val error = assertFailsWith<GradleException> {
            project.desktopRuntimeDependencyConfiguration()
        }

        assertTrue(error.message.orEmpty().contains("target 'desktop' main compilation was not found"))
    }
}
