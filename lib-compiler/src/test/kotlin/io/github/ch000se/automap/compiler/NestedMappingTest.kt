package io.github.ch000se.automap.compiler

import com.tschuchort.compiletesting.SourceFile
import io.github.ch000se.automap.compiler.support.CompilationHelper
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertOk
import io.github.ch000se.automap.compiler.support.CompilationHelper.callMapper
import io.github.ch000se.automap.compiler.support.CompilationHelper.newSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NestedMappingTest {

    @Test
    fun `nested AutoMap type is auto-converted via toXxxDto`() {
        val src = SourceFile.kotlin(
            "Order.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap

            @AutoMap(target = AddressDto::class)
            data class Address(val street: String, val city: String)
            data class AddressDto(val street: String, val city: String)

            @AutoMap(target = OrderDto::class)
            data class Order(val id: Long, val address: Address)
            data class OrderDto(val id: Long, val address: AddressDto)
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(src).also { it.assertOk() }
        val addr = r.newSource("demo.Address", "Main", "Kyiv")
        val order = r.newSource("demo.Order", 11L, addr)
        val dto = r.callMapper("demo.OrderToOrderDtoMapperKt", "toOrderDto", order)
        assertEquals("OrderDto(id=11, address=AddressDto(street=Main, city=Kyiv))", dto.toString())
    }
}