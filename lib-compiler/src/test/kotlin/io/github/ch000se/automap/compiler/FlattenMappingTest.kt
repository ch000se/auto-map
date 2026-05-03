package io.github.ch000se.automap.compiler

import com.tschuchort.compiletesting.SourceFile
import io.github.ch000se.automap.compiler.support.CompilationHelper
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertError
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertErrorContains
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertOk
import io.github.ch000se.automap.compiler.support.CompilationHelper.callMapper
import io.github.ch000se.automap.compiler.support.CompilationHelper.generatedSource
import io.github.ch000se.automap.compiler.support.CompilationHelper.newSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FlattenMappingTest {

    @Test
    fun `flatten true maps nested fields`() {
        val src = SourceFile.kotlin(
            "Note.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap

            @AutoMap(target = Note::class, flatten = true)
            data class NoteWithAuthorDbModel(
                val noteDbModel: NoteDbModel,
                val authorDbModel: AuthorDbModel,
            )

            data class NoteDbModel(val id: Int, val title: String)
            data class AuthorDbModel(val authorName: String)
            data class Note(val id: Int, val title: String, val authorName: String)
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(src).also { it.assertOk() }
        val noteDb = r.newSource("demo.NoteDbModel", 7, "Draft")
        val authorDb = r.newSource("demo.AuthorDbModel", "Ada")
        val source = r.newSource("demo.NoteWithAuthorDbModel", noteDb, authorDb)
        val note = r.callMapper("demo.NoteWithAuthorDbModelToNoteMapperKt", "toNote", source)

        assertEquals("Note(id=7, title=Draft, authorName=Ada)", note.toString())
        val generated = r.generatedSource("NoteWithAuthorDbModelToNoteMapper.kt")
        assertTrue(generated.contains("id = noteDbModel.id"))
        assertTrue(generated.contains("title = noteDbModel.title"))
        assertTrue(generated.contains("authorName = authorDbModel.authorName"))
    }

    @Test
    fun `Flatten marker maps selected nested fields`() {
        val src = SourceFile.kotlin(
            "Marked.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap
            import io.github.ch000se.automap.annotations.Flatten

            @AutoMap(target = Note::class)
            data class NoteWithAuthorDbModel(
                @Flatten val note: NoteDbModel,
                @Flatten val author: AuthorDbModel,
                val debugName: String,
            )

            data class NoteDbModel(val id: Int, val title: String)
            data class AuthorDbModel(val authorName: String)
            data class Note(val id: Int, val title: String, val authorName: String)
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(src).also { it.assertOk() }
        val noteDb = r.newSource("demo.NoteDbModel", 1, "Title")
        val authorDb = r.newSource("demo.AuthorDbModel", "Grace")
        val source = r.newSource("demo.NoteWithAuthorDbModel", noteDb, authorDb, "debug")
        val note = r.callMapper("demo.NoteWithAuthorDbModelToNoteMapperKt", "toNote", source)

        assertEquals("Note(id=1, title=Title, authorName=Grace)", note.toString())
    }

    @Test
    fun `direct top-level field maps when there is no flattened conflict`() {
        val src = SourceFile.kotlin(
            "Direct.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap

            @AutoMap(target = Target::class, flatten = true)
            data class Source(val id: Int, val nested: Nested)

            data class Nested(val title: String)
            data class Target(val id: Int)
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(src).also { it.assertOk() }
        val nested = r.newSource("demo.Nested", "ignored")
        val source = r.newSource("demo.Source", 9, nested)
        val target = r.callMapper("demo.SourceToTargetMapperKt", "toTarget", source)

        assertEquals("Target(id=9)", target.toString())
    }

    @Test
    fun `top-level and flattened candidates conflict`() {
        val src = SourceFile.kotlin(
            "Conflict.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap

            @AutoMap(target = Target::class, flatten = true)
            data class Source(val id: Int, val note: NoteDbModel)

            data class NoteDbModel(val id: Int)
            data class Target(val id: Int)
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(src).also { it.assertError() }

        r.assertErrorContains("Ambiguous mapping for target field \"id\"")
        r.assertErrorContains("- id: kotlin.Int")
        r.assertErrorContains("- note.id: kotlin.Int")
    }

    @Test
    fun `multiple flattened candidates fail`() {
        val src = SourceFile.kotlin(
            "Multiple.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap

            @AutoMap(target = Target::class, flatten = true)
            data class Source(val note: NoteDbModel, val user: UserDbModel)

            data class NoteDbModel(val id: Int)
            data class UserDbModel(val id: Int)
            data class Target(val id: Int)
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(src).also { it.assertError() }

        r.assertErrorContains("Cannot infer flattened mapping for target field \"id\"")
        r.assertErrorContains("- note.id: kotlin.Int")
        r.assertErrorContains("- user.id: kotlin.Int")
    }

    @Test
    fun `explicit MapName resolves flatten conflict`() {
        val src = SourceFile.kotlin(
            "Explicit.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap
            import io.github.ch000se.automap.annotations.MapName

            @AutoMap(target = Target::class, flatten = true)
            data class Source(
                @MapName("id") val localId: Int,
                val note: NoteDbModel,
            )

            data class NoteDbModel(val id: Int)
            data class Target(val id: Int)
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(src).also { it.assertOk() }
        val note = r.newSource("demo.NoteDbModel", 99)
        val source = r.newSource("demo.Source", 5, note)
        val target = r.callMapper("demo.SourceToTargetMapperKt", "toTarget", source)

        assertEquals("Target(id=5)", target.toString())
    }

    @Test
    fun `recursive flatten maps nested path`() {
        val src = SourceFile.kotlin(
            "Recursive.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap

            @AutoMap(target = Note::class, flatten = true)
            data class Source(val wrapper: Wrapper)

            data class Wrapper(val note: NoteDbModel)
            data class NoteDbModel(val id: Int)
            data class Note(val id: Int)
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(src).also { it.assertOk() }
        val noteDb = r.newSource("demo.NoteDbModel", 3)
        val wrapper = r.newSource("demo.Wrapper", noteDb)
        val source = r.newSource("demo.Source", wrapper)
        val note = r.callMapper("demo.SourceToNoteMapperKt", "toNote", source)

        assertEquals("Note(id=3)", note.toString())
        assertTrue(r.generatedSource("SourceToNoteMapper.kt").contains("id = wrapper.note.id"))
    }

    @Test
    fun `recursion depth limit stops deep lookup`() {
        val src = SourceFile.kotlin(
            "Depth.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap

            @AutoMap(target = Target::class, flatten = true)
            data class Source(val one: One)

            data class One(val two: Two)
            data class Two(val three: Three)
            data class Three(val four: Four)
            data class Four(val id: Int)
            data class Target(val id: Int = -1)
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(src).also { it.assertOk() }

        assertTrue(!r.generatedSource("SourceToTargetMapper.kt").contains("one.two.three.four.id"))
    }

    @Test
    fun `type conversion works for flattened field`() {
        val src = SourceFile.kotlin(
            "Conversion.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap

            @AutoMap(target = Target::class, flatten = true)
            data class Source(val nested: Nested)

            data class Nested(val id: Int)
            data class Target(val id: Long)
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(src).also { it.assertOk() }
        val nested = r.newSource("demo.Nested", 12)
        val source = r.newSource("demo.Source", nested)
        val target = r.callMapper("demo.SourceToTargetMapperKt", "toTarget", source)

        assertEquals("Target(id=12)", target.toString())
        assertTrue(r.generatedSource("SourceToTargetMapper.kt").contains("id = nested.id.toLong()"))
    }

    @Test
    fun `incompatible flattened type gives helpful error`() {
        val src = SourceFile.kotlin(
            "BadType.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap

            @AutoMap(target = Target::class, flatten = true)
            data class Source(val nested: Nested)

            data class Nested(val id: String)
            data class Target(val id: Int)
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(src).also { it.assertError() }

        r.assertErrorContains("incompatible types")
        r.assertErrorContains("- nested.id: kotlin.String")
    }

    @Test
    fun `flatten does not recurse into standard library or enum types`() {
        val src = SourceFile.kotlin(
            "Excluded.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap

            @AutoMap(target = Target::class, flatten = true)
            data class Source(
                val name: String,
                val items: List<Nested>,
                val labels: Map<String, Nested>,
                val status: Status,
            )

            data class Nested(val id: Int)
            enum class Status { Ready }
            data class Target(val id: Int = -1)
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(src).also { it.assertOk() }

        assertTrue(!r.generatedSource("SourceToTargetMapper.kt").contains("id ="))
    }
}
