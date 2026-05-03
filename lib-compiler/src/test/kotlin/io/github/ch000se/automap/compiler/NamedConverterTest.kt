package io.github.ch000se.automap.compiler

import com.tschuchort.compiletesting.SourceFile
import io.github.ch000se.automap.compiler.support.CompilationHelper
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertOk
import io.github.ch000se.automap.compiler.support.CompilationHelper.callMapper
import io.github.ch000se.automap.compiler.support.CompilationHelper.newSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NamedConverterTest {

    @Test
    fun `MapWith converter class is invoked`() {
        val src = SourceFile.kotlin(
            "Product.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap
            import io.github.ch000se.automap.annotations.AutoMapConverter
            import io.github.ch000se.automap.annotations.MapWith

            object FormatCents : AutoMapConverter<Long, String> {
                override fun convert(value: Long): String =
                    "$" + (value / 100) + "." + (value % 100).toString().padStart(2, '0')
            }

            @AutoMap(target = ProductDto::class)
            data class Product(
                val id: Long,
                @MapWith(FormatCents::class) val priceInCents: Long,
            )

            data class ProductDto(val id: Long, val priceInCents: String)
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(src).also { it.assertOk() }
        val p = r.newSource("demo.Product", 1L, 4999L)
        val dto = r.callMapper("demo.ProductToProductDtoMapperKt", "toProductDto", p)
        assertEquals("ProductDto(id=1, priceInCents=\$49.99)", dto.toString())
    }
}
