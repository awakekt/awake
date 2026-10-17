/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.definition

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** Which document a [AiFormatMigration] applies to. */
enum class AiDocumentType {
    /** A [BehaviorTreeDefinition]. */
    BehaviorTree,

    /** A [StateMachineDefinition]. */
    StateMachine,
}

/**
 * Rewrites a document of [document] type from format [fromVersion] to `fromVersion + 1`, as JSON,
 * before it is read. [AiDefinitionJson] runs these in order to bring an older document up to the
 * current format.
 *
 * @property document The document type.
 * @property fromVersion The format it reads.
 * @property migrate The rewrite. It need not set `formatVersion`; the reader does.
 */
class AiFormatMigration(
    val document: AiDocumentType,
    val fromVersion: Int,
    val migrate: (JsonObject) -> JsonObject,
)

/**
 * The JSON form of behaviour tree and state machine definitions.
 *
 * Strict, as a project file should be: an unknown field fails rather than being dropped, and the
 * message says where. A document records its `formatVersion`; one without it is format 1. An older
 * document is brought up to date by [migrations], and a newer one is refused, since this build
 * cannot know what it means.
 *
 * Reading does not check node kinds or parameters: that needs a registry, and
 * [AiRegistry.compileTree] does it.
 *
 * @property migrations The format migrations to apply, in any order.
 */
class AiDefinitionJson(val migrations: List<AiFormatMigration> = emptyList()) {
    private val json = Json {
        prettyPrint = true
        explicitNulls = false
    }

    /** [definition] as pretty-printed JSON. Empty fields are left out. */
    fun encode(definition: BehaviorTreeDefinition): String = json.encodeToString(BehaviorTreeDefinition.serializer(), definition)

    /** [definition] as pretty-printed JSON. Empty fields are left out. */
    fun encode(definition: StateMachineDefinition): String = json.encodeToString(StateMachineDefinition.serializer(), definition)

    /**
     * Reads a behaviour tree definition, migrating an older format.
     *
     * @throws AiDefinitionException when [text] is not one.
     */
    fun decodeTree(text: String): BehaviorTreeDefinition =
        decode(
            text,
            AiDocumentType.BehaviorTree,
            BehaviorTreeDefinition.FORMAT_VERSION,
            BehaviorTreeDefinition.serializer(),
        )

    /**
     * Reads a state machine definition, migrating an older format.
     *
     * @throws AiDefinitionException when [text] is not one.
     */
    fun decodeStateMachine(text: String): StateMachineDefinition =
        decode(
            text,
            AiDocumentType.StateMachine,
            StateMachineDefinition.FORMAT_VERSION,
            StateMachineDefinition.serializer(),
        )

    private fun <T> decode(text: String, type: AiDocumentType, current: Int, serializer: KSerializer<T>): T {
        val document = try {
            json.parseToJsonElement(text) as? JsonObject ?: fail(AiProblemCode.MALFORMED, "a definition is a JSON object.")
        } catch (e: SerializationException) {
            fail(AiProblemCode.MALFORMED, "not JSON: ${e.message?.lineSequence()?.first()}")
        }
        val version = when (val field = document["formatVersion"]) {
            null -> 1
            else -> (field as? JsonPrimitive)?.intOrNull ?: fail(AiProblemCode.MALFORMED, "formatVersion is not a whole number.")
        }
        if (version > current) {
            fail(AiProblemCode.UNSUPPORTED_FORMAT, "formatVersion $version is newer than this build reads ($current).")
        }
        var migrated = document
        for (from in version until current) {
            val step = migrations.firstOrNull { it.document == type && it.fromVersion == from }
                ?: fail(AiProblemCode.UNSUPPORTED_FORMAT, "no migration reads formatVersion $from to ${from + 1}.")
            migrated = step.migrate(migrated)
        }
        migrated = JsonObject(migrated + ("formatVersion" to JsonPrimitive(current)))
        return try {
            json.decodeFromJsonElement(serializer, migrated)
        } catch (e: IllegalArgumentException) {
            fail(AiProblemCode.MALFORMED, e.message?.lineSequence()?.first() ?: "the document could not be read.")
        }
    }

    private fun fail(code: AiProblemCode, message: String): Nothing =
        throw AiDefinitionException(listOf(AiDefinitionProblem(code, "", message)))

    /** A reader with no migrations. */
    companion object {
        /** Reads and writes the current formats, with no migrations. */
        val Default: AiDefinitionJson = AiDefinitionJson()
    }
}
