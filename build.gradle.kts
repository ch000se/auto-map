import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.dokka)
    alias(libs.plugins.detekt)
}

detekt {
    config.setFrom(files("$rootDir/detekt.yml"))
    buildUponDefaultConfig = true
    source.setFrom(
        "lib-core/src/main/kotlin",
        "lib-compiler/src/main/kotlin",
        "app/src/main/java",
        "app/src/main/kotlin",
    )
}

dokka {
    moduleName.set("AutoMap")
    dokkaPublications.html {
        outputDirectory.set(layout.buildDirectory.dir("docs/html"))
    }
    dokkaSourceSets.configureEach {
        documentedVisibilities.set(setOf(VisibilityModifier.Public))
        jdkVersion.set(17)
        skipDeprecated.set(false)
        reportUndocumented.set(false)
    }
}

dependencies {
    dokka(projects.libCore)
    dokka(projects.libCompiler)
}