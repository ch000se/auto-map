# Compiler Notes

This page describes how `automap-compiler` processes annotations and what to check when debugging
generated code.

## Processing Flow

The KSP provider creates one `AutoMapSymbolProcessor` for the compilation. Each processing round:

1. Finds classes annotated with `@AutoMap`.
2. Normalizes each declaration into a `Source -> Target` mapping job.
3. Builds a registry of known mapping pairs for nested and collection resolution.
4. Generates mapper extension functions with `MapperGenerator`.

## Normalization

Users can declare a mapping from either side:

```kotlin
@AutoMap(target = UserDto::class)
data class User(...)
```

or:

```kotlin
@AutoMap(source = User::class)
data class UserDto(...)
```

Internally, both forms become:

```text
sourceClass = User
targetClass = UserDto
```

Exactly one of `target` or `source` must be provided.

## Incremental Dependencies

Mapper source files are isolating: each generated mapper depends on its source and target files,
plus directly known mapper files that can affect nested calls. The documentation and metadata
artifacts are aggregating because they list all mappings generated in the module.

## Mapper Registry

Before generating files, the processor builds a registry:

```text
source FQN -> target FQN -> generated function FQN
```

The registry includes current-module mappings and dependency-module metadata discovered from
generated AutoMap metadata. For bidirectional mappings, the reverse pair is added as well. The
generator uses this registry to decide when it can emit calls such as:

```kotlin
address.toAddressDto()
tags.map { it.toTagDto() }
```

Each module that generates mappers also emits:

```text
META-INF/automap/mappings
```

and a compile-time metadata source under `io.github.ch000se.automap.generated`. Downstream KSP runs
read the generated metadata through KSP symbol resolution, so nested mapper calls can cross Gradle
module boundaries without runtime reflection.

## Diagnostics

Mapping failures are reported as KSP compiler errors. The diagnostic usually includes:

- the target field that could not be mapped;
- available source candidates;
- suggested fixes such as `@MapName`, `@MapWithFn`, or lambda `@MapWith`.

Example:

```text
Cannot map field "displayName" in UserDto.

Source candidates:
  - id: kotlin.Long
  - fullName: kotlin.String

Fix:
  1. Add a source property named "displayName"
  2. Add @MapName on the matching source or target property
  3. Add a default value in the target constructor
  4. Use @MapWithFn for custom mapping
```

## Public API Surface

The compiler artifact exposes only the KSP entry points needed by service loading:

- `AutoMapSymbolProcessorProvider`
- `AutoMapSymbolProcessor`

The generator, mapping descriptors, and exceptions are internal implementation details.

## Incremental KSP

Regular mapper files are emitted as isolating outputs. Each generated mapper declares its source and
target files, plus the AutoMap declaration files discovered in the same round so nested mapper
availability cannot become stale during incremental builds. The documentation-only
`AutoMapMappers.kt` file is aggregating because it lists every mapping pair discovered in the
processing run.

## Debug Checklist

1. Confirm generated files exist under `build/generated/ksp/<sourceSet>/kotlin`.
2. Open the generated mapper and inspect the constructor call.
3. Check that `@MapWithFn` or legacy string `@MapWith` functions accept exactly one source value and return the target type.
4. Check that target constructor parameters have defaults when a source field is ignored.
5. Check that nested source types also declare `@AutoMap`.
6. Run a clean build if generated sources look stale.
