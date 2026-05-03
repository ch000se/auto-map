package io.github.ch000se.automap.compiler.generation

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter

/**
 * Returns annotations declared on both a Kotlin property and the matching primary-constructor
 * parameter.
 *
 * KSP can expose use-site annotations through either symbol depending on how the source is written,
 * so mapper resolution must inspect both locations.
 */
internal fun annotationsFor(
    propertyName: String,
    propertyAnnotations: List<KSAnnotation>,
    sourceCtorParams: List<KSValueParameter>,
): List<KSAnnotation> {
    val constructorAnnotations = sourceCtorParams
        .firstOrNull { it.name?.asString() == propertyName }
        ?.annotations
        ?.toList()
        .orEmpty()
    return propertyAnnotations + constructorAnnotations
}

/** Returns `true` when the annotation simple name equals [name]. */
internal fun KSAnnotation.isNamed(name: String): Boolean = shortName.asString() == name

/** Returns `true` when the annotation simple name is one of [names]. */
internal fun KSAnnotation.isAnyOf(vararg names: String): Boolean = shortName.asString() in names

/** Returns the first string argument from the first annotation with simple name [name]. */
internal fun List<KSAnnotation>.firstValueOf(name: String): String? =
    firstOrNull { it.isNamed(name) }?.arguments?.firstOrNull()?.value as? String

/** Convenience accessor for a property simple name as source text. */
internal val KSPropertyDeclaration.name: String
    get() = simpleName.asString()

/** Returns a stable type name for diagnostics and AutoMap lookup. */
internal fun KSType.fqn(): String = classFqn() ?: declaration.simpleName.asString()

/** Returns the qualified name of the class declaration behind this type, if it has one. */
internal fun KSType.classFqn(): String? =
    (declaration as? KSClassDeclaration)?.qualifiedName?.asString()
