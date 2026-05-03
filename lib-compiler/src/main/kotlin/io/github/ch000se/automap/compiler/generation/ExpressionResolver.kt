package io.github.ch000se.automap.compiler.generation

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSAnnotated
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
    private val allowNarrowing: Boolean = false,
) {

    /**
     * Builds an expression that maps [ExpressionContext.propName] from source type to target type.
     *
     * @throws MappingException when no supported exact, primitive, nested, or collection conversion
     *   exists for the source/target pair.
     */
    fun expressionFor(context: ExpressionContext): CodeBlock {
        return exactExpression(context)
            ?: primitiveExpression(context.sourceType, context.targetType, context.valueExpression)
            ?: nestedExpression(context.sourceType, context.targetType, context.valueExpression)
            ?: collectionExpression(context)
            ?: errorTypeMismatch(context)
    }

    /** Returns true when a flattened field can be assigned or converted to a target parameter. */
    fun canMapFlattened(sourceType: KSType, targetType: KSType): Boolean {
        val sameBase = sourceType.fqn() == targetType.fqn()
        val sameNullability = sourceType.isMarkedNullable == targetType.isMarkedNullable
        return (sameBase && sameNullability && typeArgsEqual(sourceType, targetType)) ||
            primitiveExpression(sourceType, targetType, CodeBlock.of("value")) != null
    }

    private fun exactExpression(context: ExpressionContext): CodeBlock? {
        val sameBase = context.sourceType.fqn() == context.targetType.fqn()
        val nullabilityOk = context.targetType.isMarkedNullable || !context.sourceType.isMarkedNullable
        return if (sameBase && nullabilityOk && typeArgsEqual(context.sourceType, context.targetType)) {
            context.valueExpression
        } else {
            null
        }
    }

    private fun primitiveExpression(
        sourceType: KSType,
        targetType: KSType,
        valueExpression: CodeBlock,
    ): CodeBlock? {
        val conversion = primitiveConversion(sourceType, targetType) ?: return null
        if (sourceType.isMarkedNullable && !targetType.isMarkedNullable) return null
        if (sourceType.isMarkedNullable) {
            return CodeBlock.of("%L?%L", valueExpression, conversion)
        }
        return CodeBlock.of("%L%L", valueExpression, conversion)
    }

    private fun nestedExpression(
        sourceType: KSType,
        targetType: KSType,
        valueExpression: CodeBlock,
    ): CodeBlock? {
        if (sourceType.isMarkedNullable && !targetType.isMarkedNullable) return null
        if (!hasAutoMap(sourceType.fqn(), targetType.fqn())) return null
        val call = "to${targetType.declaration.simpleName.asString()}"
        return if (sourceType.isMarkedNullable) {
            CodeBlock.of("%L?.%N()", valueExpression, call)
        } else {
            CodeBlock.of("%L.%N()", valueExpression, call)
        }
    }

    private fun collectionExpression(context: ExpressionContext): CodeBlock? {
        val sourceBase = context.sourceType.classFqn()
        val targetBase = context.targetType.classFqn()
        if (sourceBase == null || sourceBase != targetBase) return null
        if (context.sourceType.isMarkedNullable && !context.targetType.isMarkedNullable) return null

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
            context.valueExpression
        } else if (context.sourceType.isMarkedNullable) {
            val nullableSuffix = if (suffix == ".toSet()") "?.toSet()" else suffix
            CodeBlock.of("%L?.map { %L }%L", context.valueExpression, elementExpression.code, nullableSuffix)
        } else {
            CodeBlock.of("%L.map { %L }%L", context.valueExpression, elementExpression.code, suffix)
        }
    }

    private fun mapExpression(context: ExpressionContext): CodeBlock? {
        val sourceValue = context.sourceType.arguments.getOrNull(1)?.type?.resolve() ?: return null
        val targetValue = context.targetType.arguments.getOrNull(1)?.type?.resolve() ?: return null
        val valueExpression = elementExpr(sourceValue, targetValue, CodeBlock.of("entry.value")) ?: return null
        return if (valueExpression.isIdentity) {
            context.valueExpression
        } else if (context.sourceType.isMarkedNullable) {
            CodeBlock.of("%L?.mapValues { entry -> %L }", context.valueExpression, valueExpression.code)
        } else {
            CodeBlock.of("%L.mapValues { entry -> %L }", context.valueExpression, valueExpression.code)
        }
    }

    private fun elementExpr(
        sourceElement: KSType,
        targetElement: KSType,
        valueExpression: CodeBlock = CodeBlock.of("it"),
    ): ElementExpression? {
        val sameBase = sourceElement.fqn() == targetElement.fqn()
        val nullabilityOk = targetElement.isMarkedNullable || !sourceElement.isMarkedNullable
        if (sameBase && nullabilityOk && typeArgsEqual(sourceElement, targetElement)) {
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

    private fun primitiveConversion(sourceType: KSType, targetType: KSType): String? {
        val sourceFqn = sourceType.fqn()
        val targetFqn = targetType.fqn()
        val widenings = mapOf(
            ("kotlin.Byte" to "kotlin.Short") to ".toShort()",
            ("kotlin.Byte" to "kotlin.Int") to ".toInt()",
            ("kotlin.Byte" to "kotlin.Long") to ".toLong()",
            ("kotlin.Short" to "kotlin.Int") to ".toInt()",
            ("kotlin.Short" to "kotlin.Long") to ".toLong()",
            ("kotlin.Int" to "kotlin.Long") to ".toLong()",
            ("kotlin.Int" to "kotlin.String") to ".toString()",
            ("kotlin.Long" to "kotlin.String") to ".toString()",
            ("kotlin.Boolean" to "kotlin.String") to ".toString()",
            ("kotlin.Char" to "kotlin.String") to ".toString()",
            ("kotlin.Byte" to "kotlin.String") to ".toString()",
            ("kotlin.Short" to "kotlin.String") to ".toString()",
            ("kotlin.Float" to "kotlin.Double") to ".toDouble()",
            ("kotlin.Float" to "kotlin.String") to ".toString()",
            ("kotlin.Double" to "kotlin.String") to ".toString()",
        )
        if (targetFqn == "kotlin.String" && sourceType.isEnum()) return ".name"
        val narrowings = if (allowNarrowing) {
            mapOf(
                ("kotlin.Long" to "kotlin.Int") to ".toInt()",
                ("kotlin.Double" to "kotlin.Float") to ".toFloat()",
            )
        } else {
            emptyMap()
        }
        return widenings[sourceFqn to targetFqn]
            ?: narrowings[sourceFqn to targetFqn]
    }

    private fun KSType.isEnum(): Boolean =
        (declaration as? KSClassDeclaration)?.classKind == ClassKind.ENUM_CLASS

    private fun errorTypeMismatch(context: ExpressionContext): Nothing {
        if (context.sourceType.isMarkedNullable && !context.targetType.isMarkedNullable) {
            throw MappingException(
                buildString {
                    append("Cannot map nullable field \"")
                        .append(context.targetParamName)
                        .append("\" to non-null target field.\n\n")
                    append("Source:\n")
                    append("- ").append(context.renderedSource).append(": ").append(context.sourceType.render()).append("\n\n")
                    append("Target:\n")
                    append("- ").append(context.targetParamName).append(": ").append(context.targetType.render()).append("\n\n")
                    append("AutoMap does not generate \"!!\".\n\n")
                    append("Fix:\n")
                    append("1. Make target field nullable: ").append(context.targetType.render()).append("?\n")
                    append("2. Add @MapWith(\"converter\") to handle null explicitly\n")
                    append("3. Add a default value strategy")
                },
                context.sourceSymbol ?: context.source,
            )
        }
        throw MappingException(
            buildString {
                append("Cannot map field \"").append(context.targetParamName).append("\" in ")
                append(context.target.simpleName.asString()).append(".\n\n")
                append("Source candidates:\n  - ")
                    .append(context.renderedSource)
                    .append(": ")
                    .append(context.sourceType.fqn())
                    .append("\n\n")
                appendMissingNestedMapperHint(context)
                appendFixHints(context.targetParamName)
            },
            context.sourceSymbol ?: context.source,
        )
    }

    private fun KSType.render(): String {
        val args = arguments.mapNotNull { it.type?.resolve() }
        val suffix = if (args.isEmpty()) "" else args.joinToString(prefix = "<", postfix = ">") { it.render() }
        val nullable = if (isMarkedNullable) "?" else ""
        return "${fqn()}$suffix$nullable"
    }

    private fun StringBuilder.appendMissingNestedMapperHint(context: ExpressionContext) {
        val directSource = context.sourceType.declaration as? KSClassDeclaration
        val directTarget = context.targetType.declaration as? KSClassDeclaration
        if (directSource != null &&
            directTarget != null &&
            directSource.qualifiedName != directTarget.qualifiedName &&
            directSource.isUserType() &&
            directTarget.isUserType()
        ) {
            append("Source type:\n")
            append("- ").append(directSource.simpleName.asString()).append("\n\n")
            append("Target type:\n")
            append("- ").append(directTarget.simpleName.asString()).append("\n\n")
            append("No mapper found for ")
                .append(directSource.simpleName.asString())
                .append(" -> ")
                .append(directTarget.simpleName.asString())
                .append(".\n\n")
            append("To enable nested auto-mapping, annotate ")
                .append(directSource.simpleName.asString())
                .append(" with @AutoMap(")
                .append(directTarget.simpleName.asString())
                .append("::class).\n\n")
        }

        val sourceElement = context.sourceType.arguments.firstOrNull()?.type?.resolve()
        val targetElement = context.targetType.arguments.firstOrNull()?.type?.resolve()
        val sourceDecl = sourceElement?.declaration as? KSClassDeclaration
        val targetDecl = targetElement?.declaration as? KSClassDeclaration
        if (sourceDecl != null &&
            targetDecl != null &&
            sourceDecl.qualifiedName != targetDecl.qualifiedName &&
            sourceDecl.isUserType() &&
            targetDecl.isUserType()
        ) {
            append("No mapper found for ")
                .append(sourceDecl.simpleName.asString())
                .append(" -> ")
                .append(targetDecl.simpleName.asString())
                .append(".\n\n")
        }
    }

    private fun KSClassDeclaration.isUserType(): Boolean {
        val fqn = qualifiedName?.asString() ?: return false
        return !fqn.startsWith("kotlin.") && !fqn.startsWith("java.") && !fqn.startsWith("javax.")
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
 * @property valueExpression Kotlin expression that reads the selected source value.
 */
internal data class ExpressionContext(
    val source: KSClassDeclaration,
    val target: KSClassDeclaration,
    val propName: String,
    val sourceType: KSType,
    val targetType: KSType,
    val targetParamName: String,
    val valueExpression: CodeBlock = CodeBlock.of("%N", propName),
    val sourceSymbol: KSAnnotated? = null,
) {
    val renderedSource: String = valueExpression.toString()
}
