pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}

rootProject.name = "atlan-kotlin-samples"
include("common")
include("api-token-connection-admin")
include("duplicate-detector")
include("migration-assistant")
include("openapi-spec-loader")