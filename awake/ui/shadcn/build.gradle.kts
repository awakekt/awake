/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    id("awake.kmp-library-convention")
    id("awake.publish-convention")
    id("awake.dokka-convention")
    id("awake.detekt-convention")
    id("awake.spotless-convention")
    id("awake.ui-ownership-convention")
    id("awake.ui-authored-units-convention")
    id("awake.test-resources-convention")
}

kotlin {
    android {
        namespace = "io.github.awakelab.awake.ui.shadcn"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":awake:core:graphics2d"))
            implementation(project(":awake:core:math2d"))
            implementation(project(":awake:core:math"))
            implementation(project(":awake:core:color"))
            implementation(project(":awake:core:input"))
            api(project(":awake:tailwind"))
            api(project(":awake:heroicons"))
            api(project(":awake:compose:foundation"))
        }
        commonTest.dependencies {
            implementation(project(":awake:compose:ui-testing"))
            // The coalescer, so UiFrameCostRatchetTest can count the draw runs a frame actually
            // produces rather than infer them from primitives.
            implementation(project(":awake:engine:render:passes2d"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(kotlin("test"))
        }
        desktopTest.dependencies {
            implementation(project(":awake:compose:ui-testing"))
        }
    }
}

awakeTestResources {
    roots.from(layout.projectDirectory.dir("src/commonMain/resources"))
}

tasks.named<Test>("desktopTest") {
    // `-DAWAKE_RECORD_SNAPSHOTS=true` on the Gradle CLI only sets the property on Gradle's own
    // JVM -- desktopTest runs in a forked test JVM, so forward it explicitly. Same fix
    // awake.ui-preview-report-convention applies for the ui-preview modules.
    System.getProperty("AWAKE_RECORD_SNAPSHOTS")
        ?.let { systemProperty("AWAKE_RECORD_SNAPSHOTS", it) }

    // ShadcnBaselineCoverageTest reads the component sources and the baseline directory off disk,
    // so neither is an input Gradle can infer from the classpath. Without these, adding a component
    // leaves the compiled output identical and the task is served FROM-CACHE -- the coverage gate
    // reports green on exactly the change it exists to catch. Verified: it did.
    inputs.dir(layout.projectDirectory.dir("src/commonMain/kotlin/io/github/awakelab/awake/ui/shadcn/components"))
        .withPropertyName("shadcnComponentSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(layout.projectDirectory.dir("src/desktopTest/resources/baselines"))
        .withPropertyName("shadcnVisualBaselines")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

// The Core-import boundary is enforced by verifyUiOwnership (awake.ui-ownership-convention);
// the former auditUiShadcnHeadlessBoundary/verifyUiShadcnClasspath/
// reportUiShadcnMigrationProgress tasks were deleted 2026-08-17 — the first duplicated
// the convention's rules without being wired into `check`, the second asserted the opposite
// of its own description, and the third read a module path deleted with designsystem-compat.

tasks.register("auditUiShadcnComponentNaming") {
    group = "verification"
    description =
        "Verifies design-system component files use Shadcn naming and matching family packages."
    val componentsRoot = layout.projectDirectory.dir(
        "src/commonMain/kotlin/io/github/awakelab/awake/ui/shadcn/components",
    )
    val packagePrefix = "io.github.awakelab.awake.ui.shadcn.components"
    doLast {
        val violations = componentsRoot.asFile.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .mapNotNull { file ->
                val relative = file.relativeTo(componentsRoot.asFile).invariantSeparatorsPath
                val family = relative.substringBeforeLast('/', missingDelimiterValue = "")
                val expectedPackage = if (family.isEmpty()) {
                    packagePrefix
                } else {
                    "$packagePrefix.${family.replace('/', '.')}"
                }
                val packageDeclaration = file.useLines { lines ->
                    lines.firstOrNull { it.startsWith("package ") }
                }
                when {
                    !file.name.startsWith("Shadcn") ->
                        "${file.relativeTo(rootProject.projectDir)} must start with Shadcn"

                    packageDeclaration != "package $expectedPackage" ->
                        "${file.relativeTo(rootProject.projectDir)} must declare package $expectedPackage"

                    else -> null
                }
            }
            .toList()
        check(violations.isEmpty()) {
            "ui-shadcn component naming/package violations:\n${violations.joinToString("\n")}"
        }
        println(
            "ui-shadcn component naming: ${
                componentsRoot.asFile.walkTopDown().count { it.isFile && it.extension == "kt" }
            } files verified"
        )
    }
}

tasks.register("auditUiShadcnRecipeDuplicates") {
    group = "verification"
    description =
        "Rejects the same-receiver Shadcn recipe declared in more than one file."
    val componentsRoot = layout.projectDirectory.dir(
        "src/commonMain/kotlin/io/github/awakelab/awake/ui/shadcn/components",
    )
    val declaration = Regex("""fun\s+(?:<[^>]+>\s*)?((?:[\w.]+)\.)?(shadcn[A-Z]\w*)\s*\(""")
    doLast {
        // Keyed by file, not package: components live flat in one package by design, so a
        // package key can never fire (two shadcnEmpty implementations shipped behind it).
        // Same-file overloads (string convenience beside the slot form) stay legal.
        val declarations = componentsRoot.asFile.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                declaration.findAll(file.readText()).map { match ->
                    val receiver = match.groupValues[1].removeSuffix(".").ifEmpty { "<top-level>" }
                    val name = match.groupValues[2]
                    "$receiver.$name" to file.relativeTo(rootProject.projectDir)
                }.asSequence()
            }
            .groupBy({ it.first }, { it.second })
        val violations = declarations
            .filterValues { files -> files.distinct().size > 1 }
            .flatMap { (signature, files) ->
                files.distinct().map { file -> "$signature declared in $file" }
            }
        check(violations.isEmpty()) {
            "Same ui-shadcn Shadcn recipe declared in multiple files:\n${violations.joinToString("\n")}"
        }
        println("ui-shadcn recipe duplicates: none across files")
    }
}

