package io.github.ch000se.automap.compiler.generation

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.CodeBlock
import io.github.ch000se.automap.compiler.AutoMapOptions
import io.github.ch000se.automap.compiler.DefaultVisibility
import io.github.ch000se.automap.compiler.MappingException
import io.github.ch000se.automap.compiler.MappingJob

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
    kspResolver: Resolver,
    autoMapIndex: Map<String, Set<String>>,
    private val options: AutoMapOptions,
    private val knownMappingFiles: List<KSFile>,
    private val validator: BidirectionalMappingValidator = BidirectionalMappingValidator(),
    private val expressionResolver: ExpressionResolver = ExpressionResolver(autoMapIndex, options.allowNarrowing),
    private val flattenResolver: FlattenResolver = FlattenResolver(expressionResolver),
    private val functionConverterResolver: FunctionConverterResolver = FunctionConverterResolver(kspResolver),
) {
    private val functionConvertersByPair = mutableMapOf<Pair<String, String>, String>()

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
            flatten = job.flatten,
        )
        val reverse = resolveReverse(job)

        return MapperFile(
            packageName = job.sourceClass.packageName.asString(),
            fileName = mapperFileName(job),
            functions = listOfNotNull(
                MapperFunction(
                    source = job.sourceClass,
                    target = job.targetClass,
                    resolutions = forward,
                    generateListVariant = job.generateListVariant,
                    functionName = job.functionName.takeIf { it.isNotBlank() }
                        ?: "to${job.targetClass.simpleName.asString()}",
                    jvmName = job.jvmName.takeIf { it.isNotBlank() },
                    visibility = visibilityFor(job.sourceClass, job.targetClass, job.visibility),
                ),
                reverse,
            ),
            dependencyFiles = listOfNotNull(
                job.sourceClass.containingFile,
                job.targetClass.containingFile,
            ).plus(knownMappingFiles).distinct(),
        )
    }

    private fun resolveReverse(job: MappingJob): MapperFunction? {
        if (!job.bidirectional) return null

        val inverseRenames = buildInverseRenames(job.sourceClass)
        val resolutions = resolveAll(
            source = job.targetClass,
            target = job.sourceClass,
            inverseRenames = inverseRenames,
            flatten = false,
        )
        return MapperFunction(
            source = job.targetClass,
            target = job.sourceClass,
            resolutions = resolutions,
            generateListVariant = job.generateListVariant,
            functionName = "to${job.sourceClass.simpleName.asString()}",
            jvmName = null,
            visibility = visibilityFor(job.targetClass, job.sourceClass, job.visibility),
        )
    }

    private fun buildInverseRenames(source: KSClassDeclaration): Map<String, String> {
        val sourceCtorParams = source.primaryConstructor?.parameters.orEmpty()
        return sourceProperties(source).mapNotNull { property ->
            val propertyName = property.simpleName.asString()
            val paramAnnotations = sourceCtorParams.firstOrNull { it.name?.asString() == propertyName }
                ?.annotations
                ?.toList()
                .orEmpty()
            val annotations = mergeFieldAnnotations(
                propertyName = propertyName,
                propertyAnnotations = property.annotations.toList(),
                parameterAnnotations = paramAnnotations,
                symbol = property,
            )
            val targetName = annotations.firstValueOf("MapName")
            targetName?.let { propertyName to it }
        }.toMap()
    }

    private fun resolveAll(
        source: KSClassDeclaration,
        target: KSClassDeclaration,
        inverseRenames: Map<String, String>,
        flatten: Boolean,
    ): List<Resolution> {
        val ctor = target.primaryConstructor
            ?: throw MappingException(
                "@AutoMap target ${target.qualifiedName?.asString()} must have a primary constructor",
                source,
            )
        val context = ResolveContext(
            source = source,
            target = target,
            sourceProps = sourceProperties(source),
            sourceCtorParams = source.primaryConstructor?.parameters.orEmpty(),
            inverseRenames = inverseRenames,
            flatten = flatten,
            annotationsByProperty = annotationsByProperty(
                sourceProperties(source),
                source.primaryConstructor?.parameters.orEmpty(),
            ),
        )
        return ctor.parameters.mapNotNull { resolveParam(context, it) }
    }

    private fun resolveParam(context: ResolveContext, targetParam: KSValueParameter): Resolution? {
        val targetParamName = targetParam.name!!.asString()
        val targetType = targetParam.type.resolve()
        val matched = findMatchedSource(context, targetParamName, targetType)
            ?: return omitOrError(context, targetParam, targetType)

        if (matched.annotations.any { it.isNamed("MapIgnore") }) {
            return omitIgnoredOrError(context, targetParam, matched.propertyName)
        }

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
                    valueExpression = matched.expression,
                    sourceSymbol = matched.property,
                ),
            )
            Resolution.Plain(targetParamName, expression)
        }
    }

    private fun findMatchedSource(
        context: ResolveContext,
        targetParamName: String,
        targetType: KSType,
    ): MatchedSource? {
        val inverse = context.inverseRenames[targetParamName]
        val inverseMatch = inverse?.let { context.sourceProps.firstOrNull { p -> p.name == it } }
        val mapNameMatches = context.sourceProps.filter { property ->
            val annotations = context.annotationsByProperty[property.name].orEmpty()
            annotations.firstValueOf("MapName") == targetParamName
        }
        if (mapNameMatches.size > 1) {
            errorMultipleExplicitMappings(context.source, targetParamName, mapNameMatches)
        }
        val mapNameMatch = mapNameMatches.singleOrNull()
        val explicit = inverseMatch ?: mapNameMatch
        if (explicit != null) {
            return explicit.toMatched(context)
        }

        val nameMatches = context.sourceProps.filter { property ->
            property.name == targetParamName &&
                context.annotationsByProperty[property.name].orEmpty().none { it.isNamed("MapName") }
        }
        val nameMatch = nameMatches.firstOrNull()
        val flattenLookup = flattenResolver.candidatesFor(
            FlattenContext(
                sourceProps = context.sourceProps,
                annotationsByProperty = context.annotationsByProperty,
                targetParamName = targetParamName,
                targetType = targetType,
                flatten = context.flatten,
            ),
        )

        if (nameMatch != null && flattenLookup.valid.isNotEmpty()) {
            errorTopLevelFlattenConflict(context.source, targetParamName, nameMatch, flattenLookup.valid)
        }
        if (nameMatch != null) return nameMatch.toMatched(context)

        if (flattenLookup.mapWith.isNotEmpty()) {
            errorMapWithOnFlattenedPath(context.source, flattenLookup.mapWith.first())
        }
        if (flattenLookup.valid.size > 1) {
            errorMultipleFlattenCandidates(context.source, targetParamName, flattenLookup.valid)
        }
        flattenLookup.valid.singleOrNull()?.let { return it.toMatched() }

        if (flattenLookup.incompatible.isNotEmpty()) {
            errorIncompatibleFlattenCandidates(context.source, targetParamName, targetType, flattenLookup.incompatible)
        }
        return null
    }

    private fun omitOrError(
        context: ResolveContext,
        targetParam: KSValueParameter,
        targetType: KSType,
    ): Resolution? {
        val targetParamName = targetParam.name!!.asString()
        return if (targetParam.hasDefault) {
            Resolution.Omit(targetParamName)
        } else {
            errorNoMapping(context.source, context.target, targetParamName, targetType, context.sourceProps, targetParam)
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
        val sourceType = matched.property.type.resolve()
        val functionRef = mapWith.functionReference()
        return if (functionRef == null) {
            val lambdaName = "${targetParamName}Mapper"
            Resolution.LambdaConverter(
                targetParamName = targetParamName,
                expression = CodeBlock.of("%N(%L)", lambdaName, matched.expression),
                lambdaName = lambdaName,
                sourceType = sourceType,
                targetType = targetType,
            )
        } else {
            val converter = functionConverterResolver.resolve(
                FunctionConverterContext(
                    source = matched.owner,
                    property = matched.property,
                    fieldName = targetParamName,
                    functionRef = functionRef,
                    sourceType = sourceType,
                    targetType = targetType,
                ),
            )
            registerFunctionConverter(sourceType, targetType, converter.reference, matched.property)
            Resolution.Plain(
                targetParamName,
                CodeBlock.of("%L(%L)", converter.reference, matched.expression),
            )
        }
    }

    private fun errorNoMapping(
        source: KSClassDeclaration,
        target: KSClassDeclaration,
        targetParamName: String,
        targetType: KSType,
        sourceProps: List<KSPropertyDeclaration>,
        targetParam: KSValueParameter,
    ): Nothing {
        val candidates = sourceProps.joinToString("\n") {
            "  - ${it.name}: ${it.type.resolve().fqn()}"
        }
        throw MappingException(
            buildString {
                append("Cannot map required field \"").append(targetParamName).append("\" in ")
                append(target.simpleName.asString()).append(".\n\n")
                append("No source property found and target parameter has no default value.\n\n")
                append("Source candidates:\n").append(candidates).append("\n\n")
                val flattenLookup = flattenResolver.candidatesFor(
                    FlattenContext(
                        sourceProps = sourceProps,
                        annotationsByProperty = sourceProps.associate { property ->
                            property.name to property.annotations.toList()
                        },
                        targetParamName = targetParamName,
                        targetType = targetType,
                        flatten = false,
                    ),
                )
                if (flattenLookup.incompatible.isNotEmpty()) {
                    append("Incompatible flattened candidates:\n")
                    append(flattenLookup.incompatible.renderCandidates()).append("\n\n")
                }
                appendFixHints(targetParamName)
            },
            targetParam,
        )
    }

    private fun errorTopLevelFlattenConflict(
        source: KSClassDeclaration,
        targetParamName: String,
        topLevel: KSPropertyDeclaration,
        flattened: List<FlattenCandidate>,
    ): Nothing {
        throw MappingException(
            buildString {
                append("Ambiguous mapping for target field \"").append(targetParamName).append("\".\n\n")
                append("Found top-level candidate:\n")
                append("- ").append(topLevel.name).append(": ").append(topLevel.type.resolve().fqn()).append("\n\n")
                append("Found flattened candidate:\n")
                append(flattened.renderCandidates()).append("\n\n")
                append("Fix:\n")
                append("1. Add @MapName(\"").append(targetParamName)
                    .append("\") to the exact source property you want\n")
                append("2. Remove @Flatten / disable flatten = true")
            },
            source,
        )
    }

    private fun errorMultipleFlattenCandidates(
        source: KSClassDeclaration,
        targetParamName: String,
        candidates: List<FlattenCandidate>,
    ): Nothing {
        throw MappingException(
            buildString {
                append("Cannot infer flattened mapping for target field \"")
                    .append(targetParamName)
                    .append("\".\n\n")
                append("Multiple candidates found:\n")
                append(candidates.renderCandidates()).append("\n\n")
                append("Fix:\n")
                append("1. Add @MapName(\"").append(targetParamName)
                    .append("\") to the correct source property\n")
                append("2. Disable flatten = true and use @Flatten only on the intended property\n")
                append("3. Rename one of the fields")
            },
            source,
        )
    }

    private fun errorIncompatibleFlattenCandidates(
        source: KSClassDeclaration,
        targetParamName: String,
        targetType: KSType,
        candidates: List<FlattenCandidate>,
    ): Nothing {
        throw MappingException(
            buildString {
                append("Cannot infer flattened mapping for target field \"")
                    .append(targetParamName)
                    .append("\".\n\n")
                append("Flattened candidates with matching name have incompatible types for target type ")
                    .append(targetType.fqn())
                    .append(":\n")
                append(candidates.renderCandidates()).append("\n\n")
                appendFixHints(targetParamName)
            },
            source,
        )
    }

    private fun errorMapWithOnFlattenedPath(
        source: KSClassDeclaration,
        candidate: FlattenCandidate,
    ): Nothing {
        throw MappingException(
            buildString {
                append("Cannot apply @MapWith to inferred flattened path \"")
                    .append(candidate.renderedPath)
                    .append("\".\n\n")
                append("For custom conversion, expose a top-level property annotated with @MapWith ")
                append("or use explicit mapping support if available.")
            },
            source,
        )
    }

    private fun errorMultipleExplicitMappings(
        source: KSClassDeclaration,
        targetParamName: String,
        candidates: List<KSPropertyDeclaration>,
    ): Nothing {
        throw MappingException(
            buildString {
                append("Ambiguous explicit mapping for target field \"")
                    .append(targetParamName)
                    .append("\".\n\n")
                append("Multiple @MapName candidates found:\n")
                candidates.forEach {
                    append("- ").append(it.name).append(": ").append(it.type.resolve().fqn()).append("\n")
                }
                append("\nFix:\n")
                append("1. Keep @MapName(\"").append(targetParamName).append("\") on only one source property\n")
                append("2. Rename one source property\n")
                append("3. Use a converter-backed top-level property")
            },
            candidates.firstOrNull() ?: source,
        )
    }

    private fun registerFunctionConverter(
        sourceType: KSType,
        targetType: KSType,
        reference: String,
        symbol: KSPropertyDeclaration,
    ) {
        val pair = sourceType.typeKey() to targetType.typeKey()
        val existing = functionConvertersByPair[pair]
        if (existing != null && existing != reference) {
            throw MappingException(
                buildString {
                    append("Duplicate converter detected for ")
                        .append(pair.first)
                        .append(" -> ")
                        .append(pair.second)
                        .append(".\n\n")
                    append("Converters:\n")
                    append("- ").append(existing).append("\n")
                    append("- ").append(reference).append("\n\n")
                    append("Fix:\n")
                    append("1. Remove one converter\n")
                    append("2. Use @MapWith on the field to select explicitly")
                },
                symbol,
            )
        }
        functionConvertersByPair[pair] = reference
    }

    private fun KSType.typeKey(): String {
        val args = arguments.mapNotNull { it.type?.resolve() }
        val suffix = if (args.isEmpty()) "" else args.joinToString(prefix = "<", postfix = ">") { it.typeKey() }
        val nullable = if (isMarkedNullable) "?" else ""
        return "${fqn()}$suffix$nullable"
    }

    private fun List<FlattenCandidate>.renderCandidates(): String =
        joinToString("\n") { "- ${it.renderedPath}: ${it.type.fqn()}" }

    private fun KSPropertyDeclaration.toMatched(context: ResolveContext): MatchedSource =
        MatchedSource(
            owner = context.source,
            property = this,
            propertyName = name,
            annotations = context.annotationsByProperty[name].orEmpty(),
            expression = CodeBlock.of("%N", name),
        )

    private fun FlattenCandidate.toMatched(): MatchedSource =
        MatchedSource(
            owner = path.first().parentDeclaration as KSClassDeclaration,
            property = path.last(),
            propertyName = renderedPath,
            annotations = emptyList(),
            expression = CodeBlock.of("%L", renderedPath),
        )

    private fun mapperFileName(job: MappingJob): String {
        val suffix = job.functionName.takeIf { it.isNotBlank() }
            ?.replaceFirstChar { it.uppercase() }
            ?: "To${job.targetClass.simpleName.asString()}"
        return "${job.sourceClass.simpleName.asString()}${suffix}Mapper"
    }

    private fun sourceProperties(source: KSClassDeclaration): List<KSPropertyDeclaration> {
        val declared = source.declarations.filterIsInstance<KSPropertyDeclaration>().toList()
        val declaredNames = declared.map { it.name }.toSet()
        val inherited = source.getAllProperties()
            .filterNot { it.name in declaredNames }
            .toList()
        return declared + inherited
    }

    private fun annotationsByProperty(
        sourceProps: List<KSPropertyDeclaration>,
        sourceCtorParams: List<KSValueParameter>,
    ): Map<String, List<KSAnnotation>> = sourceProps.associate { property ->
        val propertyName = property.simpleName.asString()
        val constructorAnnotations = sourceCtorParams
            .firstOrNull { it.name?.asString() == propertyName }
            ?.annotations
            ?.toList()
            .orEmpty()
        propertyName to mergeFieldAnnotations(
            propertyName = propertyName,
            propertyAnnotations = property.annotations.toList(),
            parameterAnnotations = constructorAnnotations,
            symbol = property,
        )
    }

    private fun visibilityFor(
        source: KSClassDeclaration,
        target: KSClassDeclaration,
        requested: String,
    ): MapperVisibility {
        return when (requested.uppercase()) {
            "PUBLIC" -> MapperVisibility.PUBLIC
            "INTERNAL" -> MapperVisibility.INTERNAL
            else -> when (options.defaultVisibility) {
                DefaultVisibility.PUBLIC -> MapperVisibility.PUBLIC
                DefaultVisibility.INTERNAL -> MapperVisibility.INTERNAL
                DefaultVisibility.AUTO -> if (Modifier.INTERNAL in source.modifiers || Modifier.INTERNAL in target.modifiers) {
                    MapperVisibility.INTERNAL
                } else {
                    MapperVisibility.PUBLIC
                }
            }
        }
    }

    private data class ResolveContext(
        val source: KSClassDeclaration,
        val target: KSClassDeclaration,
        val sourceProps: List<KSPropertyDeclaration>,
        val sourceCtorParams: List<KSValueParameter>,
        val inverseRenames: Map<String, String>,
        val flatten: Boolean,
        val annotationsByProperty: Map<String, List<KSAnnotation>>,
    )

    private data class MatchedSource(
        val owner: KSClassDeclaration,
        val property: KSPropertyDeclaration,
        val propertyName: String,
        val annotations: List<KSAnnotation>,
        val expression: CodeBlock,
    )

    private fun KSAnnotation.functionReference(): String? {
        val value = arguments.firstOrNull { it.name?.asString() == "value" }?.value as? String
        val fn = arguments.firstOrNull { it.name?.asString() == "fn" }?.value as? String
        return listOfNotNull(fn, value).firstOrNull { it.isNotBlank() }
    }

    private fun mergeFieldAnnotations(
        propertyName: String,
        propertyAnnotations: List<KSAnnotation>,
        parameterAnnotations: List<KSAnnotation>,
        symbol: KSPropertyDeclaration,
    ): List<KSAnnotation> {
        failOnConflictingStringAnnotation("MapName", propertyName, propertyAnnotations, parameterAnnotations, symbol)
        failOnConflictingStringAnnotation("MapWith", propertyName, propertyAnnotations, parameterAnnotations, symbol)
        return propertyAnnotations + parameterAnnotations
    }

    private fun failOnConflictingStringAnnotation(
        annotationName: String,
        propertyName: String,
        propertyAnnotations: List<KSAnnotation>,
        parameterAnnotations: List<KSAnnotation>,
        symbol: KSPropertyDeclaration,
    ) {
        val propertyValue = propertyAnnotations.firstOrNull { it.isNamed(annotationName) }?.stringValue()
        val parameterValue = parameterAnnotations.firstOrNull { it.isNamed(annotationName) }?.stringValue()
        if (propertyValue != null && parameterValue != null && propertyValue != parameterValue) {
            throw MappingException(
                buildString {
                    append("Conflicting @").append(annotationName)
                        .append(" annotations found for property \"")
                        .append(propertyName)
                        .append("\".\n\n")
                    append("Found:\n")
                    append("- @param:").append(annotationName).append("(\"").append(parameterValue).append("\")\n")
                    append("- @property:").append(annotationName).append("(\"").append(propertyValue).append("\")\n\n")
                    append("Fix:\n")
                    append("1. Keep only one annotation\n")
                    append("2. Make both values identical")
                },
                symbol,
            )
        }
    }

    private fun KSAnnotation.stringValue(): String? {
        val value = arguments.firstOrNull { it.name?.asString() == "value" }?.value as? String
        val fn = arguments.firstOrNull { it.name?.asString() == "fn" }?.value as? String
        return listOfNotNull(fn, value).firstOrNull { it.isNotBlank() }
    }
}
