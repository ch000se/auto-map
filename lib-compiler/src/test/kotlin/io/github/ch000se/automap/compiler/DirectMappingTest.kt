package io.github.ch000se.automap.compiler

import io.github.ch000se.automap.compiler.support.CompilationHelper.assertContent
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertHasGeneratedFile
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertOk
import io.github.ch000se.automap.compiler.support.CompilationHelper.compile
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

class DirectMappingTest {

    @Test
    fun `generates extension function for identical fields`() {
        val result = compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package test
                import io.github.ch000se.automap.annotations.AutoMap

                @AutoMap(target = UserDto::class)
                data class User(val id: Long, val name: String)

                data class UserDto(val id: Long, val name: String)
                """.trimIndent(),
            ),
        )

        result.assertOk()
        result.assertHasGeneratedFile("UserToUserDtoMapper.kt").assertContent(
            """
            package test
            public fun User.toUserDto(): UserDto = UserDto(
                id = this.id,
                name = this.name,
            )
            """,
        )
    }

    @Test
    fun `generates extension function when source has extra fields`() {
        val result = compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package test
                import io.github.ch000se.automap.annotations.AutoMap

                @AutoMap(target = UserDto::class)
                data class User(val id: Long, val name: String, val password: String)

                data class UserDto(val id: Long, val name: String)
                """.trimIndent(),
            ),
        )

        result.assertOk()
        result.assertHasGeneratedFile("UserToUserDtoMapper.kt").assertContent(
            """
            package test
            public fun User.toUserDto(): UserDto = UserDto(
                id = this.id,
                name = this.name,
            )
            """,
        )
    }

    @Test
    fun `single field mapped directly`() {
        val result = compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package test
                import io.github.ch000se.automap.annotations.AutoMap

                @AutoMap(target = IdDto::class)
                data class Entity(val id: Long)

                data class IdDto(val id: Long)
                """.trimIndent(),
            ),
        )

        result.assertOk()
        result.assertHasGeneratedFile("EntityToIdDtoMapper.kt").assertContent(
            """
            package test
            public fun Entity.toIdDto(): IdDto = IdDto(
                id = this.id,
            )
            """,
        )
    }
}