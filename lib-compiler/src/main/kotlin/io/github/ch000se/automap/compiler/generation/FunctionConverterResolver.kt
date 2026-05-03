package io.github.ch000se.automap.compiler.generation

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import io.github.ch000se.automap.compiler.MappingException

/**
 * Resolves and validates function-based `@MapWith` converters.
 */
internal class FunctionConverterResolver(
    private val resolver: Resolver,
) {

    fun resolve(context: FunctionConverterContext): FunctionConverter {
        val candidates = referenceCandidates(context.functionRef, context.source.packageName.asString())
            .flatMap(::functionsNamed)
            .distinctBy { it.qualifiedName?.asString() ?: it.simpleName.asString() }

        val valid = candidates.filter { it.matches(context.sourceType, context.targetType) }
        if (valid.size == 1) {
            return FunctionConverter(context.functionRef)
        }

        val found = candidates.firstOrNull()
        throw MappingException(
            buildString {
                append("Invalid @MapWith converter for field \"")
                    .append(context.fieldName)
                    .append("\".\n\n")
                append("Expected:\n")
                append("- function accepting ").append(context.sourceType.render()).append("\n")
                append("- returning ").append(context.targetType.render()).append("\n\n")
                append("Found:\n")
                if (found == null) {
                    append("- no function named ").append(context.functionRef).append("\n\n")
                } else {
                    append("- ").append(found.render()).append("\n\n")
                }
                append("Fix:\n")
                append("1. Change converter parameter type\n")
                append("2. Change converter return type\n")
                append("3. Remove @MapWith if automatic mapping is possible")
            },
            context.property,
        )
    }

    private fun referenceCandidates(functionRef: String, sourcePackage: String): List<String> {
        return if ("." in functionRef) {
            listOf(functionRef)
        } else {
            listOf(functionRef, "$sourcePackage.$functionRef")
        }
    }

    private fun functionsNamed(name: String): List<KSFunctionDeclaration> =
        resolver.getFunctionDeclarationsByName(resolver.getKSNameFromString(name), includeTopLevel = true).toList()

    private fun KSFunctionDeclaration.matches(sourceType: KSType, targetType: KSType): Boolean {
        val parameter = parameters.singleOrNull()?.type?.resolve() ?: return false
        val returnType = returnType?.resolve() ?: return false
        return parameter.accepts(sourceType) && targetType.accepts(returnType)
    }

    private fun KSType.accepts(valueType: KSType): Boolean {
        val sameBase = fqn() == valueType.fqn()
        val nullabilityOk = isMarkedNullable || !valueType.isMarkedNullable
        return sameBase && nullabilityOk && typeArgsEqual(this, valueType)
    }

    private fun typeArgsEqual(a: KSType, b: KSType): Boolean {
        return a.arguments.size == b.arguments.size &&
            a.arguments.indices.all { index ->
                val aType = a.arguments[index].type?.resolve()
                val bType = b.arguments[index].type?.resolve()
                aType != null &&
                    bType != null &&
                    aType.fqn() == bType.fqn() &&
                    (aType.isMarkedNullable || !bType.isMarkedNullable) &&
                    typeArgsEqual(aType, bType)
            }
    }

    private fun KSFunctionDeclaration.render(): String {
        val params = parameters.joinToString(", ") { parameter ->
            val name = parameter.name?.asString() ?: "value"
            "$name: ${parameter.type.resolve().render()}"
        }
        val returnText = returnType?.resolve()?.render() ?: "Unit"
        return "fun ${simpleName.asString()}($params): $returnText"
    }

    private fun KSType.render(): String {
        val args = arguments.mapNotNull { it.type?.resolve() }
        val suffix = if (args.isEmpty()) "" else args.joinToString(prefix = "<", postfix = ">") { it.render() }
        val nullable = if (isMarkedNullable) "?" else ""
        return "${fqn()}$suffix$nullable"
    }
}

internal data class FunctionConverterContext(
    val source: KSClassDeclaration,
    val property: KSPropertyDeclaration,
    val fieldName: String,
    val functionRef: String,
    val sourceType: KSType,
    val targetType: KSType,
)

internal data class FunctionConverter(
    val reference: String,
)
