# Usage Guide

AutoMap exposes five annotations:

| Annotation | Target | Purpose |
|------------|--------|---------|
| `@AutoMap` | class | Declares a `Source -> Target` mapper pair. |
| `@Flatten` | property / value parameter | Marks a composite source property for auto-flatten lookup. |
| `@MapName` | property / value parameter | Matches a source property to a differently named target parameter. |
| `@MapIgnore` | property / value parameter | Skips a source property when the target has a default value. |
| `@MapWith` | property / value parameter | Uses custom conversion logic for a single property. |

For every mapping pair, the processor emits `<Source>To<Target>Mapper.kt` in the source package.
Generated functions are public extension functions.

## Mapping Model

AutoMap always normalizes declarations into a `Source -> Target` mapping:

- `@AutoMap(target = Target::class)` means the annotated class is the source.
- `@AutoMap(source = Source::class)` means the annotated class is the target.

The target must have a primary constructor. Every target constructor parameter is resolved from the
source class by name, annotation, or conversion rule.

The generated API is intentionally small:

```kotlin
fun Source.toTarget(...optionalConverters): Target
fun List<Source>.toTargetList(...optionalConverters): List<Target>
```

## 1. Basic Mapping

Use `@AutoMap(target = Target::class)` when the annotated class is the source type.

```kotlin
@AutoMap(target = UserDto::class)
data class User(
    val id: Long,
    val name: String,
    val email: String,
)

data class UserDto(
    val id: Long,
    val name: String,
    val email: String,
)
```

Generated:

```kotlin
public fun User.toUserDto(): UserDto = UserDto(
    id = id,
    name = name,
    email = email,
)

public fun List<User>.toUserDtoList(): List<UserDto> = map { it.toUserDto() }
```

Target classes must have a primary constructor. AutoMap matches target constructor parameters by
name.

Resolution order for a basic field:

1. source property with `@MapName` matching the target parameter;
2. source property with the same name and no `@MapName`;
3. target default value, when the target parameter cannot be resolved;
4. compile-time error.

## 2. Target-Side Declaration

Use `@AutoMap(source = Source::class)` when the annotated class is the target type.

This keeps domain models free from data-layer imports:

```kotlin
// domain module
data class User(val id: Long, val name: String)

// data module
@AutoMap(source = User::class)
data class UserDto(val id: Long, val name: String)
```

Generated output is still source-oriented:

```kotlin
public fun User.toUserDto(): UserDto = UserDto(id = id, name = name)
public fun List<User>.toUserDtoList(): List<UserDto> = map { it.toUserDto() }
```

## 3. Bidirectional Mapping

Set `bidirectional = true` to generate both directions from one annotation:

```kotlin
@AutoMap(target = UserDto::class, bidirectional = true)
data class User(
    val id: Long,
    val name: String,
)

data class UserDto(
    val id: Long,
    val name: String,
)
```

Generated:

```kotlin
public fun User.toUserDto(): UserDto = UserDto(id = id, name = name)
public fun List<User>.toUserDtoList(): List<UserDto> = map { it.toUserDto() }

public fun UserDto.toUser(): User = User(id = id, name = name)
public fun List<UserDto>.toUserList(): List<User> = map { it.toUser() }
```

Bidirectional mapping is intentionally strict. `@MapWith`, `@MapName`, `@MapIgnore`, and other
asymmetric mapping annotations are rejected because AutoMap cannot prove the reverse mapping is
correct.

Target-side bidirectional declaration works as well:

```kotlin
data class User(val id: Long, val name: String)

@AutoMap(source = User::class, bidirectional = true)
data class UserDto(val id: Long, val name: String)
```

## 4. Renamed Fields

Use `@MapName` when a source property and target constructor parameter represent the same value but
use different names:

```kotlin
@AutoMap(target = ContactDto::class)
data class Contact(
    val id: Long,
    @MapName("displayName") val fullName: String,
)

data class ContactDto(
    val id: Long,
    val displayName: String,
)
```

Generated:

```kotlin
public fun Contact.toContactDto(): ContactDto = ContactDto(
    id = id,
    displayName = fullName,
)
```

