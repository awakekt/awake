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
}

// libktx's natives for every desktop Awake runs on; LWJGL loads the one for the host.
val lwjglNatives = listOf("natives-linux", "natives-macos-arm64", "natives-windows")

kotlin {
    android { namespace = "com.awakekt.awake.asset.ktx.loader" }
    sourceSets {
        commonMain.dependencies {
            api(project(":awake:asset:texture-ktx"))
            api(project(":awake:engine:render:contract"))
        }
        commonTest.dependencies { implementation(kotlin("test")) }
        named("desktopMain") {
            dependencies {
                // The BOM lifts every LWJGL module an app has, wgpu4k's lwjgl-glfw included, to the
                // version lwjgl-ktx needs: LWJGL modules of different versions don't load together.
                implementation(project.dependencies.platform(libs.lwjgl.bom))
                implementation(libs.lwjgl.core)
                implementation(libs.lwjgl.ktx)
                val version = libs.versions.lwjgl.get()
                lwjglNatives.forEach { natives ->
                    runtimeOnly("org.lwjgl:lwjgl:$version:$natives")
                    runtimeOnly("org.lwjgl:lwjgl-ktx:$version:$natives")
                }
            }
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake KTX2 Texture Loader")
        description.set("Loads KTX2 textures to a GPU block format or RGBA8, transcoding Basis Universal on desktop with libktx")
    }
}
