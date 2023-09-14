//val jarName = "duplicate-detector.jar"
val jarPath = "$rootDir/jars"

plugins {
    kotlin("jvm") version "1.9.0"
    id("com.github.johnrengelman.shadow") version "7.1.2"
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

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

tasks {
    shadowJar {
        isZip64 = true
        manifest {
            attributes["Main-Class"] = "DuplicateDetector"
        }
        destinationDirectory.set(file(jarPath))
        //archiveFileName.set(jarName)
        dependencies {
            include(dependency("org.jetbrains.kotlin:.*:.*"))
            include(dependency("io.github.microutils:kotlin-logging-jvm:.*"))
            include(dependency("org.slf4j:slf4j-simple:.*"))
        }
        mergeServiceFiles()
    }

    jar {
        dependsOn(shadowJar)
    }

    clean {
        delete(jarPath)
    }
}