import java.util.*

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.shadow) apply false
}

// Load secrets from local.properties (not committed) if present
val localProperties: Properties by lazy {
    val props = Properties()
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { props.load(it) }
    props
}

subprojects {
    plugins.withId("maven-publish") {
        the<org.gradle.api.publish.PublishingExtension>().repositories {
            maven {
                name = "staging"
                url = uri(rootProject.layout.buildDirectory.dir("staging-deploy"))
            }
        }
    }
}
