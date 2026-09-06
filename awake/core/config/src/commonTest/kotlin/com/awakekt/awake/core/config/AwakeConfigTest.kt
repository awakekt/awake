/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AwakeConfigTest {

    enum class TestStage { Local, Dev, Staging, Prod }

    @Test
    fun dotEnvParserParsesStandardAndQuotedValues() {
        val sample = """
            # Server configuration
            STAGE=staging
            PORT=8080
            DEBUG="true"
            
            // Comment with slashes
            SINGLE_QUOTED='custom string value'
            UNQUOTED=some_token_123
            EMPTY=
        """.trimIndent()

        val parsed = DotEnvParser.parse(sample)

        assertEquals("staging", parsed["STAGE"])
        assertEquals("8080", parsed["PORT"])
        assertEquals("true", parsed["DEBUG"])
        assertEquals("custom string value", parsed["SINGLE_QUOTED"])
        assertEquals("some_token_123", parsed["UNQUOTED"])
        assertEquals("", parsed["EMPTY"])
        assertNull(parsed["NON_EXISTENT"])
    }

    @Test
    fun compositeEnvSourceRespectsPrecedence() {
        val highPriority = MapEnvSource(mapOf("KEY" to "high", "HIGH_ONLY" to "yes"))
        val lowPriority = MapEnvSource(mapOf("KEY" to "low", "LOW_ONLY" to "no"))

        val composite = CompositeEnvSource(highPriority, lowPriority)

        assertEquals("high", composite.get("KEY"))
        assertEquals("yes", composite.get("HIGH_ONLY"))
        assertEquals("no", composite.get("LOW_ONLY"))
        assertNull(composite.get("UNKNOWN"))
    }

    @Test
    fun awakeConfigResolvesTypedValuesCorrectly() {
        val config = AwakeConfig.fromMap(
            mapOf(
                "APP_NAME" to "Awake Engine",
                "IS_ENABLED" to "true",
                "IS_DISABLED" to "0",
                "MAX_CONNECTIONS" to "128",
                "TIMEOUT_MS" to "50000",
                "DEPLOY_STAGE" to "staging",
            ),
        )

        assertEquals("Awake Engine", config.getString("APP_NAME"))
        assertEquals("Default", config.getString("MISSING", default = "Default"))
        assertNull(config.getStringOrNull("MISSING"))

        assertTrue(config.getBoolean("IS_ENABLED"))
        assertFalse(config.getBoolean("IS_DISABLED"))
        assertTrue(config.getBoolean("MISSING_BOOL", default = true))

        assertEquals(128, config.getInt("MAX_CONNECTIONS"))
        assertEquals(42, config.getInt("MISSING_INT", default = 42))

        assertEquals(50000L, config.getLong("TIMEOUT_MS"))
        assertEquals(999L, config.getLong("MISSING_LONG", default = 999L))

        assertEquals(TestStage.Staging, config.getEnum("DEPLOY_STAGE", TestStage.Local))
        assertEquals(TestStage.Dev, config.getEnum("MISSING_STAGE", TestStage.Dev))
    }
}
