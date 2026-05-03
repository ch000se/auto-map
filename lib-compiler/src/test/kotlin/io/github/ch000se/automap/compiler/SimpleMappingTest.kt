package io.github.ch000se.automap.compiler

import com.tschuchort.compiletesting.SourceFile
import io.github.ch000se.automap.compiler.support.CompilationHelper
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertOk
import io.github.ch000se.automap.compiler.support.CompilationHelper.callMapper
import io.github.ch000se.automap.compiler.support.CompilationHelper.newSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SimpleMappingTest {

    @Test
    fun `direct mapping by name and type`() {
        val src = SourceFile.kotlin(
            "User.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap

            @AutoMap(target = UserDto::class)
            data class User(val id: Long, val name: String)

            data class UserDto(val id: Long, val name: String)
            """.trimIndent(),
        )
        val result = CompilationHelper.compile(src).also { it.assertOk() }
        val user = result.newSource("demo.User", 7L, "Alice")
        val dto = result.callMapper("demo.UserToUserDtoMapperKt", "toUserDto", user)
        assertEquals("UserDto(id=7, name=Alice)", dto.toString())
    }
}