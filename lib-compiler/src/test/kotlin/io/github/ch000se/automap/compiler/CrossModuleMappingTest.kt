package io.github.ch000se.automap.compiler

import com.tschuchort.compiletesting.SourceFile
import io.github.ch000se.automap.compiler.support.CompilationHelper
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertError
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertErrorContains
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertOk
import io.github.ch000se.automap.compiler.support.CompilationHelper.generatedSource
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CrossModuleMappingTest {

    @Test
    fun `downstream module uses mapper metadata from dependency module`() {
        val upstream = compileUpstream()
        val metadata = upstream.outputDirectory.resolve("META-INF/automap/mappings")
        if (!metadata.isFile) {
            metadata.parentFile.mkdirs()
            metadata.writeText(
                "domain.Note|data.NoteDto|domain.toNoteDto|toNoteDto|domain|data\n",
            )
        }

        val feature = SourceFile.kotlin(
            "Feature.kt",
            """
            package feature
            import data.NoteDto
            import domain.Note
            import io.github.ch000se.automap.annotations.AutoMap

            data class ScreenModel(val note: Note)

            @AutoMap(source = ScreenModel::class)
            data class ScreenDto(val note: NoteDto)
            """.trimIndent(),
        )

        val result = CompilationHelper.compile(feature, classpaths = listOf(upstream.outputDirectory)).also {
            it.assertOk()
        }

        val generated = result.generatedSource("ScreenModelToScreenDtoMapper.kt")
        assertTrue(generated.contains("import domain.toNoteDto"))
        assertTrue(generated.contains("note = note.toNoteDto()"))
    }

    @Test
    fun `downstream module maps nullable and list values using dependency metadata`() {
        val upstream = compileUpstream()
        val metadata = upstream.outputDirectory.resolve("META-INF/automap/mappings")
        if (!metadata.isFile) {
            metadata.parentFile.mkdirs()
            metadata.writeText(
                "domain.Note|data.NoteDto|domain.toNoteDto|toNoteDto|domain|data\n",
            )
        }

        val feature = SourceFile.kotlin(
            "FeatureCollections.kt",
            """
            package feature
            import data.NoteDto
            import domain.Note
            import io.github.ch000se.automap.annotations.AutoMap

            data class ScreenModel(
                val note: Note?,
                val notes: List<Note>,
            )

            @AutoMap(source = ScreenModel::class)
            data class ScreenDto(
                val note: NoteDto?,
                val notes: List<NoteDto>,
            )
            """.trimIndent(),
        )

        val result = CompilationHelper.compile(feature, classpaths = listOf(upstream.outputDirectory)).also {
            it.assertOk()
        }

        val generated = result.generatedSource("ScreenModelToScreenDtoMapper.kt")
        assertTrue(generated.contains("note = note?.toNoteDto()"))
        assertTrue(generated.contains("notes = notes.map { it.toNoteDto() }"))
    }

    @Test
    fun `duplicate dependency metadata mapping fails`() {
        val upstream = compileUpstream()
        val feature = SourceFile.kotlin(
            "DuplicateExternal.kt",
            """
            package feature
            import data.NoteDto
            import domain.Note
            import io.github.ch000se.automap.annotations.AutoMap

            data class ScreenModel(val note: Note)

            @AutoMap(source = ScreenModel::class)
            data class ScreenDto(val note: NoteDto)
            """.trimIndent(),
        )
        val duplicateMetadata = SourceFile.kotlin(
            "DuplicateMetadata.kt",
            """
            package io.github.ch000se.automap.generated
            import io.github.ch000se.automap.annotations.AutoMapGeneratedMapping

            @AutoMapGeneratedMapping(
                sourceFqn = "domain.Note",
                targetFqn = "data.NoteDto",
                functionFqn = "other.toNoteDto",
                functionName = "toNoteDto",
                sourcePackage = "domain",
                targetPackage = "data",
            )
            internal object DuplicateNoteMapping
            """.trimIndent(),
        )

        val result = CompilationHelper.compile(feature, duplicateMetadata, classpaths = listOf(upstream.outputDirectory)).also {
            it.assertError()
        }

        result.assertErrorContains("Duplicate AutoMap mapping found")
        result.assertErrorContains("domain.Note -> data.NoteDto")
    }

    private fun compileUpstream(): com.tschuchort.compiletesting.JvmCompilationResult {
        val domain = SourceFile.kotlin(
            "Note.kt",
            """
            package domain

            data class Note(val id: Int, val title: String)
            """.trimIndent(),
        )
        val data = SourceFile.kotlin(
            "NoteDto.kt",
            """
            package data
            import domain.Note
            import io.github.ch000se.automap.annotations.AutoMap

            @AutoMap(source = Note::class)
            data class NoteDto(val id: Int = 0, val title: String = "")
            """.trimIndent(),
        )
        return CompilationHelper.compile(domain, data).also { it.assertOk() }
    }

}
