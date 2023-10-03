val jarPath = "$rootDir/jars"
val jarFile = "verification-enforcer-$version.jar"

plugins {
    id("atlan-kotlin-sample")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":events"))
    implementation("io.numaproj.numaflow:numaflow-java:0.4.8")
}

tasks {
    jar {
        archiveFileName.set(jarFile)
        destinationDirectory.set(file(jarPath))
    }
}
