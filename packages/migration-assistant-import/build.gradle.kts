val jarPath = "$rootDir/jars"
val jarFile = "migration-assistant-import-$version.jar"

plugins {
    id("com.atlan.kotlin-custom-package")
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

dependencies {
    implementation("de.siegmar:fastcsv:2.2.2")
}

tasks {
    shadowJar {
        isZip64 = true
        archiveFileName.set(jarFile)
        destinationDirectory.set(file(jarPath))
        dependencies {
            include(dependency("de.siegmar:fastcsv:.*"))
        }
        mergeServiceFiles()
    }

    jar {
        dependsOn(shadowJar)
    }
}
