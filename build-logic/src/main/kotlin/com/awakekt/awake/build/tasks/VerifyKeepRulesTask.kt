/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * Keeps an app that shrinks and obfuscates its release able to load Awake's native code.
 *
 * A module's native code reaches its Kotlin by name: JNI binds each `Java_…` symbol to a class and
 * method by name, and calls `FindClass`, `throw_new` or a type signature with a class name. R8 and
 * ProGuard rename what no rule keeps, and the app then fails at launch, or the first time that path
 * runs. So every class a module's native sources name is kept by its `consumer-rules.pro`, which the
 * library convention ships to apps (in the Android library, and in the desktop jar under
 * `META-INF/proguard/`). A module that declares `external fun` on the JVM has that file at all.
 *
 * What a third-party native library reaches, such as jolt-jni's, isn't in these sources, so its
 * rules can't be checked here; the template's obfuscated release build checks those by running.
 */
@DisableCachingByDefault(because = "Verification task with no outputs")
abstract class VerifyKeepRulesTask : DefaultTask() {
    /** Every `:awake:` module: module path to its directory. */
    @get:Input
    abstract val moduleDirectories: MapProperty<String, String>

    @TaskAction
    fun verify() {
        var checked = 0
        val failures = moduleDirectories.get().toSortedMap().flatMap { (module, path) ->
            val directory = File(path)
            val nativeSources = jniSources(directory)
            val declaresJni = declaresJvmExternals(directory)
            if (nativeSources.isNotEmpty() || declaresJni) checked++
            val rules = directory.resolve(CONSUMER_RULES).takeIf(File::isFile)?.readText()
            keepRuleFailures(module, rules, nativeSources, declaresJni)
        }
        if (failures.isNotEmpty()) {
            throw GradleException(
                "Keep rules check failed (${failures.size} problem(s)); an obfuscated app would rename what native code finds by name:\n" +
                    failures.joinToString("\n") { "  $it" },
            )
        }
        logger.lifecycle("Keep rules passed: $checked module(s) with JNI keep what their native code names")
    }

    /** The text of each native source under [module] that uses JNI, skipping build output and the modules nested in it. */
    private fun jniSources(module: File): List<String> =
        module.walkTopDown()
            .onEnter { directory -> directory == module || (directory.name !in SKIPPED_DIRECTORIES && !directory.isModule()) }
            .filter { it.isFile && it.extension in NATIVE_EXTENSIONS }
            .map(File::readText)
            .filter { text -> JNI_MARKERS.any(text::contains) }
            .toList()

    /** Whether a JVM or Android source set under [module] declares an `external fun`. */
    private fun declaresJvmExternals(module: File): Boolean =
        JVM_SOURCE_SETS.any { sourceSet ->
            module.resolve("src/$sourceSet").walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .any { file -> externalFunction.containsMatchIn(file.readText()) }
        }

    private fun File.isModule() = resolve("build.gradle.kts").isFile || resolve("build.gradle").isFile

    private companion object {
        const val CONSUMER_RULES = "consumer-rules.pro"
        val NATIVE_EXTENSIONS = setOf("c", "cc", "cpp", "h", "hpp", "m", "mm", "rs")
        val SKIPPED_DIRECTORIES = setOf("build", "target", ".cxx", ".gradle", "node_modules")
        val JNI_MARKERS = listOf("JNIEnv", "jni::", "Java_")
        val JVM_SOURCE_SETS = listOf("desktopMain", "androidMain", "jvmMain")
        val externalFunction = Regex("""^\s*(?:(?:private|internal|public|protected|actual|@JvmStatic)\s+)*external\s+fun\b""", RegexOption.MULTILINE)
    }
}

/**
 * What [module]'s [rules] fail to keep of what its [nativeSources] name: each class a `Java_…`
 * symbol binds native methods to, kept with its native method names, and each class named in a
 * string or type signature, kept by name. A module with JNI and no rules at all is a failure too,
 * whether [declaresJni] says its Kotlin declares `external fun` or its native sources show it.
 */
