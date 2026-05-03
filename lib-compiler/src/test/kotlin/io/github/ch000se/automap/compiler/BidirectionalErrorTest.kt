package io.github.ch000se.automap.compiler

import com.tschuchort.compiletesting.SourceFile
import io.github.ch000se.automap.compiler.support.CompilationHelper
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertError
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertErrorContains
import org.junit.jupiter.api.Test

class BidirectionalErrorTest {

    @Test
    fun `bidirectional with MapWith errors with field name`() {
        val src = SourceFile.kotlin(
            "Product.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap
            import io.github.ch000se.automap.annotations.MapWith

            fun computeTax(value: Long): Long = value

            @AutoMap(target = ProductDto::class, bidirectional = true)
            data class Product(
                val id: Long,
                @MapWith("computeTax") val priceInCents: Long,
            )

            data class ProductDto(val id: Long, val priceInCents: Long)
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(src)
        r.assertError()
        r.assertErrorContains("priceInCents")
        r.assertErrorContains("bidirectional")
    }

    @Test
    fun `bidirectional with MapName errors with field name`() {
        val src = SourceFile.kotlin(
            "Contact.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap
            import io.github.ch000se.automap.annotations.MapName

            @AutoMap(target = ContactDto::class, bidirectional = true)
            data class Contact(
                val id: Long,
                @MapName("displayName") val name: String,
            )

            data class ContactDto(val id: Long, val displayName: String)
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(src)
        r.assertError()
        r.assertErrorContains("name")
        r.assertErrorContains("bidirectional")
    }
}
