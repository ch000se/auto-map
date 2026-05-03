package io.github.ch000se.automap.compiler.generation

import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.toTypeName

/**
 * Converts resolved mapper metadata into KotlinPoet function declarations.
 *
 * Every [MapperFunction] produces two extension functions: a single-object mapper on the source
 * type and a list mapper on `List<Source>`. The renderer has no access to KSP file writing; it only
 * describes the functions that [MapperEmitter] will place into a generated file.
 */
internal class MapperFunctionRenderer {

    /**
     * Renders the single-object and list mapper functions for [function].
     */
    fun render(function: MapperFunction): List<FunSpec> {
        val context = RenderContext(
            sourceType = function.source.asStarProjectedType(),
            targetType = function.target.asStarProjectedType(),
            funName = "to${function.target.simpleName.asString()}",
            resolutions = function.resolutions,
        )
        return listOf(context.singleMapper(), context.listMapper())
    }

    private fun RenderContext.singleMapper(): FunSpec {
        val body = CodeBlock.builder()
            .add("return %T(\n", targetType.toTypeName())
            .indent()
        for (resolution in resolutions) {
            if (resolution !is Resolution.Omit) {
                body.add("%N = %L,\n", resolution.targetParamName, resolution.expression)
            }
        }
        body.unindent()
            .add(")\n")

        return FunSpec.builder(funName)
            .addModifiers(KModifier.PUBLIC)
            .receiver(sourceType.toTypeName())
            .returns(targetType.toTypeName())
            .addParameters(lambdaParams.map { it.toParameterSpec() })
            .addCode(body.build())
            .build()
    }

    private fun RenderContext.listMapper(): FunSpec {
        val sourceTypeName = sourceType.toTypeName()
        val targetTypeName = targetType.toTypeName()
        return FunSpec.builder(listFunName)
            .addModifiers(KModifier.PUBLIC)
            .receiver(List::class.asClassName().parameterizedBy(sourceTypeName))
            .returns(List::class.asClassName().parameterizedBy(targetTypeName))
            .addParameters(lambdaParams.map { it.toParameterSpec() })
            .addStatement("return map { it.%N(%L) }", funName, lambdaCallArguments())
            .build()
    }

    private fun RenderContext.lambdaCallArguments(): CodeBlock {
        val callArgs = CodeBlock.builder()
        lambdaParams.forEachIndexed { index, param ->
            if (index > 0) callArgs.add(", ")
            callArgs.add("%N", param.lambdaName)
        }
        return callArgs.build()
    }

    private fun Resolution.LambdaConverter.toParameterSpec(): ParameterSpec {
        val lambdaType = LambdaTypeName.get(
            parameters = arrayOf(sourceType.toTypeName()),
            returnType = targetType.toTypeName(),
        )
        return ParameterSpec.builder(lambdaName, lambdaType).build()
    }

    private data class RenderContext(
        val sourceType: KSType,
        val targetType: KSType,
        val funName: String,
        val resolutions: List<Resolution>,
    ) {
        val listFunName: String = "${funName}List"
        val lambdaParams: List<Resolution.LambdaConverter> =
            resolutions.filterIsInstance<Resolution.LambdaConverter>()
    }
}
