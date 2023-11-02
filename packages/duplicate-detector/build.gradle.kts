val jarPath = "$rootDir/jars"

plugins {
    id("atlan-kotlin-sample")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":serde"))
    implementation(project(":pkg-config"))
}

tasks {
    jar {
        destinationDirectory.set(file(jarPath))
    }
}
