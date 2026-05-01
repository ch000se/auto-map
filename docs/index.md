# AutoMap

**AutoMap** is a KSP annotation processor that generates type-safe mappers between Kotlin `data class`es.

- No reflection at runtime
- Pure Kotlin/JVM — works in any Kotlin project (Android, server, multiplatform JVM)
- Three mapping modes: extension function, abstract class with custom resolver, or constructor-injected delegate

## Quick start

```kotlin
@AutoMap(target = UserDto::class)
data class User(val id: Long, val name: String)
data class UserDto(val id: Long, val name: String)

// use the generated extension:
val dto = user.toUserDto()
```

See the [Usage Guide](usage.md) for all options.