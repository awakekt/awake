/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */

// Every module applying this convention must be classified here. The `when` blocks below
// previously matched the pre-rename paths `:awake:ui:ui-headless`/`:awake:ui:ui-shadcn`,
// so both modules silently fell to `emptyList()` and the check passed vacuously for months.
// This guard makes that failure mode impossible: an unlisted module fails the build instead
// of getting an empty rule set.
val classifiedUiModules = setOf(
    ":awake:ui:material3",
    ":awake:ui:shadcn",
    ":awake:tailwind",
    ":awake:heroicons",
    ":samples:ui-showcase",
    ":apps:studio",
)
check(project.path in classifiedUiModules) {
    "Unclassified module applies awake.ui-ownership-convention: ${project.path}. " +
        "Add it to classifiedUiModules with explicit (possibly empty) rules."
}

val forbiddenUiDeclarationNames = emptyList<String>()

val forbiddenUiTypeReferences = emptyList<String>()

val uiNamingLexiconPatterns = listOf(
    "\\bfun\\s+\\w*Scope\\.(emit|paint|render)[A-Z]",
    "\\bfun\\s+[\\w.]*\\.?Provide[A-Z]",
    "\\bfun\\s+\\w*Scope\\.(?!claim)[a-z]\\w*Slot\\s*\\(",
)

val forbiddenUiSourcePatterns = when (project.path) {
    ":awake:ui:material3",
    ":awake:ui:shadcn" -> uiNamingLexiconPatterns + listOf(
        "\\bprimitive\\s*\\.\\s*context\\b",
        "(?m)^import\\s+io\\.github\\.awakelab\\.awake\\.ui\\.UiScope",
        "(?m)^import\\s+io\\.github\\.awakelab\\.awake\\.ui\\.(layouts|popup|scope|animate|child|modifier|unstyled)",
        "(?m)^import\\s+io\\.github\\.awakelab\\.awake\\.ui\\.context\\.(?!UiLocal\\b|uiLocalOf\\b)",
    )
    ":samples:ui-showcase",
    ":apps:studio" -> listOf(
        "(?m)^import\\s+io\\.github\\.awakelab\\.awake\\.ui\\.modifier\\.",
        "\\bStyle\\s*\\{",
    )
    else -> emptyList()
}

val exemptUiSourcePatternFiles = emptyList<String>()

val verifyUiOwnership = tasks.register<VerifyUiOwnershipTask>("verifyUiOwnership") {
    group = "verification"
    description = "Reject helper-shaped or runtime-bound API drift in reusable UI modules."
    modulePath.set(project.path)
    sourceFiles.from(
        fileTree("src") {
            include("**/*Main/**/*.kt")
            exclude("**/*Test/**/*.kt")
        }
    )
    forbiddenDeclarationNames.set(forbiddenUiDeclarationNames)
    forbiddenTypeReferences.set(forbiddenUiTypeReferences)
    forbiddenSourcePatterns.set(forbiddenUiSourcePatterns)
    exemptSourcePatternFiles.set(exemptUiSourcePatternFiles)
}

tasks.named("check").configure {
    dependsOn(verifyUiOwnership)
}
