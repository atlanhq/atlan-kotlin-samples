val jarPath = "$rootDir/jars"

plugins {
    kotlin("jvm")
    id("com.diffplug.spotless")
}

group = "com.atlan"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.atlan:atlan-java:1.4.1")
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    runtimeOnly("org.apache.logging.log4j:log4j-core:2.20.0")
    runtimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl:2.20.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.8.10")
}

tasks {
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
