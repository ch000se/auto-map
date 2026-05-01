package io.github.ch000se.automap.annotations

import kotlin.reflect.KClass

/**
 * Marks a class as the source for automatic mapper generation.
 *
 * The shape of the generated code depends on whether the mapping requires injected dependencies
 * (nested mappers, element mappers for collections/maps, custom resolvers) or lifecycle hooks:
 *
 * - **Simple case** — no custom resolvers, no nested/collection/map element mappers, and neither
 *   [beforeMap] nor [afterMap] enabled: a single file `SourceToTargetMapper.kt` is generated
 *   containing only a top‑level extension function `fun Source.toTarget(): Target`.
 * - **Complex case** — custom resolvers, nested/collection/map element mappers, or hooks present:
 *   two files are generated. `SourceToTargetMapper.kt` declares a `SourceToTargetMapper` class
 *   exposing `fun map(source: Source): Target` (abstract when custom resolvers or element mappers
 *   are involved), and `SourceToTargetMapperExt.kt` declares a convenience extension
 *   `fun Source.toTarget(...): Target` that constructs the mapper and delegates to `map`.
 *
 * @param target The target class to map into.
 * @param fields Optional field-level overrides.
 * @param reverse When `true`, also generates the reverse mapper (target → source) using
 *   auto-detection only. For custom reverse logic, annotate the target class separately.
 * @param beforeMap When `true`, adds a `beforeMap: (Source) -> Source` constructor parameter to
 *   the generated mapper class. The function is invoked on the source object before any field
 *   is read, allowing callers to pre-process or replace the input.
 * @param afterMap When `true`, adds an `afterMap: (Target) -> Target` constructor parameter to
 *   the generated mapper class. The function is invoked on the fully-constructed target object
 *   before it is returned, allowing callers to post-process the result.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
public annotation class AutoMap(
    val target: KClass<*>,
    val fields: Array<Field> = [],
    val reverse: Boolean = false,
    val beforeMap: Boolean = false,
    val afterMap: Boolean = false,
) {
    /**
     * Overrides mapping behaviour for a single target field.
     *
     * Exactly one of [source], [constant], [defaultIfNull], [ignore], [custom], or [convert] may
     * be set at a time, with the exception that [source] may be combined with [defaultIfNull]
     * (rename + nullable fallback) and [source] may be combined with [convert] (rename + convert).
     *
     * @param target Name of the target constructor parameter this override applies to.
     * @param source Name of the source property to read from (rename).
     *   Can be combined with [defaultIfNull] or [convert].
     * @param constant Kotlin source literal embedded verbatim (e.g. `"\"hello\""`, `"42"`).
     * @param defaultIfNull Kotlin expression used as the Elvis right-hand side when the
     *   source property is nullable (e.g. `"\"\""` generates `fieldName = source.field ?: ""`).
     *   Can be combined with [source].
     * @param ignore Skip this parameter (target field must have a default value).
     * @param custom Declare this field as requiring a custom resolver. The processor generates an
     *   `abstract fun resolveXxx(source: Source): FieldType` method in an abstract mapper class;
     *   callers subclass the mapper and implement the method.
     * @param convert Name of a no-arg conversion function to call on the source property value
     *   (e.g. `"toInt"` generates `source.field.toInt()`). Can be combined with [source] to
     *   rename the source field before converting. Mutually exclusive with [ignore], [custom],
     *   [constant], and [defaultIfNull].
     */
    public annotation class Field(
        val target: String,
        val source: String = "",
        val constant: String = "",
        val defaultIfNull: String = "",
        val ignore: Boolean = false,
        val custom: Boolean = false,
        val convert: String = "",
    )
}