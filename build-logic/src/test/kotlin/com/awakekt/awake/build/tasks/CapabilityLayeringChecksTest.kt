/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.tasks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CapabilityLayeringChecksTest {
    private fun found(buildFile: String) = sceneDependenciesInMainCode(buildFile.trimIndent())

    @Test
    fun aMainDependencyOnASceneModuleIsFound() {
        val dependencies = found(
            """
            kotlin {
                sourceSets {
                    commonMain.dependencies {
                        api(project(":awake:core:math"))
                        api(project(":awake:scene:scene-core"))
                        implementation(project(":awake:scene:document"))
                    }
                }
            }
            """,
        )

        assertEquals(listOf(":awake:scene:scene-core", ":awake:scene:document"), dependencies)
    }

    @Test
    fun aDependencyOnAnythingElseIsNotFound() {
        assertEquals(
            emptyList(),
            found(
                """
                commonMain.dependencies {
                    api(project(":awake:core:math"))
                    implementation(project(":awake:scenery"))
                    implementation("com.example:scene:1.0")
                }
                """,
            ),
        )
    }

    @Test
    fun aTestOnlyDependencyIsNotFoundInAnyShapeTheRepoUses() {
        val dependencies = found(
            """
            kotlin {
                sourceSets {
                    commonTest.dependencies {
                        implementation(project(":awake:scene:runtime"))
                        if (true) {
                            implementation(project(":awake:scene:authoring"))
                        }
                    }
                    named("desktopTest").dependencies {
                        implementation(project(":awake:scene:binding"))
                    }
                    val androidInstrumentedTest by getting {
                        dependencies {
                            implementation(project(":awake:scene:document"))
                        }
                    }
                }
            }
            dependencies {
                testImplementation(project(":awake:scene:scene3d"))
            }
            """,
        )

        assertEquals(emptyList(), dependencies)
    }

    /** Leaving a test block must put the walk back in main code, or a later main dependency would hide. */
    @Test
    fun aMainDependencyAfterATestBlockIsStillFound() {
        val dependencies = found(
            """
            kotlin {
                sourceSets {
                    commonTest.dependencies {
                        implementation(project(":awake:scene:runtime"))
                    }
                    desktopMain.dependencies {
                        implementation(project(":awake:scene:world"))
                    }
                }
            }
            """,
        )

        assertEquals(listOf(":awake:scene:world"), dependencies)
    }

    @Test
    fun aTopLevelDependenciesBlockIsMainCodeUnlessTheConfigurationIsATestOne() {
        val dependencies = found(
            """
            dependencies {
                implementation(project(":awake:scene:scene3d"))
                testImplementation(project(":awake:scene:runtime"))
                androidTestImplementation(project(":awake:scene:document"))
            }
            """,
        )

        assertEquals(listOf(":awake:scene:scene3d"), dependencies)
    }

    @Test
    fun aDependencyDeclaredOnTheSameLineAsItsBlockIsClassifiedByThatBlock() {
        assertEquals(
            listOf(":awake:scene:world"),
            found("commonMain.dependencies { api(project(\":awake:scene:world\")) }"),
        )
        assertEquals(
            emptyList(),
            found("commonTest.dependencies { implementation(project(\":awake:scene:world\")) }"),
        )
    }

    @Test
    fun aCommentedOutDependencyIsNotFound() {
        assertEquals(emptyList(), found("""    // api(project(":awake:scene:scene-core"))"""))
    }

    @Test
    fun aTypeSafeAccessorIsFoundToo() {
        assertEquals(
            listOf("projects.awake.scene.sceneCore"),
            found(
                """
                commonMain.dependencies {
                    api(projects.awake.scene.sceneCore)
                }
                """,
            ),
        )
    }

    @Test
    fun aDependencyDeclaredTwiceIsReportedOnce() {
        assertEquals(
            listOf(":awake:scene:document"),
            found(
                """
                commonMain.dependencies { api(project(":awake:scene:document")) }
                desktopMain.dependencies { api(project(":awake:scene:document")) }
                """,
            ),
        )
    }

    // --- the ledger

    @Test
    fun aModuleWithASceneDependencyThatIsNotOnTheLedgerFailsAndSaysWhatToDo() {
        val failures = capabilityLayeringFailures(
            mainSceneDependencies = mapOf(":awake:particles" to listOf(":awake:scene:scene-core"), ":awake:core:math" to emptyList()),
            debt = emptySet(),
        )

        assertEquals(1, failures.size)
        assertTrue(":awake:particles depends on :awake:scene:scene-core" in failures.single(), failures.single())
        assertTrue("awake/scene/README.md" in failures.single(), "the message should say where the rule is: ${failures.single()}")
    }

    @Test
    fun aModuleOnTheLedgerIsToleratedWhileItStillHasTheDependency() {
        assertEquals(
            emptyList(),
            capabilityLayeringFailures(
                mainSceneDependencies = mapOf(":awake:navigation" to listOf(":awake:scene:world")),
                debt = setOf(":awake:navigation"),
            ),
        )
    }

    /** The ledger only shrinks: a debt that has been paid fails until its line is deleted. */
    @Test
    fun aModuleOnTheLedgerThatNoLongerHasTheDependencyFails() {
        val failures = capabilityLayeringFailures(
            mainSceneDependencies = mapOf(":awake:navigation" to emptyList()),
            debt = setOf(":awake:navigation", ":awake:gone"),
        )

        assertEquals(2, failures.size)
        assertTrue(failures.all { "Remove it from the list" in it }, failures.toString())
    }

    @Test
    fun cleanModulesAndAnEmptyLedgerPass() {
        assertEquals(
            emptyList(),
            capabilityLayeringFailures(mapOf(":awake:core:math" to emptyList(), ":awake:ecs" to emptyList()), emptySet()),
        )
    }
}