## 5. Ignored Fields

Use `@MapIgnore` when a source property should not be passed to the target constructor.

The target parameter must have a default value:

```kotlin
@AutoMap(target = UserDto::class)
data class User(
    val id: Long,
    @MapIgnore val passwordHash: String,
)

data class UserDto(
    val id: Long,
    val passwordHash: String = "",
)
```

Generated:

```kotlin
public fun User.toUserDto(): UserDto = UserDto(id = id)
```

If the target parameter has no default, AutoMap reports a compile-time error.

## 6. Custom Conversion

Use `@MapWith` when source and target field types do not match or when a field needs explicit
conversion.

### Function Converter

Pass a function name when the conversion is stateless and should be known at compile time. AutoMap
supports same-package top-level functions, fully qualified top-level functions, and object function
references:

```kotlin
@AutoMap(target = ProductDto::class)
data class Product(
    val id: Long,
    @MapWith("centsToDollars") val priceInCents: Long,
)

fun centsToDollars(value: Long): String =
    "$" + "%.2f".format(value / 100.0)

data class ProductDto(
    val id: Long,
    val priceInCents: String,
)
```

Generated:

```kotlin
public fun Product.toProductDto(): ProductDto = ProductDto(
    id = id,
    priceInCents = centsToDollars(priceInCents),
)
```

Use `@MapWith(fn = "com.example.mapping.centsToDollars")` when an explicit argument name reads
better. Converter functions must accept exactly one parameter compatible with the source field and
return a type compatible with the target field.

### Lambda Parameter

Leave `@MapWith` empty when conversion depends on runtime context:

```kotlin
@AutoMap(target = ProductDto::class)
data class Product(
    val id: Long,
    @MapWith val priceInCents: Long,
)

data class ProductDto(
    val id: Long,
    val priceInCents: String,
)
```

Generated:

```kotlin
public fun Product.toProductDto(
    priceInCentsMapper: (Long) -> String,
): ProductDto = ProductDto(
    id = id,
    priceInCents = priceInCentsMapper(priceInCents),
)
```

Caller:

```kotlin
product.toProductDto { cents -> "$" + "%.2f".format(cents / 100.0) }
```

## 7. Nested Mapping

If a source property and target parameter have different object types, AutoMap can call another
generated mapper when that source type is also annotated:

```kotlin
@AutoMap(target = AddressDto::class)
data class Address(val street: String, val city: String)

data class AddressDto(val street: String, val city: String)

@AutoMap(target = OrderDto::class)
data class Order(val id: Long, val address: Address)

data class OrderDto(val id: Long, val address: AddressDto)
```

Generated:

```kotlin
public fun Order.toOrderDto(): OrderDto = OrderDto(
    id = id,
    address = address.toAddressDto(),
)
```

Nullable nested types map with safe calls when both sides are nullable:

```kotlin
address = address?.toAddressDto()
```

## 8. Collections

AutoMap handles `List`, `Set`, and `Map` when the source and target collection types match and the
element or value mapping is known.

```kotlin
@AutoMap(target = TagDto::class)
data class Tag(val name: String)

data class TagDto(val name: String)

@AutoMap(target = ArticleDto::class)
data class Article(val tags: List<Tag>)

data class ArticleDto(val tags: List<TagDto>)
```

Generated:

```kotlin
public fun Article.toArticleDto(): ArticleDto = ArticleDto(
    tags = tags.map { it.toTagDto() },
)
```

Rules:

- `List<S>` maps to `List<T>` with `map { ... }`.
- `List<S>?` maps to `List<T>?` with `?.map { ... }`.
- `Set<S>` maps to `Set<T>` with `map { ... }.toSet()`.
- `Set<S>?` maps to `Set<T>?` with `?.map { ... }?.toSet()`.
- `Map<K, S>` maps to `Map<K, T>` with `mapValues { ... }`.
- Map keys are not converted; only map values are converted.

Primitive conversions also work inside supported collection elements and map values.

## 9. Primitive Conversions

AutoMap applies a small set of safe built-in conversions:

