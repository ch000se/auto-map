package io.github.ch000se.automap.compiler

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
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
 * @property annotatedSymbol Original class declaration that carried the `@AutoMap` annotation.
 */
internal data class MappingJob(
    val sourceClass: KSClassDeclaration,
    val targetClass: KSClassDeclaration,
    val bidirectional: Boolean,
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
        val annotated = resolver.getSymbolsWithAnnotation(AUTOMAP_FQN)
            .filterIsInstance<KSClassDeclaration>()
            .toList()

        val jobs = annotated.mapNotNull(::normalizeOrReport)
        val autoMapIndex = buildAutoMapIndex(jobs)
        val dependencyFiles = jobs.mapNotNull { it.annotatedSymbol.containingFile }.distinct()

        val generator = MapperGenerator(codeGenerator, autoMapIndex, dependencyFiles)
        for (job in jobs) {
            try {
                generator.generate(job)
            } catch (e: MappingException) {
                logger.error(e.message ?: "AutoMap error", e.symbol ?: job.annotatedSymbol)
            }
        }
        return emptyList()
    }

    private fun normalizeOrReport(decl: KSClassDeclaration): MappingJob? {
        return try {
            normalize(decl)
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

    private fun normalize(decl: KSClassDeclaration): MappingJob? {
        val ann = decl.annotations.firstOrNull { it.shortName.asString() == "AutoMap" } ?: return null

        val targetType = ann.arguments.firstOrNull { it.name?.asString() == "target" }?.value as? KSType
        val sourceType = ann.arguments.firstOrNull { it.name?.asString() == "source" }?.value as? KSType
        val bidirectional = ann.arguments.firstOrNull { it.name?.asString() == "bidirectional" }?.value as? Boolean
            ?: false

        val effectiveTarget = targetType?.takeUnless { isNothing(it) }
        val effectiveSource = sourceType?.takeUnless { isNothing(it) }

        requireSingleDirection(effectiveTarget, effectiveSource, decl)

        return if (effectiveTarget != null) {
            val target = effectiveTarget.classDeclarationOrError("@AutoMap target must resolve to a class", decl)
            MappingJob(sourceClass = decl, targetClass = target, bidirectional = bidirectional, annotatedSymbol = decl)
        } else {
            val source = effectiveSource!!.classDeclarationOrError("@AutoMap source must resolve to a class", decl)
            MappingJob(sourceClass = source, targetClass = decl, bidirectional = bidirectional, annotatedSymbol = decl)
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

    private fun isNothing(t: KSType): Boolean {
        val q = t.declaration.qualifiedName?.asString()
        return q == "kotlin.Nothing" || q == "java.lang.Void"
    }
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
