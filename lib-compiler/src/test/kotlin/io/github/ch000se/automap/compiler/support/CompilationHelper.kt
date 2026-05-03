package io.github.ch000se.automap.compiler.support

import io.github.ch000se.automap.compiler.AutoMapSymbolProcessorProvider
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp
import com.tschuchort.compiletesting.kspProcessorOptions
import com.tschuchort.compiletesting.sourcesGeneratedBySymbolProcessor
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

object CompilationHelper {

    fun compile(
        vararg sources: SourceFile,
        options: Map<String, String> = emptyMap(),
        classpaths: List<File> = emptyList(),
    ): JvmCompilationResult {
        return KotlinCompilation().apply {
            this.sources = sources.toList()
            this.classpaths = classpaths
            configureKsp {
                symbolProcessorProviders += AutoMapSymbolProcessorProvider()
            }
            kspProcessorOptions.putAll(options)
            inheritClassPath = true
            messageOutputStream = System.out
        }.compile()
    }

    fun JvmCompilationResult.assertOk() {
        assertEquals(KotlinCompilation.ExitCode.OK, exitCode, "Compilation failed:\n$messages")
    }

    fun JvmCompilationResult.assertError() {
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, exitCode)
    }

    fun JvmCompilationResult.assertErrorContains(substring: String) {
        assertTrue(
            messages.contains(substring, ignoreCase = true),
            "Expected error message to contain '$substring' but got:\n$messages",
        )
    }

    fun JvmCompilationResult.generatedSource(fileName: String): String {
        val generated = sourcesGeneratedBySymbolProcessor.toList()
        val file = generated.firstOrNull { it.name == fileName }
        assertTrue(file != null, "Expected generated file '$fileName' but found: ${generated.map { it.name }}")
        return file!!.readText()
    }

    fun JvmCompilationResult.loadClass(fqn: String): Class<*> = classLoader.loadClass(fqn)

    /** Instantiate a source class via its single constructor. */
    fun JvmCompilationResult.newSource(fqn: String, vararg args: Any?): Any {
        val ctor = loadClass(fqn).constructors.first()
        return ctor.newInstance(*args)
    }

    /**
     * Invokes the generated extension function defined in `mapperFqn` (e.g., "demo.UserToUserDtoMapperKt").
     * The first argument is the receiver; following ones are extra lambda params.
     */
    fun JvmCompilationResult.callMapper(
        mapperFqn: String,
        mapperFun: String,
        receiver: Any,
        vararg extra: Any?,
    ): Any {
        val method = loadClass(mapperFqn).declaredMethods.first { it.name == mapperFun }
        return method.invoke(null, receiver, *extra)
    }
}
