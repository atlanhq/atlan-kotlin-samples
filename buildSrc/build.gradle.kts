plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin.jvm:org.jetbrains.kotlin.jvm.gradle.plugin:1.9.0")
    implementation("com.diffplug.spotless:spotless-plugin-gradle:6.21.0")
}
