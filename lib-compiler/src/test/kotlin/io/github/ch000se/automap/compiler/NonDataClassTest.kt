package io.github.ch000se.automap.compiler

import io.github.ch000se.automap.compiler.support.CompilationHelper.assertContent
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertHasGeneratedFile
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertOk
import io.github.ch000se.automap.compiler.support.CompilationHelper.compile
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

class NonDataClassTest {

    @Test
    fun `open class as source maps correctly`() {
        val result = compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package test
                import io.github.ch000se.automap.annotations.AutoMap

                @AutoMap(target = UserDto::class)
                open class User(val id: Long, val name: String)

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
    fun `regular class as target maps correctly`() {
        val result = compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package test
                import io.github.ch000se.automap.annotations.AutoMap

                @AutoMap(target = UserDto::class)
                data class User(val id: Long, val name: String)

                class UserDto(val id: Long, val name: String)
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
    fun `target with private constructor and companion invoke is used as factory`() {
        val result = compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package test
                import io.github.ch000se.automap.annotations.AutoMap

                @AutoMap(target = UserDto::class)
                data class User(val id: Long, val name: String)

                // Private primary constructor — builder pattern via companion invoke
                class UserDto private constructor(val id: Long, val name: String) {
                    companion object {
                        operator fun invoke(id: Long, name: String) = UserDto(id, name)
                    }
                }
                """.trimIndent(),
            ),
        )

        result.assertOk()
        // Generated code should use UserDto(id = ..., name = ...) which resolves to companion invoke
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

}