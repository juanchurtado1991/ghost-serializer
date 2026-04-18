package com.ghost.serialization.compiler

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.TypeName

internal data class GhostPropertyModel(
    val kotlinName: String,
    val jsonName: String,
    val type: KSType,
    val typeName: TypeName,
    val isNullable: Boolean,
    val isGhost: Boolean,
    val isList: Boolean,
    val isEnum: Boolean,
    val listInnerType: KSType? = null,
    val listInnerIsGhost: Boolean = false,
    val listInnerIsEnum: Boolean = false,
    val hasDefaultValue: Boolean = false,
    val isMap: Boolean = false,
    val mapValueType: KSType? = null,
    val mapValueIsGhost: Boolean = false,
    val isPrimitiveArray: Boolean = false,
    val primitiveArrayType: String? = null,
    val isValueClass: Boolean = false,
    val valueClassProperty: GhostPropertyModel? = null,
    val isSealedClass: Boolean = false,
    val sealedSubclasses: List<KSClassDeclaration> = emptyList(),
    val enumValues: Map<String, String>? = null
)