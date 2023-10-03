val jarPath = "$rootDir/jars"
val jarFile = "asset-scorer-$version.jar"

plugins {
    id("atlan-kotlin-sample")
    // id("com.github.johnrengelman.shadow") version "7.1.2"
}

dependencies {
    implementation(project(":common"))
    implementation(project(":events"))
    implementation("io.numaproj.numaflow:numaflow-java:0.4.8")
}

/*tasks {
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
}*/

tasks {
    jar {
        archiveFileName.set(jarFile)
        destinationDirectory.set(file(jarPath))
    }
}
