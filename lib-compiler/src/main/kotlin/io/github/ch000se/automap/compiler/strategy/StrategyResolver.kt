package io.github.ch000se.automap.compiler.strategy

import io.github.ch000se.automap.compiler.ksp.arg
import io.github.ch000se.automap.compiler.ksp.classDeclaration
import io.github.ch000se.automap.compiler.ksp.findAnnotation
import io.github.ch000se.automap.compiler.ksp.isEnumClass
import io.github.ch000se.automap.compiler.ksp.resolvedParams
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.squareup.kotlinpoet.ksp.toTypeName

private const val AUTO_MAP_FQN = "io.github.ch000se.automap.annotations.AutoMap"

/** Maps (srcQualifiedName, tgtQualifiedName) → conversionCall (with leading dot). */
private val TYPE_CONVERSIONS: Map<Pair<String, String>, String> = buildMap {
    // Any numeric/bool → String
    put(Pair("kotlin.Int", "kotlin.String"), ".toString()")
    put(Pair("kotlin.Long", "kotlin.String"), ".toString()")
    put(Pair("kotlin.Short", "kotlin.String"), ".toString()")
    put(Pair("kotlin.Byte", "kotlin.String"), ".toString()")
    put(Pair("kotlin.Float", "kotlin.String"), ".toString()")
    put(Pair("kotlin.Double", "kotlin.String"), ".toString()")
    put(Pair("kotlin.Boolean", "kotlin.String"), ".toString()")
    // Int widening
    put(Pair("kotlin.Int", "kotlin.Long"), ".toLong()")
    put(Pair("kotlin.Int", "kotlin.Double"), ".toDouble()")
    put(Pair("kotlin.Int", "kotlin.Float"), ".toFloat()")
    // Short widening
    put(Pair("kotlin.Short", "kotlin.Int"), ".toInt()")
    put(Pair("kotlin.Short", "kotlin.Long"), ".toLong()")
    // Byte widening
    put(Pair("kotlin.Byte", "kotlin.Int"), ".toInt()")
    put(Pair("kotlin.Byte", "kotlin.Long"), ".toLong()")
    // Float/Double
    put(Pair("kotlin.Float", "kotlin.Double"), ".toDouble()")
    // Narrowing (explicit intent via annotation or auto-detect)
    put(Pair("kotlin.Long", "kotlin.Int"), ".toInt()")
    put(Pair("kotlin.Long", "kotlin.Double"), ".toDouble()")
    put(Pair("kotlin.Double", "kotlin.Float"), ".toFloat()")
}

