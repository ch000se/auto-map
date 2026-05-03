# AutoMap

[![Maven Central](https://img.shields.io/maven-central/v/io.github.ch000se/automap-core?label=Maven%20Central)](https://search.maven.org/artifact/io.github.ch000se/automap-core)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-supported-7F52FF.svg?logo=kotlin)](https://kotlinlang.org)
[![KSP](https://img.shields.io/badge/KSP-supported-3DDC84.svg)](https://github.com/google/ksp)

AutoMap is a KSP annotation processor that generates type-safe Kotlin mapper extension functions
between DTO, entity, and domain classes.

It removes repetitive constructor-copying code while keeping mapping calls explicit, statically
typed, and visible in generated Kotlin sources. There is no reflection and no runtime mapper
registry.

## Contents

- [Why AutoMap](#why-automap)
- [Documentation](#documentation)
- [Quick Start](#quick-start)
- [Generated Output](#generated-output)
- [Features](#features)
- [Artifacts](#artifacts)
- [Comparison](#comparison)
- [Requirements](#requirements)
- [Troubleshooting](#troubleshooting)
- [License](#license)

## Why AutoMap

Typical Kotlin apps contain many mechanically similar mappings:

- API DTO to domain model
- database entity to domain model
- domain model to UI model
- one layer's list of models to another layer's list of models

Hand-written mappers are simple, but they become noisy and easy to forget during model changes.
AutoMap keeps the call site as a normal extension function while making the repetitive constructor
call compiler-generated.

AutoMap is a good fit when:

- the target can be constructed from a primary constructor;
- fields mostly match by name and type;
- mismatches can be described with small annotations;
- you want generated code that can be inspected under `build/generated/ksp`.

Use a hand-written mapper when conversion contains business rules, validation, branching, or
multiple source fields feeding one target field.

## Documentation

- [Full documentation site](https://ch000se.github.io/auto-map/)
- [Installation](docs/installation.md)
- [Usage guide](docs/usage.md)
- [Compiler notes](docs/compiler.md)

## Quick Start

Add the KSP plugin and both AutoMap artifacts to the module that contains annotated models:

```kotlin
plugins {
    kotlin("jvm") // or kotlin("android")
    id("com.google.devtools.ksp") version "<ksp-version>"
}

dependencies {
    implementation("io.github.ch000se:automap-core:<automap-version>")
    ksp("io.github.ch000se:automap-compiler:<automap-version>")
}
```

Use the latest AutoMap version from Maven Central or the GitHub releases page. Use the KSP version
that matches your Kotlin compiler version.

Annotate one side of the mapping:

```kotlin
import io.github.ch000se.automap.annotations.AutoMap

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

Use the generated mapper:

```kotlin
val dto = User(1L, "Alice", "alice@example.com").toUserDto()
val dtoList = users.toUserDtoList()
```

## Generated Output

For `User -> UserDto`, AutoMap emits `UserToUserDtoMapper.kt` in the source package:

```kotlin
public fun User.toUserDto(): UserDto = UserDto(
    id = id,
    name = name,
    email = email,
)

public fun List<User>.toUserDtoList(): List<UserDto> = map { it.toUserDto() }
```

The generated file is regular Kotlin and is compiled with the rest of the module. You can inspect
it at:

```text
build/generated/ksp/<sourceSet>/kotlin
```

## Features

| Feature | Annotation / parameter | Description |
|---------|------------------------|-------------|
| [Basic mapping](docs/usage.md#1-basic-mapping) | `@AutoMap(target = ...)` | Generates `Source.toTarget()` and `List<Source>.toTargetList()` extension functions. |
| [Target-side declaration](docs/usage.md#2-target-side-declaration) | `@AutoMap(source = ...)` | Lets DTO or data-layer classes declare mappings without importing them into the domain layer. |
| [Bidirectional mapping](docs/usage.md#3-bidirectional-mapping) | `bidirectional = true` | Generates both directions from one annotation when the mapping is symmetric. |
| [Field rename](docs/usage.md#4-renamed-fields) | `@MapName` | Maps a source property to a target constructor parameter with a different name. |
| [Ignored field](docs/usage.md#5-ignored-fields) | `@MapIgnore` | Omits a source property; the target parameter must provide a default value. |
| [Class converter](docs/usage.md#class-converter) | `@MapWith(Converter::class)` | Calls custom conversion logic for one field. |
| [Lambda converter](docs/usage.md#lambda-parameter) | `@MapWith` | Adds a mapper lambda parameter when conversion depends on runtime context. |
| [Nested mapping](docs/usage.md#7-nested-mapping) | `@AutoMap` on nested types | Calls generated nested mapper functions automatically. |
| [Collection mapping](docs/usage.md#8-collections) | `List`, `Set`, `Map` | Maps list/set elements and map values when element mappings are known. |
| [Primitive conversion](docs/usage.md#9-primitive-conversions) | built in | Converts common primitive pairs such as `Int -> Long` and values to `String`. |
| [Compiler diagnostics](docs/usage.md#13-error-messages) | KSP errors | Reports missing fields, type mismatches, and unsupported bidirectional mappings at compile time. |

## Artifacts

| Artifact | Gradle configuration | Purpose |
|----------|----------------------|---------|
| `io.github.ch000se:automap-core` | `implementation(...)` | Source-retained annotations used in your code. |
| `io.github.ch000se:automap-compiler` | `ksp(...)` | KSP processor that generates mapper source files. |

`automap-core` has no mapper runtime. Generated functions are compiled into the module that owns
the annotated classes.

## Comparison

| Feature | AutoMap | Manual extension functions | Reflection mappers |
|---------|---------|----------------------------|--------------------|
| Compile-time type checking | Yes | Yes | Usually limited |
| Runtime reflection | No | No | Yes |
| Generated source is inspectable | Yes | Not generated | Usually no |
| Constructor-based data class mapping | Yes | Yes | Depends on library |
| List helper generation | Yes | Manual | Depends on library |
| Target-side annotation for clean architecture | Yes | Manual | Depends on library |
| Bidirectional generation | Yes | Manual | Usually yes |
| Custom per-field converters | Yes | Yes | Usually yes |

## Requirements

| Dependency | Requirement |
|------------|-------------|
| Kotlin | A Kotlin version supported by the KSP plugin you use. |
| KSP | The KSP plugin version matching your Kotlin compiler version. |
| JDK | JDK 17 for builds, Java 11 bytecode for library modules. |
| Android Gradle Plugin | A modern AGP version with KSP support for Android consumers. |

KSP versions are tied to Kotlin compiler versions. Keep them aligned when upgrading Kotlin.

## Troubleshooting

- Make sure `automap-core` is added with `implementation(...)` and `automap-compiler` is added with `ksp(...)`.
- Keep the KSP version aligned with the Kotlin version used by your project.
- Rebuild after annotation changes: `./gradlew clean build`.
- Inspect generated sources under `build/generated/ksp/<sourceSet>/kotlin`.
- Stop the Gradle daemon with `./gradlew --stop` if stale generated files keep appearing in the IDE.

## License

Apache 2.0. See [LICENSE](LICENSE).
