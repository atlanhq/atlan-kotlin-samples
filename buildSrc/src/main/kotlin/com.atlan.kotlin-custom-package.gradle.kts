val jarPath = "$rootDir/jars"

plugins {
    kotlin("jvm")
    id("com.diffplug.spotless")
}

group = providers.gradleProperty("group").get()
version = providers.gradleProperty("version").get()

repositories {
    mavenCentral()
    maven {
        url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
        mavenContent {
            snapshotsOnly()
        }
    }
}

dependencies {
    implementation("com.atlan:atlan-java:1.7.0-SNAPSHOT")
    implementation("com.atlan:package-toolkit-runtime:1.7.0-SNAPSHOT")
    implementation("com.atlan:package-toolkit-config:1.7.0-SNAPSHOT")
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    runtimeOnly("org.apache.logging.log4j:log4j-core:2.20.0")
    runtimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl:2.20.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.8.10")
}

tasks {
    jar {
        destinationDirectory.set(file(jarPath))
    }
    test {
        useJUnitPlatform()
    }
    clean {
        delete(jarPath)
    }
}

kotlin {
    jvmToolchain(17)
}

spotless {
    kotlin {
        licenseHeaderFile("$rootDir/LICENSE_HEADER")
        ktlint()
    }
    kotlinGradle {
        ktlint()
    }
}
