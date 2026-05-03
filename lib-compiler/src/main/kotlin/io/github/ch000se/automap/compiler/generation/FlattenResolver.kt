package io.github.ch000se.automap.compiler.generation

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType

private const val DEFAULT_MAX_DEPTH = 3

/**
 * Finds compile-time flattened source property paths for target constructor parameters.
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
                if (nested.annotations.any { it.isNamed("MapWith") }) {
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

internal data class FlattenContext(
    val sourceProps: List<KSPropertyDeclaration>,
    val annotationsByProperty: Map<String, List<KSAnnotation>>,
    val targetParamName: String,
    val targetType: KSType,
    val flatten: Boolean,
)

internal data class FlattenCandidate(
    val path: List<KSPropertyDeclaration>,
    val renderedPath: String,
    val type: KSType,
    val depth: Int,
)

internal data class FlattenLookup(
    val valid: List<FlattenCandidate>,
    val incompatible: List<FlattenCandidate>,
    val mapWith: List<FlattenCandidate>,
)
