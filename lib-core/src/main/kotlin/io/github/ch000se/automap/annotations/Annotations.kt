package io.github.ch000se.automap.annotations

import kotlin.reflect.KClass

/**
 * Marks a Kotlin class as a source or target for generated mapper functions.
 *
 * AutoMap is a source-retained annotation consumed by the `automap-compiler` KSP processor. For
 * each valid declaration, the processor emits a Kotlin source file in the source class package
 * containing a `toTargetType()` extension function and a matching `List<Source>.toTargetTypeList()`
 * helper.
 *
 * Exactly one of [target] or [source] must be provided:
 *
 * - Use [target] when the annotated class is the source type.
 * - Use [source] when the annotated class is the target type. This is useful for clean architecture
 *   setups where a domain model must not import DTO or persistence-layer classes.
 *
 * Set [bidirectional] to `true` to generate both `Source -> Target` and `Target -> Source`
 * functions from a single annotation. Bidirectional generation is intentionally strict: field-level
 * mapping annotations such as [MapName], [MapWithFn], [MapWith], and [MapIgnore] are rejected
 * because AutoMap cannot prove their reverse direction is correct.
 *
 * Set [flatten] to `true` to allow target constructor parameters to be inferred from nested
 * composite source properties. Use [Flatten] on selected properties when only specific nested
 * objects should participate in flatten lookup.
 *
 * [functionName] changes the generated Kotlin extension function name. [jvmName] adds `@JvmName`
 * to the generated function for Java callers and JVM signature collision avoidance.
 *
 * Example:
 *
 * ```kotlin
 * @AutoMap(target = UserDto::class)
 * data class User(val id: Long, val name: String)
 *
 * data class UserDto(val id: Long, val name: String)
 *
 * // Generated:
 * // fun User.toUserDto(): UserDto
 * // fun List<User>.toUserDtoList(): List<UserDto>
 * ```
 *
 * @property target Target class that should be constructed from the annotated source class.
 * @property source Source class that should be mapped into the annotated target class.
 * @property bidirectional Whether the processor should generate mapper functions in both
 *   directions.
 * @property flatten Whether unresolved target parameters may be inferred from nested composite
 *   source properties.
 * @property generateListVariant Whether AutoMap should also generate `List<Source>.toTargetList()`.
 * @property functionName Optional custom Kotlin extension function name.
 * @property jvmName Optional `@JvmName` value for Java callers and JVM signature collision
 *   avoidance.
 * @property visibility Visibility policy for generated mapper functions.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
public annotation class AutoMap(
    val target: KClass<*> = Nothing::class,
    val source: KClass<*> = Nothing::class,
    val bidirectional: Boolean = false,
    val flatten: Boolean = false,
    val generateListVariant: Boolean = true,
    val functionName: String = "",
    val jvmName: String = "",
    val visibility: AutoMapVisibility = AutoMapVisibility.AUTO,
)

/**
 * Controls visibility of generated mapper extension functions.
 *
 * `AUTO` is the default. It generates an `internal` mapper when either side of the mapping is
 * internal, otherwise it generates a `public` mapper.
 */
public enum class AutoMapVisibility {
    /** Use `internal` when either side of the mapping is internal, otherwise `public`. */
    AUTO,

    /** Always generate a public mapper function. */
    PUBLIC,

    /** Always generate an internal mapper function. */
    INTERNAL,
}

