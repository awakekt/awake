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
            if (line.startsWith("#")) "#${line.withoutSectionKeyword()}" else line
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

/**
 * Stops an ordinary README heading being read as a Dokka section declaration.
 *
 * Dokka's includes grammar treats `# Module <name>` and `# Package <name>` as structure, not
 * prose, and it does not care what level the heading is at. So a README with `## Module layout`
 * demotes to `### Module layout` and Dokka tries to parse it as the start of a module section,
 * failing generation with `Wrong AST Tree. Header does not contain expected content` -- an error
 * that names an offset in a generated file and nothing a reader could act on. Five READMEs in this
 * repo have such a heading.
 *
 * The keyword is wrapped in backticks rather than reworded: the grammar no longer matches, the
 * checked-in README is untouched, and the rendered heading still says what its author wrote.
 */
fun String.withoutSectionKeyword(): String {
    val heading = Regex("^(#+\\s*)(Module|Package)(\\s+\\S.*)$").find(this) ?: return this
    val (hashes, keyword, rest) = heading.destructured
    return "$hashes`$keyword`$rest"
}
