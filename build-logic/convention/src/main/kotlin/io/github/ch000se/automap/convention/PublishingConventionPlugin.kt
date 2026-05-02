package io.github.ch000se.automap.convention

import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
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

                val artifactName = project.name.replace("lib-", "automap-")

                coordinates(
                    groupId = project.findProperty("GROUP")?.toString() ?: "io.github.ch000se",
                    artifactId = artifactName,
                    version = project.findProperty("VERSION_NAME")?.toString() ?: "0.1.1"
                )

                pom {
                    name.set(artifactName)
                    description.set(project.findProperty("POM_DESCRIPTION")?.toString())
                    inceptionYear.set(project.findProperty("POM_INCEPTION_YEAR")?.toString())
                    url.set(project.findProperty("POM_URL")?.toString())

                    licenses {
                        license {
                            name.set(project.findProperty("POM_LICENSE_NAME")?.toString())
                            url.set(project.findProperty("POM_LICENSE_URL")?.toString())
                        }
                    }

                    developers {
                        developer {
                            id.set(project.findProperty("POM_DEVELOPER_ID")?.toString())
                            name.set(project.findProperty("POM_DEVELOPER_NAME")?.toString())
                            email.set(project.findProperty("POM_DEVELOPER_EMAIL")?.toString())
                        }
                    }

                    scm {
                        connection.set(project.findProperty("POM_SCM_CONNECTION")?.toString())
                        developerConnection.set(project.findProperty("POM_SCM_DEV_CONNECTION")?.toString())
                        url.set(project.findProperty("POM_SCM_URL")?.toString())
                    }
                }
            }

            extensions.configure<PublishingExtension> {
                repositories {
                    maven {
                        name = "LocalRepo"
                        url = uri(rootProject.layout.buildDirectory.dir("repo"))
                    }
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