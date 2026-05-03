package io.github.ch000se.automap.compiler

import com.tschuchort.compiletesting.SourceFile
import io.github.ch000se.automap.compiler.support.CompilationHelper
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertOk
import io.github.ch000se.automap.compiler.support.CompilationHelper.callMapper
import io.github.ch000se.automap.compiler.support.CompilationHelper.newSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LambdaConverterTest {

    @Test
    fun `MapWith without name generates lambda parameter`() {
        val src = SourceFile.kotlin(
            "Product.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap
            import io.github.ch000se.automap.annotations.MapWith

            @AutoMap(target = ProductDto::class)
            data class Product(
                val id: Long,
                @MapWith val priceInCents: Long,
            )

            data class ProductDto(val id: Long, val priceInCents: String)
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(src).also { it.assertOk() }
        val p = r.newSource("demo.Product", 7L, 250L)
        val function1 = object : Function1<Long, String> {
            override fun invoke(p1: Long): String = "cents=$p1"
        }
        val dto = r.callMapper("demo.ProductToProductDtoMapperKt", "toProductDto", p, function1)
        assertEquals("ProductDto(id=7, priceInCents=cents=250)", dto.toString())
    }
}