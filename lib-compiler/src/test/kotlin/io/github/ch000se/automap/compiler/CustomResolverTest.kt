package io.github.ch000se.automap.compiler

import io.github.ch000se.automap.compiler.support.CompilationHelper.assertContent
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertError
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertHasGeneratedFile
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertOk
import io.github.ch000se.automap.compiler.support.CompilationHelper.compile
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

class CustomResolverTest {

    @Test
    fun `explicit custom=true generates abstract class with resolver method and convenience ext`() {
        val result = compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package test
                import io.github.ch000se.automap.annotations.AutoMap

                @AutoMap(
                    target = UserDto::class,
                    fields = [AutoMap.Field(target = "emailLower", custom = true)],
                )
                data class User(val id: Long, val name: String)

                data class UserDto(val id: Long, val name: String, val emailLower: String)
                """.trimIndent(),
            ),
        )

        result.assertOk()
        result.assertHasGeneratedFile("UserToUserDtoMapper.kt").assertContent(
            """
            package test
            import kotlin.String
            public abstract class UserToUserDtoMapper {
                public abstract fun resolveEmailLower(source: User): String
                public fun map(source: User): UserDto = UserDto(
                    id = source.id,
                    name = source.name,
                    emailLower = resolveEmailLower(source),
                )
            }
            """,
        )
        result.assertHasGeneratedFile("UserToUserDtoMapperExt.kt").assertContent(
            """
            package test
            import kotlin.String
            public fun User.toUserDto(resolveEmailLower: (User) -> String): UserDto = object : UserToUserDtoMapper() {
                override fun resolveEmailLower(source: User) = resolveEmailLower(source)
            }.map(this)
            """,
        )
    }

    @Test
    fun `auto-custom when field absent from source generates broken ext file`() {
        val result = compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package test
                import io.github.ch000se.automap.annotations.AutoMap

                @AutoMap(target = ProfileDto::class)
                data class Profile(val id: Long)

                data class ProfileDto(val id: Long, val avatarUrl: String)
                """.trimIndent(),
            ),
        )

        // The KSP processor generates an abstract mapper class and a broken extension file that
        // tries to instantiate the abstract class directly. Kotlin reports a compile error.
        result.assertError()
        result.assertHasGeneratedFile("ProfileToProfileDtoMapper.kt").assertContent(
            """
            package test
            import kotlin.String
            public abstract class ProfileToProfileDtoMapper {
                public abstract fun resolveAvatarUrl(source: Profile): String
                public fun map(source: Profile): ProfileDto = ProfileDto(
                    id = source.id,
                    avatarUrl = resolveAvatarUrl(source),
                )
            }
            """,
        )
    }

    @Test
    fun `multiple custom fields produce multiple abstract methods and multi-lambda ext`() {
        val result = compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package test
                import io.github.ch000se.automap.annotations.AutoMap

                @AutoMap(
                    target = ReportDto::class,
                    fields = [
                        AutoMap.Field(target = "title", custom = true),
                        AutoMap.Field(target = "summary", custom = true),
                    ],
                )
                data class Report(val id: Long)

                data class ReportDto(val id: Long, val title: String, val summary: String)
                """.trimIndent(),
            ),
        )

        result.assertOk()
        result.assertHasGeneratedFile("ReportToReportDtoMapper.kt").assertContent(
            """
            package test
            import kotlin.String
            public abstract class ReportToReportDtoMapper {
                public abstract fun resolveTitle(source: Report): String
                public abstract fun resolveSummary(source: Report): String
                public fun map(source: Report): ReportDto = ReportDto(
                    id = source.id,
                    title = resolveTitle(source),
                    summary = resolveSummary(source),
                )
            }
            """,
        )
        result.assertHasGeneratedFile("ReportToReportDtoMapperExt.kt").assertContent(
            """
            package test
            import kotlin.String
            public fun Report.toReportDto(resolveTitle: (Report) -> String, resolveSummary: (Report) -> String): ReportDto = object : ReportToReportDtoMapper() {
                override fun resolveTitle(source: Report) = resolveTitle(source)
                override fun resolveSummary(source: Report) = resolveSummary(source)
            }.map(this)
            """,
        )
    }
}