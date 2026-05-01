package io.github.ch000se.automap.compiler.strategy

import com.squareup.kotlinpoet.TypeName

internal sealed class FieldStrategy {

    /** Same name and type in source — emit `source.fieldName`. */
    data class Direct(val name: String) : FieldStrategy()

    /** Field renamed via `@AutoMap.Field(source = "...")` — emit `source.sourceName`. */
    data class Renamed(val sourceName: String) : FieldStrategy()

    /** Literal constant — embed verbatim in generated code. */
    data class Constant(val literal: String) : FieldStrategy()

    /**
     * Nullable source field with a fallback default.
     * Emits `source.sourceName ?: defaultExpr`.
     */
    data class NullableWithDefault(val sourceName: String, val defaultExpr: String) : FieldStrategy()

    /**
     * Automatically match enum values by name across different enum types.
     * Emits `TargetEnum.valueOf(source.sourceName.name)`.
     */
    data class EnumByName(val sourceName: String, val targetEnumTypeName: TypeName) : FieldStrategy()

    /** Skip parameter (target field must have a default value). */
    object Ignore : FieldStrategy()

    /**
     * Call a conversion function on the source property value.
     * Emits `source.sourceName.conversionCall` (e.g. `source.count.toLong()`).
     *
     * @param sourceName Name of the source property to read.
     * @param conversionCall The conversion call including the leading dot (e.g. `.toLong()`).
     */
    data class TypeConvert(val sourceName: String, val conversionCall: String) : FieldStrategy()

    /**
     * Declare this field as requiring a custom resolver.
     *
     * When [isExplicit] is `true` the field was annotated with `@AutoMap.Field(custom = true)` and
     * the processor generates an `abstract fun resolveXxx(source: Source): FieldType` method; the
     * caller subclasses the mapper and provides the implementation.
     *
     * When [isExplicit] is `false` the strategy was chosen automatically because no matching source
     * property could be found and the target parameter has no default value.
     */
    data class Custom(
        val resolverName: String,
        val returnType: TypeName,
        val isExplicit: Boolean,
    ) : FieldStrategy()

    /**
     * Delegate a nested object mapping via a constructor-injected lambda.
     * Emits `fieldNameMapper(source.fieldName)`.
     */
    data class Nested(
        val fieldName: String,
        val sourceTypeName: TypeName,
        val targetTypeName: TypeName,
    ) : FieldStrategy()

    /**
     * Delegate a List<T>/Set<T> element mapping via a constructor-injected lambda.
     * Emits `source.fieldName.map { elementMapper(it) }` or `.mapTo(linkedSetOf())`.
     */
    data class Collection(
        val fieldName: String,
        val sourceElementTypeName: TypeName,
        val targetElementTypeName: TypeName,
        val isSet: Boolean,
    ) : FieldStrategy()

    /**
     * Delegate a Map<K,V> value mapping via a constructor-injected lambda.
     * Emits `source.fieldName.mapValues { (_, v) -> valueMapper(v) }`.
     */
    data class MapEntries(
        val fieldName: String,
        val sourceValueTypeName: TypeName,
        val targetValueTypeName: TypeName,
    ) : FieldStrategy()

    /**
     * Inline-expand a compatible nested object without requiring a separate `@AutoMap` annotation.
     * Emits a nested constructor call such as:
     * ```
     * address = AddressDto(
     *     street = source.address.street,
     *     city = source.address.city,
     * )
     * ```
     * Supports recursive nesting: [nestedStrategies] may contain other [InlineNested] entries,
     * allowing arbitrary depth without an explicit lambda per level.
     *
     * @param fieldName Name of the field on both source and target.
     * @param targetTypeName The KotlinPoet [TypeName] of the target nested type.
     * @param nestedStrategies Resolved strategies for each constructor parameter of the target
     *   nested type.
     */
    data class InlineNested(
        val fieldName: String,
        val targetTypeName: TypeName,
        val nestedStrategies: Map<String, FieldStrategy>,
    ) : FieldStrategy()

    /**
     * Apply a type-conversion function to every element of a collection when the source and target
     * element types differ by a safe auto-detectable conversion.
     *
     * For List: emits `source.fieldName.map { it.conversionCall }`.
     * For Set:  emits `source.fieldName.mapTo(linkedSetOf()) { it.conversionCall }`.
     *
     * @param fieldName Name of the collection field in the source class.
     * @param conversionCall The conversion call including the leading dot (e.g. `.toString()`).
     * @param isSet `true` when the target type is `Set`.
     */
    data class CollectionConvert(
        val fieldName: String,
        val conversionCall: String,
        val isSet: Boolean,
    ) : FieldStrategy()
}