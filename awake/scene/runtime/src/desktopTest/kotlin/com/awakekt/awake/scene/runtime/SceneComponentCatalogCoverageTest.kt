/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneComponentCatalog
import com.awakekt.awake.scene.document.SceneSerializers
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.SerializersModuleCollector
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The catalog must list every component the loader can decode. The loader decodes through the
 * serializers module, so that module is the independent answer to "what is registered".
 */
class SceneComponentCatalogCoverageTest {

    /** The component ids the serializers module would decode, read from the module itself. */
    private fun idsTheLoaderCanDecode(): List<String> {
        val found = sortedSetOf<String>()
        SceneSerializers.buildSerializersModule().dumpTo(
            object : SerializersModuleCollector {
                override fun <Base : Any, Sub : Base> polymorphic(
                    baseClass: KClass<Base>,
                    actualClass: KClass<Sub>,
                    actualSerializer: KSerializer<Sub>,
                ) {
                    if (baseClass == SceneComponent::class) found += actualSerializer.descriptor.serialName
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
        return found.toList()
    }

    @Test
    fun theCatalogListsExactlyTheComponentsTheLoaderCanDecode() {
        installEveryComponentKit()

        assertEquals(idsTheLoaderCanDecode(), SceneComponentCatalog.ids())
    }

    @Test
    fun everyEnginesComponentReadsIntoASchemaWithoutError() {
        installEveryComponentKit()

        val schemas = SceneComponentCatalog.schemas()

        assertEquals(SceneComponentCatalog.ids(), schemas.keys.toList())
        assertTrue(schemas.isNotEmpty())
        assertTrue(schemas.values.all { it.name in schemas.keys }, "each schema is named by its component id")
    }
}
