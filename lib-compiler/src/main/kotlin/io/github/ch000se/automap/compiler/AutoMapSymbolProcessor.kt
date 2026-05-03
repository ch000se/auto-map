package io.github.ch000se.automap.compiler

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.KSType

internal const val ANNOTATIONS_PACKAGE = "io.github.ch000se.automap.annotations"
internal const val AUTOMAP_FQN = "$ANNOTATIONS_PACKAGE.AutoMap"

/**
 * Normalized mapping descriptor used by the generator.
 *
 * The public annotation can be placed on either side of a mapping, but the compiler pipeline works
 * with one canonical direction: [sourceClass] mapped into [targetClass]. [annotatedSymbol] keeps
 * the original declaration so diagnostics and KSP dependency tracking point to the file that
 * declared `@AutoMap`.
 *
 * @property sourceClass Class whose properties are read by the generated mapper.
 * @property targetClass Class whose primary constructor is called by the generated mapper.
 * @property bidirectional Whether the same declaration should also emit the reverse mapper.
 * @property flatten Whether nested composite source properties may be searched for target fields.
 * @property generateListVariant Whether `List<Source>.toTargetList()` should be emitted.
 * @property annotatedSymbol Original class declaration that carried the `@AutoMap` annotation.
 */
internal data class MappingJob(
    val sourceClass: KSClassDeclaration,
    val targetClass: KSClassDeclaration,
    val bidirectional: Boolean,
    val flatten: Boolean,
    val generateListVariant: Boolean,
    val functionName: String,
    val jvmName: String,
    val visibility: String,
    val annotatedSymbol: KSClassDeclaration,
)

/**
 * KSP symbol processor that discovers `@AutoMap` declarations and delegates mapper source
 * generation.
 *
 * The processor runs in two phases for each KSP round:
 *
 * 1. It validates every class annotated with `io.github.ch000se.automap.annotations.AutoMap` and
 *    normalizes the declaration into a [MappingJob]. Normalization always produces a
 *    `sourceClass -> targetClass` descriptor, regardless of whether the user wrote
 *    `@AutoMap(target = ...)` on the source or `@AutoMap(source = ...)` on the target.
 * 2. It builds an index of known AutoMap pairs so nested objects and collection elements can call
 *    other generated mapper functions, then asks [MapperGenerator] to emit Kotlin sources.
 *
 * This class is internal because application code should not create or call it directly. Add
 * `automap-compiler` to the Gradle `ksp(...)` configuration and let
 * [AutoMapSymbolProcessorProvider] register the processor with KSP.
 *
 * @property codeGenerator KSP code generator used to create generated Kotlin mapper files.
 * @property logger KSP logger used to report validation and mapping errors at compile time.
 */
