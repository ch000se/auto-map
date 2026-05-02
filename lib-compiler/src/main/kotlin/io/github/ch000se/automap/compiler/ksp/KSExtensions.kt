package io.github.ch000se.automap.compiler.ksp

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier

internal fun KSClassDeclaration.isDataClass(): Boolean =
    Modifier.DATA in modifiers

internal fun KSClassDeclaration.isEnumClass(): Boolean =
    classKind == ClassKind.ENUM_CLASS

internal fun KSClassDeclaration.primaryConstructorParams(): List<KSValueParameter> =
    primaryConstructor?.parameters.orEmpty()

/** Returns the companion object's `operator fun invoke(...)` if present. */
internal fun KSClassDeclaration.companionInvokeFunction(): KSFunctionDeclaration? {
    val companion = declarations
        .filterIsInstance<KSClassDeclaration>()
        .find { it.isCompanionObject } ?: return null
    return companion.getDeclaredFunctions()
        .find { fn -> fn.simpleName.asString() == "invoke" && Modifier.OPERATOR in fn.modifiers }
}

/**
 * Resolves the parameters to use when constructing this class in generated code.
 *
 * Prefers the primary constructor when it is publicly accessible. Falls back to a companion
 * `operator fun invoke(...)` when the primary constructor is absent or private/protected
 * (builder-style classes)
 */
internal fun KSClassDeclaration.resolvedParams(): List<KSValueParameter>? {
    val ctor = primaryConstructor
    if (ctor != null) {
        val isInaccessible = Modifier.PRIVATE in ctor.modifiers || Modifier.PROTECTED in ctor.modifiers
        if (!isInaccessible) return ctor.parameters.toList()
    }
    return companionInvokeFunction()?.parameters?.toList()
}

internal fun KSType.classDeclaration(): KSClassDeclaration? =
    declaration as? KSClassDeclaration

internal fun KSAnnotated.findAnnotation(qualifiedName: String): KSAnnotation? {
    val shortName = qualifiedName.substringAfterLast('.')
    return annotations
        .filter { it.shortName.asString() == shortName }
        .find { it.annotationType.resolve().declaration.qualifiedName?.asString() == qualifiedName }
}

internal fun KSAnnotation.arg(name: String): Any? =
    arguments.find { it.name?.asString() == name }?.value