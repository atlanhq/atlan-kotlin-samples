val jarPath = "$rootDir/jars"
val jarFile = "pkg-config-$version.jar"

plugins {
    id("atlan-kotlin-sample")
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

dependencies {
    implementation(project(":common"))
    implementation(project(":serde"))
    // TODO: confirm we can drop this? implementation("io.kubernetes:client-java:14.0.1")
    // TODO: confirm we can drop this? implementation(files("lib/argo-client-java-v3.4.9.jar"))
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.15.3")
}

tasks {
    shadowJar {
        isZip64 = true
        archiveFileName.set(jarFile)
        destinationDirectory.set(file(jarPath))
        dependencies {
            include(dependency("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:.*"))
        }
        mergeServiceFiles()
    }

    jar {
        dependsOn(shadowJar)
    }
}
