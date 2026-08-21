plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
    id("com.vanniktech.maven.publish") version "0.34.0"
    signing
}

group = "net.tactware.ftdi"
version = "0.1.3"

dependencies {
    // Consumers need these on the classpath (thin jar; not shaded).
    api(libs.jna)
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.atomicfu)

    // Used internally only; callers do not need Swing dispatchers.
    implementation(libs.kotlinx.coroutines.swing)
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates(project.group as String, "ftdidesktop", project.version as String)

    pom {
        name.set("KotlinFTD2XX")
        description.set("Kotlin JVM JNA wrapper for the FTDI D2XX driver with coroutines support")
        inceptionYear.set("2025")
        url.set("https://github.com/TactWareInc/KotlinFTD2XX")

        licenses {
            license {
                name.set("MIT")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("https://opensource.org/licenses/MIT")
            }
        }

        developers {
            developer {
                id.set("kmbisset89")
                name.set("Kerry Bisset")
                url.set("https://github.com/kmbisset89")
            }
        }

        scm {
            url.set("https://github.com/TactWareInc/KotlinFTD2XX")
            connection.set("scm:git:git://github.com/TactWareInc/KotlinFTD2XX.git")
            developerConnection.set("scm:git:ssh://git@github.com/TactWareInc/KotlinFTD2XX.git")
        }
    }
}

signing {
    val signingKey = (findProperty("signingKey") as String?)
        ?: (findProperty("signing.key") as String?)
    val signingPassword = (findProperty("signingPassword") as String?)
        ?: (findProperty("signing.password") as String?)
    if (!signingKey.isNullOrBlank()) {
        useInMemoryPgpKeys(signingKey, signingPassword.orEmpty())
    }
}

tasks.named("publish").configure {
    dependsOn(tasks.named("build"))
}
