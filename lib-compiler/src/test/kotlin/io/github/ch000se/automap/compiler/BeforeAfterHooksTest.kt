package io.github.ch000se.automap.compiler

import io.github.ch000se.automap.compiler.support.CompilationHelper.assertContent
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertHasGeneratedFile
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertOk
import io.github.ch000se.automap.compiler.support.CompilationHelper.compile
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

class BeforeAfterHooksTest {

    @Test
    fun `beforeMap hook adds parameter and wraps source`() {
        val result = compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package test
                import io.github.ch000se.automap.annotations.AutoMap

                @AutoMap(target = UserDto::class, beforeMap = true)
                data class User(val id: Long, val name: String)

                data class UserDto(val id: Long, val name: String)
                """.trimIndent(),
            ),
        )

        result.assertOk()
        result.assertHasGeneratedFile("UserToUserDtoMapper.kt").assertContent(
            """
            package test
            public class UserToUserDtoMapper(
                private val beforeMap: (User) -> User,
            ) {
                public fun map(source: User): UserDto {
                    val s = beforeMap(source)
                    return UserDto(
                        id = s.id,
                        name = s.name,
                    )
                }
            }
            """,
        )
        result.assertHasGeneratedFile("UserToUserDtoMapperExt.kt").assertContent(
            """
            package test
            public fun User.toUserDto(beforeMap: (User) -> User): UserDto = UserToUserDtoMapper(beforeMap).map(this)
            """,
        )
    }

    @Test
    fun `afterMap hook adds parameter and wraps result`() {
        val result = compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package test
                import io.github.ch000se.automap.annotations.AutoMap

                @AutoMap(target = UserDto::class, afterMap = true)
                data class User(val id: Long, val name: String)

                data class UserDto(val id: Long, val name: String)
                """.trimIndent(),
            ),
        )

        result.assertOk()
        result.assertHasGeneratedFile("UserToUserDtoMapper.kt").assertContent(
            """
            package test
            public class UserToUserDtoMapper(
                private val afterMap: (UserDto) -> UserDto,
            ) {
                public fun map(source: User): UserDto = afterMap(UserDto(
                    id = source.id,
                    name = source.name,
                ))
            }
            """,
        )
        result.assertHasGeneratedFile("UserToUserDtoMapperExt.kt").assertContent(
            """
            package test
            public fun User.toUserDto(afterMap: (UserDto) -> UserDto): UserDto = UserToUserDtoMapper(afterMap).map(this)
            """,
        )
    }

    @Test
    fun `both hooks combined`() {
        val result = compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package test
                import io.github.ch000se.automap.annotations.AutoMap

                @AutoMap(target = UserDto::class, beforeMap = true, afterMap = true)
                data class User(val id: Long, val name: String)

                data class UserDto(val id: Long, val name: String)
                """.trimIndent(),
            ),
        )

        result.assertOk()
        result.assertHasGeneratedFile("UserToUserDtoMapper.kt").assertContent(
            """
            package test
            public class UserToUserDtoMapper(
                private val beforeMap: (User) -> User,
                private val afterMap: (UserDto) -> UserDto,
            ) {
                public fun map(source: User): UserDto {
                    val s = beforeMap(source)
                    return afterMap(UserDto(
                        id = s.id,
                        name = s.name,
                    ))
                }
            }
            """,
        )
        result.assertHasGeneratedFile("UserToUserDtoMapperExt.kt").assertContent(
            """
            package test
            public fun User.toUserDto(beforeMap: (User) -> User, afterMap: (UserDto) -> UserDto): UserDto = UserToUserDtoMapper(beforeMap, afterMap).map(this)
            """,
        )
    }
}