tasks.register("auditUiShadcnComponentCoverage") {
    group = "verification"
    description = "Verifies every public design-system recipe is backed by a behaviour layer."
    val componentsRoot = layout.projectDirectory.dir(
        "src/commonMain/kotlin/io/github/awakelab/awake/ui/shadcn/components",
    )
    doLast {
        val componentFiles = componentsRoot.asFile.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
        val violations = componentFiles
            .mapNotNull { file ->
                val source = file.readText()
                // Only a file that *composes* can delegate. A recipe is a function taking a
                // `Composer` context or a `UiScope` receiver; everything else here -- variant enums,
                // token tables, pure geometry like ShadcnSliderMath -- has nothing to delegate *to*.
                //
                // Keyed on that rather than on filenames, because a suffix list grew a new exception
                // per file (Contracts, then Math, then Variant) until the list was the rule. Keyed
                // on the name `shadcn*` alone it also failed, since `shadcnSliderTrack` is a
                // function and not a recipe.
                val composes = source.contains("context(_: Composer)")
                val delegates = source.contains("io.github.awakelab.awake.compose.")
                when {
                    !composes -> null
                    !delegates ->
                        "${file.relativeTo(rootProject.projectDir)} must delegate through :compose:foundation"

                    else -> null
                }
            }
            .toList()
        check(violations.isEmpty()) {
            "ui-shadcn component coverage violations:\n${violations.joinToString("\n")}"
        }
        println(
            "ui-shadcn component coverage: " +
                    componentFiles.count { !it.name.endsWith("Contracts.kt") } +
                    " public files are Compose-backed",
        )
    }
}

tasks.named("check") {
    dependsOn(
        "auditUiShadcnComponentNaming",
        "auditUiShadcnRecipeDuplicates",
        "auditUiShadcnComponentCoverage",
    )
}
