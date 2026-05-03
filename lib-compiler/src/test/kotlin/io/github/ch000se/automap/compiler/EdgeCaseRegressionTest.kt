package io.github.ch000se.automap.compiler

import com.tschuchort.compiletesting.SourceFile
import io.github.ch000se.automap.compiler.support.CompilationHelper
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertError
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertErrorContains
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertOk
import io.github.ch000se.automap.compiler.support.CompilationHelper.callMapper
import io.github.ch000se.automap.compiler.support.CompilationHelper.newSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EdgeCaseRegressionTest {

    @Test
    fun `one source can map to multiple targets and nested mapping picks requested target`() {
        val source = SourceFile.kotlin(
            "MultiTarget.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap

            data class Address(val street: String)

            @AutoMap(source = Address::class)
            data class AddressDto(val street: String)

            @AutoMap(source = Address::class)
            data class AddressView(val street: String)

            @AutoMap(target = OrderDto::class)
            data class Order(val address: Address)

            data class OrderDto(val address: AddressDto)
            """.trimIndent(),
        )
        val result = CompilationHelper.compile(source).also { it.assertOk() }
        val address = result.newSource("demo.Address", "Main")
        val order = result.newSource("demo.Order", address)
        val dto = result.callMapper("demo.OrderToOrderDtoMapperKt", "toOrderDto", order)
        assertEquals("OrderDto(address=AddressDto(street=Main))", dto.toString())
    }

    @Test
    fun `nullable source is not converted into non-null primitive target`() {
        val source = SourceFile.kotlin(
            "Nullable.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap

            @AutoMap(target = Target::class)
            data class Source(val count: Int?)

            data class Target(val count: Long)
            """.trimIndent(),
        )
        val result = CompilationHelper.compile(source)
        result.assertError()
        result.assertErrorContains("Cannot map field")
        result.assertErrorContains("count")
    }

    @Test
    fun `keyword property names are escaped in generated source`() {
        val source = SourceFile.kotlin(
            "Keyword.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap

            @AutoMap(target = KeywordDto::class)
            data class KeywordSource(val `class`: String)

            data class KeywordDto(val `class`: String)
            """.trimIndent(),
        )
        val result = CompilationHelper.compile(source).also { it.assertOk() }
        val keyword = result.newSource("demo.KeywordSource", "value")
        val dto = result.callMapper("demo.KeywordSourceToKeywordDtoMapperKt", "toKeywordDto", keyword)
        assertEquals("KeywordDto(class=value)", dto.toString())
    }
}
