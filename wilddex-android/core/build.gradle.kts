// Pure-Kotlin game rules and data: no Android dependencies, so this module can
// later be shared with an iPhone app through Kotlin Multiplatform.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
