# Usage Guide

## Installation

```kotlin
// settings.gradle.kts
plugins { id("com.google.devtools.ksp") version "<version>" }

// module build.gradle.kts
dependencies {
    implementation("io.github.ch000se.automap:automap-core:<version>")
    ksp("io.github.ch000se.automap:automap-compiler:<version>")
}
```

## Strategy selection

For each target constructor parameter the processor selects a strategy in order:

1. **Explicit `@AutoMap.Field`** annotation for this parameter → see field options below.
2. **Direct** — source has a property with the same name and same type.
3. **Nested** — source property's type is annotated `@AutoMap(target = TargetFieldType::class)`.
4. **Collection** — target is `List<T>` or `Set<T>` and element type has `@AutoMap`.
5. **Ignore** — target parameter has a default value (parameter is simply omitted).
6. **Auto-custom** — none of the above; an abstract `resolveXxx(source)` method is generated.

## Output form

| Condition | Generated form |
|---|---|
| Only direct / renamed / constant / ignore strategies | `public fun Source.toTarget(): Target = Target(...)` |
| Any custom, nested, or collection strategy | `public abstract class SourceToTargetMapper(...) { fun map(source): Target }` |

## `@AutoMap.Field` options

```kotlin
@AutoMap(
    target = TargetClass::class,
    fields = [
        AutoMap.Field(target = "fieldName", source  = "sourceField"),  // rename
        AutoMap.Field(target = "fieldName", constant = "\"literal\""), // constant
        AutoMap.Field(target = "fieldName", ignore   = true),          // skip (needs default)
        AutoMap.Field(target = "fieldName", custom   = true),          // abstract resolver
    ],
)
```

Only one of `source` / `constant` / `ignore` / `custom` may be set per `Field`.

## Nested mapping

```kotlin
@AutoMap(target = AddressDto::class)
data class Address(val city: String, val zip: String)
data class AddressDto(val city: String, val zip: String)

@AutoMap(target = UserDto::class)
data class User(val id: Long, val address: Address)
data class UserDto(val id: Long, val address: AddressDto)
```

Generated for `User → UserDto`:
```kotlin
public abstract class UserToUserDtoMapper(
    private val addressMapper: (Address) -> AddressDto,
) {
    public fun map(source: User): UserDto = UserDto(
        id = source.id,
        address = addressMapper(source.address),
    )
}
```

Usage:
```kotlin
val mapper = UserToUserDtoMapper(addressMapper = { it.toAddressDto() })
val dto = mapper.map(user)
```

## Collection mapping

```kotlin
@AutoMap(target = TagDto::class)
data class Tag(val name: String)
data class TagDto(val name: String)

@AutoMap(target = PostDto::class)
data class Post(val id: Long, val tags: List<Tag>)
data class PostDto(val id: Long, val tags: List<TagDto>)
```

Generated for `Post → PostDto`:
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