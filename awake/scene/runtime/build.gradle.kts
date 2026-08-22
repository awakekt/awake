plugins {
    id("awake.kmp-library-convention")
    alias(libs.plugins.kotlin.serialization)
    id("awake.dokka-convention")
    id("awake.detekt-convention")
    id("awake.spotless-convention")
    id("awake.test-resources-convention")
}

kotlin {
    android {
        namespace = "io.github.ronjunevaldoz.awake.scene.runtime"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":awake:core:graphics2d"))
            implementation(project(":awake:core:math2d"))
            implementation(project(":awake:core:host"))
            implementation(project(":awake:core:input"))
            api(project(":awake:scene:scene-core"))
            api(project(":awake:scene:rendering"))
            api(project(":awake:core:math"))
            api(project(":awake:ecs"))
            api(project(":awake:engine:platform"))
            api(project(":awake:ui:ui-core"))
            // Needed by SceneGameFrame.kt's frameStats() -- textLayoutCacheStats() lives in
            // ui-headless, not ui-core.
            implementation(project(":awake:ui:headless"))
            api(project(":awake:engine:render:contract"))
            implementation(libs.kotlinx.serialization.json)
        }

        // SceneWriter's wasmJs actual hands the exported document to a browser download;
        // there is no filesystem to write to. Desktop needs nothing beyond java.io.
        named("wasmJsMain") {
            dependencies {
                implementation(libs.kotlinx.browser)
            }
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

awakeTestResources {
    roots.from(layout.projectDirectory.dir("src/commonTest/resources"))
}
