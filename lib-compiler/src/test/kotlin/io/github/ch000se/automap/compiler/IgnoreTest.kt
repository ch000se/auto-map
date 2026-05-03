package io.github.ch000se.automap.compiler

import com.tschuchort.compiletesting.SourceFile
import io.github.ch000se.automap.compiler.support.CompilationHelper
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertOk
import io.github.ch000se.automap.compiler.support.CompilationHelper.callMapper
import io.github.ch000se.automap.compiler.support.CompilationHelper.generatedSource
import io.github.ch000se.automap.compiler.support.CompilationHelper.newSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class IgnoreTest {

    @Test
    fun `MapIgnore source prop is dropped and target default is used`() {
        val src = SourceFile.kotlin(
            "Report.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap
            import io.github.ch000se.automap.annotations.MapIgnore

            @AutoMap(target = ReportDto::class)
            data class Report(
                val id: Long,
                val title: String,
                @MapIgnore val internalNote: String,
            )

            data class ReportDto(val id: Long, val title: String, val internalNote: String = "default")
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(src).also { it.assertOk() }
        val generated = r.generatedSource("ReportToReportDtoMapper.kt")
        assertFalse(generated.contains("internalNote"), "Generated mapping must omit internalNote:\n$generated")

        val report = r.newSource("demo.Report", 1L, "Q1", "secret")
        val dto = r.callMapper("demo.ReportToReportDtoMapperKt", "toReportDto", report)
        assertEquals("ReportDto(id=1, title=Q1, internalNote=default)", dto.toString())
    }
}