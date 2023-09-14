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
    implementation("com.atlan:atlan-java:1.2.2")
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("org.slf4j:slf4j-simple:2.0.7")
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
