# Usage Guide

A complete reference for the `@AutoMap` annotation and every supported strategy.

## Table of Contents

- [Basic Mapping](#basic-mapping)
- [Field Annotation Reference](#field-annotation-reference)
  - [Rename](#rename)
  - [Constant](#constant)
  - [Default if null](#default-if-null)
  - [Ignore](#ignore)
  - [Custom](#custom)
  - [Convert](#convert)
- [Type Conversion](#type-conversion)
- [Nested Mapping](#nested)
- [Collections](#collections)
- [Enums](#enums)
- [Reverse Mapping](#reverse)
- [Hooks](#hooks)
- [Strategy Selection](#strategy-selection)
- [Generated Output Forms](#generated-output-forms)
- [Validation Errors](#validation-errors)

## Basic Mapping

Annotate a source class with `@AutoMap`, pointing it at the target class:

```kotlin
@AutoMap(target = UserDto::class)
data class User(val id: Long, val name: String)

data class UserDto(val id: Long, val name: String)
```

**Generated code:**

```kotlin
public fun User.toUserDto(): UserDto = UserDto(
    id = this.id,
    name = this.name,
)
```

The processor walks the **target's primary constructor** parameters and, for each, picks
the best strategy from the source. When all parameters resolve to a simple direct copy
(or another straightforward strategy), the result is a single top-level extension function.

> Both classes must be either `data class` or have a public primary constructor.
> The target may also expose a `companion operator fun invoke(...)` factory; AutoMap
> will use it as a fallback when the primary constructor is not directly usable.

## Field Annotation Reference

`@AutoMap.Field` overrides the automatic strategy selection for a single target parameter:

```kotlin
public annotation class Field(
    val target: String,           // required: name of the target ctor parameter
    val source: String = "",      // rename: read from this source property
    val constant: String = "",    // embed a Kotlin literal verbatim
    val defaultIfNull: String = "", // Elvis fallback for nullable source
    val ignore: Boolean = false,  // skip — target must declare a default value
    val custom: Boolean = false,  // generate abstract resolveXxx(source)
    val convert: String = "",     // call a no-arg function on the source value
)
```

**Mutual exclusivity rules:**

- Only one of `source` / `constant` / `defaultIfNull` / `ignore` / `custom` may be set per field.
  - Exception: `source` may combine with `defaultIfNull` (rename + nullable fallback).
- `convert` may combine with `source` (rename + convert) but **not** with
  `ignore`, `custom`, `constant`, or `defaultIfNull`.

### Rename

Use `source` to read from a differently-named property:

```kotlin
@AutoMap(
    target = UserDto::class,
    fields = [AutoMap.Field(target = "fullName", source = "name")],
)
data class User(val id: Long, val name: String)

data class UserDto(val id: Long, val fullName: String)
```

**Generated code:**

```kotlin
public fun User.toUserDto(): UserDto = UserDto(
    id = this.id,
    fullName = this.name,
)
```

### Constant

Embed a Kotlin literal verbatim — useful for tagging mapped DTOs with a discriminator:

```kotlin
@AutoMap(
    target = EventDto::class,
    fields = [AutoMap.Field(target = "type", constant = "\"USER_CREATED\"")],
)
data class UserCreated(val userId: Long)

data class EventDto(val userId: Long, val type: String)
```

**Generated code:**

```kotlin
public fun UserCreated.toEventDto(): EventDto = EventDto(
    userId = this.userId,
    type = "USER_CREATED",
)
```

`constant` accepts any Kotlin expression — quoted strings, numbers, enum references
(`"Status.ACTIVE"`), function calls, etc. The string is inlined as written.

### Default if Null

Use `defaultIfNull` to provide an Elvis fallback when the source value is nullable:

```kotlin
@AutoMap(
    target = UserDto::class,
    fields = [AutoMap.Field(target = "name", defaultIfNull = "\"Anonymous\"")],
)
data class User(val id: Long, val name: String?)

data class UserDto(val id: Long, val name: String)
```

**Generated code:**

```kotlin
public fun User.toUserDto(): UserDto = UserDto(
    id = this.id,
    name = this.name ?: "Anonymous",
)
```

Combine with `source` to rename and provide a fallback:

```kotlin
AutoMap.Field(target = "displayName", source = "nickname", defaultIfNull = "\"Guest\"")
```

### Ignore

Skip a parameter entirely — the target must declare a default value for it:

```kotlin
@AutoMap(
    target = UserDto::class,
    fields = [AutoMap.Field(target = "createdAt", ignore = true)],
)
data class User(val id: Long, val name: String)

data class UserDto(
    val id: Long,
    val name: String,
    val createdAt: Instant = Instant.now(),
)
```

**Generated code:**

```kotlin
public fun User.toUserDto(): UserDto = UserDto(
    id = this.id,
    name = this.name,
    // createdAt omitted — uses the default value
)
```

The compiler emits an error if the ignored target parameter has no default value.

### Custom

Generate an abstract resolver method to fill in by hand:

```kotlin
@AutoMap(
    target = ProductDto::class,
    fields = [AutoMap.Field(target = "displayPrice", custom = true)],
)
data class Product(val id: Long, val priceInCents: Long)

data class ProductDto(val id: Long, val displayPrice: String)
```

**Generated code:**

```kotlin
public abstract class ProductToProductDtoMapper {
    public abstract fun resolveDisplayPrice(source: Product): String

    public fun map(source: Product): ProductDto = ProductDto(
        id = source.id,
        displayPrice = resolveDisplayPrice(source),
    )
}
```

**Usage:**

```kotlin
val mapper = object : ProductToProductDtoMapper() {
    override fun resolveDisplayPrice(source: Product): String =
        "$%.2f".format(source.priceInCents / 100.0)
}
val dto = mapper.map(product)
```

### Convert

Call a no-arg function on the source value:

```kotlin
@AutoMap(
    target = LogDto::class,
    fields = [AutoMap.Field(target = "id", source = "uuid", convert = "toString")],
)
data class LogEntry(val uuid: UUID, val message: String)

data class LogDto(val id: String, val message: String)
```

**Generated code:**

```kotlin
public fun LogEntry.toLogDto(): LogDto = LogDto(
    id = this.uuid.toString(),
    message = this.message,
)
```

## Type Conversion

For matching field names, AutoMap automatically widens common primitive types and falls back
to `toString()` when the target is `String`:

| Source type      | Target type | Result                |
|------------------|-------------|-----------------------|
| `Int`            | `Long`      | `value.toLong()`      |
| `Float`          | `Double`    | `value.toDouble()`    |
| `Int` / `Long`   | `String`    | `value.toString()`    |
| Any              | `String`    | `value.toString()`    |

```kotlin
@AutoMap(target = ResponseDto::class)
data class Response(val statusCode: Int, val timestamp: Long)

data class ResponseDto(val statusCode: Long, val timestamp: String)
```

**Generated code:**

```kotlin
public fun Response.toResponseDto(): ResponseDto = ResponseDto(
    statusCode = this.statusCode.toLong(),
    timestamp = this.timestamp.toString(),
)
```

## Nested

When a source field's type has its own `@AutoMap` to the target field's type, the processor
generates an abstract mapper with a constructor-injected lambda:

```kotlin
@AutoMap(target = AddressDto::class)
data class Address(val city: String, val zip: String)
data class AddressDto(val city: String, val zip: String)

@AutoMap(target = UserDto::class)
data class User(val id: Long, val address: Address)
data class UserDto(val id: Long, val address: AddressDto)
```

**Generated code (User → UserDto):**

```kotlin
public abstract class UserToUserDtoMapper(
    private val addressMapper: (Address) -> AddressDto,
) {
    public fun map(source: User): UserDto = UserDto(
        id = source.id,
        address = addressMapper(source.address),
    )
}

public fun User.toUserDto(addressMapper: (Address) -> AddressDto): UserDto =
    object : UserToUserDtoMapper(addressMapper) {}.map(this)
```

**Usage:**

```kotlin
val dto = user.toUserDto(addressMapper = { it.toAddressDto() })
```

Reusing the generated extension for the nested type keeps things composable — no
manual mapper instances needed.

## Collections

`List`, `Set`, and `Map` whose element / value type has `@AutoMap` are supported the same way:

```kotlin
@AutoMap(target = TagDto::class)
data class Tag(val name: String)
data class TagDto(val name: String)

@AutoMap(target = PostDto::class)
data class Post(val id: Long, val tags: List<Tag>)
data class PostDto(val id: Long, val tags: List<TagDto>)
```

**Generated code:**

```kotlin
public abstract class PostToPostDtoMapper(
    private val tagMapper: (Tag) -> TagDto,
) {
    public fun map(source: Post): PostDto = PostDto(
        id = source.id,
        tags = source.tags.map { tagMapper(it) },
    )
}
```

For `List<Int>` / `Set<Int>` of primitives, AutoMap performs element-level type
conversion automatically when the target element type is a wider primitive or `String`.

## Enums

When source and target are different enum types, AutoMap maps by `name`:

```kotlin
enum class Status { ACTIVE, INACTIVE, PENDING }
enum class StatusDto { ACTIVE, INACTIVE, PENDING }

@AutoMap(target = UserDto::class)
data class User(val id: Long, val status: Status)
data class UserDto(val id: Long, val status: StatusDto)
```

**Generated code:**

```kotlin
public fun User.toUserDto(): UserDto = UserDto(
    id = this.id,
    status = StatusDto.valueOf(this.status.name),
)
```

> Both enums must declare entries with matching names. Unknown names throw
> `IllegalArgumentException` at runtime — this is by design.

## Reverse

Set `reverse = true` to also generate the target → source mapper:

```kotlin
@AutoMap(target = UserDto::class, reverse = true)
data class User(val id: Long, val name: String)

data class UserDto(val id: Long, val name: String)
```

This generates **both** `User.toUserDto()` and `UserDto.toUser()`.

> Reverse uses **automatic strategy selection only** — `@AutoMap.Field` overrides do
> not apply in reverse. For asymmetric mappings, annotate the target class
> separately with its own `@AutoMap`.

## Hooks

Inject pre- and post-processing lambdas with `beforeMap` / `afterMap`:

```kotlin
@AutoMap(target = UserDto::class, beforeMap = true, afterMap = true)
data class User(val id: Long, val name: String)
```

**Generated code:**

```kotlin
public class UserToUserDtoMapper(
    private val beforeMap: (User) -> User,
    private val afterMap: (UserDto) -> UserDto,
) {
    public fun map(source: User): UserDto {
        val transformed = beforeMap(source)
        val result = UserDto(
            id = transformed.id,
            name = transformed.name,
        )
        return afterMap(result)
    }
}
```

**Usage:**

```kotlin
val mapper = UserToUserDtoMapper(
    beforeMap = { it.copy(name = it.name.trim()) },
    afterMap  = { it.copy(name = it.name.uppercase()) },
)
val dto = mapper.map(user)
```

## Strategy Selection

For each target constructor parameter, AutoMap evaluates strategies in this order
and the **first match wins**:

1. **Explicit `@AutoMap.Field`** — overrides everything below.
2. **Direct** — same name and exact type.
3. **Type conversion** — same name, auto-widening primitive or `Any → String`.
4. **Nested** — source property's type has `@AutoMap(target = TargetFieldType::class)`.
5. **Enum-by-name** — both source and target are different enum types.
6. **Collection (annotated)** — `List<T>` / `Set<T>` / `Map<K, V>` with element / value
   type carrying `@AutoMap`.
7. **Collection conversion** — `List<T>` / `Set<T>` of primitives with auto-detectable
   element conversion.
8. **Inline nested** — recursive structural match (up to depth 10) when the field type
   has no `@AutoMap` but every nested parameter still resolves cleanly.
9. **Ignore** — target parameter has a default value.
10. **Auto-custom** — none of the above apply; an `abstract resolveXxx(source)` method
    is emitted as a last resort.

> Inline nested matches happen silently. If you want an explicit error instead, give
> the type its own `@AutoMap` and let the processor enforce that contract.

## Generated Output Forms

| Condition                                                                  | Generated form                                                |
|----------------------------------------------------------------------------|---------------------------------------------------------------|
| Only direct / renamed / constant / converted / ignored strategies          | Top-level `public fun Source.toTarget(): Target`              |
| Any custom strategy (explicit or auto)                                     | `abstract class SourceToTargetMapper { ... }` + extension     |
| Any nested / collection / enum / hook strategy                             | `class SourceToTargetMapper(...)` + extension helper          |

When a mapper class is generated, AutoMap emits **two files**:

- `SourceToTargetMapper.kt` — the mapper class itself.
- `SourceToTargetMapperExt.kt` — a `Source.toTarget(...)` extension that constructs
  the mapper and delegates to its `map` function. The extension takes the same
  constructor parameters (mapper lambdas, hooks).

## Validation Errors

The processor rejects invalid configurations with a clear compile-time error:

| Error                                                                                                                            | Cause                                                                            |
|----------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------|
| `@AutoMap target must resolve to a class`                                                                                        | The `target` argument is not a class                                             |
| `@AutoMap target ... must have a public primary constructor with at least one parameter or a companion operator fun invoke(...)` | The target has no usable constructor or factory                                  |
| `@AutoMap on {Class}: duplicate Field.target entries: {names}`                                                                   | The same target parameter is annotated more than once                            |
| `@AutoMap.Field target='X': convert cannot be combined with ignore/custom/constant/defaultIfNull`                                | Conflicting options on the same `Field`                                          |
| `@AutoMap.Field target='X': only one of ignore/custom/constant/defaultIfNull may be set`                                         | Multiple exclusive options on the same `Field`                                   |
| `@AutoMap.Field target='X' has no strategy configured`                                                                           | A `Field` annotation with no values                                              |
| `@AutoMap.Field ignore=true for 'X' requires a default value in {TargetClass}`                                                   | Ignored a parameter that has no default                                          |
| `@AutoMap.Field source='X' not found in {SourceClass}`                                                                           | Renamed source property doesn't exist                                            |