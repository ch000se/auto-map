package io.github.ch000se.automap.example.models

import io.github.ch000se.automap.annotations.AutoMap

// ── Case 1: Direct mapping ────────────────────────────────────────────────────
// All fields match by name and type → plain extension function, no class.
// Generates: fun User.toUserDto(): UserDto = UserDto(...)

@AutoMap(target = UserDto::class, reverse = true)
data class User(val id: Long, val name: String, val email: String)

data class UserDto(val id: Long, val name: String, val email: String)

// ── Case 2: Field rename ──────────────────────────────────────────────────────
// source.fullName → target.displayName
// Generates: fun Contact.toContactDto(): ContactDto = ContactDto(...)

@AutoMap(
    target = ContactDto::class,
    fields = [
        AutoMap.Field(target = "displayName", source = "fullName"),
    ],
)
data class Contact(val id: Long, val fullName: String, val phone: String)

data class ContactDto(val id: Long, val displayName: String, val phone: String)

// ── Case 3: Constant field ────────────────────────────────────────────────────
// Injects a compile-time literal into every generated target object.
// Generates: fun SystemMessage.toSystemMessageDto(): SystemMessageDto = SystemMessageDto(... version = "1.0", ...)

@AutoMap(
    target = SystemMessageDto::class,
    fields = [
        AutoMap.Field(target = "version", constant = "\"1.0\""),
        AutoMap.Field(target = "type", constant = "\"push\""),
    ],
)
data class SystemMessage(val id: Long, val text: String)

data class SystemMessageDto(
    val id: Long,
    val text: String,
    val version: String,
    val type: String,
)

// ── Case 4: Nullable field with default ──────────────────────────────────────
// source.bio?: String? → target.bio: String via elvis operator
// Generates: fun Profile.toProfileDto(): ProfileDto = ProfileDto(... bio = source.bio ?: "", ...)

@AutoMap(
    target = ProfileDto::class,
    fields = [
        AutoMap.Field(target = "bio", defaultIfNull = "\"\""),
        AutoMap.Field(target = "avatarUrl", defaultIfNull = "\"https://example.com/default.png\""),
    ],
)
data class Profile(val userId: Long, val bio: String?, val avatarUrl: String?)

data class ProfileDto(val userId: Long, val bio: String, val avatarUrl: String)

// ── Case 5: Rename + nullable default combined ────────────────────────────────
// source.rawNickname? renamed to target.nickname with fallback

@AutoMap(
    target = PlayerDto::class,
    fields = [
        AutoMap.Field(target = "nickname", source = "rawNickname", defaultIfNull = "\"Anonymous\""),
    ],
)
data class Player(val id: Long, val rawNickname: String?, val score: Int)

data class PlayerDto(val id: Long, val nickname: String, val score: Int)

// ── Case 6: Auto type conversion (same field name, compatible types) ──────────
// Int → Long, Float → String — detected automatically, no annotation needed.
// Generates: count = this.count.toLong(), ratio = this.ratio.toString()

@AutoMap(target = MetricsDto::class)
data class Metrics(val label: String, val count: Int, val ratio: Float)

data class MetricsDto(val label: String, val count: Long, val ratio: String)

// ── Case 7: Explicit convert = "funcName" ────────────────────────────────────
// Calls a named conversion function on the source property value.
// Can combine with source rename: source = "priceRaw", convert = "toInt"
// Generates: price = this.priceRaw.toInt()

@AutoMap(
    target = ProductSnapshotDto::class,
    fields = [
        AutoMap.Field(target = "price", source = "priceRaw", convert = "toInt"),
    ],
)
data class ProductSnapshot(val name: String, val priceRaw: Long)

data class ProductSnapshotDto(val name: String, val price: Int)

// ── Case 8: Auto-toString (arbitrary type → String) ──────────────────────────
// Any source type → kotlin.String is handled automatically via .toString().
// CustomId is not a primitive — still auto-converts with no annotation.
// Generates: id = this.id.toString()

data class CustomId(val raw: Long) {
    override fun toString() = raw.toString()
}

@AutoMap(target = EventDto::class)
data class Event(val id: CustomId, val title: String)

data class EventDto(val id: String, val title: String)

// ── Case 9: Ignore field (target field must have default value) ───────────────
// Generates: fun Report.toReportDto(): ReportDto = ReportDto(id=..., title=...) — internal note omitted

@AutoMap(
    target = ReportDto::class,
    fields = [
        AutoMap.Field(target = "internalNote", ignore = true),
    ],
)
data class Report(val id: Long, val title: String, val internalNote: String)

data class ReportDto(val id: Long, val title: String, val internalNote: String = "")

// ── Case 10: Custom resolver (explicit custom = true) ─────────────────────────
// Generates abstract class + convenience extension with resolver as lambda:
//   fun Product.toProductDto(resolveDisplayPrice: (Product) -> String): ProductDto
// Caller supplies the lambda — no subclassing required.

@AutoMap(
    target = ProductDto::class,
    fields = [
        AutoMap.Field(target = "displayPrice", custom = true),
    ],
)
data class Product(val id: Long, val name: String, val priceInCents: Long)

data class ProductDto(val id: Long, val name: String, val displayPrice: String)

// ── Case 11: Before/after mapping hooks ──────────────────────────────────────
// beforeMap: (Source) → Source  runs before any field is read.
// afterMap:  (Target) → Target  runs after the target is fully built.
// Generates: class RawUserToSanitizedUserDtoMapper(beforeMap, afterMap) + ext fun

@AutoMap(target = SanitizedUserDto::class, beforeMap = true, afterMap = true)
data class RawUser(val name: String, val email: String)

