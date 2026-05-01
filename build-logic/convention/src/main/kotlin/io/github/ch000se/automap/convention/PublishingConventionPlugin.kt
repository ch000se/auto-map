package io.github.ch000se.automap.convention

import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.dokka.gradle.DokkaExtension
import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier

class PublishingConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.vanniktech.maven.publish")
            pluginManager.apply("org.jetbrains.dokka")

            extensions.configure<MavenPublishBaseExtension> {
                publishToMavenCentral()
                if (findProperty("signingInMemoryKey") != null) {
                    signAllPublications()
                }
            }

            extensions.configure<DokkaExtension> {
                moduleName.set(name)
                dokkaSourceSets.configureEach {
                    documentedVisibilities.set(setOf(VisibilityModifier.Public))
                    jdkVersion.set(17)
                    skipDeprecated.set(false)
                    reportUndocumented.set(false)
                }
            }
        }
    }
}