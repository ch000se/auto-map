package io.github.ch000se.automap.compiler

import com.tschuchort.compiletesting.SourceFile
import io.github.ch000se.automap.compiler.support.CompilationHelper
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertError
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertErrorContains
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertOk
import io.github.ch000se.automap.compiler.support.CompilationHelper.callMapper
import io.github.ch000se.automap.compiler.support.CompilationHelper.generatedSource
import io.github.ch000se.automap.compiler.support.CompilationHelper.loadClass
import io.github.ch000se.automap.compiler.support.CompilationHelper.newSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AutoMapFixesTest {

    @Test
    fun `nullable nested AutoMap uses safe call`() {
        val src = SourceFile.kotlin(
            "NullableNested.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap

            @AutoMap(target = AddressDto::class)
            data class Address(val city: String)
            data class AddressDto(val city: String)

            @AutoMap(target = UserDto::class)
            data class User(val address: Address?)
            data class UserDto(val address: AddressDto?)
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(src).also { it.assertOk() }
        val user = r.newSource("demo.User", null)
        val dto = r.callMapper("demo.UserToUserDtoMapperKt", "toUserDto", user)

        assertEquals("UserDto(address=null)", dto.toString())
        assertTrue(r.generatedSource("UserToUserDtoMapper.kt").contains("address = address?.toAddressDto()"))
    }

    @Test
    fun `nullable list AutoMap uses safe map`() {
        val src = SourceFile.kotlin(
            "NullableList.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap

            @AutoMap(target = ItemDto::class)
            data class Item(val name: String)
            data class ItemDto(val name: String)

            @AutoMap(target = Target::class)
            data class Source(val items: List<Item>?)
            data class Target(val items: List<ItemDto>?)
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(src).also { it.assertOk() }

        assertTrue(r.generatedSource("SourceToTargetMapper.kt").contains("items = items?.map { it.toItemDto() }"))
    }

    @Test
    fun `missing nested mapper gives helpful error`() {
        val src = SourceFile.kotlin(
            "MissingNested.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap

            data class Address(val city: String)
            data class AddressDto(val city: String)

            @AutoMap(target = UserDto::class)
            data class User(val address: Address)
            data class UserDto(val address: AddressDto)
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(src).also { it.assertError() }

        r.assertErrorContains("No mapper found for Address -> AddressDto")
        r.assertErrorContains("Annotate Address")
    }

    @Test
    fun `MapWith fn calls fully qualified top-level function`() {
        val src = SourceFile.kotlin(
            "FunctionConverter.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap
            import io.github.ch000se.automap.annotations.MapWith

            fun toLabel(value: Long): String = "value=" + value

            @AutoMap(target = Target::class)
            data class Source(@MapWith(fn = "demo.toLabel") val amount: Long)

            data class Target(val amount: String)
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(src).also { it.assertOk() }
        val source = r.newSource("demo.Source", 42L)
        val target = r.callMapper("demo.SourceToTargetMapperKt", "toTarget", source)

        assertEquals("Target(amount=value=42)", target.toString())
        assertTrue(r.generatedSource("SourceToTargetMapper.kt").contains("amount = demo.toLabel(amount)"))
    }

    @Test
    fun `MapWith calls object function reference`() {
        val src = SourceFile.kotlin(
            "ObjectFunctionConverter.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap
            import io.github.ch000se.automap.annotations.MapWith

            object Converters {
                fun toLabel(value: Long): String = "value=" + value
            }

            @AutoMap(target = Target::class)
            data class Source(@MapWith("demo.Converters.toLabel") val amount: Long)

            data class Target(val amount: String)
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(src).also { it.assertOk() }
        val source = r.newSource("demo.Source", 42L)
        val target = r.callMapper("demo.SourceToTargetMapperKt", "toTarget", source)

        assertEquals("Target(amount=value=42)", target.toString())
    }

    @Test
    fun `MapWith wrong converter parameter type fails`() {
        val src = SourceFile.kotlin(
            "WrongParam.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap
            import io.github.ch000se.automap.annotations.MapWith

            fun convert(value: String): String = value

            @AutoMap(target = Target::class)
            data class Source(@MapWith("convert") val amount: Long)

            data class Target(val amount: String)
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(src).also { it.assertError() }

        r.assertErrorContains("Invalid converter for field \"amount\"")
        r.assertErrorContains("(kotlin.Long) -> kotlin.String")
    }

    @Test
    fun `MapWith wrong converter return type fails`() {
        val src = SourceFile.kotlin(
            "WrongReturn.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap
            import io.github.ch000se.automap.annotations.MapWith

            fun convert(value: Long): Long = value

            @AutoMap(target = Target::class)
            data class Source(@MapWith("convert") val amount: Long)

            data class Target(val amount: String)
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(src).also { it.assertError() }

        r.assertErrorContains("Invalid converter for field \"amount\"")
        r.assertErrorContains("(kotlin.Long) -> kotlin.String")
    }

    @Test
    fun `enum maps to String using name`() {
        val src = SourceFile.kotlin(
            "EnumString.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap

            enum class Status { Ready }

            @AutoMap(target = Target::class)
            data class Source(val status: Status)

            data class Target(val status: String)
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(src).also { it.assertOk() }
        val status = r.loadClass("demo.Status").enumConstants.first()
        val source = r.newSource("demo.Source", status)
        val target = r.callMapper("demo.SourceToTargetMapperKt", "toTarget", source)

        assertEquals("Target(status=Ready)", target.toString())
        assertTrue(r.generatedSource("SourceToTargetMapper.kt").contains("status = status.name"))
    }

    @Test
    fun `narrowing Long to Int fails`() {
        val src = SourceFile.kotlin(
            "Narrowing.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap

            @AutoMap(target = Target::class)
            data class Source(val id: Long)

            data class Target(val id: Int)
            """.trimIndent(),
        )
        CompilationHelper.compile(src).also { it.assertError() }
    }

    @Test
    fun `generateListVariant false skips list extension`() {
        val src = SourceFile.kotlin(
            "NoList.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap

            @AutoMap(target = Target::class, generateListVariant = false)
            data class Source(val id: Int)

            data class Target(val id: Int)
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(src).also { it.assertOk() }
        val mapperClass = r.loadClass("demo.SourceToTargetMapperKt")

        assertTrue(mapperClass.declaredMethods.any { it.name == "toTarget" })
        assertFalse(mapperClass.declaredMethods.any { it.name == "toTargetList" })
    }

    @Test
    fun `missing target field with default value is omitted`() {
        val src = SourceFile.kotlin(
            "DefaultValue.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap

            @AutoMap(target = Target::class)
            data class Source(val id: Int)

            data class Target(val id: Int, val label: String = "default")
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(src).also { it.assertOk() }
        val source = r.newSource("demo.Source", 10)
        val target = r.callMapper("demo.SourceToTargetMapperKt", "toTarget", source)

        assertEquals("Target(id=10, label=default)", target.toString())
        assertFalse(r.generatedSource("SourceToTargetMapper.kt").contains("label ="))
    }

    @Test
    fun `duplicate mapping pair fails`() {
        val src = SourceFile.kotlin(
            "Duplicate.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap

            @AutoMap(target = Target::class)
            data class Source(val id: Int)

            @AutoMap(source = Source::class)
            data class Target(val id: Int)
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(src).also { it.assertError() }

        r.assertErrorContains("Duplicate AutoMap mapping detected")
        r.assertErrorContains("Source -> Target")
    }

    @Test
    fun `duplicate converter pair fails`() {
        val src = SourceFile.kotlin(
            "DuplicateConverter.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap
            import io.github.ch000se.automap.annotations.MapWith

            fun formatPrice(value: Long): String = value.toString()
            fun longToString(value: Long): String = value.toString()

            @AutoMap(target = TargetOne::class)
            data class SourceOne(@MapWith("formatPrice") val amount: Long)
            data class TargetOne(val amount: String)

            @AutoMap(target = TargetTwo::class)
            data class SourceTwo(@MapWith("longToString") val amount: Long)
            data class TargetTwo(val amount: String)
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(src).also { it.assertError() }

        r.assertErrorContains("Duplicate converter detected for kotlin.Long -> kotlin.String")
        r.assertErrorContains("formatPrice")
        r.assertErrorContains("longToString")
    }
}
