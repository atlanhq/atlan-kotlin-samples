val jarPath = "$rootDir/jars"

plugins {
    id("atlan-kotlin-sample")
}

dependencies {
    implementation(project(":common"))
}

tasks {
    jar {
        destinationDirectory.set(file(jarPath))
    }
}
