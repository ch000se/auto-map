package io.github.ch000se.automap.compiler

import io.github.ch000se.automap.compiler.support.CompilationHelper.assertContent
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertHasGeneratedFile
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertOk
import io.github.ch000se.automap.compiler.support.CompilationHelper.compile
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

class ConstantFieldTest {

    @Test
    fun `string constant`() {
        val result = compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package test
                import io.github.ch000se.automap.annotations.AutoMap

                @AutoMap(
                    target = EventDto::class,
                    fields = [AutoMap.Field(target = "source", constant = "\"api\"")],
                )
                data class Event(val id: Long)

                data class EventDto(val id: Long, val source: String)
                """.trimIndent(),
            ),
        )

        result.assertOk()
        result.assertHasGeneratedFile("EventToEventDtoMapper.kt").assertContent(
            """
            package test
            public fun Event.toEventDto(): EventDto = EventDto(
                id = this.id,
                source = "api",
            )
            """,
        )
    }

    @Test
    fun `integer constant`() {
        val result = compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package test
                import io.github.ch000se.automap.annotations.AutoMap

                @AutoMap(
                    target = VersionDto::class,
                    fields = [AutoMap.Field(target = "version", constant = "1")],
                )
                data class VersionEntity(val name: String)

                data class VersionDto(val name: String, val version: Int)
                """.trimIndent(),
            ),
        )

        result.assertOk()
        result.assertHasGeneratedFile("VersionEntityToVersionDtoMapper.kt").assertContent(
            """
            package test
            public fun VersionEntity.toVersionDto(): VersionDto = VersionDto(
                name = this.name,
                version = 1,
            )
            """,
        )
    }

    @Test
    fun `boolean constant`() {
        val result = compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package test
                import io.github.ch000se.automap.annotations.AutoMap

                @AutoMap(
                    target = ItemDto::class,
                    fields = [AutoMap.Field(target = "active", constant = "true")],
                )
                data class Item(val id: Long)

                data class ItemDto(val id: Long, val active: Boolean)
                """.trimIndent(),
            ),
        )

        result.assertOk()
        result.assertHasGeneratedFile("ItemToItemDtoMapper.kt").assertContent(
            """
            package test
            public fun Item.toItemDto(): ItemDto = ItemDto(
                id = this.id,
                active = true,
            )
            """,
        )
    }

    @Test
    fun `enum constant`() {
        val result = compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package test
                import io.github.ch000se.automap.annotations.AutoMap

                enum class Status { ACTIVE, INACTIVE }

                @AutoMap(
                    target = UserDto::class,
                    fields = [AutoMap.Field(target = "status", constant = "Status.ACTIVE")],
                )
                data class User(val id: Long)

                data class UserDto(val id: Long, val status: Status)
                """.trimIndent(),
            ),
        )

        result.assertOk()
        result.assertHasGeneratedFile("UserToUserDtoMapper.kt").assertContent(
            """
            package test
            public fun User.toUserDto(): UserDto = UserDto(
                id = this.id,
                status = Status.ACTIVE,
            )
            """,
        )
    }
}