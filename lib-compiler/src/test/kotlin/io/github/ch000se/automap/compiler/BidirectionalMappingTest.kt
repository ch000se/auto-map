package io.github.ch000se.automap.compiler

import com.tschuchort.compiletesting.SourceFile
import io.github.ch000se.automap.compiler.support.CompilationHelper
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertOk
import io.github.ch000se.automap.compiler.support.CompilationHelper.callMapper
import io.github.ch000se.automap.compiler.support.CompilationHelper.newSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BidirectionalMappingTest {

    @Test
    fun `bidirectional generates both directions and round-trips`() {
        val src = SourceFile.kotlin(
            "Contact.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap

            @AutoMap(target = ContactDto::class, bidirectional = true)
            data class Contact(
                val id: Long,
                val name: String,
            )

            data class ContactDto(val id: Long, val name: String)
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(src).also { it.assertOk() }
        val mapper = "demo.ContactToContactDtoMapperKt"

        val contact = r.newSource("demo.Contact", 1L, "Jane")
        val dto = r.callMapper(mapper, "toContactDto", contact)
        assertEquals("ContactDto(id=1, name=Jane)", dto.toString())

        val roundTrip = r.callMapper(mapper, "toContact", dto)
        assertEquals("Contact(id=1, name=Jane)", roundTrip.toString())
    }
}
