pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "automap"

val publicProps = java.util.Properties().apply {
    val file = rootDir.resolve("gradle-public.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
gradle.beforeProject {
    publicProps.forEach { (k, v) ->
        if (!project.hasProperty(k.toString())) {
            project.extensions.extraProperties[k.toString()] = v.toString()
        }
    }
}

include(":lib-core")
include(":lib-compiler")
include(":app-example")
project(":app-example").projectDir = file("app")