/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

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

// Preserve the console assertion details as well as the browser adapter's shortened Error.
tasks.withType<AbstractTestTask>().matching { it.name == "wasmJsBrowserTest" }.configureEach {
    testLogging {
        exceptionFormat = TestExceptionFormat.FULL
        showStandardStreams = true
    }
}

// Run by hand to move to a new Unicode version; both outputs are checked in. -PucdDirectory=<dir>
// reads the UCD files from a directory instead of downloading them from unicode.org.
tasks.register<com.awakekt.awake.build.tasks.GenerateGraphemeBreakTableTask>("generateGraphemeBreakTable") {
    description = "Regenerates the grapheme break table and its Unicode test data from the Unicode Character Database"
    unicodeVersion.set("18.0.0")
    providers.gradleProperty("ucdDirectory").orNull?.let { ucdDirectory.set(file(it)) }
    tableFile.set(layout.projectDirectory.file("src/commonMain/kotlin/com/awakekt/awake/core/text/unicode/GraphemeBreakTable.kt"))
    testDataFile.set(layout.projectDirectory.file("src/commonTest/resources/unicode/GraphemeBreakTest.txt"))
}

mavenPublishing {
    pom {
        name.set("Awake Core Text")
        description.set("Font atlases, glyph metrics and text shaping shared by every UI surface")
    }
}
