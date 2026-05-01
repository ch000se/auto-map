package io.github.ch000se.automap.compiler

import io.github.ch000se.automap.compiler.support.CompilationHelper.assertContent
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertHasGeneratedFile
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertOk
import io.github.ch000se.automap.compiler.support.CompilationHelper.compile
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

class TypeConversionTest {

    @Test
    fun `Int field auto-converts to Long`() {
        val result = compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package test
                import io.github.ch000se.automap.annotations.AutoMap

                @AutoMap(target = UserDto::class)
                data class User(val id: Int, val name: String)

                data class UserDto(val id: Long, val name: String)
                """.trimIndent(),
            ),
        )

        result.assertOk()
        result.assertHasGeneratedFile("UserToUserDtoMapper.kt").assertContent(
            """
            package test
            public fun User.toUserDto(): UserDto = UserDto(
                id = this.id.toLong(),
                name = this.name,
            )
            """,
        )
    }

    @Test
    fun `Int field auto-converts to String`() {
        val result = compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package test
                import io.github.ch000se.automap.annotations.AutoMap

                @AutoMap(target = ProductDto::class)
                data class Product(val count: Int, val label: String)

                data class ProductDto(val count: String, val label: String)
                """.trimIndent(),
            ),
        )

        result.assertOk()
        result.assertHasGeneratedFile("ProductToProductDtoMapper.kt").assertContent(
            """
            package test
            public fun Product.toProductDto(): ProductDto = ProductDto(
                count = this.count.toString(),
                label = this.label,
            )
            """,
        )
    }

    @Test
    fun `explicit convert=toInt in Field annotation`() {
        val result = compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package test
                import io.github.ch000se.automap.annotations.AutoMap

                @AutoMap(
                    target = StatsDto::class,
                    fields = [AutoMap.Field(target = "score", convert = "toInt")],
                )
                data class Stats(val score: Long, val label: String)

                data class StatsDto(val score: Int, val label: String)
                """.trimIndent(),
            ),
        )

        result.assertOk()
        result.assertHasGeneratedFile("StatsToStatsDtoMapper.kt").assertContent(
            """
            package test
            public fun Stats.toStatsDto(): StatsDto = StatsDto(
                score = this.score.toInt(),
                label = this.label,
            )
            """,
        )
    }

    @Test
    fun `explicit convert with source rename`() {
        val result = compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package test
                import io.github.ch000se.automap.annotations.AutoMap

                @AutoMap(
                    target = ItemDto::class,
                    fields = [AutoMap.Field(target = "quantity", source = "count", convert = "toLong")],
                )
                data class Item(val count: Int, val name: String)

                data class ItemDto(val quantity: Long, val name: String)
                """.trimIndent(),
            ),
        )

        result.assertOk()
        result.assertHasGeneratedFile("ItemToItemDtoMapper.kt").assertContent(
            """
            package test
            public fun Item.toItemDto(): ItemDto = ItemDto(
                quantity = this.count.toLong(),
                name = this.name,
            )
            """,
        )
    }

    @Test
    fun `arbitrary type auto-converts to String via toString()`() {
        val result = compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package test
                import io.github.ch000se.automap.annotations.AutoMap

                // CustomId is not in the TYPE_CONVERSIONS table — should still auto-toString
                data class CustomId(val value: Long)

                @AutoMap(target = EventDto::class)
                data class Event(val id: CustomId, val name: String)

                data class EventDto(val id: String, val name: String)
                """.trimIndent(),
            ),
        )

        result.assertOk()
        result.assertHasGeneratedFile("EventToEventDtoMapper.kt").assertContent(
            """
            package test
            public fun Event.toEventDto(): EventDto = EventDto(
                id = this.id.toString(),
                name = this.name,
            )
            """,
        )
    }

    @Test
    fun `Long auto-converts to String via toString()`() {
        val result = compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package test
                import io.github.ch000se.automap.annotations.AutoMap

                @AutoMap(target = UserDto::class)
                data class User(val id: Long, val name: String)

                data class UserDto(val id: String, val name: String)
                """.trimIndent(),
            ),
        )

        result.assertOk()
        result.assertHasGeneratedFile("UserToUserDtoMapper.kt").assertContent(
            """
            package test
            public fun User.toUserDto(): UserDto = UserDto(
                id = this.id.toString(),
                name = this.name,
            )
            """,
        )
    }
}