| Source | Target | Generated suffix |
|--------|--------|------------------|
| `Byte` | `Short` | `.toShort()` |
| `Byte` | `Int` | `.toInt()` |
| `Byte` | `Long` | `.toLong()` |
| `Short` | `Int` | `.toInt()` |
| `Short` | `Long` | `.toLong()` |
| `Int` | `Long` | `.toLong()` |
| `Int` | `String` | `.toString()` |
| `Long` | `String` | `.toString()` |
| `Boolean` | `String` | `.toString()` |
| `Enum` | `String` | `.name` |
| `Float` | `Double` | `.toDouble()` |
| `Float` | `String` | `.toString()` |
| `Double` | `String` | `.toString()` |
| `Char` / numeric / `Boolean` primitives | `String` | `.toString()` |
| `Enum` | `String` | `.name` |

Nullable primitive widening works when the target is nullable, for example `Int? -> Long?`
generates `value?.toLong()`. AutoMap does not perform narrowing conversions such as `Long -> Int`,
`Double -> Float`, or `String -> Int`. It also does not convert arbitrary objects or collections to
`String` implicitly. For those cases, add `@MapWith`.

## 10. Auto-flatten Mapping

Auto-flatten mapping lets a composite source model populate a flat target constructor. This is useful
for Room projection and `@Embedded`-style models.

Enable flattening for all composite source properties with `flatten = true`:

```kotlin
@AutoMap(target = Note::class, flatten = true)
data class NoteWithAuthorDbModel(
    val noteDbModel: NoteDbModel,
    val authorDbModel: AuthorDbModel,
)

data class NoteDbModel(
    val id: Int,
    val title: String,
)

data class AuthorDbModel(
    val authorName: String,
)

data class Note(
    val id: Int,
    val title: String,
    val authorName: String,
)
```

Generated:

```kotlin
fun NoteWithAuthorDbModel.toNote(): Note = Note(
    id = noteDbModel.id,
    title = noteDbModel.title,
    authorName = authorDbModel.authorName,
)
```

Use `@Flatten` when only selected source properties should be searched:

```kotlin
@AutoMap(target = Note::class)
data class NoteWithAuthorDbModel(
    @Flatten val note: NoteDbModel,
    @Flatten val author: AuthorDbModel,
    val debugName: String,
)
```

Flatten lookup is recursive up to a fixed depth and only enters user-defined composite classes. It
does not recurse into primitives, `String`, collections, maps, arrays, enums, or Kotlin/Java
standard library types.

Conflict behavior is strict. If a top-level source field and a flattened field both match a target
parameter, AutoMap reports an ambiguous mapping instead of choosing silently. If multiple flattened
paths match, AutoMap reports all candidates.

Use `@MapName("targetName")` on the exact top-level source property when you need to resolve a
flatten conflict explicitly.

Troubleshooting:

- If AutoMap reports multiple flattened candidates, disable global `flatten = true` and use
  `@Flatten` only on the intended property.
- If a top-level and flattened field conflict, add `@MapName` to the top-level source property you
  want or remove flattening from that mapping.
- `@MapWith` is supported on top-level source properties. For custom conversion of a nested field,
  expose a top-level property annotated with `@MapWith` or map that case manually.

## 11. Resolution Rules

For each target constructor parameter, AutoMap chooses the first matching strategy:

1. explicit inverse rename for generated reverse mappings;
2. source property annotated with `@MapName("targetName")`;
3. source property with the same name, unless flattening creates an ambiguity;
4. flattened property path when `flatten = true` or `@Flatten` enables lookup;
5. `@MapIgnore`, if the target parameter has a default value;
6. `@MapWith("functionName")` function converter;
7. `@MapWith` lambda converter;
8. exact type match, including generic arguments and nullability;
9. built-in primitive conversion;
10. nested generated mapper;
11. supported collection mapper;
12. target default value;
13. compile-time error.

AutoMap does not silently guess business logic. If no rule applies, the processor reports a KSP
error and lists source candidates.

## 12. Nullability

Exact type matching includes nullability. A nullable source value is not automatically passed into a
non-null target parameter.

