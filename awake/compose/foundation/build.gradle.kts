plugins {
    id("awake.kmp-library-convention")
    id("awake.dokka-convention")
    id("awake.detekt-convention")
    id("awake.spotless-convention")
    alias(libs.plugins.kover)
}

kotlin {
    android {
        namespace = "io.github.ronjunevaldoz.awake.compose.foundation"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":awake:core:graphics2d"))
            implementation(project(":awake:core:math2d"))
            api(project(":awake:compose:ui"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }

    compilerOptions {
        freeCompilerArgs.addAll(listOf("-Xcontext-parameters"))
    }
}
