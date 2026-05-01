import org.gradle.accessors.dm.LibrariesForLibs
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

val catalog = the<LibrariesForLibs>()

group = "io.github.ch000se.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(files(catalog.javaClass.superclass.protectionDomain.codeSource.location))
    compileOnly(libs.plugin.kotlin.jvm)
    compileOnly(libs.plugin.android.application)
    compileOnly(libs.plugin.kotlin.compose)
    compileOnly(libs.plugin.maven.publish)
    compileOnly(libs.plugin.dokka)
}

tasks {
    validatePlugins {
        enableStricterValidation = true
        failOnWarning = true
    }
}

gradlePlugin {
    plugins {
        register("kotlinLibrary") {
            id = "automap.kotlin.library"
            implementationClass = "io.github.ch000se.automap.convention.KotlinLibraryConventionPlugin"
        }
        register("publishing") {
            id = "automap.publishing"
            implementationClass = "io.github.ch000se.automap.convention.PublishingConventionPlugin"
        }
        register("androidApplication") {
            id = "automap.android.application"
            implementationClass = "io.github.ch000se.automap.convention.AndroidApplicationConventionPlugin"
        }
        register("androidApplicationCompose") {
            id = "automap.android.application.compose"
            implementationClass = "io.github.ch000se.automap.convention.AndroidApplicationComposeConventionPlugin"
        }
    }
}