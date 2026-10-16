/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import com.vanniktech.maven.publish.JavaPlatform

// The Core alignment BOM (#811, docs/release-process.md "Core alignment BOM"). The root build script
// has every published Core module depend on this platform and fills in its constraints from the
// publications it finds, so the platform lists exactly what is published.
//
// Not `com.awakekt.awake.plugin.publish`: that convention publishes Kotlin Multiplatform, and this
// module has no Kotlin plugin.
plugins {
    `java-platform`
    id("com.vanniktech.maven.publish")
    id("com.awakekt.awake.plugin.central-staging")
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    val hasSigningKey = hasProperty("signing.keyId") ||
        hasProperty("signing.secretKey") ||
        hasProperty("signingInMemoryKey") ||
        hasProperty("signingKey") ||
        System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKey") != null ||
        System.getenv("SIGNING_KEY") != null
    if (hasSigningKey) {
        signAllPublications()
    }
    configure(JavaPlatform())

    pom {
        name.set("Awake Engine - bom")
        description.set("Version alignment platform for Awake Core modules.")
        url.set("https://docs.awakekt.com")
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