internal fun keepRuleFailures(module: String, rules: String?, nativeSources: List<String>, declaresJni: Boolean): List<String> {
    val owners = nativeSources.flatMapTo(sortedSetOf(), ::jniMethodOwners)
    val named = nativeSources.flatMapTo(sortedSetOf(), ::jniNamedClasses) - owners
    if (rules == null) {
        return if (declaresJni || owners.isNotEmpty() || named.isNotEmpty()) {
            listOf("$module uses JNI but has no consumer-rules.pro")
        } else {
            emptyList()
        }
    }
    val kept = keepRules(rules)
    return owners.filterNot { owner -> kept.any { it.keepsNativeMethodsOf(owner) } }
        .map { "$module: no rule keeps the native methods of $it" } +
        named.filterNot { name -> kept.any { it.keepsName(name) } }
            .map { "$module: no rule keeps $it, which its native code names" }
}

/** The classes whose native methods [source]'s `Java_…` symbols implement. */
internal fun jniMethodOwners(source: String): Set<String> =
    Regex("""\bJava_(\w+)""").findAll(source).mapNotNull { match ->
        // An overloaded method's symbol ends in "__" and its mangled signature.
        val segments = match.groupValues[1].substringBefore("__").split(Regex("_(?![0-9])"))
        if (segments.size < 2) return@mapNotNull null
        segments.dropLast(1).joinToString(".") { segment ->
            segment.replace("_00024", "$").replace("_1", "_")
        }
    }.toSet()

/** The classes [source] names in a string such as `"com/example/Thing"` or a signature's `Lcom/example/Thing;`. */
internal fun jniNamedClasses(source: String): Set<String> {
    val reference = Regex(""""((?:com|org|net|io|kotlin|kotlinx|androidx)/[\w$/]+)"|L([a-z]\w*(?:/[\w$]+)+);""")
    return reference.findAll(source)
        .map { (it.groupValues[1].ifEmpty { it.groupValues[2] }).replace('/', '.') }
        // The platform's own classes are never renamed.
        .filterNot { name -> PLATFORM_PACKAGES.any(name::startsWith) }
        .toSet()
}

private val PLATFORM_PACKAGES = listOf("java.", "javax.", "android.", "dalvik.")

/** A `-keep…` rule, as much of it as the check reads. */
internal data class KeepRule(val option: String, val classPattern: Regex, val members: String) {
    /** Whether this rule keeps [className] and the names of its native methods. */
    fun keepsNativeMethodsOf(className: String): Boolean =
        option in NATIVE_KEEPING_OPTIONS && classPattern.matches(className) &&
            (members.contains("*;") || members.contains("<methods>"))

    /** Whether this rule keeps [className]'s name. */
    fun keepsName(className: String): Boolean = option in NAME_KEEPING_OPTIONS && classPattern.matches(className)

    private companion object {
        val NAME_KEEPING_OPTIONS = setOf("keep", "keepnames")
        val NATIVE_KEEPING_OPTIONS = NAME_KEEPING_OPTIONS + setOf("keepclasseswithmembers", "keepclasseswithmembernames")
    }
}

/**
 * The rules in [text] that name their classes by a pattern. A rule that allows obfuscation, or picks
 * classes by what they extend or implement, keeps no name the check can rely on, so it's left out.
 */
internal fun keepRules(text: String): List<KeepRule> {
    val uncommented = text.lineSequence().joinToString("\n") { it.substringBefore('#') }
    val rule = Regex(
        """-(keep\w*)((?:,\w+)*)\s+(?:(?:public|final|abstract)\s+)*(?:class|interface|enum)\s+([\w.*?$]+)""" +
            """(\s+(?:extends|implements)\s+\S+)?\s*(?:\{([^}]*)})?""",
    )
    return rule.findAll(uncommented)
        .filter { match -> "allowobfuscation" !in match.groupValues[2] && match.groupValues[4].isEmpty() }
        .map { match -> KeepRule(match.groupValues[1], classPattern(match.groupValues[3]), match.groupValues[5]) }
        .toList()
}

/** A ProGuard class name pattern as a regex: `**` spans packages, `*` and `?` stay within one. */
private fun classPattern(pattern: String): Regex =
    Regex(
        pattern.split("**").joinToString(".*") { part ->
            part.split("*").joinToString("[^.]*") { piece ->
                piece.split("?").joinToString("[^.]") { Regex.escape(it) }
            }
        },
    )
