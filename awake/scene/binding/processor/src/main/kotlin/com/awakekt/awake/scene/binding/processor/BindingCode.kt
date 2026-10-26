/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.binding.processor

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.joinToCode

// The expressions a generated binding is built from.

/** [field]'s value read from [source], converted to the live side's type when [toLive] and to the saved side's otherwise. */
internal fun value(field: FieldPlan, source: CodeBlock, toLive: Boolean, enums: List<Conversion.EnumByName>): CodeBlock {
    val function = when (val conversion = field.conversion) {
        Conversion.Direct -> return CodeBlock.of("%L", source)
        is Conversion.Converted -> CodeBlock.of("%T.%L", conversion.converter, if (toLive) "toLive" else "toSaved")
        is Conversion.EnumByName -> CodeBlock.of("%L%L", if (toLive) "liveEnum" else "savedEnum", enums.indexOf(conversion))
    }
    return if (field.nullable) CodeBlock.of("%L?.let { %L(it) }", source, function) else CodeBlock.of("%L(%L)", function, source)
}

/** `val [name] = [type](...)`, one argument a line once there are several. */
internal fun construct(name: String, type: ClassName, arguments: List<CodeBlock>): CodeBlock = when (arguments.size) {
    0 -> CodeBlock.of("val %L = %T()\n", name, type)
    1 -> CodeBlock.of("val %L = %T(%L)\n", name, type, arguments.single())
    else -> CodeBlock.builder().add("val %L = %T(\n", name, type).indent()
        .add(arguments.map { CodeBlock.of("%L,\n", it) }.joinToCode(""))
        .unindent().add(")\n").build()
}

internal fun enumFunction(name: String, from: TypeName, to: TypeName, constants: List<String>): FunSpec {
    val body = CodeBlock.builder().add("return when (value) {\n").indent()
    constants.forEach { body.add("%T.%L -> %T.%L\n", from, it, to, it) }
    body.unindent().add("}\n")
    return FunSpec.builder(name).addModifiers(KModifier.PRIVATE).addParameter("value", from).returns(to).addCode(body.build()).build()
}
