package io.github.ch000se.automap.compiler

import io.github.ch000se.automap.compiler.support.CompilationHelper.assertContent
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertHasGeneratedFile
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertOk
import io.github.ch000se.automap.compiler.support.CompilationHelper.compile
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

class NestedDelegationTest {

    @Test
    fun `nested mapping injects delegate mapper via constructor`() {
        val result = compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package test
                import io.github.ch000se.automap.annotations.AutoMap

                @AutoMap(target = AddressDto::class)
                data class Address(val city: String, val zip: String)
                data class AddressDto(val city: String, val zip: String)

                @AutoMap(target = UserDto::class)
                data class User(val id: Long, val address: Address)
                data class UserDto(val id: Long, val address: AddressDto)
                """.trimIndent(),
            ),
        )

        result.assertOk()
        result.assertHasGeneratedFile("UserToUserDtoMapper.kt").assertContent(
            """
            package test
            public abstract class UserToUserDtoMapper(
                private val addressMapper: (Address) -> AddressDto,
            ) {
                public fun map(source: User): UserDto = UserDto(
                    id = source.id,
                    address = addressMapper(source.address),
                )
            }
            """,
        )
        result.assertHasGeneratedFile("UserToUserDtoMapperExt.kt").assertContent(
            """
            package test
            public fun User.toUserDto(addressMapper: (Address) -> AddressDto): UserDto = object : UserToUserDtoMapper(addressMapper) {}.map(this)
            """,
        )
    }

    @Test
    fun `nested inner mapping inside order-orderDto`() {
        val result = compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package test
                import io.github.ch000se.automap.annotations.AutoMap

                @AutoMap(target = ItemDto::class)
                data class Item(val name: String, val price: Double)
                data class ItemDto(val name: String, val price: Double)

                @AutoMap(target = OrderDto::class)
                data class Order(val orderId: Long, val item: Item)
                data class OrderDto(val orderId: Long, val item: ItemDto)
                """.trimIndent(),
            ),
        )

        result.assertOk()
        // Inner Item→ItemDto mapper: pure extension function
        result.assertHasGeneratedFile("ItemToItemDtoMapper.kt").assertContent(
            """
            package test
            public fun Item.toItemDto(): ItemDto = ItemDto(
                name = this.name,
                price = this.price,
            )
            """,
        )
        // Outer Order→OrderDto mapper: abstract class with constructor
        result.assertHasGeneratedFile("OrderToOrderDtoMapper.kt").assertContent(
            """
            package test
            public abstract class OrderToOrderDtoMapper(
                private val itemMapper: (Item) -> ItemDto,
            ) {
                public fun map(source: Order): OrderDto = OrderDto(
                    orderId = source.orderId,
                    item = itemMapper(source.item),
                )
            }
            """,
        )
        // Extension function in separate file
        result.assertHasGeneratedFile("OrderToOrderDtoMapperExt.kt").assertContent(
            """
            package test
            public fun Order.toOrderDto(itemMapper: (Item) -> ItemDto): OrderDto = object : OrderToOrderDtoMapper(itemMapper) {}.map(this)
            """,
        )
    }
}