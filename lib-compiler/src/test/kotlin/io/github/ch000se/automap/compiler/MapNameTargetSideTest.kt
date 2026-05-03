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

class MapNameTargetSideTest {

    @Test
    fun `source-side MapName maps source property to target field`() {
        val src = SourceFile.kotlin(
            "SourceSide.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap
            import io.github.ch000se.automap.annotations.MapName

            @AutoMap(target = BodyPartDto::class)
            data class BodyPart(
                val name: String,
                @MapName("active") val isActive: Boolean,
            )

            data class BodyPartDto(
                val name: String = "",
                val active: Boolean = false,
            )
            """.trimIndent(),
        )

        val r = CompilationHelper.compile(src).also { it.assertOk() }
        val bodyPart = r.newSource("demo.BodyPart", "Arm", true)
        val dto = r.callMapper("demo.BodyPartToBodyPartDtoMapperKt", "toBodyPartDto", bodyPart)

        assertEquals("BodyPartDto(name=Arm, active=true)", dto.toString())
    }

    @Test
    fun `target-side MapName maps target field from source property`() {
        val src = SourceFile.kotlin(
            "TargetSide.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap
            import io.github.ch000se.automap.annotations.MapName

            data class BodyPart(
                val name: String,
                val isActive: Boolean,
            )

            @AutoMap(source = BodyPart::class)
            data class BodyPartDto(
                val name: String = "",
                @MapName("isActive") val active: Boolean = false,
            )
            """.trimIndent(),
        )

        val r = CompilationHelper.compile(src).also { it.assertOk() }
        val bodyPart = r.newSource("demo.BodyPart", "Leg", true)
        val dto = r.callMapper("demo.BodyPartToBodyPartDtoMapperKt", "toBodyPartDto", bodyPart)

        assertEquals("BodyPartDto(name=Leg, active=true)", dto.toString())
    }

    @Test
    fun `target-side MapName has priority over same-name source property`() {
        val src = SourceFile.kotlin(
            "TargetSidePriority.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap
            import io.github.ch000se.automap.annotations.MapName

            data class BodyPart(
                val name: String,
                val active: Boolean,
                val isActive: Boolean,
            )

            @AutoMap(source = BodyPart::class)
            data class BodyPartDto(
                val name: String = "",
                @MapName("isActive") val active: Boolean = false,
            )
            """.trimIndent(),
        )

        val r = CompilationHelper.compile(src).also { it.assertOk() }
        val generated = r.generatedSource("BodyPartToBodyPartDtoMapper.kt")

        assertTrue(generated.contains("active = isActive"))
    }

    @Test
    fun `target-side MapName references missing source property fails clearly`() {
        val src = SourceFile.kotlin(
            "MissingTargetSideSource.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap
            import io.github.ch000se.automap.annotations.MapName

            data class BodyPart(
                val name: String,
                val isActive: Boolean,
            )

            @AutoMap(source = BodyPart::class)
            data class BodyPartDto(
                val name: String = "",
                @MapName("isActve") val active: Boolean = false,
            )
            """.trimIndent(),
        )

        val r = CompilationHelper.compile(src).also { it.assertError() }

        r.assertErrorContains("@MapName(\"isActve\") on BodyPartDto.active references missing source property")
        r.assertErrorContains("Available source properties")
        r.assertErrorContains("- isActive")
        r.assertErrorContains("Change @MapName(\"isActve\") to @MapName(\"isActive\")")
    }

    @Test
    fun `conflicting source-side and target-side MapName fails`() {
        val src = SourceFile.kotlin(
            "ConflictingTargetAndSource.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap
            import io.github.ch000se.automap.annotations.MapName

            data class BodyPart(
                val name: String,
                @MapName("enabled") val isActive: Boolean,
            )

            @AutoMap(source = BodyPart::class)
            data class BodyPartDto(
                val name: String = "",
                @MapName("isActive") val active: Boolean = false,
                val enabled: Boolean = false,
            )
            """.trimIndent(),
        )

        val r = CompilationHelper.compile(src).also { it.assertError() }

        r.assertErrorContains("Conflicting @MapName declarations for target field \"active\"")
        r.assertErrorContains("BodyPartDto.active maps from BodyPart.isActive")
        r.assertErrorContains("BodyPart.isActive maps to BodyPartDto.enabled")
    }

    @Test
    fun `documentation target-side MapName example compiles without param use-site`() {
        val src = SourceFile.kotlin(
            "DocumentationExample.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap
            import io.github.ch000se.automap.annotations.MapName

            data class BodyPart(
                val name: String,
                val isActive: Boolean,
            )

            @AutoMap(source = BodyPart::class)
            data class BodyPartDto(
                val name: String = "",
                @MapName("isActive") val active: Boolean = false,
            )
            """.trimIndent(),
        )

        CompilationHelper.compile(src).also { it.assertOk() }
    }
}
