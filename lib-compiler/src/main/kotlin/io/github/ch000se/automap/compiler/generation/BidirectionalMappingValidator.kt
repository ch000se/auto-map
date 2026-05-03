package io.github.ch000se.automap.compiler.generation

import io.github.ch000se.automap.compiler.MappingException
import io.github.ch000se.automap.compiler.MappingJob

/**
 * Validates constraints that are specific to generated bidirectional mappings.
 *
 * Reverse mapper generation is only safe when every source-side annotation has an automatic inverse.
 * Custom converters, renames, and ignored fields can make the reverse direction unsafe without
 * user-provided logic, so this validator rejects those combinations early with a clear KSP error.
 */
internal class BidirectionalMappingValidator {

    /**
     * Checks whether [job] can safely generate both forward and reverse mapper functions.
     *
     * Non-bidirectional jobs are accepted without additional checks.
     *
     * @throws MappingException when a bidirectional source property uses an annotation that has no
     *   automatic reverse semantics.
     */
    fun validate(job: MappingJob) {
        if (!job.bidirectional) return

        val sourceProps = job.sourceClass.getAllProperties().toList()
        val sourceCtorParams = job.sourceClass.primaryConstructor?.parameters.orEmpty()
        for (property in sourceProps) {
            val name = property.simpleName.asString()
            val annotations = annotationsFor(name, property.annotations.toList(), sourceCtorParams)
            val asymmetric = annotations.firstOrNull { it.isAnyOf("MapWith", "MapWithFn", "MapName", "MapIgnore", "Flatten") }
            if (asymmetric != null) {
                throw MappingException(
                    buildString {
                        append("Cannot generate bidirectional mapper for ")
                        append(job.sourceClass.simpleName.asString())
                        append(" -> ")
                        append(job.targetClass.simpleName.asString())
                        append(".\n\n")
                        append("Reason:\n")
                        append("- @")
                        append(asymmetric.shortName.asString())
                        append(" on field \"")
                        append(name)
                        append("\" is one-way or may not be reversible\n\n")
                        append("Fix:\n")
                        append("1. Declare the reverse mapping explicitly with a separate @AutoMap\n")
                        append("2. Provide a reverse converter\n")
                        append("3. Remove bidirectional = true")
                    },
                    job.annotatedSymbol,
                )
            }
        }
    }
}
