val jarPath = "$rootDir/jars"
val jarFile = "events-$version.jar"

plugins {
    id("atlan-kotlin-sample")
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

dependencies {
    implementation(project(":common"))
    implementation(project(":serde"))
    api("io.numaproj.numaflow:numaflow-java:0.4.6")
    api("io.netty:netty-transport-native-epoll:4.1.79.Final:linux-x86_64")
}

tasks {
    shadowJar {
        isZip64 = true
        archiveFileName.set(jarFile)
        destinationDirectory.set(file(jarPath))
        dependencies {
            include(dependency("io.numaproj.numaflow:numaflow-java:.*"))
            include(dependency("io.netty:netty-transport-native-epoll:4.1.79.Final:linux-x86_64"))
        }
        mergeServiceFiles()
    }

    jar {
        dependsOn(shadowJar)
    }
}
