package io.github.ch000se.automap.compiler

import io.github.ch000se.automap.compiler.support.CompilationHelper.assertContent
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertError
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertErrorContains
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertHasGeneratedFile
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertOk
import io.github.ch000se.automap.compiler.support.CompilationHelper.compile
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

class IgnoreFieldTest {

    @Test
    fun `ignored field with default value is omitted from constructor call`() {
        val result = compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package test
                import io.github.ch000se.automap.annotations.AutoMap

                @AutoMap(
                    target = UserDto::class,
                    fields = [AutoMap.Field(target = "role", ignore = true)],
                )
                data class User(val id: Long, val name: String)

                data class UserDto(val id: Long, val name: String, val role: String = "user")
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
    fun `auto-ignored field with default value (no Field annotation needed)`() {
        val result = compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package test
                import io.github.ch000se.automap.annotations.AutoMap

                @AutoMap(target = UserDto::class)
                data class User(val id: Long, val name: String)

                data class UserDto(val id: Long, val name: String, val role: String = "user")
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
    fun `error when ignore=true on field without default`() {
        val result = compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package test
                import io.github.ch000se.automap.annotations.AutoMap

                @AutoMap(
                    target = UserDto::class,
                    fields = [AutoMap.Field(target = "name", ignore = true)],
                )
                data class User(val id: Long, val name: String)

                data class UserDto(val id: Long, val name: String)
                """.trimIndent(),
            ),
        )

        result.assertError()
        result.assertErrorContains("ignore=true")
        result.assertErrorContains("default value")
    }
}