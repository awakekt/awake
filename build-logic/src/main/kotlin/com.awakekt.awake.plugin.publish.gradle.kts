/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import com.awakekt.awake.build.extension.*
import com.awakekt.awake.build.tasks.*
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SourcesJar
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven
import java.util.Properties

plugins {
    `maven-publish`
    id("com.vanniktech.maven.publish")
    id("org.jetbrains.dokka")
}

val publicationsFromMainHost =
    listOf("android", "desktop", "iosArm64", "iosSimulatorArm64", "kotlinMultiplatform")

extensions.configure<PublishingExtension>("publishing") {
    publications {
        matching { it.name in publicationsFromMainHost }.all {
            val targetPublication = this@all
            tasks.withType<AbstractPublishToMaven>()
                .matching { it.publication == targetPublication }
                .configureEach {
                    onlyIf { findProperty("isMainHost") == "true" }
                }
        }
    }
}

tasks.matching { it.name.startsWith("generateMetadataFileFor") }.configureEach {
    dependsOn(tasks.matching { it.name.endsWith("MainKlib") || it.name.startsWith("compileKotlinIos") })
}

val secretPropsFile = rootProject.file("local.properties")
if (secretPropsFile.exists()) {
    secretPropsFile.reader().use {
        Properties().apply {
            load(it)
        }
    }.onEach { (name, value) ->
        ext[name.toString()] = value
    }
}

extensions.configure<MavenPublishBaseExtension>("mavenPublishing") {
    publishToMavenCentral()
    val hasSigningKey = hasProperty("signing.keyId") ||
        hasProperty("signing.secretKey") ||
        hasProperty("signingInMemoryKey") ||
        hasProperty("signingKey") ||
        System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKey") != null ||
        System.getenv("SIGNING_KEY") != null

    if (hasSigningKey) {
        signAllPublications()
    }

    configure(
        KotlinMultiplatform(
            javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml"),
            sourcesJar = SourcesJar.Sources(),
            androidVariantsToPublish = listOf("release"),
        )
    )

    pom {
        url.set("https://awakekt.github.io/awake")
        licenses {
            license {
                name.set("Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        issueManagement {
            system.set("Github")
            url.set("https://github.com/awakekt/awake/issues")
        }
        scm {
            connection.set("scm:git:git://github.com/awakekt/awake.git")
            developerConnection.set("scm:git:ssh://github.com:awakekt/awake.git")
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
