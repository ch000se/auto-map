package io.github.ch000se.automap.compiler.generation

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ksp.toTypeName
import io.github.ch000se.automap.compiler.MappingException
import io.github.ch000se.automap.compiler.MappingJob

private const val AUTOMAP_CONVERTER_FQN = "io.github.ch000se.automap.annotations.AutoMapConverter"

/**
 * Resolves a normalized [MappingJob] into file and function metadata ready for KotlinPoet emission.
 *
 * This class owns constructor-parameter resolution: source property matching, default-value
 * omission, `@MapIgnore`, `@MapName`, and `@MapWith` handling. Type-to-expression decisions are
 * delegated to [ExpressionResolver], and bidirectional safety checks are delegated to
 * [BidirectionalMappingValidator].
 *
 * @property validator Checks constraints that only apply to bidirectional mapper generation.
 * @property expressionResolver Builds Kotlin expressions for plain source-to-target assignments.
 */
internal class MapperResolver(
    autoMapIndex: Map<String, Set<String>>,
    private val validator: BidirectionalMappingValidator = BidirectionalMappingValidator(),
    private val expressionResolver: ExpressionResolver = ExpressionResolver(autoMapIndex),
) {

    /**
     * Builds a complete mapper file plan for [job].
     *
     * @throws MappingException when any target constructor parameter cannot be mapped safely.
     */
    fun resolve(job: MappingJob): MapperFile {
        validator.validate(job)

        val forward = resolveAll(
            source = job.sourceClass,
            target = job.targetClass,
            inverseRenames = emptyMap(),
        )
        val reverse = resolveReverse(job)

        return MapperFile(
            packageName = job.sourceClass.packageName.asString(),
            fileName = mapperFileName(job.sourceClass, job.targetClass),
            functions = listOfNotNull(
                MapperFunction(job.sourceClass, job.targetClass, forward),
                reverse,
            ),
        )
    }

    private fun resolveReverse(job: MappingJob): MapperFunction? {
        if (!job.bidirectional) return null

        val inverseRenames = buildInverseRenames(job.sourceClass)
        val resolutions = resolveAll(
            source = job.targetClass,
            target = job.sourceClass,
            inverseRenames = inverseRenames,
        )
        return MapperFunction(job.targetClass, job.sourceClass, resolutions)
    }

    private fun buildInverseRenames(source: KSClassDeclaration): Map<String, String> {
        val sourceCtorParams = source.primaryConstructor?.parameters.orEmpty()
        return source.getAllProperties().mapNotNull { property ->
            val propertyName = property.simpleName.asString()
            val annotations = annotationsFor(propertyName, property.annotations.toList(), sourceCtorParams)
            val targetName = annotations.firstValueOf("MapName")
            targetName?.let { propertyName to it }
        }.toMap()
    }

    private fun resolveAll(
        source: KSClassDeclaration,
        target: KSClassDeclaration,
        inverseRenames: Map<String, String>,
    ): List<Resolution> {
        val ctor = target.primaryConstructor
            ?: throw MappingException(
                "@AutoMap target ${target.qualifiedName?.asString()} must have a primary constructor",
                source,
            )
        val context = ResolveContext(
            source = source,
            target = target,
            sourceProps = source.getAllProperties().toList(),
            sourceCtorParams = source.primaryConstructor?.parameters.orEmpty(),
            inverseRenames = inverseRenames,
        )
        return ctor.parameters.mapNotNull { resolveParam(context, it) }
    }

    private fun resolveParam(context: ResolveContext, targetParam: KSValueParameter): Resolution? {
        val targetParamName = targetParam.name!!.asString()
        val matched =
            findMatchedSource(context, targetParamName) ?: return omitOrError(context, targetParam)

        if (matched.annotations.any { it.isNamed("MapIgnore") }) {
            return omitIgnoredOrError(context, targetParam, matched.propertyName)
        }

        val targetType = targetParam.type.resolve()
        val mapWith = matched.annotations.firstOrNull { it.isNamed("MapWith") }
        return if (mapWith != null) {
            mapWithResolution(mapWith, matched, targetParamName, targetType)
        } else {
            val sourceType = matched.property.type.resolve()
            val expression = expressionResolver.expressionFor(
                ExpressionContext(
                    source = context.source,
                    target = context.target,
                    propName = matched.propertyName,
                    sourceType = sourceType,
                    targetType = targetType,
                    targetParamName = targetParamName,
                ),
            )
            Resolution.Plain(targetParamName, expression)
        }
    }

    private fun findMatchedSource(context: ResolveContext, targetParamName: String): MatchedSource? {
        val inverse = context.inverseRenames[targetParamName]
        val inverseMatch = inverse?.let { context.sourceProps.firstOrNull { p -> p.name == it } }
        val mapNameMatch = context.sourceProps.firstOrNull { property ->
            val annotations = context.annotationsByProperty[property.name].orEmpty()
            annotations.firstValueOf("MapName") == targetParamName
        }
        val nameMatch = context.sourceProps.firstOrNull { property ->
            property.name == targetParamName &&
                context.annotationsByProperty[property.name].orEmpty().none { it.isNamed("MapName") }
        }
        val property = inverseMatch ?: mapNameMatch ?: nameMatch
        return property?.let {
            MatchedSource(
                property = it,
                propertyName = it.name,
                annotations = context.annotationsByProperty[it.name].orEmpty(),
            )
        }
    }

    private fun omitOrError(context: ResolveContext, targetParam: KSValueParameter): Resolution? {
        val targetParamName = targetParam.name!!.asString()
        return if (targetParam.hasDefault) {
            Resolution.Omit(targetParamName)
        } else {
            errorNoMapping(context.source, context.target, targetParamName, context.sourceProps)
        }
    }

    private fun omitIgnoredOrError(
        context: ResolveContext,
        targetParam: KSValueParameter,
        propName: String,
    ): Resolution {
        val targetParamName = targetParam.name!!.asString()
        return if (targetParam.hasDefault) {
            Resolution.Omit(targetParamName)
        } else {
            throw MappingException(
                "Source property '$propName' is @MapIgnore but target param '$targetParamName' has no default",
                context.source,
            )
        }
    }

    private fun mapWithResolution(
        mapWith: KSAnnotation,
        matched: MatchedSource,
        targetParamName: String,
        targetType: KSType,
    ): Resolution {
        val converterType = mapWith.arguments.firstOrNull { it.name?.asString() == "value" }?.value as? KSType
        return if (converterType == null || converterType.isError || converterType.fqn() == AUTOMAP_CONVERTER_FQN) {
            val sourceType = matched.property.type.resolve()
            val lambdaName = "${targetParamName}Mapper"
            Resolution.LambdaConverter(
                targetParamName = targetParamName,
                expression = CodeBlock.of("%N(%N)", lambdaName, matched.propertyName),
                lambdaName = lambdaName,
                sourceType = sourceType,
                targetType = targetType,
            )
        } else {
            classConverterResolution(converterType, matched, targetParamName)
        }
    }

    private fun classConverterResolution(
        converterType: KSType,
        matched: MatchedSource,
        targetParamName: String,
    ): Resolution.Plain {
        val converterClass = converterType.declaration as? KSClassDeclaration
            ?: throw MappingException("@MapWith converter must resolve to a class", matched.property)
        val converterReceiver = if (converterClass.classKind == ClassKind.OBJECT) {
            CodeBlock.of("%T", converterType.toTypeName())
        } else {
            CodeBlock.of("%T()", converterType.toTypeName())
        }
        return Resolution.Plain(
            targetParamName,
            CodeBlock.of("%L.convert(%N)", converterReceiver, matched.propertyName),
        )
    }

    private fun errorNoMapping(
        source: KSClassDeclaration,
        target: KSClassDeclaration,
        targetParamName: String,
        sourceProps: List<KSPropertyDeclaration>,
    ): Nothing {
        val candidates = sourceProps.joinToString("\n") {
            "  - ${it.name}: ${it.type.resolve().fqn()}"
        }
        throw MappingException(
            buildString {
                append("Cannot map field \"").append(targetParamName).append("\" in ")
                append(target.simpleName.asString()).append(".\n\n")
                append("Source candidates:\n").append(candidates).append("\n\n")
                appendFixHints(targetParamName)
            },
            source,
        )
    }

    private fun mapperFileName(source: KSClassDeclaration, target: KSClassDeclaration): String =
        "${source.simpleName.asString()}To${target.simpleName.asString()}Mapper"

    private data class ResolveContext(
        val source: KSClassDeclaration,
        val target: KSClassDeclaration,
        val sourceProps: List<KSPropertyDeclaration>,
        val sourceCtorParams: List<KSValueParameter>,
        val inverseRenames: Map<String, String>,
    ) {
        val annotationsByProperty: Map<String, List<KSAnnotation>> = sourceProps.associate { property ->
            val propertyName = property.simpleName.asString()
            val constructorAnnotations = sourceCtorParams
                .firstOrNull { it.name?.asString() == propertyName }
                ?.annotations
                ?.toList()
                .orEmpty()
            propertyName to property.annotations.toList() + constructorAnnotations
        }
    }

    private data class MatchedSource(
        val property: KSPropertyDeclaration,
        val propertyName: String,
        val annotations: List<KSAnnotation>,
    )
}
