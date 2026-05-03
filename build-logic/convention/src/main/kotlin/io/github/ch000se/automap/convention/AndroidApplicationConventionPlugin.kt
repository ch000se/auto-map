package io.github.ch000se.automap.convention

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Gradle convention plugin that applies baseline Android application configuration.
 *
 * The plugin configures the Android application plugin, Java 17 source/target compatibility, and
 * the release build type used by the sample application module.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    /**
     * Applies Android application defaults to [target].
     *
     * @param target Gradle project receiving this convention plugin.
     */
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")

            extensions.configure<ApplicationExtension> {
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }

                buildTypes {
                    getByName("release") {
                        isMinifyEnabled = false
                        proguardFiles(
                            getDefaultProguardFile("proguard-android-optimize.txt"),
                            "proguard-rules.pro",
                        )
                    }
                }
            }
        }
    }
}
