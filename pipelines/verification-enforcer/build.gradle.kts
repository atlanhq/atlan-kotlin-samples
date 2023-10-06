val jarPath = "$rootDir/jars"
val jarFile = "verification-enforcer-$version.jar"

plugins {
    id("atlan-kotlin-sample")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":events"))
}

tasks {
    jar {
        archiveFileName.set(jarFile)
        destinationDirectory.set(file(jarPath))
    }
}
