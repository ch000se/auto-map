package io.github.ch000se.automap.compiler.generation

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.CodeBlock

/**
 * File-level mapper generation plan.
 *
 * @property packageName Package where the generated mapper file will be emitted.
 * @property fileName Kotlin source file name without the `.kt` suffix.
 * @property functions Mapper functions that should be emitted into the file.
 * @property dependencyFiles KSP source files that should invalidate this mapper output.
 * @property imports Function imports required by nested mapper calls from dependency packages.
 */
internal data class MapperFile(
    val packageName: String,
    val fileName: String,
    val functions: List<MapperFunction>,
    val dependencyFiles: List<KSFile>,
    val imports: List<MapperImport> = emptyList(),
)

/**
 * Metadata for one source-to-target mapper function pair.
 *
 * @property source Source class used as the extension receiver.
 * @property target Target class constructed by the generated mapper.
 * @property resolutions Resolved target constructor arguments.
 * @property generateListVariant Whether to emit a `List<Source>.toTargetList()` helper.
 * @property functionName Kotlin extension function name.
 * @property jvmName Optional `@JvmName` value.
 * @property visibility Generated function visibility.
 */
internal data class MapperFunction(
    val source: KSClassDeclaration,
    val target: KSClassDeclaration,
    val resolutions: List<Resolution>,
    val generateListVariant: Boolean,
    val functionName: String,
    val jvmName: String?,
    val visibility: MapperVisibility,
)

/**
 * Visibility used by generated mapper extension functions.
 */
internal enum class MapperVisibility {
    /** Emit a public top-level extension function. */
    PUBLIC,

    /** Emit an internal top-level extension function. */
    INTERNAL,
}

/**
 * File import required by a generated mapper body.
 *
 * @property packageName Package to import from.
 * @property name Imported declaration simple name.
 */
internal data class MapperImport(
    val packageName: String,
    val name: String,
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
