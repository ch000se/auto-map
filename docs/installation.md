# Installation

## Table of Contents

- [Prerequisites](#prerequisites)
- [Add AutoMap dependencies](#add-automap-dependencies)
- [Verifying the setup](#verifying-the-setup)
- [Version compatibility](#version-compatibility)

## Prerequisites

AutoMap requires [KSP (Kotlin Symbol Processing)](https://github.com/google/ksp)
to be applied to any module that contains classes annotated with `@AutoMap`.

**1. Add the KSP plugin to your root `build.gradle.kts`:**

```kotlin
plugins {
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
}
```

> Use a KSP version that matches your Kotlin version. See the
> [KSP releases page](https://github.com/google/ksp/releases) for the mapping.

**2. Apply the plugin in the consuming module's `build.gradle.kts`:**

```kotlin
plugins {
    kotlin("jvm")           // or kotlin("android")
    id("com.google.devtools.ksp")
}
```

For Android library/app modules, also add the generated sources to the source set
(needed only on older AGP versions — modern AGP picks them up automatically):

```kotlin
android {
    sourceSets["main"].kotlin.srcDir("build/generated/ksp/main/kotlin")
}
```

## Add AutoMap Dependencies

Add the runtime annotations and the KSP compiler to your module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.ch000se:automap-core:0.1.0")
    ksp("io.github.ch000se:automap-compiler:0.1.0")
}
```

> The `core` artifact only contains the `@AutoMap` annotation — there is no
> runtime code. The `compiler` artifact must be applied via the `ksp(...)`
> configuration, not `implementation(...)`.

### Using a version catalog

If your project uses a Gradle version catalog (`gradle/libs.versions.toml`):

```toml
[versions]
automap = "0.1.0"

[libraries]
automap-core = { module = "io.github.ch000se:automap-core", version.ref = "automap" }
automap-compiler = { module = "io.github.ch000se:automap-compiler", version.ref = "automap" }
```

Then reference them in `build.gradle.kts`:

```kotlin
dependencies {
    implementation(libs.automap.core)
    ksp(libs.automap.compiler)
}
```

## Verifying the Setup

After a successful build, generated mappers appear under:

```
build/generated/ksp/main/kotlin/<package>/<SourceName>To<TargetName>Mapper.kt
```

If nothing is generated, double-check that:

1. The KSP plugin is applied to the same module that holds `@AutoMap`-annotated classes.
2. The compiler artifact is applied via `ksp(...)`, not `implementation(...)`.
3. The annotated class is reachable from the module being compiled.
4. You ran a full build, not just a recompile of the file (`./gradlew build`).

## Version Compatibility

| Dependency       | Minimum version | Notes                                                                                |
|------------------|-----------------|--------------------------------------------------------------------------------------|
| Kotlin           | 2.0+            | Tested up to 2.3                                                                     |
| KSP              | 2.0+            | Use the version that matches your Kotlin version                                     |
| JDK (build)      | 17+             | Required to run the KSP processor                                                    |
| JVM (runtime)    | 11+             | Library bytecode targets JVM 11 — works on JDK 11 and any modern Android device      |
| Android minSdk   | any             | No Android dependencies — usable in any Android module                               |
| Multiplatform    | JVM target      | Works in any KMP module with a JVM source set                                        |

The only constraint that matters for most users is the **build JDK**. If your Gradle
daemon already runs on JDK 17 (default for AGP 8+ and modern Kotlin), no version
bumps are needed.