package io.github.ch000se.automap.compiler.generation

/**
 * Appends the standard user-facing remediation block for an unresolved target constructor
 * parameter.
 */
internal fun StringBuilder.appendFixHints(targetParamName: String) {
    append("Fix:\n")
    append("  1. Add a source property named \"").append(targetParamName).append("\"\n")
    append("  2. Add @MapName(\"").append(targetParamName)
        .append("\") on the matching source property\n")
    append("  3. Add a default value in the target constructor\n")
    append("  4. Use @MapWith for custom mapping")
}
