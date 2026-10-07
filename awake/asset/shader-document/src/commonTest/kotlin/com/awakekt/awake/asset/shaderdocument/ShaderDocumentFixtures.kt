/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderdocument

/** Documents the tests share: one per surface, each using what that surface allows. */
internal object ShaderDocumentFixtures {
    /** A full-screen sky: two colour parameters mixed by the view direction's height. */
    const val SKY = """
        {"formatVersion":1,"name":"Gradient sky","surface":"background",
         "parameters":[{"name":"top","type":"color","default":[0.1,0.3,0.8,1]},
                       {"name":"bottom","type":"color","default":[0.9,0.5,0.3,1]}],
         "fragment":{
           "statements":[{"statement":"let","name":"t","value":{"op":"call","fn":"saturate","args":[
             {"op":"add","a":{"op":"mul","a":{"op":"swizzle","value":{"op":"input","input":"viewDirection"},"components":"y"},
                                           "b":{"op":"const","value":[0.5]}},
                         "b":{"op":"const","value":[0.5]}}]}}],
           "color":{"op":"call","fn":"mix","args":[{"op":"param","name":"bottom"},{"op":"param","name":"top"},{"op":"local","name":"t"}]}}}
    """

    /** A full-screen tint that reads nothing at all. */
    const val TINT = """{"name":"Tint","surface":"overlay","fragment":{"color":{"op":"const","value":[1,0,0,0.25]}}}"""

    /** A displaced, alpha-blended plane with two textures, a loop and a discard. */
    const val WATER = """
        {"name":"Water","surface":"plane","blend":"alpha","plane":{"size":[10,6],"segments":4},
         "parameters":[{"name":"amplitude","type":"float","default":[0.2]},{"name":"tint","type":"color","default":[0,0.3,0.6,0.8]}],
         "textures":[{"name":"ripples"},{"name":"foam"}],
         "vertex":{
           "statements":[{"statement":"let","name":"w","value":{"op":"call","fn":"sin","args":[
             {"op":"add","a":{"op":"mul","a":{"op":"swizzle","value":{"op":"input","input":"uv"},"components":"x"},"b":{"op":"const","value":[12.566]}},
                         "b":{"op":"input","input":"time"}}]}}],
           "displacement":{"op":"mul","a":{"op":"local","name":"w"},"b":{"op":"param","name":"amplitude"}}},
         "fragment":{
           "statements":[
             {"statement":"var","name":"glow","value":{"op":"const","value":[0]}},
             {"statement":"for","counter":"i","from":0,"until":3,"body":[
               {"statement":"set","name":"glow","value":{"op":"add","a":{"op":"local","name":"glow"},
                 "b":{"op":"swizzle","value":{"op":"sample","texture":"foam","uv":{"op":"mul","a":{"op":"input","input":"uv"},"b":{"op":"local","name":"i"}}},"components":"r"}}}]},
             {"statement":"discard_if","condition":{"op":"lt","a":{"op":"local","name":"glow"},"b":{"op":"const","value":[0.01]}}}],
           "color":{"op":"mul","a":{"op":"param","name":"tint"},"b":{"op":"sample","texture":"ripples","uv":{"op":"input","input":"uv"}}}}}
    """

    /**
     * A full-screen overlay whose fragment stage is [statements] (a JSON array body) and whose colour is
     * [color] (a JSON expression), for tests that need one rule broken and nothing else.
     */
    fun overlay(color: String = """{"op":"const","value":[1,1,1,1]}""", statements: String = "", extra: String = ""): String =
        """{"name":"Test","surface":"overlay"$extra,"fragment":{"statements":[$statements],"color":$color}}"""

    /** A one-segment plane with a vertex stage of [vertexStatements] and [displacement], and a colour of [color]. */
    fun plane(
        color: String = """{"op":"const","value":[1,1,1,1]}""",
        vertexStatements: String = "",
        displacement: String? = null,
        extra: String = "",
    ): String {
        val displace = displacement?.let { ""","displacement":$it""" }.orEmpty()
        return """{"name":"Test","surface":"plane","plane":{"size":[1,1]}$extra,""" +
            """"vertex":{"statements":[$vertexStatements]$displace},"fragment":{"color":$color}}"""
    }

    /** The issues compiling [json] reports, or an empty list when it compiles. */
    fun issuesOf(json: String, limits: ShaderDocumentLimits = ShaderDocumentLimits.Default): List<ShaderDocumentIssue> = try {
        ShaderDocuments.compile(json, limits)
        emptyList()
    } catch (rejected: ShaderDocumentException) {
        rejected.issues
    }
}
