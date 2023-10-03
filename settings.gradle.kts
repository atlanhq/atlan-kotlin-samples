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
include("serde")
include("events")
include("packages:api-token-connection-admin")
include("packages:duplicate-detector")
include("packages:migration-assistant")
include("packages:openapi-spec-loader")
include("pipelines:asset-scorer")
include("pipelines:verification-enforcer")