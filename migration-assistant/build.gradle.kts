val jarPath = "$rootDir/jars"
val jarFile = "migration-assistant-$version.jar"

plugins {
    id("atlan-kotlin-sample")
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

repositories {
    maven {
        url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
        mavenContent {
            snapshotsOnly()
        }
    }
}

dependencies {
    implementation(project(":common"))
    implementation(project(":serde"))
    implementation("com.atlan:atlan-java-samples:1.3.1-SNAPSHOT")
    implementation("de.siegmar:fastcsv:2.2.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
    implementation(project(mapOf("path" to ":serde")))
}

tasks {
    shadowJar {
        isZip64 = true
        archiveFileName.set(jarFile)
        destinationDirectory.set(file(jarPath))
        dependencies {
            // include(dependency("com.atlan:atlan-java-samples:.*"))
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
