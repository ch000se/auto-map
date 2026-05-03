package io.github.ch000se.automap.compiler.generation

import com.squareup.kotlinpoet.MemberName

private const val METADATA_PARTS = 6

/**
 * One generated AutoMap mapper known to the current processing run.
 *
 * Entries come from the current module's `@AutoMap` declarations or from dependency metadata.
 *
 * @property sourceFqn Fully qualified source/receiver type name.
 * @property targetFqn Fully qualified constructed target type name.
 * @property functionFqn Fully qualified generated mapper function name.
 * @property functionName Simple generated mapper function name.
 * @property sourcePackage Package containing the generated extension function.
 * @property targetPackage Package containing the target type.
 */
internal data class RegisteredMapper(
    val sourceFqn: String,
    val targetFqn: String,
    val functionFqn: String,
    val functionName: String,
    val sourcePackage: String,
    val targetPackage: String,
) {
    /** Package containing [functionName]. */
    val functionPackage: String = functionFqn.substringBeforeLast('.', missingDelimiterValue = "")

    /** KotlinPoet member reference used to render imports for nested mapper calls. */
    fun memberName(): MemberName = MemberName(functionPackage, functionName)

    /** Serializes this entry for `META-INF/automap/mappings`. */
    fun toMetadataLine(): String =
        listOf(sourceFqn, targetFqn, functionFqn, functionName, sourcePackage, targetPackage)
            .joinToString("|")

    companion object {
        /**
         * Parses one metadata resource line. Invalid dependency metadata returns `null` so callers
         * can warn and continue.
         */
        fun parse(line: String): RegisteredMapper? {
            val parts = line.split('|')
            if (parts.size != METADATA_PARTS || parts.any { it.isBlank() }) return null
            return RegisteredMapper(
                sourceFqn = parts[0],
                targetFqn = parts[1],
                functionFqn = parts[2],
                functionName = parts[3],
                sourcePackage = parts[4],
                targetPackage = parts[5],
            )
        }
    }
}

/**
 * Lookup table used by expression resolution for nested object and collection element mappings.
 */
internal class MapperRegistry(
    mappings: List<RegisteredMapper>,
) {
    private val byPair = mappings.associateBy { it.sourceFqn to it.targetFqn }

    fun find(sourceFqn: String, targetFqn: String): RegisteredMapper? =
        byPair[sourceFqn to targetFqn]
}
