package io.github.ch000se.automap.compiler

import com.google.devtools.ksp.symbol.KSAnnotated

/**
 * Parsed global AutoMap KSP options.
 *
 * Annotation arguments can override these defaults when the user explicitly sets an annotation
 * parameter. Values are read once per KSP processing invocation from `SymbolProcessorEnvironment`.
 *
 * @property flatten Global default for auto-flatten lookup.
 * @property generateListVariant Global default for generated list extension functions.
 * @property allowNarrowing Whether explicitly configured primitive narrowing conversions are
 *   allowed.
 * @property generateRegistryDoc Whether to emit the documentation-only `AutoMapMappers.kt` file.
 * @property defaultVisibility Visibility policy used when an annotation leaves visibility as
 *   `AUTO`.
 */
internal data class AutoMapOptions(
    val flatten: Boolean = false,
    val generateListVariant: Boolean = true,
    val allowNarrowing: Boolean = false,
    val generateRegistryDoc: Boolean = true,
    val defaultVisibility: DefaultVisibility = DefaultVisibility.AUTO,
) {
    companion object {
        /**
         * Parses raw KSP options and reports invalid values through [MappingException].
         */
        fun parse(raw: Map<String, String>, symbol: KSAnnotated? = null): AutoMapOptions =
            AutoMapOptions(
                flatten = raw.booleanOption("automap.flatten", default = false, symbol),
                generateListVariant = raw.booleanOption("automap.generateListVariant", default = true, symbol),
                allowNarrowing = raw.booleanOption("automap.allowNarrowing", default = false, symbol),
                generateRegistryDoc = raw.booleanOption("automap.generateRegistryDoc", default = true, symbol),
                defaultVisibility = raw.visibilityOption("automap.defaultVisibility", symbol),
            )
    }
}

/**
 * Internal representation of the `automap.defaultVisibility` KSP option.
 */
internal enum class DefaultVisibility {
    /** Infer visibility from source and target declarations. */
    AUTO,

    /** Generate public mapper functions by default. */
    PUBLIC,

    /** Generate internal mapper functions by default. */
    INTERNAL,
}

private fun Map<String, String>.booleanOption(
    name: String,
    default: Boolean,
    symbol: KSAnnotated?,
): Boolean {
    val value = this[name] ?: return default
    return when (value.lowercase()) {
        "true" -> true
        "false" -> false
        else -> throw invalidOption(name, value, listOf("true", "false"), symbol)
    }
}

private fun Map<String, String>.visibilityOption(name: String, symbol: KSAnnotated?): DefaultVisibility {
    val value = this[name] ?: return DefaultVisibility.AUTO
    return when (value.lowercase()) {
        "auto" -> DefaultVisibility.AUTO
        "public" -> DefaultVisibility.PUBLIC
        "internal" -> DefaultVisibility.INTERNAL
        else -> throw invalidOption(name, value, listOf("public", "internal", "auto"), symbol)
    }
}

private fun invalidOption(
    name: String,
    value: String,
    expected: List<String>,
    symbol: KSAnnotated?,
): MappingException {
    return MappingException(
        buildString {
            append("Invalid AutoMap KSP option.\n\n")
            append("Option:\n")
            append("- ").append(name).append(" = ").append(value).append("\n\n")
            append("Expected:\n")
            expected.forEach { append("- ").append(it).append("\n") }
        },
        symbol,
    )
}
