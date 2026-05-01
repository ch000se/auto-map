package io.github.ch000se.automap.compiler

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

/**
 * KSP entry point for the AutoMap processor.
 *
 * Registered via `META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider`
 * and instantiated by the KSP runtime once per compilation. The provider creates an
 * [AutoMapSymbolProcessor] that scans the round's symbols for `@AutoMap`-annotated classes and
 * generates the corresponding mapper sources.
 *
 * Consumers normally do not reference this class directly — adding the `lib-compiler` artifact
 * with `ksp(...)` in Gradle is sufficient for KSP to discover it.
 */
public class AutoMapSymbolProcessorProvider : SymbolProcessorProvider {
    /**
     * Creates a new [AutoMapSymbolProcessor] for the given KSP [environment].
     *
     * Invoked once per KSP run; the returned processor is reused for every processing round.
     *
     * @param environment KSP-provided environment exposing the [code generator][SymbolProcessorEnvironment.codeGenerator]
     *   used to emit generated files and the [logger][SymbolProcessorEnvironment.logger] used to
     *   report diagnostics back to the compiler.
     * @return A configured [SymbolProcessor] that handles `@AutoMap` annotations.
     */
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        AutoMapSymbolProcessor(environment.codeGenerator, environment.logger)
}