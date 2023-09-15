val jarPath = "$rootDir/jars"
val jarFile = "atlan-kotlin-samples-common-$version.jar"

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

        archiveFileName.set(jarFile)
        destinationDirectory.set(file(jarPath))
        dependencies {
            include(dependency("org.jetbrains.kotlin:.*:.*"))
            include(dependency("io.github.microutils:kotlin-logging-jvm:.*"))
            include(dependency("org.apache.logging.log4j:log4j-api:2.20.0"))
            include(dependency("org.apache.logging.log4j:log4j-core:2.20.0"))
            include(dependency("org.apache.logging.log4j:log4j-slf4j2-impl:2.20.0"))
        }
        mergeServiceFiles()
    }

    jar {
        dependsOn(shadowJar)
        // Necessary to avoid log4j falling back to a non-performant way to walk the stack
        manifest {
            attributes(Pair("Multi-Release", "true"))
        }
    }
}
