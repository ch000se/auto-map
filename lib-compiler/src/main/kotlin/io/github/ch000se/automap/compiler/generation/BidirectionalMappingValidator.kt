package io.github.ch000se.automap.compiler.generation

import io.github.ch000se.automap.compiler.MappingException
import io.github.ch000se.automap.compiler.MappingJob

/**
 * Validates constraints that are specific to generated bidirectional mappings.
 *
 * Reverse mapper generation is only safe when every source-side annotation has an automatic inverse.
 * Rename annotations can be inverted, but custom converters and ignored fields cannot be reversed
 * without user-provided logic, so this validator rejects those combinations early with a clear KSP
 * error.
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
            val asymmetric = annotations.firstOrNull { it.isAnyOf("MapWith", "MapIgnore") }
            if (asymmetric != null) {
                throw MappingException(
                    "@AutoMap(bidirectional = true) cannot be used because field '$name' " +
                        "has @MapWith / @MapIgnore which has no automatic inverse. " +
                        "Either remove bidirectional and write the reverse mapper manually, " +
                        "or remove the @MapWith/@MapIgnore.",
                    job.annotatedSymbol,
                )
            }
        }
    }
}
