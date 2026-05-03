package io.github.ch000se.automap.compiler

import com.tschuchort.compiletesting.SourceFile
import io.github.ch000se.automap.compiler.support.CompilationHelper
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertOk
import io.github.ch000se.automap.compiler.support.CompilationHelper.callMapper
import io.github.ch000se.automap.compiler.support.CompilationHelper.newSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PrimitiveConversionTest {

    @Test
    fun `widen primitives and Any to String`() {
        val src = SourceFile.kotlin(
            "Metrics.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap

            data class Custom(val raw: Long) { override fun toString() = "C:" + raw }

            @AutoMap(target = MetricsDto::class)
            data class Metrics(
                val count: Int,           // Int -> Long
                val ratio: Float,         // Float -> Double
                val score: Int,           // Int -> String
                val id: Custom,           // Any -> String
            )

            data class MetricsDto(
                val count: Long,
                val ratio: Double,
                val score: String,
                val id: String,
            )
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(src).also { it.assertOk() }
        val custom = r.newSource("demo.Custom", 99L)
        val metrics = r.newSource("demo.Metrics", 5, 0.5f, 42, custom)
        val dto = r.callMapper("demo.MetricsToMetricsDtoMapperKt", "toMetricsDto", metrics)
        assertEquals("MetricsDto(count=5, ratio=0.5, score=42, id=C:99)", dto.toString())
    }
}