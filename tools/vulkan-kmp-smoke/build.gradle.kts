/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
// External-consumer smoke test for io.github.awake-lab:vulkan-kmp. Publish first:
//   ./gradlew :awake:backend:vulkan:bindings:publishToMavenLocal -PisMainHost=true
// then, from the repo root:
//   ./gradlew -p tools/vulkan-kmp-smoke run
// Add -Pnative=<path to desktop-native-libs> to go past compile-time resolution and create a
// real VkInstance through the published artifact.
plugins {
    kotlin("jvm") version "2.4.10"
    application
}

repositories {
    mavenLocal()
    mavenCentral()
    google()
}

dependencies {
    implementation("io.github.awake-lab:vulkan-kmp:0.1.0-dev.7-SNAPSHOT")
}

application {
    mainClass.set("SmokeKt")
}

tasks.named<JavaExec>("run") {
    (findProperty("native") as String?)?.let { jvmArgs("-Djava.library.path=$it") }
    // What every external macOS consumer must wire (inlined here on purpose -- this project
    // deliberately cannot see the repo's build-logic helpers): the loader finds MoltenVK
    // through an ICD manifest, both halves from Homebrew.
    if (System.getProperty("os.name").startsWith("Mac")) {
        val icd = listOf("/opt/homebrew/Cellar", "/usr/local/Cellar")
            .map(::File)
            .flatMap { (File(it, "molten-vk").listFiles() ?: emptyArray()).toList() }
            .map { File(it, "etc/vulkan/icd.d/MoltenVK_icd.json") }
            .firstOrNull { it.isFile }
        if (icd != null) environment("VK_ICD_FILENAMES", icd.absolutePath)
        environment(
            "DYLD_FALLBACK_LIBRARY_PATH",
            "/opt/homebrew/opt/vulkan-loader/lib:/opt/homebrew/lib:/usr/local/lib",
        )
    }
}
