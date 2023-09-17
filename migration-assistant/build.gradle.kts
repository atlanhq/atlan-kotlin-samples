val jarPath = "$rootDir/jars"

plugins {
    id("atlan-kotlin-sample")
}

dependencies {
    implementation(project(":common"))
    implementation("com.atlan:atlan-java-samples:1.3.1-SNAPSHOT")
}

tasks {
    jar {
        destinationDirectory.set(file(jarPath))
    }
}
