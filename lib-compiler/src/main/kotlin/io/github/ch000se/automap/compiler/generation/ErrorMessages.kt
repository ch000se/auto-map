package io.github.ch000se.automap.compiler.generation

/**
 * Appends the standard user-facing remediation block for an unresolved target constructor
 * parameter.
 */
internal fun StringBuilder.appendFixHints(targetParamName: String) {
    append("Fix:\n")
    append("  1. Add @MapName(\"").append(targetParamName)
        .append("\") on the matching source property\n")
    append("  2. Add @MapWith(Converter::class) to provide custom logic\n")
    append("  3. Change target type to match source")
}
