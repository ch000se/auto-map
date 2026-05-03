package io.github.ch000se.automap.compiler

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSFile
import io.github.ch000se.automap.compiler.generation.MapperEmitter
import io.github.ch000se.automap.compiler.generation.MapperRegistry
import io.github.ch000se.automap.compiler.generation.MapperResolver

/**
 * Coordinates mapper resolution and Kotlin source emission for a normalized [MappingJob].
 *
 * This class is intentionally small: symbol discovery stays in [AutoMapSymbolProcessor], mapping
 * decisions stay in [MapperResolver], and KotlinPoet output stays in [MapperEmitter]. Keeping this
 * facade in the root compiler package preserves the processor wiring while avoiding a monolithic
 * generator implementation.
 *
 * @property codeGenerator KSP file writer used by the emitter to create generated mapper sources.
 * @property mapperRegistry Lookup of current-module and dependency-module mapper functions.
 * @property dependencyFiles Source files that can affect generated mapper output.
 */
internal class MapperGenerator(
    codeGenerator: CodeGenerator,
    kspResolver: Resolver,
    mapperRegistry: MapperRegistry,
    options: AutoMapOptions,
    dependencyFiles: List<KSFile>,
) {
    private val resolver = MapperResolver(
        kspResolver = kspResolver,
        mapperRegistry = mapperRegistry,
        options = options,
        knownMappingFiles = dependencyFiles,
    )
    private val emitter = MapperEmitter(codeGenerator)

    /**
     * Generates mapper extension functions for [job].
     *
     * The generated file is named `<Source>To<Target>Mapper.kt` and is placed in the source class
     * package. If [MappingJob.bidirectional] is `true`, the same file also receives reverse
     * `Target.toSource()` and `List<Target>.toSourceList()` functions.
     *
     * @throws MappingException when a target constructor parameter cannot be resolved or when a
     *   bidirectional mapping contains asymmetric annotations.
     */
    fun generate(job: MappingJob) {
        emitter.write(resolver.resolve(job))
    }
}
