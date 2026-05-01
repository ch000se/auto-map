package io.github.ch000se.automap.compiler

import io.github.ch000se.automap.compiler.support.CompilationHelper.assertContent
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertHasGeneratedFile
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertOk
import io.github.ch000se.automap.compiler.support.CompilationHelper.compile
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

class RenamedFieldTest {

    @Test
    fun `renames one field via source`() {
        val result = compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package test
                import io.github.ch000se.automap.annotations.AutoMap

                @AutoMap(
                    target = UserDto::class,
                    fields = [AutoMap.Field(target = "fullName", source = "name")],
                )
                data class User(val id: Long, val name: String)

                data class UserDto(val id: Long, val fullName: String)
                """.trimIndent(),
            ),
        )

        result.assertOk()
        result.assertHasGeneratedFile("UserToUserDtoMapper.kt").assertContent(
            """
            package test
            public fun User.toUserDto(): UserDto = UserDto(
                id = this.id,
                fullName = this.name,
            )
            """,
        )
    }

    @Test
    fun `renames multiple fields`() {
        val result = compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package test
                import io.github.ch000se.automap.annotations.AutoMap

                @AutoMap(
                    target = PersonDto::class,
                    fields = [
                        AutoMap.Field(target = "firstName", source = "first"),
                        AutoMap.Field(target = "lastName", source = "last"),
                    ],
                )
                data class Person(val first: String, val last: String)

                data class PersonDto(val firstName: String, val lastName: String)
                """.trimIndent(),
            ),
        )

        result.assertOk()
        result.assertHasGeneratedFile("PersonToPersonDtoMapper.kt").assertContent(
            """
            package test
            public fun Person.toPersonDto(): PersonDto = PersonDto(
                firstName = this.first,
                lastName = this.last,
            )
            """,
        )
    }
}