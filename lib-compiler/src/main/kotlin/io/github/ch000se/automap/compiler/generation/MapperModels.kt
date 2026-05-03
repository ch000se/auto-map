package io.github.ch000se.automap.compiler.generation

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.CodeBlock

/**
 * File-level mapper generation plan.
 *
 * @property packageName Package where the generated mapper file will be emitted.
 * @property fileName Kotlin source file name without the `.kt` suffix.
 * @property functions Mapper functions that should be emitted into the file.
 */
internal data class MapperFile(
    val packageName: String,
    val fileName: String,
    val functions: List<MapperFunction>,
)

/**
 * Metadata for one source-to-target mapper function pair.
 *
 * @property source Source class used as the extension receiver.
 * @property target Target class constructed by the generated mapper.
 * @property resolutions Resolved target constructor arguments.
 */
internal data class MapperFunction(
    val source: KSClassDeclaration,
    val target: KSClassDeclaration,
    val resolutions: List<Resolution>,
)

/**
 * Result of resolving one target constructor parameter.
 *
 * @property targetParamName Name of the target constructor parameter.
 * @property expression KotlinPoet expression used as the argument value. [Omit] uses an empty block
 *   because it does not participate in constructor emission.
 */
internal sealed class Resolution(
    val targetParamName: String,
    val expression: CodeBlock,
) {
    /** A constructor argument that can be emitted directly. */
    class Plain(name: String, expr: CodeBlock) : Resolution(name, expr)

    /**
     * A constructor argument that requires a generated lambda parameter supplied by the caller.
     *
     * @property lambdaName Name of the lambda parameter in generated mapper functions.
     * @property sourceType Lambda input type.
     * @property targetType Lambda return type.
     */
    class LambdaConverter(
        targetParamName: String,
        expression: CodeBlock,
        val lambdaName: String,
        val sourceType: KSType,
        val targetType: KSType,
    ) : Resolution(targetParamName, expression)

    /** A target constructor parameter intentionally omitted so Kotlin can use its default value. */
    class Omit(name: String) : Resolution(name, CodeBlock.of(""))
}
