package io.github.ch000se.automap.compiler.generation

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.CodeBlock
import io.github.ch000se.automap.compiler.MappingException

private const val LIST_FQN = "kotlin.collections.List"
private const val SET_FQN = "kotlin.collections.Set"
private const val MAP_FQN = "kotlin.collections.Map"

/**
 * Resolves the Kotlin expression used for a single generated constructor argument.
 *
 * The resolver owns type-level mapping decisions: exact assignments, primitive widening/string
 * conversions, nested AutoMap calls, and supported collection element conversions. It deliberately
 * returns KotlinPoet [CodeBlock] values so identifier escaping and formatting stay centralized in
 * the code generation layer.
 *
 * @property autoMapIndex Lookup from source type FQN to known target type FQNs for nested mapper
 *   discovery.
 */
@Suppress("TooManyFunctions")
internal class ExpressionResolver(
    private val autoMapIndex: Map<String, Set<String>>,
) {

    /**
     * Builds an expression that maps [ExpressionContext.propName] from source type to target type.
     *
     * @throws MappingException when no supported exact, primitive, nested, or collection conversion
     *   exists for the source/target pair.
     */
    fun expressionFor(context: ExpressionContext): CodeBlock {
        return exactExpression(context)
            ?: primitiveExpression(context.sourceType, context.targetType, CodeBlock.of("%N", context.propName))
            ?: nestedExpression(context.sourceType, context.targetType, CodeBlock.of("%N", context.propName))
            ?: collectionExpression(context)
            ?: errorTypeMismatch(context)
    }

    private fun exactExpression(context: ExpressionContext): CodeBlock? {
        val sameBase = context.sourceType.fqn() == context.targetType.fqn()
        val sameNullability = context.sourceType.isMarkedNullable == context.targetType.isMarkedNullable
        return if (sameBase && sameNullability && typeArgsEqual(context.sourceType, context.targetType)) {
            CodeBlock.of("%N", context.propName)
        } else {
            null
        }
    }

    private fun primitiveExpression(
        sourceType: KSType,
        targetType: KSType,
        valueExpression: CodeBlock,
    ): CodeBlock? {
        if (sourceType.isMarkedNullable) return null
        val conversion = primitiveConversion(sourceType.fqn(), targetType.fqn()) ?: return null
        return CodeBlock.of("%L%L", valueExpression, conversion)
    }

    private fun nestedExpression(
        sourceType: KSType,
        targetType: KSType,
        valueExpression: CodeBlock,
    ): CodeBlock? {
        if (sourceType.isMarkedNullable != targetType.isMarkedNullable) return null
        if (!hasAutoMap(sourceType.fqn(), targetType.fqn())) return null
        return CodeBlock.of("%L.%N()", valueExpression, "to${targetType.declaration.simpleName.asString()}")
    }

    private fun collectionExpression(context: ExpressionContext): CodeBlock? {
        val sourceBase = context.sourceType.classFqn()
        val targetBase = context.targetType.classFqn()
        if (sourceBase == null || sourceBase != targetBase) return null

        return when (sourceBase) {
            LIST_FQN -> listOrSetExpression(context, suffix = "")
            SET_FQN -> listOrSetExpression(context, suffix = ".toSet()")
            MAP_FQN -> mapExpression(context)
            else -> null
        }
    }

    private fun listOrSetExpression(context: ExpressionContext, suffix: String): CodeBlock? {
        val sourceElement = context.sourceType.arguments.firstOrNull()?.type?.resolve() ?: return null
        val targetElement = context.targetType.arguments.firstOrNull()?.type?.resolve() ?: return null
        val elementExpression = elementExpr(sourceElement, targetElement) ?: return null
        return if (elementExpression.isIdentity) {
            CodeBlock.of("%N", context.propName)
        } else {
            CodeBlock.of("%N.map { %L }%L", context.propName, elementExpression.code, suffix)
        }
    }

    private fun mapExpression(context: ExpressionContext): CodeBlock? {
        val sourceValue = context.sourceType.arguments.getOrNull(1)?.type?.resolve() ?: return null
        val targetValue = context.targetType.arguments.getOrNull(1)?.type?.resolve() ?: return null
        val valueExpression = elementExpr(sourceValue, targetValue, CodeBlock.of("entry.value")) ?: return null
        return if (valueExpression.isIdentity) {
            CodeBlock.of("%N", context.propName)
        } else {
            CodeBlock.of("%N.mapValues { entry -> %L }", context.propName, valueExpression.code)
        }
    }

    private fun elementExpr(
        sourceElement: KSType,
        targetElement: KSType,
        valueExpression: CodeBlock = CodeBlock.of("it"),
    ): ElementExpression? {
        val sameBase = sourceElement.fqn() == targetElement.fqn()
        val sameNullability = sourceElement.isMarkedNullable == targetElement.isMarkedNullable
        if (sameBase && sameNullability && typeArgsEqual(sourceElement, targetElement)) {
            return ElementExpression(valueExpression, isIdentity = true)
        }

        val primitive = primitiveExpression(sourceElement, targetElement, valueExpression)
        if (primitive != null) return ElementExpression(primitive, isIdentity = false)

        val nested = nestedExpression(sourceElement, targetElement, valueExpression)
        return nested?.let { ElementExpression(it, isIdentity = false) }
    }

    private fun hasAutoMap(sourceFqn: String, targetFqn: String): Boolean =
        autoMapIndex[sourceFqn].orEmpty().contains(targetFqn)

    private fun typeArgsEqual(a: KSType, b: KSType): Boolean {
        return a.arguments.size == b.arguments.size &&
            a.arguments.indices.all { index ->
                val aType = a.arguments[index].type?.resolve()
                val bType = b.arguments[index].type?.resolve()
                aType != null &&
                    bType != null &&
                    aType.fqn() == bType.fqn() &&
                    aType.isMarkedNullable == bType.isMarkedNullable &&
                    typeArgsEqual(aType, bType)
            }
    }

    private fun primitiveConversion(sourceFqn: String, targetFqn: String): String? {
        val widenings = mapOf(
            ("kotlin.Int" to "kotlin.Long") to ".toLong()",
            ("kotlin.Int" to "kotlin.String") to ".toString()",
            ("kotlin.Long" to "kotlin.String") to ".toString()",
            ("kotlin.Float" to "kotlin.Double") to ".toDouble()",
            ("kotlin.Float" to "kotlin.String") to ".toString()",
            ("kotlin.Double" to "kotlin.String") to ".toString()",
        )
        return widenings[sourceFqn to targetFqn]
            ?: ".toString()".takeIf { targetFqn == "kotlin.String" && sourceFqn != "kotlin.String" }
    }

    private fun errorTypeMismatch(context: ExpressionContext): Nothing {
        throw MappingException(
            buildString {
                append("Cannot map field \"").append(context.targetParamName).append("\" in ")
                append(context.target.simpleName.asString()).append(".\n\n")
                append("Source candidates:\n  - ")
                    .append(context.propName)
                    .append(": ")
                    .append(context.sourceType.fqn())
                    .append("\n\n")
                appendFixHints(context.targetParamName)
            },
            context.source,
        )
    }

    private data class ElementExpression(
        val code: CodeBlock,
        val isIdentity: Boolean,
    )
}

/**
 * Type and symbol metadata required to resolve one generated constructor argument expression.
 *
 * @property source Source class that owns [propName].
 * @property target Target class whose constructor parameter is being populated.
 * @property propName Source property selected for the target parameter.
 * @property sourceType Resolved type of [propName].
 * @property targetType Resolved type of the target constructor parameter.
 * @property targetParamName Name of the target constructor parameter, used in diagnostics.
 */
internal data class ExpressionContext(
    val source: KSClassDeclaration,
    val target: KSClassDeclaration,
    val propName: String,
    val sourceType: KSType,
    val targetType: KSType,
    val targetParamName: String,
)
