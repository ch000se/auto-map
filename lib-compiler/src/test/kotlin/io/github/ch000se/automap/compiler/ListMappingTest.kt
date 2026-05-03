package io.github.ch000se.automap.compiler

import com.tschuchort.compiletesting.SourceFile
import io.github.ch000se.automap.compiler.support.CompilationHelper
import io.github.ch000se.automap.compiler.support.CompilationHelper.assertOk
import io.github.ch000se.automap.compiler.support.CompilationHelper.callMapper
import io.github.ch000se.automap.compiler.support.CompilationHelper.loadClass
import io.github.ch000se.automap.compiler.support.CompilationHelper.newSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ListMappingTest {

    @Test
    fun `List of AutoMap elements maps and list helper is generated`() {
        val src = SourceFile.kotlin(
            "Article.kt",
            """
            package demo
            import io.github.ch000se.automap.annotations.AutoMap

            @AutoMap(target = TagDto::class)
            data class Tag(val name: String)
            data class TagDto(val name: String)

            @AutoMap(target = ArticleDto::class)
            data class Article(val id: Long, val tags: List<Tag>)
            data class ArticleDto(val id: Long, val tags: List<TagDto>)
            """.trimIndent(),
        )
        val r = CompilationHelper.compile(src).also { it.assertOk() }
        val t1 = r.newSource("demo.Tag", "kotlin")
        val t2 = r.newSource("demo.Tag", "android")
        val article = r.newSource("demo.Article", 1L, listOf(t1, t2))
        val dto = r.callMapper("demo.ArticleToArticleDtoMapperKt", "toArticleDto", article)
        assertEquals("ArticleDto(id=1, tags=[TagDto(name=kotlin), TagDto(name=android)])", dto.toString())

        // List helper exists
        val mapperClass = r.loadClass("demo.ArticleToArticleDtoMapperKt")
        val listHelper = mapperClass.declaredMethods.first { it.name == "toArticleDtoList" }
        @Suppress("UNCHECKED_CAST")
        val out = listHelper.invoke(null, listOf(article)) as List<Any>
        assertEquals(1, out.size)
        assertEquals(dto.toString(), out[0].toString())
    }
}