internal class StrategyResolver(
    private val sourceClass: KSClassDeclaration,
    private val targetClass: KSClassDeclaration,
    private val fieldAnnotations: List<KSAnnotation>,
    private val logger: KSPLogger,
) {

    private val sourceProperties: Map<String, KSPropertyDeclaration> by lazy {
        sourceClass.getAllProperties()
            .filter { it.hasBackingField }
            .associateBy { it.simpleName.asString() }
    }

    fun resolve(): Map<String, FieldStrategy>? {
        val params = targetClass.resolvedParams() ?: return emptyMap()
        if (params.isEmpty()) return emptyMap()

        val result = LinkedHashMap<String, FieldStrategy>()
        for (param in params) {
            val strategy = resolveParam(param) ?: return null
            result[param.name!!.asString()] = strategy
        }
        return result
    }

    @Suppress("ReturnCount", "CyclomaticComplexMethod")
    private fun resolveParam(param: KSValueParameter): FieldStrategy? {
        val name = param.name!!.asString()

        // Step 1: @AutoMap.Field annotation override
        val fieldAnnotation = fieldAnnotations.find { (it.arg("target") as? String) == name }
        if (fieldAnnotation != null) {
            return resolveFromFieldAnnotation(param, fieldAnnotation)
        }

        val paramType = param.type.resolve()
        val sourceProp = sourceProperties[name]
        val srcTypeOrNull = sourceProp?.type?.resolve()

        // Step 2: Direct — same name and same type
        if (srcTypeOrNull != null && srcTypeOrNull == paramType) {
            return FieldStrategy.Direct(name)
        }

        if (sourceProp != null) {
            val srcType = srcTypeOrNull!!
            val srcClass = srcType.classDeclaration()
            val tgtClass = paramType.classDeclaration()

            // Step 3: Type conversion — same field name, auto-detectable type conversion
            val srcQName = srcType.classDeclaration()?.qualifiedName?.asString()
            val tgtQName = paramType.classDeclaration()?.qualifiedName?.asString()
            if (srcQName != null && tgtQName != null) {
                val conversion = findTypeConversion(srcQName, tgtQName)
                if (conversion != null) {
                    return FieldStrategy.TypeConvert(sourceName = name, conversionCall = conversion)
                }
                // Auto-toString: any non-String source type → kotlin.String
                if (srcQName != "kotlin.String" && tgtQName == "kotlin.String") {
                    return FieldStrategy.TypeConvert(sourceName = name, conversionCall = ".toString()")
                }
            }

            // Step 4: Nested — source property has @AutoMap pointing to paramType
            if (srcClass != null) {
                val mapTarget = (srcClass.findAnnotation(AUTO_MAP_FQN)?.arg("target") as? KSType)
                if (mapTarget?.classDeclaration() == tgtClass) {
                    return FieldStrategy.Nested(
                        fieldName = name,
                        sourceTypeName = srcType.toTypeName(),
                        targetTypeName = paramType.toTypeName(),
                    )
                }
            }

            // Step 5: Enum-by-name — both source and target are different enum types
            if (srcClass != null && tgtClass != null &&
                srcClass.isEnumClass() && tgtClass.isEnumClass() &&
                srcType != paramType
            ) {
                return FieldStrategy.EnumByName(
                    sourceName = name,
                    targetEnumTypeName = paramType.toTypeName(),
                )
            }
        }

        // Step 6: Collection with @AutoMap elements (existing logic)
        // Step 7: CollectionConvert (collection with primitive type conversion)
        val collectionStrategy = resolveCollection(param, sourceProp, paramType)
        if (collectionStrategy != null) return collectionStrategy

        // Step 8: InlineNested — probe auto-nesting without @AutoMap
        if (sourceProp != null) {
            val srcType = srcTypeOrNull!!
            val srcClass = srcType.classDeclaration()
            val tgtClass = paramType.classDeclaration()
            if (srcClass != null && tgtClass != null) {
                val inlineStrategies = probeInlineNested(srcClass, tgtClass)
                if (inlineStrategies != null) {
                    return FieldStrategy.InlineNested(
                        fieldName = name,
                        targetTypeName = paramType.toTypeName(),
                        nestedStrategies = inlineStrategies,
                    )
                }
            }
        }

        // Step 9: Default value → silently ignore
        if (param.hasDefault) return FieldStrategy.Ignore

        // Step 10: Auto-custom — inject resolver abstract method
        val resolverName = "resolve${name.replaceFirstChar { it.uppercaseChar() }}"
        return FieldStrategy.Custom(
            resolverName = resolverName,
            returnType = paramType.toTypeName(),
            isExplicit = false,
        )
    }

    @Suppress("ReturnCount", "CyclomaticComplexMethod")
    private fun resolveFromFieldAnnotation(
        param: KSValueParameter,
        field: KSAnnotation,
    ): FieldStrategy? {
        val name = param.name!!.asString()
        val ignore = field.arg("ignore") as? Boolean ?: false
        val custom = field.arg("custom") as? Boolean ?: false
        val constant = field.arg("constant") as? String ?: ""
        val source = field.arg("source") as? String ?: ""
        val defaultIfNull = field.arg("defaultIfNull") as? String ?: ""
        val convert = field.arg("convert") as? String ?: ""

        // Convert is mutually exclusive with ignore/custom/constant/defaultIfNull
        // but CAN be combined with source (rename + convert).
        if (convert.isNotEmpty()) {
            val incompatibleWithConvert = listOf(ignore, custom, constant.isNotEmpty(), defaultIfNull.isNotEmpty())
                .any { it }
            if (incompatibleWithConvert) {
                logger.error(
                    "@AutoMap.Field target='$name': convert cannot be combined with " +
                        "ignore/custom/constant/defaultIfNull",
                    param,
                )
                return null
            }
            val srcName = if (source.isNotEmpty()) source else name
            if (source.isNotEmpty() && !validateSourceField(source, param)) return null
            return FieldStrategy.TypeConvert(sourceName = srcName, conversionCall = ".${convert}()")
        }

        // Mutually exclusive options (ignore/custom/constant/defaultIfNull)
        val exclusiveCount = listOf(
            ignore, custom, constant.isNotEmpty(), defaultIfNull.isNotEmpty(),
        ).count { it }
        if (exclusiveCount > 1) {
            logger.error(
                "@AutoMap.Field target='$name': only one of ignore/custom/constant/defaultIfNull may be set",
                param,
            )
            return null
        }
        // source is compatible only with defaultIfNull; combining with ignore/custom/constant is invalid
        if (source.isNotEmpty() && (ignore || custom || constant.isNotEmpty())) {
            logger.error(
                "@AutoMap.Field target='$name': only one of ignore/custom/constant/defaultIfNull may be set",
                param,
            )
            return null
        }
        if (exclusiveCount == 0 && source.isEmpty()) {
            logger.error(
                "@AutoMap.Field target='$name' has no strategy configured " +
                    "(set source/constant/defaultIfNull/ignore/custom/convert)",
                param,
            )
            return null
        }

        // The actual source field name — defaults to target param name when `source` is not set
        val srcName = if (source.isNotEmpty()) source else name

        return when {
            ignore -> {
                if (!param.hasDefault) {
                    logger.error(
                        "@AutoMap.Field ignore=true for '$name' requires a default value in " +
                            targetClass.simpleName.asString(),
                        param,
                    )
                    null
                } else {
                    FieldStrategy.Ignore
                }
            }

            custom -> {
                val resolverName = "resolve${name.replaceFirstChar { it.uppercaseChar() }}"
                FieldStrategy.Custom(
                    resolverName = resolverName,
                    returnType = param.type.resolve().toTypeName(),
                    isExplicit = true,
                )
            }

            constant.isNotEmpty() -> FieldStrategy.Constant(constant)

            defaultIfNull.isNotEmpty() -> {
                if (!validateSourceField(srcName, param)) return null
                FieldStrategy.NullableWithDefault(sourceName = srcName, defaultExpr = defaultIfNull)
            }

            source.isNotEmpty() -> {
                if (!validateSourceField(source, param)) return null
                // Auto-detect enum-by-name when renaming between different enum types
                val srcType = sourceProperties[source]?.type?.resolve()
                val paramType = param.type.resolve()
                val tgtClass = paramType.classDeclaration()
                if (srcType != null &&
                    srcType.classDeclaration()?.isEnumClass() == true &&
                    tgtClass?.isEnumClass() == true &&
                    srcType != paramType
                ) {
                    FieldStrategy.EnumByName(
                        sourceName = source,
                        targetEnumTypeName = paramType.toTypeName(),
                    )
                } else {
                    FieldStrategy.Renamed(source)
                }
            }

            else -> error("unreachable: exclusiveCount==0 && source.isEmpty() already caught above")
        }
    }

    private fun validateSourceField(srcName: String, param: KSValueParameter): Boolean {
        if (sourceProperties[srcName] == null) {
            logger.error(
                "@AutoMap.Field source='$srcName' not found in ${sourceClass.simpleName.asString()}",
                param,
            )
            return false
        }
        return true
    }

    @Suppress("ReturnCount")
    private fun resolveCollection(
        param: KSValueParameter,
        sourceProp: KSPropertyDeclaration?,
        paramType: KSType,
    ): FieldStrategy? {
        val paramClass = paramType.classDeclaration() ?: return null
        val qName = paramClass.qualifiedName?.asString() ?: return null
        val isTargetList = qName == "kotlin.collections.List"
        val isTargetSet = qName == "kotlin.collections.Set"
        val isTargetMap = qName == "kotlin.collections.Map"
        if (!isTargetList && !isTargetSet && !isTargetMap) return null

        if (sourceProp == null) return null
        val srcType = sourceProp.type.resolve()
        val srcClass = srcType.classDeclaration() ?: return null
        val srcQName = srcClass.qualifiedName?.asString() ?: return null

        if (isTargetMap) {
            if (srcQName != "kotlin.collections.Map") return null
            val srcValueType = srcType.arguments.getOrNull(1)?.type?.resolve() ?: return null
            val tgtValueType = paramType.arguments.getOrNull(1)?.type?.resolve() ?: return null
            val srcValueClass = srcValueType.classDeclaration() ?: return null
            val mapTarget = (srcValueClass.findAnnotation(AUTO_MAP_FQN)?.arg("target") as? KSType)
                ?: return null
            if (mapTarget.classDeclaration() != tgtValueType.classDeclaration()) return null
            return FieldStrategy.MapEntries(
                fieldName = param.name!!.asString(),
                sourceValueTypeName = srcValueType.toTypeName(),
                targetValueTypeName = tgtValueType.toTypeName(),
            )
        }

        val isSrcList = srcQName == "kotlin.collections.List"
        val isSrcSet = srcQName == "kotlin.collections.Set"
        if (!isSrcList && !isSrcSet) return null

        val targetElement = paramType.arguments.firstOrNull()?.type?.resolve() ?: return null
        val srcElement = srcType.arguments.firstOrNull()?.type?.resolve() ?: return null
        val srcElementClass = srcElement.classDeclaration() ?: return null

        // Step 6: @AutoMap-annotated element types
        val mapTarget = (srcElementClass.findAnnotation(AUTO_MAP_FQN)?.arg("target") as? KSType)
        if (mapTarget != null && mapTarget.classDeclaration() == targetElement.classDeclaration()) {
            return FieldStrategy.Collection(
                fieldName = param.name!!.asString(),
                sourceElementTypeName = srcElement.toTypeName(),
                targetElementTypeName = targetElement.toTypeName(),
                isSet = isTargetSet,
            )
        }

        // Step 7: CollectionConvert — primitive element type conversion
        val srcElemQName = srcElementClass.qualifiedName?.asString() ?: return null
        val tgtElemQName = targetElement.classDeclaration()?.qualifiedName?.asString() ?: return null
        val elemConversion = findTypeConversion(srcElemQName, tgtElemQName) ?: return null
        return FieldStrategy.CollectionConvert(
            fieldName = param.name!!.asString(),
            conversionCall = elemConversion,
            isSet = isTargetSet,
        )
    }

    /**
     * Attempts to resolve inline nested strategies for [srcClass] → [tgtClass] without requiring
     * `@AutoMap` on the nested class. Returns a strategy map when every target constructor parameter
     * can be satisfied by Direct, TypeConvert, or (recursively) InlineNested; returns `null`
     * otherwise (silently — no logger calls).
     *
     * Recursion is bounded by [depth] (max 10 levels) and a [visited] set that guards against
     * circular class references.
     */
    @Suppress("ReturnCount")
    private fun probeInlineNested(
        srcClass: KSClassDeclaration,
        tgtClass: KSClassDeclaration,
        depth: Int = 0,
        visited: MutableSet<String> = mutableSetOf(),
    ): Map<String, FieldStrategy>? {
        if (depth > 10) return null

        val visitKey = "${srcClass.qualifiedName?.asString()}→${tgtClass.qualifiedName?.asString()}"
        if (!visited.add(visitKey)) return null  // cycle guard

        // Exclude stdlib types
        val srcPkg = srcClass.packageName.asString()
        val tgtPkg = tgtClass.packageName.asString()
        if (srcPkg.startsWith("kotlin") || srcPkg.startsWith("java")) return null
        if (tgtPkg.startsWith("kotlin") || tgtPkg.startsWith("java")) return null

        val tgtParams = tgtClass.resolvedParams() ?: return null
        if (tgtParams.isEmpty()) return null

        val srcProps: Map<String, KSPropertyDeclaration> = srcClass.getAllProperties()
            .filter { it.hasBackingField }
            .associateBy { it.simpleName.asString() }

        val result = LinkedHashMap<String, FieldStrategy>()
        for (param in tgtParams) {
            val pName = param.name?.asString() ?: return null
            val paramType = param.type.resolve()
            val srcProp = srcProps[pName] ?: return null
            val srcType = srcProp.type.resolve()

            if (srcType == paramType) {
                result[pName] = FieldStrategy.Direct(pName)
            } else {
                val srcQName = srcType.classDeclaration()?.qualifiedName?.asString() ?: return null
                val tgtQName = paramType.classDeclaration()?.qualifiedName?.asString() ?: return null
                val conversion = findTypeConversion(srcQName, tgtQName)
                if (conversion != null) {
                    result[pName] = FieldStrategy.TypeConvert(sourceName = pName, conversionCall = conversion)
                } else if (tgtQName == "kotlin.String") {
                    result[pName] = FieldStrategy.TypeConvert(sourceName = pName, conversionCall = ".toString()")
                } else {
                    // Recurse: try inline-nesting this field pair
                    val nestedSrcClass = srcType.classDeclaration() ?: return null
                    val nestedTgtClass = paramType.classDeclaration() ?: return null
                    val nestedStrategies = probeInlineNested(nestedSrcClass, nestedTgtClass, depth + 1, visited)
                        ?: return null
                    result[pName] = FieldStrategy.InlineNested(
                        fieldName = pName,
                        targetTypeName = paramType.toTypeName(),
                        nestedStrategies = nestedStrategies,
                    )
                }
            }
        }
        return result
    }

    private fun findTypeConversion(srcQName: String, tgtQName: String): String? =
        TYPE_CONVERSIONS[Pair(srcQName, tgtQName)]
}