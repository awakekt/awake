/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import com.awakekt.awake.build.extension.SceneComponentsExtension
import com.awakekt.awake.build.tasks.GenerateSceneComponentsTask
import com.awakekt.awake.build.tasks.SceneComponentDocsTask
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

// Every Kotlin source set with a `scene-components/` directory gets its components generated into
// it: `src/commonMain/scene-components/canvas_element.json` becomes `SceneCanvasElement` and
// `CanvasElementBinding`. The runtime component they bind stays hand-written.
val sceneComponents = extensions.create<SceneComponentsExtension>("awakeSceneComponents")

plugins.withId("org.jetbrains.kotlin.multiplatform") {
    extensions.configure<KotlinMultiplatformExtension> {
        sourceSets.configureEach {
            val sourceSetName = name
            val specs = layout.projectDirectory.dir("src/$sourceSetName/scene-components")
            if (!specs.asFile.isDirectory) return@configureEach
            val capitalized = sourceSetName.replaceFirstChar(Char::uppercaseChar)
            val generate = tasks.register<GenerateSceneComponentsTask>("generate${capitalized}SceneComponents") {
                group = "awake codegen"
                description = "Generate scene component documents and bindings from src/$sourceSetName/scene-components."
                specDirectory.set(specs)
                outputDirectory.set(layout.buildDirectory.dir("generated/scene-components/$sourceSetName"))
            }
            // The TASK, not the directory: a bare directory compiles fine and never runs the generator.
            kotlin.srcDir(generate)

            val update = tasks.register<SceneComponentDocsTask>("update${capitalized}SceneComponentDocs") {
                group = "awake codegen"
                description = "Write the field tables of src/$sourceSetName/scene-components into the reference page."
                specDirectory.set(specs)
                docsPage.set(sceneComponents.docsPage)
                this.update.set(true)
                updateTaskPath.set(path)
                onlyIf("a docsPage is set") { sceneComponents.docsPage.isPresent }
            }
            val check = tasks.register<SceneComponentDocsTask>("check${capitalized}SceneComponentDocs") {
                group = "verification"
                description = "Check the reference page's field tables match src/$sourceSetName/scene-components."
                specDirectory.set(specs)
                docsPage.set(sceneComponents.docsPage)
                this.update.set(false)
                updateTaskPath.set(update.map { it.path })
                onlyIf("a docsPage is set") { sceneComponents.docsPage.isPresent }
            }
            tasks.matching { it.name == "check" }.configureEach { dependsOn(check) }
        }
    }
}
