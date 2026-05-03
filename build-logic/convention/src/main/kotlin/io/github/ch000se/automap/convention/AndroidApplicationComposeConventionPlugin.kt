package io.github.ch000se.automap.convention

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Gradle convention plugin that adds Compose support to an Android application module.
 *
 * It builds on [AndroidApplicationConventionPlugin] by applying the shared Android defaults first,
 * then enabling Compose build features and the Kotlin Compose compiler plugin.
 */
class AndroidApplicationComposeConventionPlugin : Plugin<Project> {
    /**
     * Applies Android and Compose defaults to [target].
     *
     * @param target Gradle project receiving this convention plugin.
     */
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("automap.android.application")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            extensions.configure<ApplicationExtension> {
                buildFeatures.compose = true
            }
        }
    }
}
