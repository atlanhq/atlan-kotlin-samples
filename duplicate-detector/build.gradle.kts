val jarPath = "$rootDir/jars"

plugins {
    id("atlan-kotlin-sample")
}

tasks {
    jar {
        destinationDirectory.set(file(jarPath))
    }
}
