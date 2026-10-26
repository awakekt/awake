/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.binding.processor

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

@OptIn(ExperimentalCompilerApi::class)
class WholeBindingProcessorTest {
    @Test
    fun wholeValuesPreserveIdentityWithoutConstructingTheLiveClass() {
        assertRuns(
            compileSceneBinding(
                """
            class Pulse private constructor(val rate: Int) {
                companion object { fun of(rate: Int) = Pulse(rate) }
            }
            typealias SavedPulse = Pulse
            @SceneBinding(Pulse::class, name = "WholePulseBinding", whole = "when")
            data class ScenePulse(val `when`: SavedPulse, val version: Int = 7) : SceneComponent
            object Probe {
                fun verify(): String = with(WholePulseBinding) {
                    val live = Pulse.of(42)
                    check(ScenePulse(live, 99).toComponent() === live)
                    val saved = live.toSceneComponent()
                    check(saved.`when` === live)
                    check(saved.version == 7)
                    check(TestSceneComponents.bindings.single() === WholePulseBinding)
                    "ok"
                }
            }
            """,
            ),
        )
    }

    @Test
    fun wholeConvertersAndAllFourHooksRunInTheirNormalOrder() {
        assertRuns(
            compileSceneBinding(
                """
            data class Pulse(val rate: Int)
            object PulseConverter : SceneValueConverter<String, Pulse> {
                override fun toLive(saved: String) = Pulse(saved.toInt())
                override fun toSaved(live: Pulse) = live.rate.toString()
            }
            object PulseHooks : SceneBindingHooks<Pulse, ScenePulse> {
                val events = mutableListOf<String>()
                override fun afterCopyToLive(saved: ScenePulse, live: Pulse): Pulse {
                    events += "load"
                    return live.copy(rate = live.rate + saved.scale)
                }
                override fun afterCopyToSaved(live: Pulse, saved: ScenePulse): ScenePulse {
                    events += "save"
                    return saved.copy(scale = 3)
                }
                override fun afterAttach(world: World, entity: Entity, saved: ScenePulse, live: Pulse, context: SceneResolutionContext) {
                    check(world.get<Pulse>(entity) === live)
                    events += "attach"
                }
                override fun beforeExport(world: World, entity: Entity, live: Pulse, saved: ScenePulse): ScenePulse? {
                    events += "export"
                    return saved.takeIf { live.rate > 0 }
                }
            }
            @SceneBinding(Pulse::class, whole = "payload", converters = [PulseConverter::class], hooks = PulseHooks::class)
            data class ScenePulse(val payload: String, @SavedOnly val scale: Int = 1) : SceneComponent
            class Context(override val world: World) : SceneResolutionContext {
                override fun deferNodeLink(targetNodeName: String, onResolved: (Entity) -> Unit) = Unit
                override fun recordRequest(request: Any) = Unit
            }
            object Probe {
                fun verify(): String = with(PulseBinding) {
                    val live = ScenePulse("4", 2).toComponent()
                    check(live == Pulse(6))
                    check(live.toSceneComponent() == ScenePulse("6", 3))
                    val world = World()
                    val entity = world.create()
                    attachTyped(world, entity, ScenePulse("4", 2), Context(world))
                    check(world.get<Pulse>(entity) == live)
                    check(export(world, entity, live) == ScenePulse("6", 3))
                    check(PulseHooks.events == listOf("load", "save", "load", "attach", "save", "export"))
                    check(export(world, entity, Pulse(-1)) == null)
                    "ok"
                }
            }
            """,
            ),
        )
    }

    @Test
    fun aWholeObjectValueCanBeWrappedInASchemaClass() {
        assertRuns(
            compileSceneBinding(
                """
            object Pulse
            @SceneBinding(Pulse::class, whole = "payload")
            data class ScenePulse(val payload: Pulse = Pulse) : SceneComponent
            object Probe {
                fun verify(): String = with(PulseBinding) {
                    check(ScenePulse().toComponent() === Pulse)
                    check(Pulse.toSceneComponent().payload === Pulse)
                    "ok"
                }
            }
            """,
            ),
        )
    }

    @Test
    fun aMissingWholePropertyIsACompileError() {
        assertFails(
            """
            class Pulse
            @SceneBinding(Pulse::class, whole = "missing")
            data class ScenePulse(val payload: Pulse) : SceneComponent
            """,
            "ScenePulse has no constructor property missing named by whole",
        )
    }

    @Test
    fun aWholeTypeMismatchNeedsAConverter() {
        assertFails(
            """
            class Pulse
            @SceneBinding(Pulse::class, whole = "payload")
            data class ScenePulse(val payload: String) : SceneComponent
            """,
            "list a SceneValueConverter<kotlin.String, test.Pulse> in converters",
        )
    }

    @Test
    fun aNullableWholeValueCannotBecomeANonNullComponent() {
        assertFails(
            """
            class Pulse
            @SceneBinding(Pulse::class, whole = "payload")
            data class ScenePulse(val payload: Pulse?) : SceneComponent
            """,
            "one is nullable and the other is not",
        )
    }

    @Test
    fun otherSchemaParametersNeedDefaults() {
        assertFails(
            """
            class Pulse
            @SceneBinding(Pulse::class, whole = "payload")
            data class ScenePulse(val payload: Pulse, val version: Int) : SceneComponent
            """,
            "ScenePulse.version is outside whole, so it needs a default",
        )
    }

    @Test
    fun theWholePropertyMustBeReadableAndCannotBeSavedOnly() {
        for (parameter in listOf("private val payload: Pulse", "payload: Pulse", "@SavedOnly val payload: Pulse")) {
            assertFails(
                """
                class Pulse
                @SceneBinding(Pulse::class, whole = "payload")
                class ScenePulse($parameter) : SceneComponent
                """,
                "ScenePulse.payload named by whole",
            )
        }
    }

    @Test
    fun wholeCannotSilentlyIgnoreFieldMappingOptions() {
        for (option in listOf("runtimeOnly = [\"rate\"]", "renamed = [SavedAs(\"old\", property = \"rate\")]")) {
            assertFails(
                """
                class Pulse(val rate: Int = 0)
                @SceneBinding(Pulse::class, whole = "payload", $option)
                data class ScenePulse(val payload: Pulse) : SceneComponent
                """,
                "whole cannot be combined with runtimeOnly or renamed",
            )
        }
    }

    private fun assertFails(body: String, message: String) {
        val result = compileSceneBinding(body)
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        assertContains(result.messages, message)
    }

    private fun assertRuns(result: JvmCompilationResult) {
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val probe = result.classLoader.loadClass("test.Probe")
        assertEquals("ok", probe.getMethod("verify").invoke(probe.getField("INSTANCE").get(null)))
    }
}
