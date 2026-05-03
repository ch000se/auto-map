package io.github.ch000se.automap.compiler

import com.tschuchort.compiletesting.SourceFile
import io.github.ch000se.automap.compiler.support.CompilationHelper
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertOk
import io.github.ch000se.automap.compiler.support.CompilationHelper.callMapper
import io.github.ch000se.automap.compiler.support.CompilationHelper.newSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RenameTest {

    @Test
    fun `MapName renames source property`() {
        val src = SourceFile.kotlin(
            "Contact.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap
            import io.github.ch000se.automap.annotations.MapName

            @AutoMap(target = ContactDto::class)
            data class Contact(
                val id: Long,
                @MapName("displayName") val fullName: String,
            )

            data class ContactDto(val id: Long, val displayName: String)
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(src).also { it.assertOk() }
        val contact = r.newSource("demo.Contact", 1L, "Jane Doe")
        val dto = r.callMapper("demo.ContactToContactDtoMapperKt", "toContactDto", contact)
        assertEquals("ContactDto(id=1, displayName=Jane Doe)", dto.toString())
    }
}