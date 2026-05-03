package io.github.ch000se.automap.compiler

import com.tschuchort.compiletesting.SourceFile
import io.github.ch000se.automap.compiler.support.CompilationHelper
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertError
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertErrorContains
import org.junit.jupiter.api.Test

class ErrorMessagesTest {

    @Test
    fun `mismatched target type produces a Cannot map field error`() {
        val src = SourceFile.kotlin(
            "Bad.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap

            data class Other(val raw: Int)

            @AutoMap(target = TargetDto::class)
            data class Source(val id: Other)

            data class TargetDto(val id: Other)
            data class WrongTarget(val id: Long)
            """.trimIndent(),
        )
        // The above mapping is actually fine (same Other type).
        // Force a real type mismatch:
        val bad = SourceFile.kotlin(
            "Bad2.kt",
            """
            package demo2
            import io.github.ch000se.automap.annotations.AutoMap

            data class Foo(val data: Int)

            @AutoMap(target = TargetDto::class)
            data class Source(val id: Foo)

            data class TargetDto(val id: Long)
            """.trimIndent(),
        )
        val result = CompilationHelper.compile(bad)
        result.assertError()
        result.assertErrorContains("Cannot map field")
    }

    @Test
    fun `AutoMap with both target and source set errors`() {
        val src = SourceFile.kotlin(
            "Both.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap

            data class Foo(val id: Long)

            @AutoMap(target = Bar::class, source = Foo::class)
            data class Bar(val id: Long)
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(src)
        r.assertError()
        r.assertErrorContains("exactly one of 'target' or 'source'")
    }

    @Test
    fun `AutoMap with neither target nor source errors`() {
        val src = SourceFile.kotlin(
            "Neither.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap

            @AutoMap
            data class Bar(val id: Long)
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(src)
        r.assertError()
        r.assertErrorContains("exactly one of 'target' or 'source'")
    }
}