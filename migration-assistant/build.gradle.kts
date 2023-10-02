val jarPath = "$rootDir/jars"
val jarFile = "migration-assistant-$version.jar"

plugins {
    id("atlan-kotlin-sample")
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

dependencies {
    implementation(project(":common"))
    implementation(project(":serde"))
    implementation("de.siegmar:fastcsv:2.2.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
}

tasks {
    shadowJar {
        isZip64 = true
        archiveFileName.set(jarFile)
        destinationDirectory.set(file(jarPath))
        dependencies {
            include(dependency("de.siegmar:fastcsv:.*"))
            include(dependency("com.fasterxml.jackson.module:jackson-module-kotlin:.*"))
            include(dependency("org.jetbrains.kotlin:kotlin-reflect:.*"))
        }
        mergeServiceFiles()
    }

    jar {
        dependsOn(shadowJar)
    }
}