Use `@MapWith` for null handling:

```kotlin
fun displayName(value: String?): String = value ?: "Anonymous"

@AutoMap(target = UserDto::class)
data class User(@MapWith("displayName") val name: String?)

data class UserDto(val name: String)
```

## 13. Default Values

Default target constructor values are used only when AutoMap omits the argument:

```kotlin
@AutoMap(target = SessionDto::class)
data class Session(val id: Long)

data class SessionDto(
    val id: Long,
    val createdBy: String = "system",
)
```

Generated:

```kotlin
public fun Session.toSessionDto(): SessionDto = SessionDto(id = id)
```

If a same-named source property exists, AutoMap maps it instead of using the target default.

## 14. Error Messages

Missing target field:

```text
Cannot map field "displayName" in ContactDto.

Source candidates:
  - id: kotlin.Long
  - fullName: kotlin.String

Fix:
  1. Add @MapName("displayName") on the matching source property
  2. Use @MapWith for custom mapping
  3. Change target type to match source
```

Ignored source without target default:

```text
Source property 'passwordHash' is @MapIgnore but target param 'passwordHash' has no default
```

Unsupported bidirectional mapping:

```text
@AutoMap(bidirectional = true) cannot be used because field 'priceInCents' has @MapWith / @MapIgnore which has no automatic inverse.
```

Flatten conflict:

```text
Cannot infer flattened mapping for target field "id".

Multiple candidates found:
- note.id: kotlin.Int
- user.id: kotlin.Int

Fix:
1. Add @MapName("id") to the correct source property
2. Disable flatten = true and use @Flatten only on the intended property
3. Rename one of the fields
```

Duplicate mapping:

```text
Duplicate AutoMap mapping detected.

Mapping:
- Source -> Target

Fix:
1. Keep only one @AutoMap for this pair
2. Change the target type
3. Remove duplicate mapper
```

Duplicate converter:

```text
Duplicate converter detected for kotlin.Long -> kotlin.String.

Converters:
- formatPrice
- longToString
```

## 15. List Variant Control

By default, AutoMap generates both a single-object extension and a list helper:

```kotlin
fun Source.toTarget(): Target
fun List<Source>.toTargetList(): List<Target>
```

Disable the list helper when you want a smaller public API:

```kotlin
@AutoMap(target = Target::class, generateListVariant = false)
data class Source(val id: Int)
```

Generated:

```kotlin
fun Source.toTarget(): Target
```

`toTargetList()` is not emitted for that mapping.

## 16. Generated File Naming

For `Source -> Target`, the generated file is:

```text
<Source>To<Target>Mapper.kt
```

Examples:

| Mapping | Generated file | Generated function |
|---------|----------------|--------------------|
| `User -> UserDto` | `UserToUserDtoMapper.kt` | `User.toUserDto()` |
| `OrderEntity -> Order` | `OrderEntityToOrderMapper.kt` | `OrderEntity.toOrder()` |
| `PersonDto -> Person` | `PersonDtoToPersonMapper.kt` | `PersonDto.toPerson()` |

For bidirectional mappings, both directions are emitted into the same file.

## 17. FAQ

### Does AutoMap use reflection?

No. AutoMap generates Kotlin source files through KSP. Calls are normal extension functions compiled
with the rest of your project.

### Can AutoMap map private properties?

Generated functions are top-level functions in the source package. They can only access properties
that are visible from that generated file.

### Can AutoMap map multiple source fields into one target field?

Not automatically. Use `@MapWith` when one source property can be converted into the target value,
or write a manual mapper when the value depends on multiple source properties.

### Can AutoMap map sealed classes or polymorphic hierarchies?

No. AutoMap is constructor-based and maps concrete classes. Use a hand-written `when` expression for
sealed or polymorphic dispatch.

## 18. When Not To Use AutoMap

Use a hand-written mapper when:

- Mapping requires validation, branching, or business rules.
- The target cannot be constructed through a primary constructor.
- Conversion depends on multiple source properties at once.
- You need polymorphic or sealed-class dispatch.
- The mapping is one-off and clearer as a normal function.
