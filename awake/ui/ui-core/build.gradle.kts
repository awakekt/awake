plugins {
    id("awake.kmp-library-convention")
    id("awake.dokka-convention")
    id("awake.detekt-convention")
    id("awake.spotless-convention")
    id("awake.ui-ownership-convention")
}

kotlin {
    android {
        namespace = "io.github.ronjunevaldoz.awake.ui.core"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":awake:core:graphics2d"))
            implementation(project(":awake:core:math2d"))
            implementation(project(":awake:core:math"))
            implementation(project(":awake:core:color"))
            implementation(project(":awake:core:input"))
            api(project(":awake:ui:graphics"))
            api(project(":awake:ui:text"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
