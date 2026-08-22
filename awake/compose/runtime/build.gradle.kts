plugins {
    id("awake.kmp-library-convention")
    id("awake.dokka-convention")
    id("awake.detekt-convention")
    id("awake.spotless-convention")
    alias(libs.plugins.kover)
}

kotlin {
    android {
        namespace = "io.github.ronjunevaldoz.awake.compose.runtime"
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }

    compilerOptions {
        freeCompilerArgs.addAll(listOf("-Xcontext-parameters"))
    }
}