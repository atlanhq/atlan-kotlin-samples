val jarPath = "$rootDir/jars"
val jarFile = "serde-$version.jar"

plugins {
    id("atlan-kotlin-sample")
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

dependencies {
    implementation(project(":common"))
    api("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
}

tasks {
    shadowJar {
        isZip64 = true
        archiveFileName.set(jarFile)
        destinationDirectory.set(file(jarPath))
        dependencies {
            include(dependency("org.jetbrains.kotlin:kotlin-reflect:.*"))
            include(dependency("com.fasterxml.jackson.module:jackson-module-kotlin:.*"))
        }
        mergeServiceFiles()
    }

    jar {
        dependsOn(shadowJar)
    }
}
