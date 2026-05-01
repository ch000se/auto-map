package io.github.ch000se.automap.compiler

import io.github.ch000se.automap.compiler.support.CompilationHelper.assertError
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertErrorContains
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertHasGeneratedFile
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertOk
import io.github.ch000se.automap.compiler.support.CompilationHelper.compile
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

class ErrorMessagesTest {

    @Test
    fun `regular class as source is allowed`() {
        val result = compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package test
                import io.github.ch000se.automap.annotations.AutoMap

                @AutoMap(target = UserDto::class)
                class User(val id: Long)   // not data class, but still allowed

                data class UserDto(val id: Long)
                """.trimIndent(),
            ),
        )

        result.assertOk()
        result.assertHasGeneratedFile("UserToUserDtoMapper.kt")
    }

    @Test
    fun `regular class as target with primary constructor is allowed`() {
        val result = compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package test
                import io.github.ch000se.automap.annotations.AutoMap

                @AutoMap(target = UserDto::class)
                data class User(val id: Long)

                class UserDto(val id: Long)  // not data class, but has primary constructor
                """.trimIndent(),
            ),
        )

        result.assertOk()
        result.assertHasGeneratedFile("UserToUserDtoMapper.kt")
    }

    @Test
    fun `error when target has no primary constructor`() {
        val result = compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package test
                import io.github.ch000se.automap.annotations.AutoMap

                @AutoMap(target = UserDto::class)
                data class User(val id: Long)

                class UserDto {
                    val id: Long = 0
                }
                """.trimIndent(),
            ),
        )

        result.assertError()
        result.assertErrorContains("primary constructor")
    }

    @Test
    fun `error for duplicate Field target entries`() {
        val result = compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package test
                import io.github.ch000se.automap.annotations.AutoMap

                @AutoMap(
                    target = UserDto::class,
                    fields = [
                        AutoMap.Field(target = "name", source = "firstName"),
                        AutoMap.Field(target = "name", constant = "\"Bob\""),
                    ],
                )
                data class User(val firstName: String)

                data class UserDto(val name: String)
                """.trimIndent(),
            ),
        )

        result.assertError()
        result.assertErrorContains("duplicate")
    }

    @Test
    fun `error when multiple strategies set in one Field`() {
        val result = compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package test
                import io.github.ch000se.automap.annotations.AutoMap

                @AutoMap(
                    target = UserDto::class,
                    fields = [AutoMap.Field(target = "name", source = "firstName", constant = "\"Bob\"")],
                )
                data class User(val firstName: String)

                data class UserDto(val name: String)
                """.trimIndent(),
            ),
        )

        result.assertError()
        result.assertErrorContains("only one of")
    }

    @Test
    fun `error when Field source does not exist in source class`() {
        val result = compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package test
                import io.github.ch000se.automap.annotations.AutoMap

                @AutoMap(
                    target = UserDto::class,
                    fields = [AutoMap.Field(target = "name", source = "nonExistent")],
                )
                data class User(val id: Long)

                data class UserDto(val name: String)
                """.trimIndent(),
            ),
        )

        result.assertError()
        result.assertErrorContains("not found")
    }

    @Test
    fun `auto-custom generated when field absent and no default`() {
        val result = compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package test
                import io.github.ch000se.automap.annotations.AutoMap

                @AutoMap(target = Dto::class)
                data class Entity(val id: Long)

                data class Dto(val id: Long, val computed: String)
                """.trimIndent(),
            ),
        )

        // The KSP processor itself does NOT log an error; it generates an abstract mapper class
        // plus a broken extension file that tries to instantiate the abstract class directly.
        // Kotlin then reports a compile error because abstract classes cannot be instantiated.
        result.assertError()
    }
}