internal class AutoMapSymbolProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val rawOptions: Map<String, String>,
) : SymbolProcessor {

    /**
     * Processes one KSP round and generates mapper extension functions for valid `@AutoMap`
     * declarations.
     *
     * Mapping errors are reported through [logger] and do not stop other valid mappings from being
     * generated in the same round.
     *
     * @param resolver KSP resolver for the current processing round.
     * @return Symbols that KSP should revisit in a later round.
     */
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val options = try {
            AutoMapOptions.parse(rawOptions)
        } catch (e: MappingException) {
            logger.error(e.message ?: "Invalid AutoMap KSP option")
            return emptyList()
        }
        val annotated = resolver.getSymbolsWithAnnotation(AUTOMAP_FQN)
            .filterIsInstance<KSClassDeclaration>()
            .toList()

        val jobs = annotated.mapNotNull { normalizeOrReport(it, options) }
        val validJobs = removeDuplicateMappings(jobs)
        val collisionFreeJobs = removeFunctionCollisions(validJobs)
        val autoMapIndex = buildAutoMapIndex(collisionFreeJobs)
        val dependencyFiles = collisionFreeJobs.mapNotNull { it.annotatedSymbol.containingFile }.distinct()

        val generator = MapperGenerator(codeGenerator, resolver, autoMapIndex, options, dependencyFiles)
        for (job in collisionFreeJobs) {
            try {
                generator.generate(job)
            } catch (e: MappingException) {
                logger.error(e.message ?: "AutoMap error", e.symbol ?: job.annotatedSymbol)
            }
        }
        if (options.generateRegistryDoc) {
            try {
                MapperRegistryDocEmitter(codeGenerator).write(collisionFreeJobs)
            } catch (e: MappingException) {
                logger.error(e.message ?: "AutoMap error", e.symbol ?: collisionFreeJobs.firstOrNull()?.annotatedSymbol)
            }
        }
        return emptyList()
    }

    private fun removeDuplicateMappings(jobs: List<MappingJob>): List<MappingJob> {
        val expanded = jobs.flatMap { job ->
            buildList {
                add(RegisteredMapping(job.sourceClass.fqn(), job.targetClass.fqn(), job))
                if (job.bidirectional) add(RegisteredMapping(job.targetClass.fqn(), job.sourceClass.fqn(), job))
            }
        }
        val grouped = expanded.groupBy { it.sourceFqn to it.targetFqn }
        val duplicateJobs = mutableSetOf<MappingJob>()
        for ((pair, duplicates) in grouped) {
            if (duplicates.size > 1) {
                duplicateJobs += duplicates.map { it.job }
                val declarations = duplicates.joinToString("\n") {
                    "- ${it.job.annotatedSymbol.qualifiedName?.asString() ?: it.job.annotatedSymbol.simpleName.asString()}"
                }
                logger.error(
                    buildString {
                        append("Duplicate AutoMap mapping detected.\n\n")
                        append("Mapping:\n")
                        append("- ").append(pair.first.substringAfterLast('.'))
                            .append(" -> ")
                            .append(pair.second.substringAfterLast('.'))
                            .append("\n\n")
                        append("Declared in:\n").append(declarations).append("\n\n")
                        append("Fix:\n")
                        append("1. Keep only one @AutoMap for this pair\n")
                        append("2. Change the target type\n")
                        append("3. Remove duplicate mapper")
                    },
                    duplicates.first().job.annotatedSymbol,
                )
            }
        }
        return jobs.filter { it !in duplicateJobs }
    }

    private fun removeFunctionCollisions(jobs: List<MappingJob>): List<MappingJob> {
        val declarations = jobs.flatMap { job -> generatedDeclarations(job) }
        val collisions = declarations.groupBy { it.key }.filterValues { it.size > 1 }
        val collisionJobs = mutableSetOf<MappingJob>()
        for ((_, conflicting) in collisions) {
            collisionJobs += conflicting.map { it.job }
            val first = conflicting.first()
            logger.error(
                buildString {
                    append("Generated mapper function collision detected.\n\n")
                    append("Function:\n")
                    append("- ").append(first.functionName).append("\n\n")
                    append("Conflicting mappings:\n")
                    conflicting.forEach { declaration ->
                        append("- ").append(declaration.description).append("\n")
                    }
                    append("\nFix:\n")
                    append("1. Set @AutoMap(functionName = \"...\")\n")
                    append("2. Set @AutoMap(jvmName = \"...\")\n")
                    append("3. Disable list variant generation with generateListVariant = false")
                },
                first.job.annotatedSymbol,
            )
        }
        return jobs.filter { it !in collisionJobs }
    }

    private fun generatedDeclarations(job: MappingJob): List<GeneratedDeclaration> {
        val packageName = job.sourceClass.packageName.asString()
        val sourceFqn = job.sourceClass.fqn()
        val targetFqn = job.targetClass.fqn()
        val functionName = job.effectiveFunctionName()
        return buildList {
            add(
                GeneratedDeclaration(
                    key = "$packageName|$sourceFqn|$functionName",
                    functionName = functionName,
                    description = "$sourceFqn -> $targetFqn",
                    job = job,
                ),
            )
            if (job.generateListVariant) {
                val listName = "${functionName}List"
                val jvmListName = job.jvmName.takeIf { it.isNotBlank() }?.let { "${it}List" } ?: listName
                add(
                    GeneratedDeclaration(
                        key = "$packageName|kotlin.collections.List|$jvmListName",
                        functionName = listName,
                        description = "List<$sourceFqn> -> List<$targetFqn>",
                        job = job,
                    ),
                )
            }
        }
    }

    private fun normalizeOrReport(decl: KSClassDeclaration, options: AutoMapOptions): MappingJob? {
        return try {
            normalize(decl, options)
        } catch (e: MappingException) {
            logger.error(e.message ?: "AutoMap error", e.symbol ?: decl)
            null
        }
    }

    private fun buildAutoMapIndex(jobs: List<MappingJob>): Map<String, Set<String>> {
        val pairs = buildList {
            for (job in jobs) {
                val sourceFqn = job.sourceClass.qualifiedName?.asString()
                val targetFqn = job.targetClass.qualifiedName?.asString()
                if (sourceFqn != null && targetFqn != null) {
                    add(sourceFqn to targetFqn)
                    if (job.bidirectional) add(targetFqn to sourceFqn)
                }
            }
        }
        return pairs.groupBy({ it.first }, { it.second }).mapValues { it.value.toSet() }
    }

    private fun normalize(decl: KSClassDeclaration, options: AutoMapOptions): MappingJob? {
        val ann = decl.annotations.firstOrNull { it.shortName.asString() == "AutoMap" } ?: return null

        val targetType = ann.arguments.firstOrNull { it.name?.asString() == "target" }?.value as? KSType
        val sourceType = ann.arguments.firstOrNull { it.name?.asString() == "source" }?.value as? KSType
        val bidirectional = ann.arguments.firstOrNull { it.name?.asString() == "bidirectional" }?.value as? Boolean
            ?: false
        val flatten = ann.booleanArg("flatten", optionName = "automap.flatten", default = false, global = options.flatten)
        val generateListVariant =
            ann.booleanArg(
                name = "generateListVariant",
                optionName = "automap.generateListVariant",
                default = true,
                global = options.generateListVariant,
            )
        val functionName = ann.arguments.firstOrNull { it.name?.asString() == "functionName" }?.value as? String
            ?: ""
        val jvmName = ann.arguments.firstOrNull { it.name?.asString() == "jvmName" }?.value as? String
            ?: ""
        val visibility = ann.arguments.firstOrNull { it.name?.asString() == "visibility" }?.value?.toString()
            ?.substringAfterLast('.')
            ?.takeIf { ann.hasExplicitArgument("visibility") }
            ?: options.defaultVisibility.name

        val effectiveTarget = targetType?.takeUnless { isNothing(it) }
        val effectiveSource = sourceType?.takeUnless { isNothing(it) }

        requireSingleDirection(effectiveTarget, effectiveSource, decl)
        requireConcrete(decl, "Generic AutoMap source types are not supported yet", decl)

        return if (effectiveTarget != null) {
            val target = effectiveTarget.classDeclarationOrError("@AutoMap target must resolve to a class", decl)
            requireUsableSourceClass(decl, decl)
            requireConcrete(target, "Generic AutoMap target types are not supported yet", decl)
            requireUsablePrimaryConstructor(target, decl)
            MappingJob(
                sourceClass = decl,
                targetClass = target,
                bidirectional = bidirectional,
                flatten = flatten,
                generateListVariant = generateListVariant,
                functionName = functionName,
                jvmName = jvmName,
                visibility = visibility,
                annotatedSymbol = decl,
            )
        } else {
            val source = effectiveSource!!.classDeclarationOrError("@AutoMap source must resolve to a class", decl)
            requireUsableSourceClass(source, decl)
            requireConcrete(source, "Generic AutoMap source types are not supported yet", decl)
            requireUsablePrimaryConstructor(decl, decl)
            MappingJob(
                sourceClass = source,
                targetClass = decl,
                bidirectional = bidirectional,
                flatten = flatten,
                generateListVariant = generateListVariant,
                functionName = functionName,
                jvmName = jvmName,
                visibility = visibility,
                annotatedSymbol = decl,
            )
        }
    }

    private fun requireSingleDirection(
        targetType: KSType?,
        sourceType: KSType?,
        symbol: KSAnnotated,
    ) {
        if ((targetType == null) == (sourceType == null)) {
            throw MappingException("@AutoMap requires exactly one of 'target' or 'source'", symbol)
        }
    }

    private fun KSType.classDeclarationOrError(message: String, symbol: KSAnnotated): KSClassDeclaration =
        declaration as? KSClassDeclaration ?: throw MappingException(message, symbol)

    private fun requireConcrete(
        declaration: KSClassDeclaration,
        message: String,
        symbol: KSAnnotated,
    ) {
        if (declaration.typeParameters.isNotEmpty()) {
            throw MappingException(
                buildString {
                    append(message).append(".\n\n")
                    append("Source:\n")
                    append("- ").append(declaration.simpleName.asString())
                    append(declaration.typeParameters.joinToString(prefix = "<", postfix = ">") {
                        it.name.asString()
                    })
                    append("\n\n")
                    append("Fix:\n")
                    append("1. Create a concrete wrapper type\n")
                    append("2. Write this mapper manually\n")
                    append("3. Remove @AutoMap from the generic class")
                },
                symbol,
            )
        }
    }

    private fun requireUsablePrimaryConstructor(target: KSClassDeclaration, symbol: KSAnnotated) {
        val primary = target.primaryConstructor
            ?: throw MappingException(
                "Cannot generate mapper to ${target.simpleName.asString()}.\n\n" +
                    "Reason:\n- target primary constructor is private or unavailable.\n\n" +
                    "AutoMap currently supports only public primary constructors.\n\n" +
                    "Fix:\n1. Make the primary constructor public\n2. Add a supported public constructor\n3. Map manually",
                symbol,
            )
        if (Modifier.PRIVATE in primary.modifiers || Modifier.PROTECTED in primary.modifiers) {
            throw MappingException(
                "Cannot generate mapper to ${target.simpleName.asString()}.\n\n" +
                    "Reason:\n- target primary constructor is private or unavailable.\n\n" +
                    "AutoMap currently supports only public primary constructors.\n\n" +
                    "Fix:\n1. Make the primary constructor public\n2. Add a supported public constructor\n3. Map manually",
                primary,
            )
        }
    }

    private fun requireUsableSourceClass(source: KSClassDeclaration, symbol: KSAnnotated) {
        if (source.classKind != ClassKind.CLASS ||
            Modifier.SEALED in source.modifiers ||
            Modifier.ABSTRACT in source.modifiers
        ) {
            throw MappingException(
                "Cannot generate mapper from ${source.simpleName.asString()}.\n\n" +
                    "Reason:\n- AutoMap source must be a concrete class with readable properties.\n\n" +
                    "AutoMap does not generate mappers from sealed, abstract, or interface source types.\n\n" +
                    "Fix:\n1. Annotate a concrete implementation\n2. Write this mapper manually\n3. Remove @AutoMap from the abstract type",
                symbol,
            )
        }
    }

    private fun isNothing(t: KSType): Boolean {
        val q = t.declaration.qualifiedName?.asString()
        return q == "kotlin.Nothing" || q == "java.lang.Void"
    }

    private fun KSClassDeclaration.fqn(): String = qualifiedName?.asString() ?: simpleName.asString()

    private fun MappingJob.effectiveFunctionName(): String =
        functionName.takeIf { it.isNotBlank() } ?: "to${targetClass.simpleName.asString()}"

    private fun KSAnnotation.booleanArg(name: String, optionName: String, default: Boolean, global: Boolean): Boolean {
        val value = arguments.firstOrNull { it.name?.asString() == name }?.value as? Boolean ?: default
        return if (value == default && optionName in rawOptions) global else value
    }

    private fun KSAnnotation.hasExplicitArgument(name: String): Boolean =
        toString().contains("$name =") || toString().contains("$name=")

    private data class RegisteredMapping(
        val sourceFqn: String,
        val targetFqn: String,
        val job: MappingJob,
    )

    private data class GeneratedDeclaration(
        val key: String,
        val functionName: String,
        val description: String,
        val job: MappingJob,
    )
}

/**
 * Compile-time mapping failure reported through KSP diagnostics.
 *
 * @property symbol Optional symbol that should receive the compiler error location.
 */
internal class MappingException(
    message: String,
    val symbol: KSAnnotated? = null,
) : RuntimeException(message)
