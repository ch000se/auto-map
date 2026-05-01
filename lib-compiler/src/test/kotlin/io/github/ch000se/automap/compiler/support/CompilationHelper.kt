package io.github.ch000se.automap.compiler.support

import io.github.ch000se.automap.compiler.AutoMapSymbolProcessorProvider
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp
import com.tschuchort.compiletesting.sourcesGeneratedBySymbolProcessor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

object CompilationHelper {

    fun compile(vararg sources: SourceFile): JvmCompilationResult {
        return KotlinCompilation().apply {
            this.sources = sources.toList()
            configureKsp {
                symbolProcessorProviders += AutoMapSymbolProcessorProvider()
            }
            inheritClassPath = true
        }.compile()
    }

    fun JvmCompilationResult.assertOk() {
        assertEquals(KotlinCompilation.ExitCode.OK, exitCode, "Compilation failed:\n$messages")
    }

    fun JvmCompilationResult.assertError() {
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, exitCode)
    }

    fun JvmCompilationResult.assertHasGeneratedFile(fileName: String): String {
        val generated = sourcesGeneratedBySymbolProcessor.toList()
        val file = generated.firstOrNull { it.name == fileName }
        assertTrue(
            file != null,
            "Expected generated file '$fileName' but found: ${generated.map { it.name }}",
        )
        return file!!.readText()
    }

    fun JvmCompilationResult.assertErrorContains(substring: String) {
        assertTrue(
            messages.contains(substring, ignoreCase = true),
            "Expected error message to contain '$substring' but got:\n$messages",
        )
    }

    fun String.assertContent(expected: String) {
        assertEquals(expected.compacted(), this.compacted())
    }

    private fun String.compacted(): String =
        lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
}