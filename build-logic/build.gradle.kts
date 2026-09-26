/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
plugins {
    `kotlin-dsl`
    `maven-publish`
    id("com.vanniktech.maven.publish")
}

group = "com.awakekt.awake.build"
apply(from = "../gradle/git-derived-version.gradle.kts")
version = extra["gitDerivedVersion"] as String

val publicPluginIds = setOf(
    "com.awakekt.awake.plugin.application",
    "com.awakekt.awake.plugin.library",
    "com.awakekt.awake.plugin.project-content",
    "com.awakekt.awake.plugin.shader-pipeline",
    "com.awakekt.awake.plugin.dokka",
    "com.awakekt.awake.plugin.detekt",
    "com.awakekt.awake.plugin.spotless",
)

repositories {
    gradlePluginPortal()
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    implementation("com.android.tools.build:gradle:9.2.1")
    implementation(libs.detekt.gradle.plugin)
    implementation(libs.spotless.gradle.plugin)
    implementation(libs.dokka.gradle.plugin)
    implementation(libs.kotlinx.serialization.json)
    implementation("com.vanniktech:gradle-maven-publish-plugin:0.36.0")
    testImplementation(gradleTestKit())
    testImplementation(kotlin("test"))
}

extensions.configure<com.vanniktech.maven.publish.MavenPublishBaseExtension>("mavenPublishing") {
    publishToMavenCentral(automaticRelease = true)

    val hasSigningKey = hasProperty("signingInMemoryKey") ||
        System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKey") != null
    if (hasSigningKey) signAllPublications()

    pom {
        name.set("Awake Gradle Plugins")
        description.set("Gradle plugins used to build Awake Engine and Awake Studio projects.")
        url.set("https://github.com/awakekt/awake")
        licenses {
            license {
                name.set("Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        scm {
            connection.set("scm:git:git://github.com/awakekt/awake.git")
            developerConnection.set("scm:git:ssh://git@github.com/awakekt/awake.git")
            url.set("https://github.com/awakekt/awake")
        }
        developers {
            developer {
                name.set("Ron June Valdoz")
                email.set("ronjune.valdoz@gmail.com")
            }
        }
    }
}

// Precompiled script plugins also generate marker publications for internal Awake build
// conventions. Keep those available to this build's composite consumers, but publish only the
// stable IDs in publicPluginIds, explicitly supported by the public build-logic artifact.
afterEvaluate {
    val publishing = extensions.getByType<org.gradle.api.publish.PublishingExtension>()
    publishing.publications
        .withType<org.gradle.api.publish.maven.MavenPublication>()
        .toList()
        .filter { publication ->
            publication.name.endsWith("PluginMarkerMaven") &&
                publication.name.removeSuffix("PluginMarkerMaven") !in publicPluginIds
        }
        .forEach(publishing.publications::remove)
}

val verifyAwakePluginPublications = tasks.register("verifyAwakePluginPublications") {
    group = "verification"
    description = "Checks that only Awake's supported Gradle plugin markers are published."
    doLast {
        val publishing = project.extensions.getByType<org.gradle.api.publish.PublishingExtension>()
        val markerIds = publishing.publications
            .withType<org.gradle.api.publish.maven.MavenPublication>()
            .filter { it.name.endsWith("PluginMarkerMaven") }
            .map { it.name.removeSuffix("PluginMarkerMaven") }
            .toSet()
        check(markerIds == publicPluginIds) {
            "Expected only $publicPluginIds, but found marker publications $markerIds"
        }
    }
}

tasks.withType<org.gradle.api.publish.maven.tasks.AbstractPublishToMaven>().configureEach {
    dependsOn(verifyAwakePluginPublications)
    onlyIf {
        val publication = publication as? org.gradle.api.publish.maven.MavenPublication
        val isMarkerPublication = publication?.name?.endsWith("PluginMarkerMaven") == true
        !isMarkerPublication || publication.name.removeSuffix("PluginMarkerMaven") in publicPluginIds
    }
}
