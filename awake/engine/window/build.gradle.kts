/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import com.awakekt.awake.build.extension.registerDesktopNativeLibrary

plugins {
    id("com.awakekt.awake.plugin.library")
    id("com.awakekt.awake.plugin.publish")
    id("com.awakekt.awake.plugin.dokka")
    id("com.awakekt.awake.plugin.detekt")
    id("com.awakekt.awake.plugin.spotless")
}

kotlin {
    android {
        namespace = "com.awakekt.awake.engine.window"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":awake:engine:platform"))
            api(project(":awake:core:input"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// libawake-window: the GLFW window and its input. Built on demand, like the Vulkan library:
//   ./gradlew :awake:engine:window:buildDesktopNative
registerDesktopNativeLibrary(
    libName = "awake-window",
    sourceDir = layout.projectDirectory.dir("desktop-native").asFile,
    extraInputDirs = listOf(layout.projectDirectory.dir("src/main/cpp").asFile),
)

mavenPublishing {
    pom {
        name.set("Awake Engine Window")
        description.set("Each platform's native window and its input, behind the engine platform contract")
    }
}
