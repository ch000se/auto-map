package io.github.ch000se.automap.compiler

import io.github.ch000se.automap.compiler.support.CompilationHelper.assertContent
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertHasGeneratedFile
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertOk
import io.github.ch000se.automap.compiler.support.CompilationHelper.compile
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

class CollectionConvertTest {

    @Test
    fun `List of Int to List of String auto-converts`() {
        val result = compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package test
                import io.github.ch000se.automap.annotations.AutoMap

                @AutoMap(target = ReportDto::class)
                data class Report(val id: Long, val nums: List<Int>)

                data class ReportDto(val id: Long, val nums: List<String>)
                """.trimIndent(),
            ),
        )

        result.assertOk()
        result.assertHasGeneratedFile("ReportToReportDtoMapper.kt").assertContent(
            """
            package test
            public fun Report.toReportDto(): ReportDto = ReportDto(
                id = this.id,
                nums = this.nums.map { it.toString() },
            )
            """,
        )
    }

    @Test
    fun `Set of Int to Set of String auto-converts`() {
        val result = compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package test
                import io.github.ch000se.automap.annotations.AutoMap

                @AutoMap(target = TagsDto::class)
                data class Tags(val codes: Set<Int>)

                data class TagsDto(val codes: Set<String>)
                """.trimIndent(),
            ),
        )

        result.assertOk()
        result.assertHasGeneratedFile("TagsToTagsDtoMapper.kt").assertContent(
            """
            package test
            public fun Tags.toTagsDto(): TagsDto = TagsDto(
                codes = this.codes.mapTo(linkedSetOf()) { it.toString() },
            )
            """,
        )
    }

    @Test
    fun `List of Long to List of Int auto-converts`() {
        val result = compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package test
                import io.github.ch000se.automap.annotations.AutoMap

                @AutoMap(target = SummaryDto::class)
                data class Summary(val values: List<Long>)

                data class SummaryDto(val values: List<Int>)
                """.trimIndent(),
            ),
        )

        result.assertOk()
        result.assertHasGeneratedFile("SummaryToSummaryDtoMapper.kt").assertContent(
            """
            package test
            public fun Summary.toSummaryDto(): SummaryDto = SummaryDto(
                values = this.values.map { it.toInt() },
            )
            """,
        )
    }
}