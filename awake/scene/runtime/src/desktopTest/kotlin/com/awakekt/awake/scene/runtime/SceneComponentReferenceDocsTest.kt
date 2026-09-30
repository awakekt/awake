/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.ai.behavior.registerAiBehaviors
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.blueprint.registerBlueprints
import com.awakekt.awake.scene.character.registerCharacter
import com.awakekt.awake.scene.controls.movement.registerControls
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneSerializers
import com.awakekt.awake.scene.physics.registerPhysics
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.modules.SerializersModuleCollector
import java.io.File
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The scene document components reference page has one row per registered component id and one
 * field row per serialized field. A component or field added without a row fails here.
 */
class SceneComponentReferenceDocsTest {

    private val page = File("../../../website/docs/reference/scene-document-components.md").readText()

    @Test
    fun everyRegisteredComponentHasARowAndEveryFieldIsListed() {
        val registered = registeredComponents()
        val rows = Regex("""^\| \[`([a-z_]+)`]\(#""", RegexOption.MULTILINE)
            .findAll(page).map { it.groupValues[1] }.toSet()

        assertEquals(registered.keys.sorted(), rows.sorted(), "component ids on the reference page")
        for ((id, descriptor) in registered) {
            val section = page.substringAfter("## `$id`\n", missingDelimiterValue = "")
                .substringBefore("\n## ")
            assertTrue(section.isNotEmpty(), "no `## \\`$id\\`` section")
            for (field in descriptor.elementNames) {
                assertTrue("| `$field` |" in section, "`$id` field `$field` has no row")
            }
        }
    }

    /** Every component id the engine's registries install, with its serialized shape. */
    @OptIn(ExperimentalSerializationApi::class)
    private fun registeredComponents(): Map<String, SerialDescriptor> {
        DefaultSceneComponentResolvers.install()
        SceneComponentRegistry()
            .registerControls()
            .registerPhysics()
            .registerCharacter()
            .registerAiBehaviors()
            .registerBlueprints()

        val found = sortedMapOf<String, SerialDescriptor>()
        SceneSerializers.buildSerializersModule().dumpTo(
            object : SerializersModuleCollector {
                override fun <Base : Any, Sub : Base> polymorphic(
                    baseClass: KClass<Base>,
                    actualClass: KClass<Sub>,
                    actualSerializer: KSerializer<Sub>,
                ) {
                    if (baseClass == SceneComponent::class) {
                        found[actualSerializer.descriptor.serialName] = actualSerializer.descriptor
                    }
                }

                override fun <T : Any> contextual(
                    kClass: KClass<T>,
                    provider: (typeArgumentsSerializers: List<KSerializer<*>>) -> KSerializer<*>,
                ) = Unit

                override fun <Base : Any> polymorphicDefaultSerializer(
                    baseClass: KClass<Base>,
                    defaultSerializerProvider: (value: Base) -> SerializationStrategy<Base>?,
                ) = Unit

                override fun <Base : Any> polymorphicDefaultDeserializer(
                    baseClass: KClass<Base>,
                    defaultDeserializerProvider: (className: String?) -> DeserializationStrategy<Base>?,
                ) = Unit
            },
        )
        return found
    }
}
