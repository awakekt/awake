/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
plugins {
    id("com.awakekt.awake.plugin.library")
    id("com.awakekt.awake.plugin.publish")
    id("com.awakekt.awake.plugin.dokka")
    id("com.awakekt.awake.plugin.detekt")
    id("com.awakekt.awake.plugin.spotless")
    id("com.awakekt.awake.plugin.test-resources")
}

val generatorFonts = rootProject.layout.projectDirectory.dir("awake/ui/font-atlas-generator/fonts")

kotlin {
    android {
        namespace = "com.awakekt.awake.core.text"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":awake:core:math2d"))
            implementation(project(":awake:core:color"))
        }
        commonTest {
            // The generator's Roboto faces, which the loader tests bake at run time and hold against the committed atlases.
            resources.srcDir(generatorFonts)
            dependencies {
                implementation(kotlin("test"))
                implementation(project(":awake:core:host"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

awakeTestResources {
    roots.from(layout.projectDirectory.dir("src/commonTest/resources"), generatorFonts)
}

mavenPublishing {
    pom {
        name.set("Awake Core Text")
        description.set("Font atlases, glyph metrics and text shaping shared by every UI surface")
    }
}
