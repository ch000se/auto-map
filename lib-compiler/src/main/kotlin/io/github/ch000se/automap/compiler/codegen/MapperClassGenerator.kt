package io.github.ch000se.automap.compiler.codegen

import io.github.ch000se.automap.compiler.strategy.FieldStrategy
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import io.github.ch000se.automap.compiler.ksp.resolvedParams
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toClassName

// KotlinPoet 2.x uses Unicode single-char markers instead of %> / %<
private const val INDENT = "⇥"   // U+21E5
private const val UNINDENT = "⇤" // U+21E4

internal class MapperClassGenerator(
    private val sourceClass: KSClassDeclaration,
    private val targetClass: KSClassDeclaration,
    private val strategies: Map<String, FieldStrategy>,
    private val beforeMap: Boolean = false,
    private val afterMap: Boolean = false,
) {

    private val sourceName = sourceClass.simpleName.asString()
    private val targetName = targetClass.simpleName.asString()
    private val sourceClassName = sourceClass.toClassName()
    private val targetClassName = targetClass.toClassName()
    private val className = "${sourceName}To${targetName}Mapper"
    private val packageName = sourceClass.packageName.asString()
    private val mapperClassName = ClassName(packageName, className)

    fun generate(): List<FileSpec> {
        val hasCustom = strategies.values.any { it is FieldStrategy.Custom }
        val hasExplicitCustomOnly = hasCustom &&
            strategies.values.filterIsInstance<FieldStrategy.Custom>().all { it.isExplicit }
        val hasAutoCustom = hasCustom && !hasExplicitCustomOnly

        val typeParams = buildTypeParams()
        val hookParams = buildHookParams()
        val hasTypeParams = typeParams.isNotEmpty()
        val hasHooks = hookParams.isNotEmpty()

        // A mapper class is needed when there are constructor params or custom strategies
        val needsClass = hasCustom || hasTypeParams || hasHooks

        if (!needsClass) {
            // Simple case: pure extension function only
            return listOf(buildSimpleExtFile())
        }

        // Decide if the class is abstract:
        //   - abstract when: has custom strategies OR has type params (Nested/Collection/MapEntries)
        //   - concrete when: only hooks (no custom, no type params)
        val isAbstractClass = hasCustom || hasTypeParams

        val constructorParams: List<MapperParam> = hookParams + typeParams.values.toList()

        val mapperFile = buildMapperClassFile(
            hasCustom = hasCustom,
            isAbstractClass = isAbstractClass,
            constructorParams = constructorParams,
            typeParams = typeParams,
        )

        return when {
            hasExplicitCustomOnly && !hasHooks && !hasTypeParams -> {
                // Explicit custom only: abstract class + convenience ext that wraps each resolver as lambda
                val extFile = buildCustomWrapperExtFile()
                listOf(mapperFile, extFile)
            }
            hasAutoCustom -> {
                // Broken ext file: abstract class instantiated directly → compile error by design
                val brokenExt = buildBrokenExtFile()
                listOf(mapperFile, brokenExt)
            }
            else -> {
                // Has type params or hooks (and possibly explicit custom alongside type params):
                // generate ext file
                val extFile = buildComplexExtFile(constructorParams, isAbstractClass)
                listOf(mapperFile, extFile)
            }
        }
    }

    // ──────────────────────────────── Parameter building ─────────────────────────────────────

    private data class MapperParam(
        val paramName: String,
        val lambdaType: LambdaTypeName,
    )

    private fun buildHookParams(): List<MapperParam> {
        val result = mutableListOf<MapperParam>()
        if (beforeMap) {
            result += MapperParam(
                paramName = "beforeMap",
                lambdaType = LambdaTypeName.get(
                    parameters = arrayOf(sourceClassName),
                    returnType = sourceClassName,
                ),
            )
        }
        if (afterMap) {
            result += MapperParam(
                paramName = "afterMap",
                lambdaType = LambdaTypeName.get(
                    parameters = arrayOf(targetClassName),
                    returnType = targetClassName,
                ),
            )
        }
        return result
    }

    /** Deduplicated by source TypeName: Nested, Collection, MapEntries share mapper if same type. */
    private fun buildTypeParams(): LinkedHashMap<TypeName, MapperParam> {
        val result = LinkedHashMap<TypeName, MapperParam>()
        for ((_, strategy) in strategies) {
            when (strategy) {
                is FieldStrategy.Nested ->
                    result.getOrPut(strategy.sourceTypeName) {
                        MapperParam(
                            paramName = "${simpleNameOf(strategy.sourceTypeName).decapitalize()}Mapper",
                            lambdaType = LambdaTypeName.get(
                                parameters = arrayOf(strategy.sourceTypeName),
                                returnType = strategy.targetTypeName,
                            ),
                        )
                    }
                is FieldStrategy.Collection ->
                    result.getOrPut(strategy.sourceElementTypeName) {
                        MapperParam(
                            paramName = "${simpleNameOf(strategy.sourceElementTypeName).decapitalize()}Mapper",
                            lambdaType = LambdaTypeName.get(
                                parameters = arrayOf(strategy.sourceElementTypeName),
                                returnType = strategy.targetElementTypeName,
                            ),
                        )
                    }
                is FieldStrategy.MapEntries ->
                    result.getOrPut(strategy.sourceValueTypeName) {
                        MapperParam(
                            paramName = "${simpleNameOf(strategy.sourceValueTypeName).decapitalize()}Mapper",
                            lambdaType = LambdaTypeName.get(
                                parameters = arrayOf(strategy.sourceValueTypeName),
                                returnType = strategy.targetValueTypeName,
                            ),
                        )
                    }
                else -> Unit
            }
        }
        return result
    }

    private fun simpleNameOf(typeName: TypeName): String =
        (typeName.copy(nullable = false) as? ClassName)?.simpleName ?: "Item"

    private fun String.decapitalize(): String = replaceFirstChar { it.lowercase() }

    // ──────────────────────────────── map() function ─────────────────────────────────────────

    /**
     * Builds the body of the `map(source)` function.
     *
     * - No hooks: expression body `= Target(...)`.
     * - beforeMap: block body starting with `val s = beforeMap(source)`, then `return Target(s...)`.
     * - afterMap: block body returning `afterMap(Target(...))`.
     * - Both hooks: block body with val s then return afterMap(Target(s...)).
     */
    private fun buildMapBody(typeParams: LinkedHashMap<TypeName, MapperParam>): CodeBlock {
        val sourceVar = if (beforeMap) "s" else "source"

        val targetParams = targetClass.resolvedParams()!!
        val nonIgnored = targetParams.filter { strategies[it.name!!.asString()] !is FieldStrategy.Ignore }

        val argsBlock = CodeBlock.builder().apply {
            for (param in nonIgnored) {
                val name = param.name!!.asString()
                val expr = expressionFor(strategies[name]!!, name, typeParams, sourceVar)
                add("%L = %L,\n", name, expr)
            }
        }.build()

        // No hooks: single return — KotlinPoet 2.x auto-converts to expression body
        if (!beforeMap && !afterMap) {
            return CodeBlock.builder()
                .add("return %T(\n$INDENT", targetClassName)
                .add(argsBlock)
                .add("${UNINDENT})\n")
                .build()
        }

        // With hooks: block body
        val constructExpr = CodeBlock.builder()
            .add("%T(\n$INDENT", targetClassName)
            .add(argsBlock)
            .add("${UNINDENT})")
            .build()

        return CodeBlock.builder().apply {
            if (beforeMap) {
                add("val s = beforeMap(source)\n")
            }
            if (afterMap) {
                add("return afterMap(%L)\n", constructExpr)
            } else {
                add("return %L\n", constructExpr)
            }
        }.build()
    }

    // ──────────────────────────────── File builders ───────────────────────────────────────────

    /**
     * Builds the mapper class file.
     */
    private fun buildMapperClassFile(
        hasCustom: Boolean,
        isAbstractClass: Boolean,
        constructorParams: List<MapperParam>,
        typeParams: LinkedHashMap<TypeName, MapperParam>,
    ): FileSpec {
        val ctorSpecs = constructorParams.map {
            ParameterSpec.builder(it.paramName, it.lambdaType).build()
        }
        val ctorProperties = constructorParams.map { mp ->
            PropertySpec.builder(mp.paramName, mp.lambdaType)
                .addModifiers(KModifier.PRIVATE)
                .initializer(mp.paramName)
                .build()
        }

        val mapFun = FunSpec.builder("map")
            .addModifiers(KModifier.PUBLIC)
            .addParameter("source", sourceClassName)
            .returns(targetClassName)
            .addCode(buildMapBody(typeParams))
            .build()

        val typeBuilder = TypeSpec.classBuilder(className).addModifiers(KModifier.PUBLIC)

        if (isAbstractClass) {
            typeBuilder.addModifiers(KModifier.ABSTRACT)
        }

        if (ctorSpecs.isNotEmpty()) {
            typeBuilder.primaryConstructor(
                FunSpec.constructorBuilder().addParameters(ctorSpecs).build(),
            )
            typeBuilder.addProperties(ctorProperties)
        }

        // Abstract resolver methods for Custom strategies (in declaration order)
        if (hasCustom) {
            for ((_, strategy) in strategies) {
                if (strategy !is FieldStrategy.Custom) continue
                val resolverFun = FunSpec.builder(strategy.resolverName)
                    .addModifiers(KModifier.PUBLIC, KModifier.ABSTRACT)
                    .addParameter("source", sourceClassName)
                    .returns(strategy.returnType)
                    .build()
                typeBuilder.addFunction(resolverFun)
            }
        }

        typeBuilder.addFunction(mapFun)
        addOriginatingFiles(typeBuilder)

        return FileSpec.builder(packageName, className)
            .addType(typeBuilder.build())
            .build()
    }

    private fun addOriginatingFiles(builder: TypeSpec.Builder) {
        sourceClass.containingFile?.let { builder.addOriginatingKSFile(it) }
        targetClass.containingFile?.let {
            if (it != sourceClass.containingFile) builder.addOriginatingKSFile(it)
        }
    }

    private fun addOriginatingFiles(builder: FunSpec.Builder) {
        sourceClass.containingFile?.let { builder.addOriginatingKSFile(it) }
        targetClass.containingFile?.let {
            if (it != sourceClass.containingFile) builder.addOriginatingKSFile(it)
        }
    }

    /**
     * Builds the extension function file (`{Src}To{Tgt}MapperExt.kt`) for the complex case
     * (type params and/or hooks present).
     *
     * Uses `object : MapperClass(...) {}` for abstract classes (type params present), or direct
     * `MapperClass(...)` instantiation for concrete classes (hooks only, no type params).
     * Emits an expression body (`= ...`) for a single-line call.
     */
    private fun buildComplexExtFile(
        constructorParams: List<MapperParam>,
        isAbstractClass: Boolean,
    ): FileSpec {
        val ctorParamSpecs = constructorParams.map {
            ParameterSpec.builder(it.paramName, it.lambdaType).build()
        }
        val argList = constructorParams.joinToString(", ") { it.paramName }

        // Single return — KotlinPoet 2.x auto-converts to expression body
        val callExpr = if (isAbstractClass) {
            "return object : %T($argList) {}.map(this)\n"
        } else {
            "return %T($argList).map(this)\n"
        }

        val extFun = FunSpec.builder("to$targetName")
            .addModifiers(KModifier.PUBLIC)
            .receiver(sourceClassName)
            .returns(targetClassName)
            .addParameters(ctorParamSpecs)
            .addCode(callExpr, mapperClassName)
            .also { addOriginatingFiles(it) }
            .build()

        return FileSpec.builder(packageName, "${className}Ext")
            .addFunction(extFun)
            .build()
    }

    /**
     * Builds the simple extension function file (no mapper class), using `this.` as source prefix.
     * Generated when there are no custom strategies, no type params, and no hooks.
     *
     * The function uses Kotlin expression body syntax (`= ...`) to match the expected output format.
     */
    private fun buildSimpleExtFile(): FileSpec {
        val targetParams = targetClass.resolvedParams()!!
        val nonIgnored = targetParams.filter { strategies[it.name!!.asString()] !is FieldStrategy.Ignore }

        val argsBlock = CodeBlock.builder().apply {
            for (param in nonIgnored) {
                val name = param.name!!.asString()
                val expr = expressionFor(
                    strategy = strategies[name]!!,
                    name = name,
                    typeParams = LinkedHashMap(),
                    sourceVar = "this",
                )
                add("%L = %L,\n", name, expr)
            }
        }.build()

        // Single return — KotlinPoet 2.x auto-converts to expression body
        val body = CodeBlock.builder()
            .add("return %T(\n$INDENT", targetClassName)
            .add(argsBlock)
            .add("${UNINDENT})\n")
            .build()

        val extFun = FunSpec.builder("to$targetName")
            .addModifiers(KModifier.PUBLIC)
            .receiver(sourceClassName)
            .returns(targetClassName)
            .addCode(body)
            .also { addOriginatingFiles(it) }
            .build()

        return FileSpec.builder(packageName, className)
            .addFunction(extFun)
            .build()
    }

    /**
     * Builds a convenience extension file for the explicit-custom-only case.
     *
     * Instead of requiring callers to subclass the abstract mapper, each abstract `resolveXxx`
     * method is exposed as a lambda parameter on a generated extension function:
     *
     * ```kotlin
     * fun Source.toTarget(resolveXxx: (Source) -> XxxType): Target =
     *     object : SourceToTargetMapper() {
     *         override fun resolveXxx(source: Source) = resolveXxx(source)
     *     }.map(this)
     * ```
     */
    private fun buildCustomWrapperExtFile(): FileSpec {
        val customStrategies = strategies.values
            .filterIsInstance<FieldStrategy.Custom>()
            .filter { it.isExplicit }

        val lambdaParams = customStrategies.map { s ->
            ParameterSpec.builder(
                s.resolverName,
                LambdaTypeName.get(parameters = arrayOf(sourceClassName), returnType = s.returnType),
            ).build()
        }

        val body = CodeBlock.builder().apply {
            add("return object : %T() {\n$INDENT", mapperClassName)
            for (s in customStrategies) {
                add(
                    "override fun %L(source: %T) = %L(source)\n",
                    s.resolverName, sourceClassName, s.resolverName,
                )
            }
            add("${UNINDENT}}.map(this)\n")
        }.build()

        val extFun = FunSpec.builder("to$targetName")
            .addModifiers(KModifier.PUBLIC)
            .receiver(sourceClassName)
            .returns(targetClassName)
            .addParameters(lambdaParams)
            .addCode(body)
            .also { addOriginatingFiles(it) }
            .build()

        return FileSpec.builder(packageName, "${className}Ext")
            .addFunction(extFun)
            .build()
    }

    /**
     * Builds a broken extension file for the auto-custom case: tries to instantiate an abstract
     * class directly, causing a Kotlin compile error. This is intentional — it forces the user to
     * subclass the abstract mapper and implement the required abstract methods.
     */
    private fun buildBrokenExtFile(): FileSpec {
        val extFun = FunSpec.builder("to$targetName")
            .addModifiers(KModifier.PUBLIC)
            .receiver(sourceClassName)
            .returns(targetClassName)
            .addCode("return %T().map(this)\n", mapperClassName)
            .also { addOriginatingFiles(it) }
            .build()

        return FileSpec.builder(packageName, "${className}Ext")
            .addFunction(extFun)
            .build()
    }

    // ──────────────────────────────── Expression generation ──────────────────────────────────

    @Suppress("ReturnCount", "CyclomaticComplexMethod")
    private fun expressionFor(
        strategy: FieldStrategy,
        name: String,
        typeParams: Map<TypeName, MapperParam>,
        sourceVar: String,
    ): CodeBlock = when (strategy) {
        is FieldStrategy.Direct -> CodeBlock.of("%L.%L", sourceVar, name)
        is FieldStrategy.Renamed -> CodeBlock.of("%L.%L", sourceVar, strategy.sourceName)
        is FieldStrategy.Constant -> CodeBlock.of(strategy.literal)
        is FieldStrategy.NullableWithDefault ->
            CodeBlock.of("%L.%L ?: %L", sourceVar, strategy.sourceName, strategy.defaultExpr)
        is FieldStrategy.EnumByName ->
            CodeBlock.of("%T.valueOf(%L.%L.name)", strategy.targetEnumTypeName, sourceVar, strategy.sourceName)
        is FieldStrategy.TypeConvert ->
            CodeBlock.of("%L.%L%L", sourceVar, strategy.sourceName, strategy.conversionCall)
        is FieldStrategy.Custom ->
            CodeBlock.of("%L(%L)", strategy.resolverName, sourceVar)
        is FieldStrategy.Nested ->
            CodeBlock.of("%L(%L.%L)", typeParams[strategy.sourceTypeName]!!.paramName, sourceVar, strategy.fieldName)
        is FieldStrategy.Collection -> {
            val param = typeParams[strategy.sourceElementTypeName]!!
            if (strategy.isSet) {
                CodeBlock.of("%L.%L.mapTo(linkedSetOf()) { %L(it) }", sourceVar, strategy.fieldName, param.paramName)
            } else {
                CodeBlock.of("%L.%L.map { %L(it) }", sourceVar, strategy.fieldName, param.paramName)
            }
        }
        is FieldStrategy.MapEntries ->
            CodeBlock.of(
                "%L.%L.mapValues { (_, v) -> %L(v) }",
                sourceVar,
                strategy.fieldName,
                typeParams[strategy.sourceValueTypeName]!!.paramName,
            )
        is FieldStrategy.CollectionConvert -> {
            if (strategy.isSet) {
                CodeBlock.of(
                    "%L.%L.mapTo(linkedSetOf()) { it%L }",
                    sourceVar, strategy.fieldName, strategy.conversionCall,
                )
            } else {
                CodeBlock.of("%L.%L.map { it%L }", sourceVar, strategy.fieldName, strategy.conversionCall)
            }
        }
        is FieldStrategy.InlineNested -> {
            val nestedPrefix = "$sourceVar.${strategy.fieldName}"
            val innerArgs = CodeBlock.builder().apply {
                for ((fieldName, nestedStrategy) in strategy.nestedStrategies) {
                    if (nestedStrategy is FieldStrategy.Ignore) continue
                    val expr = expressionFor(nestedStrategy, fieldName, typeParams, nestedPrefix)
                    add("%L = %L,\n", fieldName, expr)
                }
            }.build()
            CodeBlock.builder()
                .add("%T(\n$INDENT", strategy.targetTypeName)
                .add(innerArgs)
                .add("${UNINDENT})")
                .build()
        }
        is FieldStrategy.Ignore -> error("Ignore should be filtered before expressionFor()")
    }
}