/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.binding.processor

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

@OptIn(ExperimentalCompilerApi::class)
class SceneBindingProcessorTest {
    @Test
    fun aMatchingPairCompilesWithRenamesConvertersEnumsAndHooks() {
        val result = compile(
            """
            enum class Mode { On, Off }
            enum class SavedMode { On, Off }
            class Pulse(var rate: Float = 1f, val mode: Mode = Mode.On, var label: Label = Label("")) {
                @RuntimeOnly var pressed: Boolean = false
                @SavedAs("speed") var step: Int = 0
            }
            data class Label(val text: String)
            object LabelConverter : SceneValueConverter<String, Label> {
                override fun toLive(saved: String) = Label(saved)
                override fun toSaved(live: Label) = live.text
            }
            object PulseHooks : SceneBindingHooks<Pulse, ScenePulse> {
                override fun afterCopyToLive(saved: ScenePulse, live: Pulse): Pulse = live.also { it.rate *= saved.legacyScale }
            }
            @SceneBinding(Pulse::class, converters = [LabelConverter::class], hooks = PulseHooks::class)
            data class ScenePulse(
                val rate: Float = 1f,
                val mode: SavedMode = SavedMode.On,
                val label: String = "",
                val speed: Int = 0,
                @SavedOnly val legacyScale: Float = 1f,
            ) : SceneComponent
            """,
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val registry = result.classLoader.loadClass("test.TestSceneComponents")
        val bindings = registry.getMethod("getBindings").invoke(registry.getField("INSTANCE").get(null)) as List<*>
        assertEquals(listOf("test.PulseBinding"), bindings.map { it!!::class.qualifiedName })
    }

    @Test
    fun aLiveFieldWithNoSavedFieldIsACompileError() {
        val result = compile(
            """
            class Pulse(var rate: Float = 1f, var depth: Float = 0f)
            @SceneBinding(Pulse::class)
            data class ScenePulse(val rate: Float = 1f) : SceneComponent
            """,
        )
        assertFails(result, "Pulse.depth has no saved field ScenePulse.depth")
    }

    @Test
    fun aSavedFieldWithNoLiveFieldIsACompileError() {
        val result = compile(
            """
            class Pulse(var rate: Float = 1f)
            @SceneBinding(Pulse::class)
            data class ScenePulse(val rate: Float = 1f, val depth: Float = 0f) : SceneComponent
            """,
        )
        assertFails(result, "ScenePulse.depth has no live property in Pulse")
    }

    @Test
    fun aTypeMismatchWithNoConverterIsACompileError() {
        val result = compile(
            """
            class Pulse(var rate: Double = 1.0)
            @SceneBinding(Pulse::class)
            data class ScenePulse(val rate: Float = 1f) : SceneComponent
            """,
        )
        assertFails(result, "kotlin.Float is not kotlin.Double")
    }

    @Test
    fun enumsWithDifferentConstantsAreACompileError() {
        val result = compile(
            """
            enum class Mode { On, Off }
            enum class SavedMode { On, Idle }
            class Pulse(var mode: Mode = Mode.On)
            @SceneBinding(Pulse::class)
            data class ScenePulse(val mode: SavedMode = SavedMode.On) : SceneComponent
            """,
        )
        assertFails(result, "differ in Idle, Off")
    }

    @Test
    fun aSceneComponentWithNoBindingIsACompileError() {
        val result = compile("data class SceneOrphan(val x: Float = 0f) : SceneComponent")
        assertFails(result, "SceneOrphan is a scene component with no binding in this module")
    }

    @Test
    fun aHandWrittenBindingTheRegistryDoesNotListIsACompileError() {
        val result = compile(
            """
            class Pulse
            @UnboundSceneComponent("bound by hand below")
            data class ScenePulse(val x: Float = 0f) : SceneComponent
            object PulseBinding : SceneComponentBinding<Pulse, ScenePulse> {
                override val componentClass = Pulse::class
                override val schemaClass = ScenePulse::class
                override fun attachTyped(world: World, entity: Entity, component: ScenePulse, context: SceneResolutionContext) = Unit
                override fun export(world: World, entity: Entity, component: Pulse): ScenePulse? = null
            }
            """,
        )
        assertFails(result, "PulseBinding is a scene component binding the module's registry doesn't list")
    }

    @Test
    fun aRegisteredBindingCoversItsSchemaAndJoinsTheRegistry() {
        val result = compile(
            """
            class Pulse
            data class ScenePulse(val x: Float = 0f) : SceneComponent
            @RegisteredBinding
            object PulseBinding : SceneComponentBinding<Pulse, ScenePulse> {
                override val componentClass = Pulse::class
                override val schemaClass = ScenePulse::class
                override fun attachTyped(world: World, entity: Entity, component: ScenePulse, context: SceneResolutionContext) = Unit
                override fun export(world: World, entity: Entity, component: Pulse): ScenePulse? = null
            }
            """,
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
    }

    private fun assertFails(result: JvmCompilationResult, message: String) {
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, result.messages)
        assertContains(result.messages, message)
    }

    private fun compile(body: String): JvmCompilationResult {
        val source = SourceFile.kotlin(
            "Test.kt",
            """
            package test

            import com.awakekt.awake.ecs.Entity
            import com.awakekt.awake.ecs.World
            import com.awakekt.awake.scene.binding.*
            import com.awakekt.awake.scene.document.SceneComponent

            ${body.trimIndent()}
            """.trimIndent(),
        )
        return KotlinCompilation().apply {
            sources = listOf(source)
            inheritClassPath = true
            jvmTarget = "17"
            messageOutputStream = System.out
            configureKsp {
                symbolProcessorProviders += SceneBindingProcessorProvider()
                processorOptions[Names.REGISTRY_OPTION] = "test.TestSceneComponents"
            }
        }.compile()
    }
}
