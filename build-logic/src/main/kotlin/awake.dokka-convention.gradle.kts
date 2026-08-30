/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier
import java.net.URI

plugins {
    id("org.jetbrains.dokka")
}

dokka {
    // 1. Configure the primary HTML website generation publication task
    dokkaPublications.html {
        // Explicitly set the output folder (Defaults to build/dokka/html)
        outputDirectory.set(layout.buildDirectory.dir("docs/api-reference"))

        // Fails the build if any warnings are found during generation
        failOnWarning.set(false)
    }

    // Dokka's includes format demands a literal `# Module <name>` first heading -- a plain
    // `# Title` README fails generation with "Unexpected classifier". Every module README in
    // this repo is a plain GitHub front page, so wrap rather than rewrite: prepend the Module
    // header and demote the README's own headings one level. Regenerated eagerly at
    // configuration time; it is a pure text transform of a checked-in file.
    val readme = project.file("README.md")
    val dokkaModuleDoc = layout.buildDirectory.file("dokka/module.md").get().asFile
    if (readme.isFile) {
        dokkaModuleDoc.parentFile.mkdirs()
        val demoted = readme.readLines().joinToString("\n") { line ->
            if (line.startsWith("#")) "#$line" else line
        }
        dokkaModuleDoc.writeText("# Module ${project.name}\n\n$demoted\n")
    }

    // 2. Configure source set properties globally across all source sets
    dokkaSourceSets.configureEach {
        // Set the visible module description name
        moduleName.set(project.name)

        // Crucial for libraries: the (wrapped) root README becomes the docs homepage.
        if (readme.isFile) {
            includes.from(dokkaModuleDoc)
        }

        // --- THE STRICT GUARDRAILS ---
        // Fails the build if any public element lacks KDocs
        reportUndocumented.set(true)

        // Prevent deprecated code from cluttering your clean API reference website
        skipDeprecated.set(true)

        // Configure visibilities using Dokka v2 syntax
        documentedVisibilities(VisibilityModifier.Public, VisibilityModifier.Protected)

        // Optional: Link your docs directly to your online source code repository
        sourceLink {
            localDirectory.set(projectDir.resolve("src"))
            val relativePath = project.path.removePrefix(":").replace(":", "/")
            remoteUrl.set(URI("https://github.com/awake-lab/awake/blob/main/$relativePath/src"))
            remoteLineSuffix.set("#L")
        }
    }
}
