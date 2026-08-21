plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.shadow)
}

dependencies {
    // Thin project artifact — JNA/coroutines come transitively via ftdidesktop's api deps.
    implementation(projects.ftdidesktop)
}

// Demo fat jar only. Do not shade Kotlin/kotlinx (consumers must use the thin Maven artifact).
tasks.shadowJar {
    exclude("kotlin/**")
    exclude("kotlinx/**")
    exclude("META-INF/*.kotlin_module")
    exclude("META-INF/services/kotlinx.coroutines.*")
}
