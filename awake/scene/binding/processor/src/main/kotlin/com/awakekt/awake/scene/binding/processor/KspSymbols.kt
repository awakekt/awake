/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.binding.processor

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.ksp.toTypeName

// Small readers over KSP's symbols and annotation arguments.

/** This type with its type aliases replaced by what they stand for, keeping its nullability. */
internal fun KSType.expanded(): KSType {
    val alias = declaration as? KSTypeAlias ?: return this
    val actual = alias.type.resolve().expanded()
    return if (isMarkedNullable) actual.makeNullable() else actual
}

internal fun KSClassDeclaration.superTypeNames(): List<TypeName> = getAllSuperTypes().map { it.toTypeName() }.toList()

internal fun KSAnnotated.findAnnotation(name: ClassName): KSAnnotation? = annotations.firstOrNull {
    it.shortName.asString() == name.simpleName &&
        it.annotationType.resolve().declaration.qualifiedName?.asString() == name.canonicalName
}

internal fun KSAnnotated.hasAnnotation(name: ClassName): Boolean = findAnnotation(name) != null

internal fun KSAnnotation.argument(name: String): Any? = arguments.firstOrNull { it.name?.asString() == name }?.value

internal fun KSAnnotation.string(name: String): String = argument(name) as? String ?: ""

internal fun KSAnnotation.stringList(name: String): List<String> = (argument(name) as? List<*>).orEmpty().filterIsInstance<String>()

internal fun KSAnnotation.typeList(name: String): List<KSType> = (argument(name) as? List<*>).orEmpty().filterIsInstance<KSType>()

internal fun KSAnnotation.annotationList(name: String): List<KSAnnotation> =
    (argument(name) as? List<*>).orEmpty().filterIsInstance<KSAnnotation>()