/**
 * Marks a source property as a composite object whose fields can be searched for auto-flattened
 * mappings.
 *
 * Use this when only selected nested properties should participate in flatten lookup instead of
 * enabling `@AutoMap(flatten = true)` globally.
 *
 * Example:
 *
 * ```kotlin
 * @AutoMap(target = Note::class)
 * data class NoteProjection(
 *     @Flatten val note: NoteDbModel,
 *     @Flatten val author: AuthorDbModel,
 * )
 * ```
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
public annotation class Flatten

/**
 * Maps this property or constructor parameter to a property with a different name on the opposite
 * side of the mapping.
 *
 * By default, AutoMap matches source properties to target primary-constructor parameters by name.
 * Apply `@MapName("otherSideName")` when the logical field is the same but the source and target
 * names differ.
 *
 * The annotation is context-aware:
 *
 * - On a source-side declaration, [value] is the target constructor parameter name.
 * - On a target-side declaration using `@AutoMap(source = ...)`, [value] is the source property
 *   name.
 *
 * Prefer target-side declarations for clean architecture when DTO or persistence-layer classes can
 * import domain classes, but domain classes should not import DTO types.
 *
 * `@MapName` cannot be used together with `@AutoMap(bidirectional = true)`. Renames are one-way
 * instructions, and AutoMap does not silently infer the reverse mapping.
 *
 * Source-side example:
 *
 * ```kotlin
 * @AutoMap(target = ContactDto::class)
 * data class Contact(@MapName("displayName") val fullName: String)
 *
 * data class ContactDto(val displayName: String)
 * ```
 *
 * Target-side example:
 *
 * ```kotlin
 * data class Contact(val fullName: String)
 *
 * @AutoMap(source = Contact::class)
 * data class ContactDto(@MapName("fullName") val displayName: String = "")
 * ```
 *
 * @property value Name of the corresponding property on the opposite side of the mapping.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
public annotation class MapName(val value: String)

/**
 * Excludes a source property or constructor parameter from generated constructor arguments.
 *
 * Use this annotation when the source contains data that must not be copied to the target. The
 * matching target constructor parameter must declare a default value; otherwise the generated
 * constructor call would be incomplete and the processor reports a compile-time error.
 *
 * `@MapIgnore` cannot be used with `@AutoMap(bidirectional = true)` because AutoMap cannot infer
 * the ignored value when generating the reverse mapper.
 *
 * Example:
 *
 * ```kotlin
 * @AutoMap(target = UserDto::class)
 * data class User(val id: Long, @MapIgnore val passwordHash: String)
 *
 * data class UserDto(val id: Long, val passwordHash: String = "")
 * ```
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
public annotation class MapIgnore

/**
 * Converts a source property through custom mapping logic.
 *
 * If [value] or [fn] names a function, AutoMap emits a direct function call and passes the source
 * property as its only argument. This string-based function form is kept for compatibility; prefer
 * [MapWithFn] for new code.
 *
 * If both are left as their defaults, AutoMap adds a lambda parameter to the generated mapper
 * function. This is useful when conversion requires runtime context such as a formatter, locale,
 * repository, or configuration object.
 *
 * `@MapWith` cannot be used with `@AutoMap(bidirectional = true)` because arbitrary conversion
 * logic has no automatic inverse.
 *
 * Example:
 *
 * ```kotlin
 * @MapWith("formatCents")
 * val priceInCents: Long
 *
 * fun formatCents(value: Long): String = "$" + value / 100
 * ```
 *
 * @property value Legacy string function reference. Supports same-package names, fully qualified
 *   top-level functions, and object function references.
 * @property fn Named alias for [value] when a more explicit argument name is preferred.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
public annotation class MapWith(
    val value: String = "",
    val fn: String = "",
)

/**
 * Converts a source property by calling a top-level or object function.
 *
 * Kotlin annotations cannot accept arbitrary `::function` references as arguments, so AutoMap uses
 * this compiler-friendly annotation for named converter functions. The function must take exactly
 * one parameter compatible with the source property type and return a type compatible with the
 * target constructor parameter.
 *
 * Supported references:
 *
 * - same-package top-level function: `@MapWithFn("formatPrice")`
 * - fully qualified top-level function: `@MapWithFn("com.example.formatPrice")`
 * - object function reference: `@MapWithFn("com.example.PriceConverters.formatPrice")`
 *
 * Example:
 *
 * ```kotlin
 * @MapWithFn("localDateToTimestamp")
 * val date: LocalDate
 *
 * fun localDateToTimestamp(value: LocalDate): Timestamp = Timestamp.valueOf(value.atStartOfDay())
 * ```
 *
 * @property value String function reference used to convert the annotated source value.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
public annotation class MapWithFn(val value: String)

/**
 * Compile-time metadata emitted by AutoMap for cross-module mapper discovery.
 *
 * This annotation is generated onto internal metadata objects. Application code should not use it
 * directly; it exists so downstream KSP runs can discover mapper functions from dependency
 * modules without runtime reflection or a runtime registry.
 *
 * @property sourceFqn Fully qualified name of the mapper receiver/source type.
 * @property targetFqn Fully qualified name of the target type constructed by the mapper.
 * @property functionFqn Fully qualified Kotlin extension function name.
 * @property functionName Simple Kotlin extension function name.
 * @property sourcePackage Package containing the source type and generated mapper extension.
 * @property targetPackage Package containing the target type.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class AutoMapGeneratedMapping(
    val sourceFqn: String,
    val targetFqn: String,
    val functionFqn: String,
    val functionName: String,
    val sourcePackage: String,
    val targetPackage: String,
)
