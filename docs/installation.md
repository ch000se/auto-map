# Installation

AutoMap has two artifacts:

- `automap-core` contains source-retained annotations used by your Kotlin code.
- `automap-compiler` contains the KSP processor that generates mapper functions during
  compilation.

Both artifacts must be added to every Gradle module that declares `@AutoMap` annotations.

## Requirements

| Dependency | Requirement |
|------------|-------------|
| Kotlin | A Kotlin version supported by the KSP plugin you use. |
| KSP | The KSP plugin version matching your Kotlin compiler version. |
| JDK | JDK 17 for builds. |
| Android Gradle Plugin | A modern AGP version with KSP support for Android modules. |

The published library modules target Java 11 bytecode. The build itself should run on JDK 17.

## Kotlin/JVM

```kotlin
plugins {
    kotlin("jvm") version "<kotlin-version>"
    id("com.google.devtools.ksp") version "<ksp-version>"
}

dependencies {
    implementation("io.github.ch000se:automap-core:<automap-version>")
    ksp("io.github.ch000se:automap-compiler:<automap-version>")
}
```

## Android

```kotlin
plugins {
    id("com.android.application")
    kotlin("android")
    id("com.google.devtools.ksp") version "<ksp-version>"
}

dependencies {
    implementation("io.github.ch000se:automap-core:<automap-version>")
    ksp("io.github.ch000se:automap-compiler:<automap-version>")
}
```

For Android library modules, use `com.android.library` instead of `com.android.application`.

## Version Catalog

If your project uses `gradle/libs.versions.toml`, keep AutoMap and KSP versions in the catalog:

```toml
[versions]
automap = "<automap-version>"
ksp = "<ksp-version>"

[libraries]
automap-core = { module = "io.github.ch000se:automap-core", version.ref = "automap" }
automap-compiler = { module = "io.github.ch000se:automap-compiler", version.ref = "automap" }

[plugins]
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

Then use the aliases in the consuming module:

```kotlin
plugins {
    alias(libs.plugins.ksp)
}

dependencies {
    implementation(libs.automap.core)
    ksp(libs.automap.compiler)
}
```

## Version alignment

KSP versions are tied to Kotlin compiler versions. If your project upgrades Kotlin, upgrade KSP to
the matching release as well. A mismatched KSP plugin usually fails before AutoMap runs.

## Multi-module projects

Configure AutoMap in the module that owns the annotated classes:

```kotlin
// data/build.gradle.kts
dependencies {
    implementation("io.github.ch000se:automap-core:<automap-version>")
    ksp("io.github.ch000se:automap-compiler:<automap-version>")
}
```

Generated mapper functions are compiled into that module. Downstream modules can call them by
depending on the module and importing the generated extension functions.

Clean-architecture example:

```text
:domain
  User

:data
  UserDto annotated with @AutoMap(source = User::class)
```

The domain module does not need to know about DTOs. The data module owns the mapping declaration and
receives the generated `User.toUserDto()` extension function.

## Generated source location

Generated files are written by KSP under:

```text
build/generated/ksp/<sourceSet>/kotlin
```

For example, `User -> UserDto` generates a file named `UserToUserDtoMapper.kt` in the source
package.

## Verifying Setup

After adding the dependencies, run:

```bash
./gradlew build
```

If no mapper is generated:

1. Confirm the KSP plugin is applied to the module with `@AutoMap`.
2. Confirm the compiler artifact is on `ksp(...)`, not `implementation(...)`.
3. Confirm the annotated source set is compiled by the task you are running.
4. Check `build/generated/ksp/<sourceSet>/kotlin`.

## Building this repository

```bash
./gradlew build
```

Generate API documentation:

```bash
./gradlew dokkaGenerate
```

Build the MkDocs site:

```bash
./build-scripts/build-docs.sh
```
