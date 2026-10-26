/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.binding.processor

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/** Compile a consumer module against the real annotations, binding contract and processor. */
@OptIn(ExperimentalCompilerApi::class)
internal fun compileSceneBinding(body: String): JvmCompilationResult = KotlinCompilation().apply {
    sources = listOf(
        SourceFile.kotlin(
            "Test.kt",
            """
        package test

        import com.awakekt.awake.ecs.Entity
        import com.awakekt.awake.ecs.World
        import com.awakekt.awake.scene.binding.*
        import com.awakekt.awake.scene.document.SceneComponent

        ${body.trimIndent()}
            """.trimIndent(),
        ),
    )
    inheritClassPath = true
    jvmTarget = "17"
    messageOutputStream = System.out
    configureKsp {
        symbolProcessorProviders += SceneBindingProcessorProvider()
        processorOptions[Names.REGISTRY_OPTION] = "test.TestSceneComponents"
    }
}.compile()
