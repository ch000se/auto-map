package io.github.ch000se.automap.compiler

import com.tschuchort.compiletesting.SourceFile
import io.github.ch000se.automap.compiler.support.CompilationHelper
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertError
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertErrorContains
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertOk
import io.github.ch000se.automap.compiler.support.CompilationHelper.generatedSource
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MapWithFnTest {

    @Test
    fun `converter by simple function name works`() {
        val src = SourceFile.kotlin(
            "SimpleConverter.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap
            import io.github.ch000se.automap.annotations.MapWithFn

            fun toLabel(value: Long): String = value.toString()

            @AutoMap(target = Target::class)
            data class Source(@MapWithFn("toLabel") val amount: Long)

            data class Target(val amount: String)
            """.trimIndent(),
        )

        val r = CompilationHelper.compile(src).also { it.assertOk() }

        assertTrue(r.generatedSource("SourceToTargetMapper.kt").contains("amount = toLabel(amount)"))
    }

    @Test
    fun `converter by fully qualified function name works`() {
        val src = SourceFile.kotlin(
            "FullyQualifiedConverter.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap
            import io.github.ch000se.automap.annotations.MapWithFn

            fun toLabel(value: Long): String = value.toString()

            @AutoMap(target = Target::class)
            data class Source(@MapWithFn("demo.toLabel") val amount: Long)

            data class Target(val amount: String)
            """.trimIndent(),
        )

        val r = CompilationHelper.compile(src).also { it.assertOk() }

        assertTrue(r.generatedSource("SourceToTargetMapper.kt").contains("amount = demo.toLabel(amount)"))
    }

    @Test
    fun `wrong converter parameter type fails`() {
        val src = SourceFile.kotlin(
            "WrongParameter.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap
            import io.github.ch000se.automap.annotations.MapWithFn

            fun toLabel(value: String): String = value

            @AutoMap(target = Target::class)
            data class Source(@MapWithFn("toLabel") val amount: Long)

            data class Target(val amount: String)
            """.trimIndent(),
        )

        val r = CompilationHelper.compile(src).also { it.assertError() }

        r.assertErrorContains("Invalid converter for field \"amount\"")
        r.assertErrorContains("Expected converter")
        r.assertErrorContains("(kotlin.Long) -> kotlin.String")
        r.assertErrorContains("fun toLabel(value: kotlin.String): kotlin.String")
    }

    @Test
    fun `wrong converter return type fails`() {
        val src = SourceFile.kotlin(
            "WrongReturn.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap
            import io.github.ch000se.automap.annotations.MapWithFn

            fun toLabel(value: Long): Long = value

            @AutoMap(target = Target::class)
            data class Source(@MapWithFn("toLabel") val amount: Long)

            data class Target(val amount: String)
            """.trimIndent(),
        )

        val r = CompilationHelper.compile(src).also { it.assertError() }

        r.assertErrorContains("Invalid converter for field \"amount\"")
        r.assertErrorContains("(kotlin.Long) -> kotlin.String")
        r.assertErrorContains("fun toLabel(value: kotlin.Long): kotlin.Long")
    }

    @Test
    fun `old string MapWith API still works`() {
        val src = SourceFile.kotlin(
            "LegacyMapWith.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap
            import io.github.ch000se.automap.annotations.MapWith

            fun toLabel(value: Long): String = value.toString()

            @AutoMap(target = Target::class)
            data class Source(@MapWith("toLabel") val amount: Long)

            data class Target(val amount: String)
            """.trimIndent(),
        )

        CompilationHelper.compile(src).also { it.assertOk() }
    }
}
