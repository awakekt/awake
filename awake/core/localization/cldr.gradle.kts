/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */

// `./gradlew :awake:core:localization:generateCldrNumberData` writes CldrNumberData.kt from Unicode
// CLDR's JSON release: for each language PluralRules covers, the Latin-digit number symbols, the
// percent pattern, minimumGroupingDigits, the short compact patterns and the ordinal rules. Nothing in
// the output is written by hand. It reads the pinned release from GitHub, or a local checkout's
// cldr-json directory with `-Pcldr.json=<dir>`. Bump CLDR_VERSION to move to a newer release, run it,
// then run :awake:core:localization:desktopTest. It is never part of a build.

val cldrVersion = "48.0.0"
val cldrLocales = listOf(
    "ar", "de", "en", "es", "et", "fi", "fil", "fr", "id", "it", "ja", "ko", "ms", "nl", "pl",
    "pt", "pt-PT", "ru", "sv", "th", "tr", "uk", "vi", "yue", "zh",
)
val cldrRoot = "und"
val pluralCategories = listOf("zero", "one", "two", "few", "many", "other")

tasks.register("generateCldrNumberData") {
    group = "localization"
    description = "Writes CldrNumberData.kt from the pinned Unicode CLDR JSON release."
    val localJson = providers.gradleProperty("cldr.json").orNull
    val out = layout.projectDirectory.file(
        "src/commonMain/kotlin/com/awakekt/awake/core/localization/CldrNumberData.kt",
    ).asFile
    notCompatibleWithConfigurationCache("A by-hand generator that reads the network.")
    doLast {
        fun read(path: String): Map<*, *> {
            val text = if (localJson != null) {
                File(localJson, path).readText()
            } else {
                uri("https://raw.githubusercontent.com/unicode-org/cldr-json/$cldrVersion/cldr-json/$path").toURL().readText()
            }
            return groovy.json.JsonSlurper().parseText(text) as Map<*, *>
        }

        fun Map<*, *>.at(vararg keys: String): Any? = keys.fold<String, Any?>(this) { node, key -> (node as Map<*, *>)[key] }

        fun kotlinString(text: String): String = buildString {
            append('"')
            for (c in text) {
                when {
                    c == '\\' || c == '"' || c == '$' -> append('\\').append(c)
                    c.code < 0x20 || c.code > 0x7E -> append("\\u").append(c.code.toString(16).uppercase().padStart(4, '0'))
                    else -> append(c)
                }
            }
            append('"')
        }

        // CLDR quotes literal text with apostrophes; '' is one apostrophe.
        fun unquote(pattern: String): String = buildString {
            var i = 0
            while (i < pattern.length) {
                val c = pattern[i]
                if (c != '\'') {
                    append(c)
                } else if (i + 1 < pattern.length && pattern[i + 1] == '\'') {
                    append('\'')
                    i++
                }
                i++
            }
        }

        // '#,##0 %' becomes '0 %', with the locale's percent sign: the number goes where the 0 is.
        fun percentPattern(pattern: String, sign: String): String {
            val positive = pattern.substringBefore(';')
            val first = positive.indexOfFirst { it == '#' || it == '0' }
            val last = positive.indexOfLast { it == '#' || it == '0' }
            return (unquote(positive.substring(0, first)) + "0" + unquote(positive.substring(last + 1))).replace("%", sign)
        }

        // Exponent 3 through 14, each one pattern, or 'category=pattern|...' when the forms differ.
        fun compactPatterns(short: Map<*, *>): List<String> = (3..14).map { exponent ->
            val power = "1" + "0".repeat(exponent)
            val forms = pluralCategories.mapNotNull { category -> (short["$power-count-$category"] as String?)?.let { category to unquote(it) } }
            when {
                forms.isEmpty() -> "0"
                forms.map { it.second }.distinct().size == 1 -> forms.first().second
                else -> forms.joinToString("|") { (category, pattern) -> "$category=$pattern" }
            }
        }

        fun entry(tag: String): String {
            val numbers = read("cldr-numbers-full/main/$tag/numbers.json").at("main", tag, "numbers") as Map<*, *>
            check((numbers["defaultNumberingSystem"] ?: "latn") == "latn") { "$tag does not default to Latin digits; the formatter only writes those." }
            val symbols = numbers["symbols-numberSystem-latn"] as Map<*, *>
            val decimalPattern = numbers.at("decimalFormats-numberSystem-latn", "standard") as String
            check(decimalPattern.substringBefore(';') == "#,##0.###") { "$tag formats decimals as $decimalPattern; the formatter only groups by three." }
            val percent = percentPattern(numbers.at("percentFormats-numberSystem-latn", "standard") as String, symbols["percentSign"] as String)
            val short = numbers.at("decimalFormats-numberSystem-latn", "short", "decimalFormat") as Map<*, *>
            val fields = listOf(
                "decimal = ${kotlinString(symbols["decimal"] as String)}",
                "group = ${kotlinString(symbols["group"] as String)}",
                "minus = ${kotlinString(symbols["minusSign"] as String)}",
                "percent = ${kotlinString(percent)}",
                "minimumGrouping = ${(numbers["minimumGroupingDigits"] as String?)?.toInt() ?: 1}",
                "compact = listOf(" + compactPatterns(short).joinToString(", ") { kotlinString(it) } + ")",
            )
            return "        ${kotlinString(tag)} to CldrNumbers(\n            ${fields.joinToString(",\n            ")},\n        ),"
        }

        val ordinals = read("cldr-core/supplemental/ordinals.json").at("supplemental", "plurals-type-ordinal") as Map<*, *>
        val ordinalEntries = cldrLocales.map { it.substringBefore('-') }.distinct().sorted().mapNotNull { language ->
            val rules = ordinals[language] as Map<*, *>? ?: emptyMap<String, String>()
            val text = pluralCategories.dropLast(1).mapNotNull { category ->
                (rules["pluralRule-count-$category"] as String?)?.let { "$category: ${it.substringBefore('@').trim()}" }
            }.joinToString("; ")
            if (text.isEmpty()) null else "        ${kotlinString(language)} to ${kotlinString(text)},"
        }
        val entries = (listOf(cldrRoot) + cldrLocales).joinToString("\n") { entry(it) }
        out.writeText(
            """
            |/*
            | * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
            | *
            | * SPDX-License-Identifier: Apache-2.0
            | */
            |package com.awakekt.awake.core.localization
            |
            |// Generated by `./gradlew :awake:core:localization:generateCldrNumberData` from Unicode CLDR $cldrVersion.
            |// Do not edit; run it again.
            |
            |/** Unicode CLDR $cldrVersion number data, by locale tag; `und` is CLDR's root. */
            |internal object CldrNumberData {
            |    const val VERSION: String = "$cldrVersion"
            |
            |    val locales: Map<String, CldrNumbers> = mapOf(
            |$entries
            |    )
            |
            |    /**
            |     * The ordinal rules of each language that has any, `category: condition` joined by `; `. A
            |     * language not here, or not covered, uses `other` for every number.
            |     */
            |    val ordinals: Map<String, String> = mapOf(
            |${ordinalEntries.joinToString("\n")}
            |    )
            |}
            |
            """.trimMargin(),
        )
        logger.lifecycle("Wrote $out")
    }
}
