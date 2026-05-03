package io.github.ch000se.automap.compiler

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.writeTo

/**
 * Emits a documentation-only list of generated AutoMap pairs.
 *
 * This file is intentionally not a runtime registry. It is aggregating because it lists all mapping
 * declarations discovered in the current processing run.
 */
internal class MapperRegistryDocEmitter(
    private val codeGenerator: CodeGenerator,
) {
    @Suppress("SpreadOperator")
    fun write(jobs: List<MappingJob>) {
        if (jobs.isEmpty()) return

        val comment = buildString {
            append("Generated AutoMap mappings:\n\n")
            jobs.forEach { job ->
                val source = job.sourceClass.qualifiedName?.asString() ?: job.sourceClass.simpleName.asString()
                val target = job.targetClass.qualifiedName?.asString() ?: job.targetClass.simpleName.asString()
                val functionName = job.functionName.takeIf { it.isNotBlank() }
                    ?: "to${job.targetClass.simpleName.asString()}"
                append("- ").append(source).append(" -> ").append(target).append("\n")
                append("  Function: ").append(job.sourceClass.simpleName.asString())
                    .append(".").append(functionName).append("()\n")
                if (job.generateListVariant) {
                    append("  List function: List<").append(job.sourceClass.simpleName.asString())
                        .append(">.").append(functionName).append("List()\n")
                }
                append("\n")
            }
            append("This file is documentation-only.")
        }

        val files = jobs.mapNotNull { it.annotatedSymbol.containingFile }.distinct().toTypedArray()
        FileSpec.builder("io.github.ch000se.automap.generated", "AutoMapMappers")
            .addType(
                TypeSpec.objectBuilder("AutoMapMappers")
                    .addModifiers(KModifier.INTERNAL)
                    .addKdoc("%L", comment)
                    .build(),
            )
            .build()
            .writeTo(codeGenerator, Dependencies(aggregating = true, *files))
    }
}
