# AutoMap

[![Maven Central](https://img.shields.io/maven-central/v/io.github.ch000se/automap-core.svg?label=Maven%20Central&color=dark-green)](https://central.sonatype.com/search?q=g:io.github.ch000se+a:automap-*)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![PR Check](https://github.com/ch000se/auto-map/actions/workflows/pr-check.yml/badge.svg)](https://github.com/ch000se/auto-map/actions/workflows/pr-check.yml)
[![Publish](https://github.com/ch000se/auto-map/actions/workflows/publish.yml/badge.svg)](https://github.com/ch000se/auto-map/actions/workflows/publish.yml)

Auto-generate type-safe Kotlin mappers between data classes with a single annotation.
Stop writing repetitive `fun toDto()` boilerplate — let the compiler do it.

- **No reflection** — pure code generation, all calls resolved at compile time.
- **Pure Kotlin/JVM** — works on Android, server, and JVM-targeted multiplatform modules.
- **Smart strategy selection** — direct copy, type conversion, nested, collection, enum-by-name, custom resolver.
- **Compile-time validation** — wrong field names, missing strategies, and type mismatches fail the build.

<!-- docs-exclude-start -->
## Documentation

Full documentation is available at [https://ch000se.github.io/auto-map/](https://ch000se.github.io/auto-map/).
<!-- docs-exclude-end -->

## Quick Start

Add dependencies to your module's `build.gradle.kts`:

```kotlin
plugins {
    id("com.google.devtools.ksp") version "<ksp-version>"
}

dependencies {
    implementation("io.github.ch000se:automap-core:0.1.0")
    ksp("io.github.ch000se:automap-compiler:0.1.0")
}
```

> Requires [KSP](https://github.com/google/ksp). See [Installation](docs/installation.md) for full setup.

Annotate your source class:

```kotlin
@AutoMap(target = UserDto::class)
data class User(val id: Long, val name: String)

data class UserDto(val id: Long, val name: String)
```

The processor generates a top-level extension function:

```kotlin
public fun User.toUserDto(): UserDto = UserDto(
    id = this.id,
    name = this.name,
)
```

Use it from anywhere:

```kotlin
val dto: UserDto = user.toUserDto()
```

## Features

| Feature                                              | Annotation / parameter         | Description                                                                          |
|------------------------------------------------------|--------------------------------|--------------------------------------------------------------------------------------|
| [Basic mapping](docs/usage.md)                       | `@AutoMap(target = ...)`       | Generates an extension function copying fields by name and type                      |
| [Field rename](docs/usage.md#rename)                 | `Field(source = ...)`          | Read from a different source property                                                |
| [Constant value](docs/usage.md#constant)             | `Field(constant = "...")`      | Embed a Kotlin literal verbatim                                                      |
| [Default if null](docs/usage.md#default-if-null)     | `Field(defaultIfNull = "...")` | Elvis fallback for nullable source                                                   |
| [Skip field](docs/usage.md#ignore)                   | `Field(ignore = true)`         | Omit the parameter — the target must declare a default value                         |
| [Custom resolver](docs/usage.md#custom)              | `Field(custom = true)`         | Generate an `abstract fun resolveXxx(source)` to fill in by hand                     |
| [Type conversion](docs/usage.md#type-conversion)     | _automatic_                    | Auto-widens primitives (Int → Long, Float → String, …) and `Any → String`            |
| [Inline conversion call](docs/usage.md#convert)      | `Field(convert = "fnName")`    | Calls a no-arg function on the source value (e.g. `.toString()`)                     |
| [Nested mapping](docs/usage.md#nested)               | _automatic_                    | When source field's type has its own `@AutoMap` to the target field's type           |
| [Collection mapping](docs/usage.md#collections)      | _automatic_                    | `List<T>` / `Set<T>` / `Map<K, V>` whose element / value type has `@AutoMap`         |
| [Enum-by-name](docs/usage.md#enums)                  | _automatic_                    | Maps between two different enum types by `name`                                      |
| [Reverse mapping](docs/usage.md#reverse)             | `reverse = true`               | Also generates the target → source mapper                                            |
| [Hooks](docs/usage.md#hooks)                         | `beforeMap` / `afterMap`       | Inject `(Source) -> Source` and `(Target) -> Target` lambdas                         |

## Strategy Resolution Order

For each target constructor parameter, AutoMap selects a strategy in this order — first match wins:

1. **Explicit `@AutoMap.Field`** for the parameter.
2. **Direct** — same name, same type.
3. **Type conversion** — same name, auto-widening primitive or `toString`.
4. **Nested** — source property's type has `@AutoMap` to the target field's type.
5. **Enum-by-name** — both sides are different enum types.
6. **Collection (annotated elements)** — element type has `@AutoMap`.
7. **Collection conversion** — element type can be auto-converted.
8. **Inline nested** — recursive structural match (up to depth 10) without `@AutoMap`.
9. **Ignore** — target parameter has a default value.
10. **Auto-custom** — none of the above; `abstract resolveXxx(source)` is generated.

See [Strategy Selection](docs/usage.md#strategy-selection) for the full reference.

## Generated Output Forms

| Condition                                                                  | Generated form                                                |
|----------------------------------------------------------------------------|---------------------------------------------------------------|
| Only direct / renamed / constant / ignored / converted strategies          | Top-level `public fun Source.toTarget(): Target`              |
| Any custom, nested, collection, or hook strategy                           | `abstract class SourceToTargetMapper(...)` + extension helper |

## Troubleshooting

If you encounter KSP errors after changing annotations or dependencies:

1. **Stop the Gradle daemon** and rebuild:
   ```bash
   ./gradlew --stop
   ```

2. **Clean and rebuild:**
   In Android Studio: **Build → Clean Project**, then **Build → Rebuild Project**.
   From the command line:
   ```bash
   ./gradlew clean assembleDebug
   ```

Stale KSP caches are a common cause of spurious build failures.

## Requirements

| Dependency       | Minimum version | Notes                                                                                |
|------------------|-----------------|--------------------------------------------------------------------------------------|
| Kotlin           | 2.0+            | Tested up to 2.3                                                                     |
| KSP              | 2.0+            | Use the version that matches your Kotlin version                                     |
| JDK (build)      | 17+             | Required to **run** the KSP processor (the Gradle daemon JDK)                        |
| JVM (runtime)    | 11+             | Library bytecode targets JVM 11 — works on JDK 11 and any modern Android device      |
| Android minSdk   | any             | The library has **no Android dependencies** — pure JVM, usable in any Android module |
| Multiplatform    | JVM target      | Works in any Kotlin Multiplatform module that has a JVM source set                   |

> **Tip:** the only constraint that affects most users is the **build JDK** — if your
> Gradle daemon already runs on JDK 17 (default for AGP 8+ and modern Kotlin), you're
> ready to go without any other version bumps.

## License

```
Copyright 2026 Sviatoslav Chaikivskyi

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```

See [LICENSE](LICENSE) for details.