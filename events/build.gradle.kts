val jarPath = "$rootDir/jars"
val jarFile = "events-$version.jar"

plugins {
    id("atlan-kotlin-sample")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":serde"))
    implementation("io.numaproj.numaflow:numaflow-java:0.4.8")
}

tasks {
    jar {
        archiveFileName.set(jarFile)
        destinationDirectory.set(file(jarPath))
    }
}
