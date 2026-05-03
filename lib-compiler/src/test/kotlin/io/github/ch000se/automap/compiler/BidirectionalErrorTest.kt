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
            import io.github.ch000se.automap.annotations.AutoMapConverter
            import io.github.ch000se.automap.annotations.MapWith

            object ComputeTax : AutoMapConverter<Long, Long> {
                override fun convert(value: Long): Long = value
            }

            @AutoMap(target = ProductDto::class, bidirectional = true)
            data class Product(
                val id: Long,
                @MapWith(ComputeTax::class) val priceInCents: Long,
            )

            data class ProductDto(val id: Long, val priceInCents: Long)
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(src)
        r.assertError()
        r.assertErrorContains("priceInCents")
        r.assertErrorContains("bidirectional")
    }
}
