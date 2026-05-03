package io.github.ch000se.automap.compiler.generation

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType

private const val DEFAULT_MAX_DEPTH = 3

/**
 * Finds compile-time flattened source property paths for target constructor parameters.
 *
 * The resolver only walks user-defined non-null composite classes, tracks visited types to avoid
 * cycles, and separates valid matches from incompatible same-name candidates so diagnostics can be
 * explicit.
 */
internal class FlattenResolver(
    private val expressionResolver: ExpressionResolver,
    private val maxDepth: Int = DEFAULT_MAX_DEPTH,
) {

    fun candidatesFor(context: FlattenContext): FlattenLookup {
        val valid = mutableListOf<FlattenCandidate>()
        val incompatible = mutableListOf<FlattenCandidate>()
        val mapWith = mutableListOf<FlattenCandidate>()

        for (property in context.sourceProps) {
            val annotations = context.annotationsByProperty[property.name].orEmpty()
            if (annotations.any { it.isNamed("MapIgnore") || it.isNamed("MapName") }) continue
            if (!context.isFlattenRoot(property, annotations)) continue

            collect(
                property = property,
                targetName = context.targetParamName,
                targetType = context.targetType,
                path = listOf(property),
                depth = 1,
                visitedTypes = emptySet(),
                valid = valid,
                incompatible = incompatible,
                mapWith = mapWith,
            )
        }

        return FlattenLookup(valid = valid, incompatible = incompatible, mapWith = mapWith)
    }

    private fun FlattenContext.isFlattenRoot(
        property: KSPropertyDeclaration,
        annotations: List<KSAnnotation>,
    ): Boolean {
        val type = property.type.resolve()
        val marked = annotations.any { it.isNamed("Flatten") }
        return (flatten || marked) && type.isComposite()
    }

    private fun collect(
        property: KSPropertyDeclaration,
        targetName: String,
        targetType: KSType,
        path: List<KSPropertyDeclaration>,
        depth: Int,
        visitedTypes: Set<String>,
        valid: MutableList<FlattenCandidate>,
        incompatible: MutableList<FlattenCandidate>,
        mapWith: MutableList<FlattenCandidate>,
    ) {
        val type = property.type.resolve()
        val typeFqn = type.fqn()
        if (typeFqn in visitedTypes || !type.isComposite()) return

        val declaration = type.declaration as? KSClassDeclaration ?: return
        val nextVisited = visitedTypes + typeFqn
        for (nested in declaration.getAllProperties()) {
            val nestedPath = path + nested
            val nestedType = nested.type.resolve()
            if (nested.name == targetName) {
                val candidate = FlattenCandidate(
                    path = nestedPath,
                    renderedPath = nestedPath.joinToString(".") { it.name },
                    type = nestedType,
                    depth = depth,
                )
                if (nested.annotations.any { it.isNamed("MapWith") || it.isNamed("MapWithFn") }) {
                    mapWith += candidate
                } else if (expressionResolver.canMapFlattened(nestedType, targetType)) {
                    valid += candidate
                } else {
                    incompatible += candidate
                }
            }
            if (depth < maxDepth && nestedType.isComposite()) {
                collect(
                    property = nested,
                    targetName = targetName,
                    targetType = targetType,
                    path = nestedPath,
                    depth = depth + 1,
                    visitedTypes = nextVisited,
                    valid = valid,
                    incompatible = incompatible,
                    mapWith = mapWith,
                )
            }
        }
    }

    private fun KSType.isComposite(): Boolean {
        if (isMarkedNullable) return false
        val declaration = declaration as? KSClassDeclaration ?: return false
        val fqn = declaration.qualifiedName?.asString() ?: return false
        if (declaration.classKind != ClassKind.CLASS) return false
        if (fqn.startsWith("kotlin.") || fqn.startsWith("java.") || fqn.startsWith("javax.")) return false
        return true
    }
}

/**
 * Input for one flatten lookup.
 *
 * @property sourceProps Top-level readable source properties.
 * @property annotationsByProperty Merged property and value-parameter annotations by source name.
 * @property targetParamName Target constructor parameter being resolved.
 * @property targetType Required target parameter type.
 * @property flatten Whether global flatten lookup is enabled for this mapping.
 */
internal data class FlattenContext(
    val sourceProps: List<KSPropertyDeclaration>,
    val annotationsByProperty: Map<String, List<KSAnnotation>>,
    val targetParamName: String,
    val targetType: KSType,
    val flatten: Boolean,
)

/**
 * One nested candidate discovered during flatten lookup.
 *
 * @property path KSP property declarations from top-level source property to leaf property.
 * @property renderedPath Kotlin expression used in generated code, relative to the extension
 *   receiver.
 * @property type Leaf property type.
 * @property depth Recursion depth at which the candidate was found.
 */
internal data class FlattenCandidate(
    val path: List<KSPropertyDeclaration>,
    val renderedPath: String,
    val type: KSType,
    val depth: Int,
)

/**
 * Result buckets for flatten lookup.
 *
 * @property valid Candidates whose names and types can map to the target parameter.
 * @property incompatible Same-name candidates with incompatible types, used for diagnostics.
 * @property mapWith Candidates that would require a nested converter annotation, which AutoMap
 *   does not infer for flattened paths.
 */
internal data class FlattenLookup(
    val valid: List<FlattenCandidate>,
    val incompatible: List<FlattenCandidate>,
    val mapWith: List<FlattenCandidate>,
)
