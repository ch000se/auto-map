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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DxCorrectnessTest {

    @Test
    fun `property-target MapName works without param use-site`() {
        val src = SourceFile.kotlin(
            "PropertyAnnotation.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap
            import io.github.ch000se.automap.annotations.MapName

            @AutoMap(target = Target::class)
            data class Source(@MapName("fullName") val name: String)

            data class Target(val fullName: String)
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(src).also { it.assertOk() }
        val source = r.newSource("demo.Source", "Ada")
        val target = r.callMapper("demo.SourceToTargetMapperKt", "toTarget", source)

        assertEquals("Target(fullName=Ada)", target.toString())
    }

    @Test
    fun `conflicting param and property MapName fails`() {
        val src = SourceFile.kotlin(
            "AnnotationConflict.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap
            import io.github.ch000se.automap.annotations.MapName

            @AutoMap(target = Target::class)
            data class Source(
                @param:MapName("name")
                @property:MapName("title")
                val title: String,
            )

            data class Target(val name: String)
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(src).also { it.assertError() }

        r.assertErrorContains("Conflicting @MapName annotations found for property \"title\"")
    }

    @Test
    fun `duplicate MapName candidates fail`() {
        val src = SourceFile.kotlin(
            "DuplicateMapName.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap
            import io.github.ch000se.automap.annotations.MapName

            @AutoMap(target = Target::class)
            data class Source(
                @MapName("id") val localId: Int,
                @MapName("id") val remoteId: Int,
            )

            data class Target(val id: Int)
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(src).also { it.assertError() }

        r.assertErrorContains("Ambiguous explicit mapping for target field \"id\"")
        r.assertErrorContains("localId")
        r.assertErrorContains("remoteId")
    }

    @Test
    fun `generic source class fails clearly`() {
        val src = SourceFile.kotlin(
            "Generic.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap

            data class PageDto<T>(val items: List<T>)

            @AutoMap(target = PageDto::class)
            data class Page<T>(val items: List<T>)
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(src).also { it.assertError() }

        r.assertErrorContains("Generic AutoMap source types are not supported yet")
    }

    @Test
    fun `target with private primary constructor fails clearly`() {
        val src = SourceFile.kotlin(
            "PrivateConstructor.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap

            @AutoMap(target = Target::class)
            data class Source(val id: Int)

            data class Target private constructor(val id: Int)
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(src).also { it.assertError() }

        r.assertErrorContains("target primary constructor is private or unavailable")
    }

    @Test
    fun `abstract source type fails clearly`() {
        val src = SourceFile.kotlin(
            "AbstractSource.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap

            @AutoMap(target = Target::class)
            interface Source {
                val id: Int
            }

            data class Target(val id: Int)
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(src).also { it.assertError() }

        r.assertErrorContains("AutoMap source must be a concrete class")
    }

    @Test
    fun `AutoMapMappers documentation file is generated`() {
        val src = SourceFile.kotlin(
            "Registry.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap

            @AutoMap(target = Target::class)
            data class Source(val id: Int)

            data class Target(val id: Int)
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(src).also { it.assertOk() }

        val registry = r.generatedSource("AutoMapMappers.kt")
        assertTrue(registry.contains("demo.Source -> demo.Target"))
        assertTrue(registry.contains("Function: Source.toTarget()"))
    }

    @Test
    fun `list function collision is detected`() {
        val targetA = SourceFile.kotlin("AUser.kt", "package a\ndata class User(val id: Int)")
        val targetB = SourceFile.kotlin("BUser.kt", "package b\ndata class User(val id: Int)")
        val src = SourceFile.kotlin(
            "Collision.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap

            @AutoMap(target = a.User::class)
            data class SourceA(val id: Int)

            @AutoMap(target = b.User::class)
            data class SourceB(val id: Int)
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(targetA, targetB, src).also { it.assertError() }

        r.assertErrorContains("Generated mapper function collision detected")
        r.assertErrorContains("toUserList")
    }

    @Test
    fun `custom jvmName resolves list JVM collision`() {
        val targetA = SourceFile.kotlin("AUser.kt", "package a\ndata class User(val id: Int)")
        val targetB = SourceFile.kotlin("BUser.kt", "package b\ndata class User(val id: Int)")
        val src = SourceFile.kotlin(
            "JvmNameCollision.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap

            @AutoMap(target = a.User::class, jvmName = "toAUser")
            data class SourceA(val id: Int)

            @AutoMap(target = b.User::class, jvmName = "toBUser")
            data class SourceB(val id: Int)
            """.trimIndent(),
        )
        CompilationHelper.compile(targetA, targetB, src).also { it.assertOk() }
    }

    @Test
    fun `KSP args are read and applied`() {
        val src = SourceFile.kotlin(
            "KspArgs.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap

            @AutoMap(target = Target::class)
            data class Source(val nested: Nested)

            data class Nested(val id: Int)
            data class Target(val id: Int)
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(
            src,
            options = mapOf("automap.flatten" to "true", "automap.generateListVariant" to "false"),
        ).also { it.assertOk() }

        val generated = r.generatedSource("SourceToTargetMapper.kt")
        assertTrue(generated.contains("id = nested.id"))
        assertFalse(generated.contains("toTargetList"))
    }

    @Test
    fun `invalid KSP arg fails clearly`() {
        val src = SourceFile.kotlin(
            "InvalidArg.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap

            @AutoMap(target = Target::class)
            data class Source(val id: Int)
            data class Target(val id: Int)
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(
            src,
            options = mapOf("automap.generateListVariant" to "maybe"),
        ).also { it.assertError() }

        r.assertErrorContains("Invalid AutoMap KSP option")
    }

    @Test
    fun `generated visibility is internal when source is internal`() {
        val src = SourceFile.kotlin(
            "Internal.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap

            @AutoMap(target = Target::class)
            internal data class Source(val id: Int)

            data class Target(val id: Int)
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(src).also { it.assertOk() }

        assertTrue(r.generatedSource("SourceToTargetMapper.kt").contains("internal fun Source.toTarget()"))
    }

    @Test
    fun `inherited interface property can be mapped`() {
        val src = SourceFile.kotlin(
            "Inherited.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap

            interface HasTimestamp {
                val timestamp: Long
            }

            @AutoMap(target = Target::class)
            data class Source(val id: Int, override val timestamp: Long) : HasTimestamp

            data class Target(val id: Int, val timestamp: Long)
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(src).also { it.assertOk() }
        val source = r.newSource("demo.Source", 1, 99L)
        val target = r.callMapper("demo.SourceToTargetMapperKt", "toTarget", source)

        assertEquals("Target(id=1, timestamp=99)", target.toString())
    }
}
