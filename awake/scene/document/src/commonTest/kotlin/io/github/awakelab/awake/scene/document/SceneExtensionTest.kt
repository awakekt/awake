/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.document

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SceneExtensionTest {
    @Test
    fun unknownExtensionPayloadRoundTripsWithoutAProvider() {
        val payload = Json.parseToJsonElement(
            """{"heightmap":"assets/island.heightmap","seed":4201,"rules":"coastal-trees"}""",
        )
        val document = SceneDocument(
            extensions = listOf(
                SceneExtensionRecord(
                    id = SceneExtensionId("private.terrain-placement"),
                    version = 1,
                    payload = payload,
                ),
            ),
        )

        val decoded = SceneLoader.decode(SceneLoader.encode(document))
        val messages = SceneExtensionRegistry().validate(decoded)

        assertEquals(document, decoded)
        assertEquals(SceneExtensionValidationSeverity.Warning, messages.single().severity)
        assertTrue(messages.single().message.contains("private.terrain-placement"))
    }

    @Test
    fun registeredProviderValidationIsIncludedInDocumentValidation() {
        val registry = SceneExtensionRegistry()
        registry.register(FailingExtensionProvider)
        val document = SceneDocument(
            extensions = listOf(
                SceneExtensionRecord(
                    id = FailingExtensionProvider.id,
                    version = 1,
                    payload = Json.parseToJsonElement("{}"),
                ),
            ),
        )

        val failure = assertFailsWith<SceneValidationException> {
            SceneValidator.requireValid(document, registry)
        }

        assertTrue(failure.issues.single().message.contains("missing a required asset"))
    }
}

private object FailingExtensionProvider : SceneExtensionProvider {
    override val id = SceneExtensionId("awake.test.extension")

    override fun validate(record: SceneExtensionRecord): List<SceneExtensionValidationMessage> = listOf(
        SceneExtensionValidationMessage(
            SceneExtensionValidationSeverity.Error,
            "Extension is missing a required asset.",
        ),
    )
}
