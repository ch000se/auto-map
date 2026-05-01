package io.github.ch000se.automap.compiler

import io.github.ch000se.automap.compiler.support.CompilationHelper.assertContent
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertHasGeneratedFile
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertOk
import io.github.ch000se.automap.compiler.support.CompilationHelper.compile
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test

class CollectionMappingTest {

    @Test
    fun `list collection with annotated element type`() {
        val result = compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package test
                import io.github.ch000se.automap.annotations.AutoMap

                @AutoMap(target = TagDto::class)
                data class Tag(val name: String)
                data class TagDto(val name: String)

                @AutoMap(target = PostDto::class)
                data class Post(val id: Long, val tags: List<Tag>)
                data class PostDto(val id: Long, val tags: List<TagDto>)
                """.trimIndent(),
            ),
        )

        result.assertOk()
        result.assertHasGeneratedFile("PostToPostDtoMapper.kt").assertContent(
            """
            package test
            public abstract class PostToPostDtoMapper(
                private val tagMapper: (Tag) -> TagDto,
            ) {
                public fun map(source: Post): PostDto = PostDto(
                    id = source.id,
                    tags = source.tags.map { tagMapper(it) },
                )
            }
            """,
        )
        result.assertHasGeneratedFile("PostToPostDtoMapperExt.kt").assertContent(
            """
            package test
            public fun Post.toPostDto(tagMapper: (Tag) -> TagDto): PostDto = object : PostToPostDtoMapper(tagMapper) {}.map(this)
            """,
        )
    }

    @Test
    fun `set collection with annotated element type`() {
        val result = compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package test
                import io.github.ch000se.automap.annotations.AutoMap

                @AutoMap(target = RoleDto::class)
                data class Role(val code: String)
                data class RoleDto(val code: String)

                @AutoMap(target = UserDto::class)
                data class User(val id: Long, val roles: Set<Role>)
                data class UserDto(val id: Long, val roles: Set<RoleDto>)
                """.trimIndent(),
            ),
        )

        result.assertOk()
        result.assertHasGeneratedFile("UserToUserDtoMapper.kt").assertContent(
            """
            package test
            public abstract class UserToUserDtoMapper(
                private val roleMapper: (Role) -> RoleDto,
            ) {
                public fun map(source: User): UserDto = UserDto(
                    id = source.id,
                    roles = source.roles.mapTo(linkedSetOf()) { roleMapper(it) },
                )
            }
            """,
        )
        result.assertHasGeneratedFile("UserToUserDtoMapperExt.kt").assertContent(
            """
            package test
            public fun User.toUserDto(roleMapper: (Role) -> RoleDto): UserDto = object : UserToUserDtoMapper(roleMapper) {}.map(this)
            """,
        )
    }

    @Test
    fun `same element mapper reused for two list fields`() {
        val result = compile(
            SourceFile.kotlin(
                "Models.kt",
                """
                package test
                import io.github.ch000se.automap.annotations.AutoMap

                @AutoMap(target = ItemDto::class)
                data class Item(val id: Long)
                data class ItemDto(val id: Long)

                @AutoMap(target = CartDto::class)
                data class Cart(val selected: List<Item>, val wishlist: List<Item>)
                data class CartDto(val selected: List<ItemDto>, val wishlist: List<ItemDto>)
                """.trimIndent(),
            ),
        )

        result.assertOk()
        result.assertHasGeneratedFile("CartToCartDtoMapper.kt").assertContent(
            """
            package test
            public abstract class CartToCartDtoMapper(
                private val itemMapper: (Item) -> ItemDto,
            ) {
                public fun map(source: Cart): CartDto = CartDto(
                    selected = source.selected.map { itemMapper(it) },
                    wishlist = source.wishlist.map { itemMapper(it) },
                )
            }
            """,
        )
        result.assertHasGeneratedFile("CartToCartDtoMapperExt.kt").assertContent(
            """
            package test
            public fun Cart.toCartDto(itemMapper: (Item) -> ItemDto): CartDto = object : CartToCartDtoMapper(itemMapper) {}.map(this)
            """,
        )
    }
}