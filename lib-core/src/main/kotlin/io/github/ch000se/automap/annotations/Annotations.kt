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
 * functions from a single annotation. Bidirectional generation supports symmetric name remapping
 * with [MapName], but it rejects [MapWith] and [MapIgnore] because those annotations do not have an
 * automatic inverse.
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
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
public annotation class AutoMap(
    val target: KClass<*> = Nothing::class,
    val source: KClass<*> = Nothing::class,
    val bidirectional: Boolean = false,
)

/**
 * Maps a source property or constructor parameter to a target constructor parameter with a
 * different name.
 *
 * By default, AutoMap matches source properties to target primary-constructor parameters by name.
 * Apply `@MapName("targetParameterName")` when the logical field is the same but the source and
 * target names differ.
 *
 * When used together with `@AutoMap(bidirectional = true)`, the rename is inverted automatically
 * for the reverse mapper.
 *
 * Example:
 *
 * ```kotlin
 * @AutoMap(target = ContactDto::class)
 * data class Contact(@MapName("displayName") val fullName: String)
 *
 * data class ContactDto(val displayName: String)
 * ```
 *
 * @property value Name of the target constructor parameter that should receive the annotated
 *   source value.
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
 * Contract for class-based field converters used by [MapWith].
 *
 * Implement this interface when a source property needs explicit conversion before it is passed to
 * the target constructor. Converters should be stateless. If the converter is declared as an
 * `object`, AutoMap calls `Converter.convert(value)` directly. If it is declared as a class,
 * AutoMap creates it with a no-argument constructor and then calls `convert(value)`.
 *
 * Example:
 *
 * ```kotlin
 * object CentsToLabel : AutoMapConverter<Long, String> {
 *     override fun convert(value: Long): String = "$" + value / 100
 * }
 *
 * @AutoMap(target = ProductDto::class)
 * data class Product(@MapWith(CentsToLabel::class) val priceInCents: Long)
 * ```
 *
 * @param Source Source property type accepted by the converter.
 * @param Target Target constructor parameter type returned by the converter.
 */
public fun interface AutoMapConverter<in Source, out Target> {
    /**
     * Converts one source property value into the value passed to the target constructor.
     *
     * @param value Source property value from the generated mapper receiver.
     * @return Converted value for the target constructor argument.
     */
    public fun convert(value: Source): Target
}

/**
 * Converts a source property through custom mapping logic.
 *
 * If [value] points to an [AutoMapConverter], AutoMap emits a call to the converter's `convert`
 * function and passes the source property as its only argument.
 *
 * If [value] is left as its default, AutoMap adds a lambda parameter to the generated mapper
 * function. This is useful when conversion requires runtime context such as a formatter, locale,
 * repository, or configuration object.
 *
 * `@MapWith` cannot be used with `@AutoMap(bidirectional = true)` because arbitrary conversion
 * logic has no automatic inverse.
 *
 * Example:
 *
 * ```kotlin
 * object CentsToLabel : AutoMapConverter<Long, String> {
 *     override fun convert(value: Long): String = "$" + value / 100
 * }
 *
 * @AutoMap(target = ProductDto::class)
 * data class Product(@MapWith(CentsToLabel::class) val priceInCents: Long)
 *
 * data class ProductDto(val priceInCents: String)
 * ```
 *
 * @property value Converter type. Leave as [AutoMapConverter] to request a generated lambda
 *   parameter named after the target field.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
public annotation class MapWith(
    val value: KClass<out AutoMapConverter<*, *>> = AutoMapConverter::class,
)
