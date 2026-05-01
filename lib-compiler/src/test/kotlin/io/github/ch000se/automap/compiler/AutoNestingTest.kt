package io.github.ch000se.automap.compiler

import io.github.ch000se.automap.compiler.support.CompilationHelper.assertContent
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertHasGeneratedFile
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertOk
import io.github.ch000se.automap.compiler.support.CompilationHelper.compile
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

class AutoNestingTest {

    @Test
    fun `compatible nested data class auto-mapped without @AutoMap`() {
        val result = compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package test
                import io.github.ch000se.automap.annotations.AutoMap

                // Address has NO @AutoMap annotation — InlineNested should kick in
                data class Address(val street: String, val city: String)
                data class AddressDto(val street: String, val city: String)

                @AutoMap(target = UserDto::class)
                data class User(val id: Long, val address: Address)
                data class UserDto(val id: Long, val address: AddressDto)
                """.trimIndent(),
            ),
        )

        result.assertOk()
        result.assertHasGeneratedFile("UserToUserDtoMapper.kt").assertContent(
            """
            package test
            public fun User.toUserDto(): UserDto = UserDto(
                id = this.id,
                address = AddressDto(
                    street = this.address.street,
                    city = this.address.city,
                ),
            )
            """,
        )
    }

    @Test
    fun `nested with type conversion auto-mapped`() {
        val result = compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package test
                import io.github.ch000se.automap.annotations.AutoMap

                // Coordinate has Int fields, CoordinateDto has Long fields
                data class Coordinate(val x: Int, val y: Int)
                data class CoordinateDto(val x: Long, val y: Long)

                @AutoMap(target = ShapeDto::class)
                data class Shape(val name: String, val origin: Coordinate)
                data class ShapeDto(val name: String, val origin: CoordinateDto)
                """.trimIndent(),
            ),
        )

        result.assertOk()
        result.assertHasGeneratedFile("ShapeToShapeDtoMapper.kt").assertContent(
            """
            package test
            public fun Shape.toShapeDto(): ShapeDto = ShapeDto(
                name = this.name,
                origin = CoordinateDto(
                    x = this.origin.x.toLong(),
                    y = this.origin.y.toLong(),
                ),
            )
            """,
        )
    }

    @Test
    fun `two-level deep nesting without @AutoMap`() {
        val result = compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package test
                import io.github.ch000se.automap.annotations.AutoMap

                // Three levels of nesting, none annotated with @AutoMap
                data class Country(val code: String)
                data class CountryDto(val code: String)

                data class Address(val street: String, val country: Country)
                data class AddressDto(val street: String, val country: CountryDto)

                @AutoMap(target = UserDto::class)
                data class User(val id: Long, val address: Address)
                data class UserDto(val id: Long, val address: AddressDto)
                """.trimIndent(),
            ),
        )

        result.assertOk()
        result.assertHasGeneratedFile("UserToUserDtoMapper.kt").assertContent(
            """
            package test
            public fun User.toUserDto(): UserDto = UserDto(
                id = this.id,
                address = AddressDto(
                    street = this.address.street,
                    country = CountryDto(
                        code = this.address.country.code,
                    ),
                ),
            )
            """,
        )
    }

    @Test
    fun `deep nesting with type conversion at inner level`() {
        val result = compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package test
                import io.github.ch000se.automap.annotations.AutoMap

                data class Point(val x: Int, val y: Int)
                data class PointDto(val x: Long, val y: Long)

                data class Shape(val label: String, val origin: Point)
                data class ShapeDto(val label: String, val origin: PointDto)

                @AutoMap(target = SceneDto::class)
                data class Scene(val name: String, val shape: Shape)
                data class SceneDto(val name: String, val shape: ShapeDto)
                """.trimIndent(),
            ),
        )

        result.assertOk()
        result.assertHasGeneratedFile("SceneToSceneDtoMapper.kt").assertContent(
            """
            package test
            public fun Scene.toSceneDto(): SceneDto = SceneDto(
                name = this.name,
                shape = ShapeDto(
                    label = this.shape.label,
                    origin = PointDto(
                        x = this.shape.origin.x.toLong(),
                        y = this.shape.origin.y.toLong(),
                    ),
                ),
            )
            """,
        )
    }

    @Test
    fun `Nested via @AutoMap takes priority over InlineNested`() {
        val result = compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package test
                import io.github.ch000se.automap.annotations.AutoMap

                // Address HAS @AutoMap — Nested strategy should be used, not InlineNested
                @AutoMap(target = AddressDto::class)
                data class Address(val street: String, val city: String)
                data class AddressDto(val street: String, val city: String)

                @AutoMap(target = UserDto::class)
                data class User(val id: Long, val address: Address)
                data class UserDto(val id: Long, val address: AddressDto)
                """.trimIndent(),
            ),
        )

        result.assertOk()
        // Outer mapper uses Nested strategy (constructor param), NOT inline expansion
        result.assertHasGeneratedFile("UserToUserDtoMapper.kt").assertContent(
            """
            package test
            public abstract class UserToUserDtoMapper(
                private val addressMapper: (Address) -> AddressDto,
            ) {
                public fun map(source: User): UserDto = UserDto(
                    id = source.id,
                    address = addressMapper(source.address),
                )
            }
            """,
        )
        // Extension file proves that the Nested (lambda) approach was used
        result.assertHasGeneratedFile("UserToUserDtoMapperExt.kt").assertContent(
            """
            package test
            public fun User.toUserDto(addressMapper: (Address) -> AddressDto): UserDto = object : UserToUserDtoMapper(addressMapper) {}.map(this)
            """,
        )
    }
}