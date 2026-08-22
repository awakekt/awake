// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlinx.benchmark)
    id("awake.dokka-convention")
    id("awake.detekt-convention")
    id("awake.spotless-convention")
}

kotlin {
    jvmToolchain(17)
}

dependencies {

    implementation(project(":awake:core:math2d"))
    implementation(project(":awake:core:math"))
    implementation(project(":awake:ui:ui-core"))
    implementation(libs.kotlinx.benchmark.runtime)
}

benchmark {
    targets {
        register("main")
    }
}
