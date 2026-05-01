# AutoMap

KSP annotation processor that generates type-safe mappers between Kotlin data classes — no reflection, pure JVM/Kotlin.

## Installation

```kotlin
// build.gradle.kts
plugins {
    id("com.google.devtools.ksp") version "<ksp-version>"
}

dependencies {
    implementation("io.github.ch000se.automap:automap-core:0.1.0")
    ksp("io.github.ch000se.automap:automap-compiler:0.1.0")
}
```

## Usage

### Case 1 — direct mapping (generates extension function)

```kotlin
@AutoMap(target = UserDto::class)
data class User(val id: Long, val name: String)

data class UserDto(val id: Long, val name: String)

// Generated:
// public fun User.toUserDto(): UserDto = UserDto(id = this.id, name = this.name)

val dto = user.toUserDto()
```

### Case 2 — custom field (generates abstract class)

```kotlin
@AutoMap(
    target = ProductDto::class,
    fields = [AutoMap.Field(target = "displayPrice", custom = true)],
)
data class Product(val id: Long, val priceInCents: Long)

data class ProductDto(val id: Long, val displayPrice: String)

// Generated:
// public abstract class ProductToProductDtoMapper {
//     abstract fun resolveDisplayPrice(source: Product): String
//     fun map(source: Product): ProductDto = ProductDto(...)
// }

val mapper = object : ProductToProductDtoMapper() {
    override fun resolveDisplayPrice(source: Product) =
        "$%.2f".format(source.priceInCents / 100.0)
}
val dto = mapper.map(product)
```

### Case 3 — nested mapping (constructor-injected lambda delegate)

```kotlin
@AutoMap(target = AddressDto::class)
data class Address(val city: String)
data class AddressDto(val city: String)

@AutoMap(target = OrderDto::class)
data class Order(val id: Long, val address: Address)
data class OrderDto(val id: Long, val address: AddressDto)

// Generated:
// public abstract class OrderToOrderDtoMapper(
//     private val addressMapper: (Address) -> AddressDto,
// ) { fun map(source: Order): OrderDto = ... }

val orderMapper = OrderToOrderDtoMapper(
    addressMapper = { it.toAddressDto() },  // reuse the generated extension
)
val dto = orderMapper.map(order)
```

## `@AutoMap.Field` options

| Option | Description |
|---|---|
| `target` | Name of the target constructor parameter (required) |
| `source` | Rename: read from this source property instead |
| `constant` | Embed a Kotlin literal verbatim (e.g. `"\"hello\""`, `"42"`, `"Status.ACTIVE"`) |
| `ignore` | Skip parameter — target field must have a default value |
| `custom` | Generate an abstract `resolveXxx(source)` method |

## License

Apache 2.0 — see [LICENSE](LICENSE).