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

kotlin {
    android {
        namespace = "com.awakekt.awake.scene.physics"
        // Instrumented, because the ragdoll tests below need jolt-jni's native library and its
        // Android artifact ships device ABIs only. `sourceSetTreeName = "test"` puts
        // androidDeviceTest in the test tree, so it inherits commonTest rather than needing a copy.
        withDeviceTestBuilder { sourceSetTreeName = "test" }
            .configure { instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":awake:core:math"))
            api(project(":awake:scene:scene-core"))
            api(project(":awake:scene:world"))
            api(project(":awake:physics:api"))
            // Skeleton and AnimationPose, for RagdollSkeleton -- the seam that lets a ragdoll drive
            // a skinned character's bones rather than only invisible capsules.
            api(project(":awake:core:animation"))
            // For LineSegment only, so collider wireframes can be built here rather than in a
            // sample -- the same reason :awake:scene:navigation depends on it.
            api(project(":awake:engine:render:contract"))
        }
        named("androidDeviceTest") {
            dependencies {
                implementation(libs.androidx.test.runner)
            }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            // PhysicsCellStreamer's load half suspends, matching the async streaming contract.
            implementation(libs.kotlinx.coroutines.test)
            // Test-only, and the one place it is worth it: a ragdoll is bodies and constraints, so
            // whether it holds together is a question only a real solver answers. Here rather than
            // in a sample because commonTest runs it on all four backends, and three of them assert
            // where desktop's release build runs on quietly.
            implementation(project(":awake:backend:jolt"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("Awake Scene Physics")
        description.set("Physics components and systems that bind rigid bodies to scene transforms")
    }
}

// jolt-jni publishes device ABIs and nothing for a JVM host, so `System.loadLibrary("joltjni")`
// has nothing to load in an Android *host* test and every ragdoll case dies with
// UnsatisfiedLinkError. Excluded rather than disabling the whole task the way `:awake:backend:jolt`
// does: this module's other host tests need no native library, and there are fifty-odd of them.
// `connectedAndroidDeviceTest` runs the same sources, ragdoll included, on a real device.
// Registered by AGP after this script runs, so matched lazily rather than looked up by name.
tasks.matching { it.name == "testAndroidHostTest" }.configureEach {
    (this as? Test)?.filter?.excludeTestsMatching("*.RagdollJoltTest")
}
