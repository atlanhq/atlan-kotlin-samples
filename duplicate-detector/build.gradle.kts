val jarPath = "$rootDir/jars"

plugins {
    id("atlan-kotlin-sample")
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

tasks {
    shadowJar {
        isZip64 = true
        manifest {
            attributes["Main-Class"] = "DuplicateDetector"
        }
        destinationDirectory.set(file(jarPath))
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