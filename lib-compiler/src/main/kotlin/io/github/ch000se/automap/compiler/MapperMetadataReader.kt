package io.github.ch000se.automap.compiler

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import io.github.ch000se.automap.compiler.generation.RegisteredMapper

internal const val AUTOMAP_METADATA_PATH = "META-INF/automap/mappings"
private const val GENERATED_METADATA_PACKAGE = "io.github.ch000se.automap.generated"

/**
 * Reads mapper metadata exposed by dependency modules.
 *
 * The generated Kotlin metadata path is preferred because KSP can resolve it from dependency
 * classpaths. Resource metadata is also read when available and invalid resource lines are ignored
 * with a warning, since dependency artifacts should not crash the current processor.
 */
internal class MapperMetadataReader(
    private val logger: KSPLogger,
    private val resolver: Resolver,
    private val classLoader: ClassLoader = Thread.currentThread().contextClassLoader
        ?: MapperMetadataReader::class.java.classLoader,
) {
    /**
     * Returns distinct mapper metadata entries visible to this processing run.
     */
    fun readFromClasspath(): List<RegisteredMapper> {
        return (readGeneratedDeclarations() + readResourceMetadata()).distinctBy {
            listOf(it.sourceFqn, it.targetFqn, it.functionFqn, it.functionName)
        }
    }

    @OptIn(KspExperimental::class)
    private fun readGeneratedDeclarations(): List<RegisteredMapper> =
        resolver.getDeclarationsFromPackage(GENERATED_METADATA_PACKAGE)
            .mapNotNull { declaration ->
                val annotation = declaration.annotations.firstOrNull {
                    it.shortName.asString() == "AutoMapGeneratedMapping"
                } ?: return@mapNotNull null
                RegisteredMapper(
                    sourceFqn = annotation.stringArg("sourceFqn") ?: return@mapNotNull null,
                    targetFqn = annotation.stringArg("targetFqn") ?: return@mapNotNull null,
                    functionFqn = annotation.stringArg("functionFqn") ?: return@mapNotNull null,
                    functionName = annotation.stringArg("functionName") ?: return@mapNotNull null,
                    sourcePackage = annotation.stringArg("sourcePackage") ?: return@mapNotNull null,
                    targetPackage = annotation.stringArg("targetPackage") ?: return@mapNotNull null,
                )
            }
            .toList()

    private fun readResourceMetadata(): List<RegisteredMapper> {
        val resources = classLoader.getResources(AUTOMAP_METADATA_PATH).toList()
        return resources.flatMap { url ->
            url.openStream().bufferedReader().useLines { lines ->
                lines.mapIndexedNotNull { index, line ->
                    val trimmed = line.trim()
                    if (trimmed.isBlank() || trimmed.startsWith("#")) {
                        null
                    } else {
                        RegisteredMapper.parse(trimmed).also {
                            if (it == null) {
                                logger.warn(
                                    "Ignoring invalid AutoMap metadata at $url:${index + 1}: $trimmed",
                                )
                            }
                        }
                    }
                }.toList()
            }
        }
    }

    private fun com.google.devtools.ksp.symbol.KSAnnotation.stringArg(name: String): String? =
        arguments.firstOrNull { it.name?.asString() == name }?.value as? String
}
