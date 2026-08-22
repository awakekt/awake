plugins {
    id("awake.kmp-library-convention")
    id("awake.dokka-convention")
    id("awake.detekt-convention")
    id("awake.spotless-convention")
}

kotlin {
    android {
        namespace = "io.github.ronjunevaldoz.awake.ui.text"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":awake:core:math2d"))
            implementation(project(":awake:core:color"))
            api(project(":awake:ui:graphics"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":awake:ui:ui-core"))
            implementation(project(":awake:ui:testing"))
        }
    }
}
