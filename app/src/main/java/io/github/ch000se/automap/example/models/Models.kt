package io.github.ch000se.automap.example.models

import io.github.ch000se.automap.annotations.AutoMap
import io.github.ch000se.automap.annotations.AutoMapConverter
import io.github.ch000se.automap.annotations.MapIgnore
import io.github.ch000se.automap.annotations.MapName
import io.github.ch000se.automap.annotations.MapWith

private const val SAMPLE_TAX_DIVISOR = 10L

/**
 * Example domain user used to demonstrate direct mapping and ignored sensitive fields.
 *
 * The generated mapper copies [id], [name], and [email] into [UserDto]. [passwordHash] is marked
 * with [MapIgnore], so the DTO constructor uses its default value instead.
 *
 * @property id Stable user identifier.
 * @property name Display name copied directly into [UserDto.name].
 * @property email Email address copied directly into [UserDto.email].
 * @property passwordHash Sensitive source-only value excluded from the generated mapper.
 */
@AutoMap(target = UserDto::class)
data class User(
    val id: Long,
    val name: String,
    val email: String,
    @param:MapIgnore val passwordHash: String,
)

/**
 * Example transfer object produced from [User].
 *
 * The default [passwordHash] value makes it legal for `@MapIgnore` to omit the source password
 * hash when constructing this DTO.
 *
 * @property id Stable user identifier.
 * @property name Display name received from [User.name].
 * @property email Email address received from [User.email].
 * @property passwordHash Defaulted DTO field that is intentionally not populated from [User].
 */
data class UserDto(val id: Long, val name: String, val email: String, val passwordHash: String = "")

/**
 * Example class-based converter used by [Product.priceInCents].
 */
object ComputeTax : AutoMapConverter<Long, Long> {
    /**
     * Calculates an example tax amount from [value].
     *
     * @param value Price amount in cents.
     * @return Example tax amount calculated from [value].
     */
    override fun convert(value: Long): Long = value / SAMPLE_TAX_DIVISOR
}

/**
 * Example product model used to demonstrate renamed fields and custom conversions.
 *
 * [name] is mapped to `ProductDto.title` with [MapName]. [priceInCents] uses the named
 * [ComputeTax] converter, while [displayPrice] uses a generated lambda parameter supplied by the
 * caller.
 *
 * @property id Stable product identifier.
 * @property name Product name mapped into `ProductDto.title`.
 * @property priceInCents Source price used by the named converter.
 * @property displayPrice Source price used by a caller-supplied lambda converter.
 */
@AutoMap(target = ProductDto::class)
data class Product(
    val id: Long,
    @param:MapName("title") val name: String,
    @param:MapWith(ComputeTax::class) val priceInCents: Long,
    @param:MapWith val displayPrice: Long,
)

/**
 * Example DTO produced from [Product].
 *
 * [title] receives [Product.name], [priceInCents] receives the named converter result, and
 * [displayPrice] receives the generated lambda converter result.
 *
 * @property id Stable product identifier.
 * @property title Product title produced from [Product.name].
 * @property priceInCents Converted price value produced by [ComputeTax].
 * @property displayPrice Formatted price text supplied by the lambda converter.
 */
data class ProductDto(val id: Long, val title: String, val priceInCents: Long, val displayPrice: String)

/**
 * Example nested value object used by [Order].
 *
 * Because [Address] has its own `@AutoMap` declaration, the generated `Order -> OrderDto` mapper
 * can call the generated `Address -> AddressDto` mapper automatically.
 *
 * @property street Street line copied into [AddressDto.street].
 * @property city City copied into [AddressDto.city].
 */
@AutoMap(target = AddressDto::class)
data class Address(val street: String, val city: String)

/**
 * Example DTO produced from [Address].
 *
 * @property street Street line received from [Address.street].
 * @property city City received from [Address.city].
 */
data class AddressDto(val street: String, val city: String)

/**
 * Example aggregate model used to demonstrate nested object mapping and list element mapping.
 *
 * The generated mapper converts [address] through the `Address` mapper and converts every [tags]
 * element through the `Tag` mapper.
 *
 * @property id Stable order identifier.
 * @property address Nested address converted into [OrderDto.address].
 * @property tags Collection of source tags converted into [OrderDto.tags].
 */
@AutoMap(target = OrderDto::class)
data class Order(val id: Long, val address: Address, val tags: List<Tag>)

/**
 * Example DTO produced from [Order].
 *
 * @property id Stable order identifier.
 * @property address Nested DTO address produced from [Order.address].
 * @property tags Collection of tag DTOs produced from [Order.tags].
 */
data class OrderDto(val id: Long, val address: AddressDto, val tags: List<TagDto>)

/**
 * Example collection element model.
 *
 * The generated `Order -> OrderDto` mapper can map `List<Tag>` into `List<TagDto>` because this
 * element type declares its own AutoMap pair.
 *
 * @property name Tag label copied into [TagDto.name].
 */
@AutoMap(target = TagDto::class)
data class Tag(val name: String)

/**
 * Example DTO produced from [Tag].
 *
 * @property name Tag label received from [Tag.name].
 */
data class TagDto(val name: String)

/**
 * Example domain model that intentionally stays annotation-free.
 *
 * [PersonDto] declares `@AutoMap(source = Person::class)` so a data-layer type can own the mapping
 * dependency without forcing the domain model to import AutoMap annotations.
 *
 * @property id Stable person identifier.
 * @property fullName Person name copied into [PersonDto.fullName].
 */
data class Person(val id: Long, val fullName: String)

/**
 * Example DTO that drives clean-architecture mapping from the target side.
 *
 * `bidirectional = true` generates both `Person.toPersonDto()` and `PersonDto.toPerson()`.
 *
 * @property id Stable person identifier.
 * @property fullName Person name copied in both mapping directions.
 */
@AutoMap(source = Person::class, bidirectional = true)
data class PersonDto(val id: Long, val fullName: String)
