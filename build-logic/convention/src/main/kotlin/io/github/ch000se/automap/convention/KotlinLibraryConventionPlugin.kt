package io.github.ch000se.automap.convention

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/**
 * Gradle convention plugin that applies shared Kotlin/JVM library settings.
 *
 * Library modules use Java 11 bytecode, a Java 17 toolchain, explicit Kotlin API mode, and warnings
 * as errors so published APIs stay intentional.
 */
class KotlinLibraryConventionPlugin : Plugin<Project> {
    /**
     * Applies Kotlin/JVM library defaults to [target].
     *
     * @param target Gradle project receiving this convention plugin.
     */
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("java-library")
            pluginManager.apply("org.jetbrains.kotlin.jvm")

            extensions.configure<JavaPluginExtension> {
                sourceCompatibility = JavaVersion.VERSION_11
                targetCompatibility = JavaVersion.VERSION_11
            }

            extensions.configure<KotlinJvmProjectExtension> {
                jvmToolchain(17)
                explicitApi()
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_11)
                    allWarningsAsErrors.set(true)
                }
            }
        }
    }
}