data class SanitizedUserDto(val name: String, val email: String)

// ── Case 12: Enum auto-match by name ─────────────────────────────────────────
// Both enums have the same value names → TargetEnum.valueOf(source.field.name)
// Generates: fun Account.toAccountDto(): AccountDto = AccountDto(... status = UserStatus.valueOf(source.status.name))

enum class ApiStatus { ACTIVE, SUSPENDED, DELETED }
enum class UserStatus { ACTIVE, SUSPENDED, DELETED }

@AutoMap(target = AccountDto::class)
data class Account(val id: Long, val status: ApiStatus)

data class AccountDto(val id: Long, val status: UserStatus)

// ── Case 13: Nested object with @AutoMap (lambda delegate) ───────────────────
// When source.address has @AutoMap → target.address, a lambda constructor param is generated.
// Caller wires the lambdas at the call site.
// Generates: class OrderToOrderDtoMapper(addressMapper: (Address) -> AddressDto)

@AutoMap(target = AddressDto::class, reverse = true)
data class Address(val street: String, val city: String)

data class AddressDto(val street: String, val city: String)

@AutoMap(target = OrderDto::class)
data class Order(val orderId: Long, val address: Address)

data class OrderDto(val orderId: Long, val address: AddressDto)

// ── Case 14: Inline nested — 1 level, no @AutoMap ────────────────────────────
// Source and target nested types are compatible but NOT annotated with @AutoMap.
// AutoMap inlines the constructor call without requiring a lambda.
// Generates: fun Venue.toVenueDto(): VenueDto = VenueDto(name=..., coord=CoordDto(x=..., y=...))

data class Coord(val x: Int, val y: Int)
data class CoordDto(val x: Int, val y: Int)

@AutoMap(target = VenueDto::class)
data class Venue(val name: String, val coord: Coord)

data class VenueDto(val name: String, val coord: CoordDto)

// ── Case 15: Deep inline nested — 2+ levels, no @AutoMap anywhere ────────────
// AutoMap recurses into each level and inlines all constructors.
// Generates a single extension function — no lambdas, no subclassing.

data class City(val name: String, val zip: String)
data class CityDto(val name: String, val zip: String)

data class Location(val street: String, val city: City)
data class LocationDto(val street: String, val city: CityDto)

@AutoMap(target = StoreDto::class)
data class Store(val name: String, val location: Location)

data class StoreDto(val name: String, val location: LocationDto)

// ── Case 16: List<T> collection with @AutoMap elements ───────────────────────
// Element type Tag has @AutoMap → TagDto; AutoMap generates a lambda for each element.
// Generates: class ArticleToArticleDtoMapper(tagMapper: (Tag) -> TagDto)

@AutoMap(target = TagDto::class)
data class Tag(val name: String, val color: String)

data class TagDto(val name: String, val color: String)

@AutoMap(target = ArticleDto::class)
data class Article(val id: Long, val title: String, val tags: List<Tag>)

data class ArticleDto(val id: Long, val title: String, val tags: List<TagDto>)

// ── Case 17: Set<T> collection with @AutoMap elements ────────────────────────
// Like List but uses mapTo(linkedSetOf()) to preserve set semantics.
// Generates: class RoleToRoleDtoMapper(permissionMapper: (Permission) -> PermissionDto)

@AutoMap(target = PermissionDto::class)
data class Permission(val code: String)

data class PermissionDto(val code: String)

@AutoMap(target = RoleDto::class)
data class Role(val name: String, val permissions: Set<Permission>)

data class RoleDto(val name: String, val permissions: Set<PermissionDto>)

// ── Case 18: Map<K, V> value mapping ─────────────────────────────────────────
// Values are @AutoMap-annotated; keys pass through unchanged.
// Generates: class CatalogToCatalogDtoMapper(categoryMapper: (Category) -> CategoryDto)

@AutoMap(target = CategoryDto::class)
data class Category(val label: String)

data class CategoryDto(val label: String)

@AutoMap(target = CatalogDto::class)
data class Catalog(val name: String, val categories: Map<String, Category>)

data class CatalogDto(val name: String, val categories: Map<String, CategoryDto>)

// ── Case 19: Collection of primitives transform ───────────────────────────────
// List<Int> → List<String> auto-converts each element via .toString() — no lambda needed.
// Generates: fun Scoreboard.toScoreboardDto(): ScoreboardDto = ScoreboardDto(... scores = this.scores.map { it.toString() })

@AutoMap(target = ScoreboardDto::class)
data class Scoreboard(val name: String, val scores: List<Int>)

data class ScoreboardDto(val name: String, val scores: List<String>)

// ── Case 20: Builder pattern — companion operator invoke ─────────────────────
// Target ConfigDto has a private primary constructor; its companion operator fun invoke(...)
// acts as the factory. AutoMap detects this and uses the same call-site syntax ConfigDto(...).
// Generates: fun Config.toConfigDto(): ConfigDto = ConfigDto(host = ..., port = ...)

@AutoMap(target = ConfigDto::class)
data class Config(val host: String, val port: Int)

class ConfigDto private constructor(val host: String, val port: Int) {
    companion object {
        operator fun invoke(host: String, port: Int) = ConfigDto(host, port)
    }

    override fun toString() = "ConfigDto(host=$host, port=$port)"
}

// ── Case 21: Non-data class source ───────────────────────────────────────────
// Source can be any class — open, abstract, plain — not just data class.
// Generates: fun Device.toDeviceDto(): DeviceDto = DeviceDto(id = this.id, model = this.model)

@AutoMap(target = DeviceDto::class)
open class Device(val id: Long, val model: String)

data class DeviceDto(val id: Long, val model: String)