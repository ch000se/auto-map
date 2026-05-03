package io.github.ch000se.automap.convention

import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.kotlin.dsl.configure
import org.jetbrains.dokka.gradle.DokkaExtension
import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier
import java.util.Properties

/**
 * Gradle convention plugin that configures publication metadata and Dokka output.
 *
 * The plugin reads public Maven metadata from `gradle-public.properties`, lets project properties
 * override those defaults, configures Vanniktech Maven Publish, adds a local test repository under
 * `build/repo`, and limits Dokka documentation to public declarations.
 */
class PublishingConventionPlugin : Plugin<Project> {

    /**
     * Applies publishing and documentation defaults to [target].
     *
     * @param target Gradle project receiving this convention plugin.
     */
    override fun apply(target: Project) = with(target) {

        pluginManager.apply("com.vanniktech.maven.publish")
        pluginManager.apply("org.jetbrains.dokka")

        val publicProps = Properties().apply {
            val file = rootProject.file("gradle-public.properties")
            if (file.exists()) {
                file.inputStream().use { load(it) }
            }
        }

        fun gp(name: String, default: String? = null): String =
            findProperty(name)?.toString()
                ?: rootProject.findProperty(name)?.toString()
                ?: publicProps.getProperty(name)
                ?: default
                ?: error("Missing property: $name")

        val artifactName = project.name.replace("lib-", "automap-")

        extensions.configure<MavenPublishBaseExtension> {

            publishToMavenCentral()

            if (findProperty("signingInMemoryKey") != null) {
                signAllPublications()
            }

            coordinates(
                groupId = gp("GROUP"),
                artifactId = artifactName,
                version = gp("VERSION_NAME")
            )

            pom {

                name.set(artifactName)
                description.set(
                    when (artifactName) {
                        "automap-core" ->
                            "Core runtime APIs and annotations for AutoMap"

                        "automap-compiler" ->
                            "KSP compiler that generates mapper implementations for AutoMap"

                        else ->
                            gp("POM_DESCRIPTION")
                    }
                )

                inceptionYear.set(gp("POM_INCEPTION_YEAR"))
                url.set(gp("POM_URL"))

                licenses {
                    license {
                        name.set(gp("POM_LICENSE_NAME"))
                        url.set(gp("POM_LICENSE_URL"))
                    }
                }

                developers {
                    developer {
                        id.set(gp("POM_DEVELOPER_ID"))
                        name.set(gp("POM_DEVELOPER_NAME"))
                        email.set(gp("POM_DEVELOPER_EMAIL"))
                    }
                }

                scm {
                    connection.set(gp("POM_SCM_CONNECTION"))
                    developerConnection.set(gp("POM_SCM_DEV_CONNECTION"))
                    url.set(gp("POM_SCM_URL"))
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
                documentedVisibilities.set(
                    setOf(VisibilityModifier.Public)
                )
                jdkVersion.set(17)
                skipDeprecated.set(false)
                reportUndocumented.set(false)
            }
        }
    }
}
