package io.github.ch000se.automap.compiler

import com.tschuchort.compiletesting.SourceFile
import io.github.ch000se.automap.compiler.support.CompilationHelper
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertError
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertErrorContains
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertOk
import io.github.ch000se.automap.compiler.support.CompilationHelper.callMapper
import io.github.ch000se.automap.compiler.support.CompilationHelper.generatedSource
import io.github.ch000se.automap.compiler.support.CompilationHelper.newSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PrimitiveConversionTest {

    @Test
    fun `widen primitives and whitelisted primitives to String`() {
        val src = SourceFile.kotlin(
            "Metrics.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap

            @AutoMap(target = MetricsDto::class)
            data class Metrics(
                val count: Int,           // Int -> Long
                val ratio: Float,         // Float -> Double
                val score: Int,           // Int -> String
                val enabled: Boolean,     // Boolean -> String
            )

            data class MetricsDto(
                val count: Long,
                val ratio: Double,
                val score: String,
                val enabled: String,
            )
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(src).also { it.assertOk() }
        val metrics = r.newSource("demo.Metrics", 5, 0.5f, 42, true)
        val dto = r.callMapper("demo.MetricsToMetricsDtoMapperKt", "toMetricsDto", metrics)
        assertEquals("MetricsDto(count=5, ratio=0.5, score=42, enabled=true)", dto.toString())
    }

    @Test
    fun `nullable primitive widening maps to nullable target`() {
        val src = SourceFile.kotlin(
            "NullablePrimitive.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap

            @AutoMap(target = Target::class)
            data class Source(val count: Int?)

            data class Target(val count: Long?)
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(src).also { it.assertOk() }

        assertTrue(r.generatedSource("SourceToTargetMapper.kt").contains("count = count?.toLong()"))
    }

    @Test
    fun `arbitrary object to String fails without converter`() {
        val src = SourceFile.kotlin(
            "ObjectString.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap

            data class Custom(val raw: Long)

            @AutoMap(target = Target::class)
            data class Source(val id: Custom)

            data class Target(val id: String)
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(src).also { it.assertError() }

        r.assertErrorContains("Cannot map field \"id\"")
    }
}
