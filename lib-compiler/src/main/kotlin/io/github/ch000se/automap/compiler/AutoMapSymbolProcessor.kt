package io.github.ch000se.automap.compiler

import io.github.ch000se.automap.compiler.codegen.MapperClassGenerator
import io.github.ch000se.automap.compiler.ksp.arg
import io.github.ch000se.automap.compiler.ksp.classDeclaration
import io.github.ch000se.automap.compiler.ksp.findAnnotation
import io.github.ch000se.automap.compiler.ksp.resolvedParams
import io.github.ch000se.automap.compiler.strategy.StrategyResolver
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.ksp.writeTo

private const val AUTO_MAP_FQN = "io.github.ch000se.automap.annotations.AutoMap"

internal class AutoMapSymbolProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(AUTO_MAP_FQN).toList()
        val (valid, deferred) = symbols.partition { it.validate() }
        valid.filterIsInstance<KSClassDeclaration>().forEach { processClass(it) }
        return deferred
    }

    @Suppress("ReturnCount")
    private fun processClass(sourceClass: KSClassDeclaration) {
        // No longer require data class for source — any class is accepted

        val annotation = sourceClass.findAnnotation(AUTO_MAP_FQN) ?: return
        val targetType = annotation.arg("target") as? KSType ?: return
        val targetClass = targetType.classDeclaration() ?: run {
            logger.error("@AutoMap target must resolve to a class", sourceClass)
            return
        }

        // Require an accessible primary constructor (with at least one parameter) or a companion
        // operator invoke factory on the target. An empty no-arg primary constructor cannot be
        // used for mapping because there is no way to populate the target's properties
        val targetParams = targetClass.resolvedParams()
        if (targetParams.isNullOrEmpty()) {
            logger.error(
                "@AutoMap target ${targetClass.qualifiedName?.asString()} must have a public primary " +
                    "constructor with at least one parameter or a companion `operator fun invoke(...)` factory",
                sourceClass,
            )
            return
        }

        val fields = (annotation.arg("fields") as? List<*>)
            ?.filterIsInstance<KSAnnotation>()
            ?: emptyList()

        val duplicates = fields
            .mapNotNull { it.arg("target") as? String }
            .groupBy { it }
            .filter { it.value.size > 1 }
            .keys
        if (duplicates.isNotEmpty()) {
            logger.error(
                "@AutoMap on ${sourceClass.simpleName.asString()}: " +
                    "duplicate Field.target entries: ${duplicates.joinToString()}",
                sourceClass,
            )
            return
        }

        val reverse = annotation.arg("reverse") as? Boolean ?: false
        val beforeMap = annotation.arg("beforeMap") as? Boolean ?: false
        val afterMap = annotation.arg("afterMap") as? Boolean ?: false

        val strategies = StrategyResolver(sourceClass, targetClass, fields, logger)
            .resolve() ?: return

        MapperClassGenerator(sourceClass, targetClass, strategies, beforeMap, afterMap)
            .generate()
            .forEach { fileSpec -> fileSpec.writeTo(codeGenerator, aggregating = false) }

        if (reverse) {
            val reverseStrategies = StrategyResolver(targetClass, sourceClass, emptyList(), logger)
                .resolve() ?: return
            MapperClassGenerator(targetClass, sourceClass, reverseStrategies)
                .generate()
                .forEach { fileSpec -> fileSpec.writeTo(codeGenerator, aggregating = false) }
        }
    }
}