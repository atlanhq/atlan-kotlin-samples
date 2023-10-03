val jarPath = "$rootDir/jars"
val jarFile = "events-$version.jar"

plugins {
    id("atlan-kotlin-sample")
    // id("com.github.johnrengelman.shadow") version "7.1.2"
}

dependencies {
    implementation(project(":common"))
    implementation(project(":serde"))
    implementation("io.numaproj.numaflow:numaflow-java:0.4.8")
}

/*tasks {
    shadowJar {
        isZip64 = true
        archiveFileName.set(jarFile)
        destinationDirectory.set(file(jarPath))
        dependencies {
            include(dependency("io.numaproj.numaflow:numaflow-java:.*"))
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
