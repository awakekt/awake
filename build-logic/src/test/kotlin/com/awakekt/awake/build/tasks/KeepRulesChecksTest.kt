/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.tasks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KeepRulesChecksTest {
    private val rust = """
        fn throw(env: &mut JNIEnv, message: &str) {
            let _ = env.throw_new("com/example/shaders/ShaderException", message);
        }
        #[no_mangle]
        pub extern "system" fn Java_com_example_shaders_ShaderJni_compile(env: JNIEnv, source: JString) -> jbyteArray {}
    """.trimIndent()
    private val rules = """
        # Native methods, by name.
        -keepclasseswithmembernames class com.example.shaders.** {
            native <methods>;
        }
        -keep class com.example.shaders.ShaderException { <init>(java.lang.String); }
    """.trimIndent()

    @Test
    fun rulesThatKeepWhatTheNativeCodeNamesPass() {
        assertTrue(keepRuleFailures(MODULE, rules, listOf(rust), declaresJni = true).isEmpty())
    }

    @Test
    fun aClassTheNativeCodeThrowsByNameNeedsItsOwnRule() {
        val nativeMethodsOnly = rules.lines().take(4).joinToString("\n")

        assertEquals(
            listOf("$MODULE: no rule keeps com.example.shaders.ShaderException, which its native code names"),
            keepRuleFailures(MODULE, nativeMethodsOnly, listOf(rust), declaresJni = true),
        )
    }

    @Test
    fun nativeMethodsNoRuleKeepsFail() {
        val exceptionOnly = rules.lines().last()

        assertEquals(
            listOf("$MODULE: no rule keeps the native methods of com.example.shaders.ShaderJni"),
            keepRuleFailures(MODULE, exceptionOnly, listOf(rust), declaresJni = true),
        )
    }

    @Test
    fun aModuleWithJniAndNoRulesFails() {
        assertEquals(listOf("$MODULE uses JNI but has no consumer-rules.pro"), keepRuleFailures(MODULE, null, listOf(rust), declaresJni = false))
        assertEquals(listOf("$MODULE uses JNI but has no consumer-rules.pro"), keepRuleFailures(MODULE, null, emptyList(), declaresJni = true))
        assertTrue(keepRuleFailures(MODULE, null, emptyList(), declaresJni = false).isEmpty(), "a module with no JNI needs no rules")
    }

    @Test
    fun symbolsUnmangleToTheirClass() {
        val cpp = """
            JNIEXPORT void JNICALL Java_com_example_window_GlfwWindow_glfwInit(JNIEnv*, jobject) {}
            JNIEXPORT void JNICALL Java_com_example_my_1pkg_Outer_00024Inner_set_1value(JNIEnv*, jobject) {}
            JNIEXPORT void JNICALL Java_com_example_window_GlfwWindow_glfwHint__II(JNIEnv*, jobject, jint, jint) {}
        """.trimIndent()

        assertEquals(setOf("com.example.window.GlfwWindow", "com.example.my_pkg.Outer\$Inner"), jniMethodOwners(cpp))
    }

    @Test
    fun namedClassesComeFromStringsAndSignaturesButNotThePlatform() {
        val cpp = """
            jclass type = env->FindClass("com/example/models/Extent");
            jmethodID make = env->GetMethodID(type, "<init>", "(ILcom/example/models/Offset;Ljava/lang/String;)V");
            jclass callback = env->FindClass("kotlin/jvm/functions/Function4");
            #include "vulkan/vulkan.h"
        """.trimIndent()

        assertEquals(
            setOf("com.example.models.Extent", "com.example.models.Offset", "kotlin.jvm.functions.Function4"),
            jniNamedClasses(cpp),
        )
    }

    @Test
    fun patternsFollowProGuardWildcards() {
        val kept = keepRules("-keep class com.example.models.** { *; }\n-keep class com.example.* { *; }")
        val single = keepRules("-keep class com.example.Item? { *; }")

        assertTrue(kept.any { it.keepsName("com.example.models.info.Extent") }, "** spans packages")
        assertTrue(kept.any { it.keepsName("com.example.Vulkan") }, "* within one package")
        assertTrue(kept.none { it.keepsName("com.example.enums.Format") }, "* doesn't span packages")
        assertTrue(single.any { it.keepsName("com.example.Item2") } && single.none { it.keepsName("com.example.Item22") }, "? is one character")
    }

    @Test
    fun rulesThatKeepNoReliableNameAreIgnored() {
        val kept = keepRules(
            """
            -keep,allowobfuscation class com.example.Obfuscated { *; }
            -keep class * extends com.example.Listener { *; }
            -keepclassmembers class com.example.Members { native <methods>; }
            # -keep class com.example.Commented { *; }
            """.trimIndent(),
        )

        assertTrue(kept.none { it.keepsName("com.example.Obfuscated") }, "allowobfuscation keeps no name")
        assertTrue(kept.none { it.keepsName("com.example.Anything") }, "a rule by supertype can't be checked")
        assertTrue(kept.none { it.keepsNativeMethodsOf("com.example.Members") }, "keepclassmembers keeps no class name")
        assertTrue(kept.none { it.keepsName("com.example.Commented") }, "a comment is no rule")
    }

    private companion object {
        const val MODULE = ":awake:asset:shader-compiler"
    }
}
