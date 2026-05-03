package io.github.ch000se.automap.compiler

import com.tschuchort.compiletesting.SourceFile
import io.github.ch000se.automap.compiler.support.CompilationHelper
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertOk
import io.github.ch000se.automap.compiler.support.CompilationHelper.callMapper
import io.github.ch000se.automap.compiler.support.CompilationHelper.generatedSource
import io.github.ch000se.automap.compiler.support.CompilationHelper.newSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SourceSidedMappingTest {

    @Test
    fun `source-sided AutoMap generates SourceToTarget`() {
        val src = SourceFile.kotlin(
            "User.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap

            data class User(val id: Long, val name: String)

            @AutoMap(source = User::class)
            data class UserDto(val id: Long, val name: String)
            """.trimIndent(),
        )
        val result = CompilationHelper.compile(src).also { it.assertOk() }
        // File is named after Source -> Target, regardless of where the annotation lives.
        val generated = result.generatedSource("UserToUserDtoMapper.kt")
        assertTrue(generated.contains("fun User.toUserDto"), "Expected User.toUserDto in:\n$generated")

        val user = result.newSource("demo.User", 5L, "Bob")
        val dto = result.callMapper("demo.UserToUserDtoMapperKt", "toUserDto", user)
        assertEquals("UserDto(id=5, name=Bob)", dto.toString())
    }
}
