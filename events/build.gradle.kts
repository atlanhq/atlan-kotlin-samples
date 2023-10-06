val jarPath = "$rootDir/jars"
val jarFile = "events-$version.jar"

plugins {
    id("atlan-kotlin-sample")
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

dependencies {
    implementation(project(":common"))
    implementation(project(":serde"))
    api("io.numaproj.numaflow:numaflow-java:0.4.8")
    api("io.netty:netty-transport-native-epoll:4.1.86.Final:linux-x86_64")
    api("software.amazon.awssdk:s3:2.20.68")
}

tasks {
    shadowJar {
        isZip64 = true
        archiveFileName.set(jarFile)
        destinationDirectory.set(file(jarPath))
        dependencies {
            include(dependency("io.numaproj.numaflow:numaflow-java:.*"))
            include(dependency("io.netty:netty-transport-native-epoll:4.1.86.Final:linux-x86_64"))
            include(dependency("software.amazon.awssdk:.*:.*"))
            include(dependency("org.reactivestreams:reactive-streams:.*"))
            include(dependency("org.apache.httpcomponents:httpclient:.*"))
            include(dependency("org.apache.httpcomponents:httpcore:.*"))
            include(dependency("commons-logging:commons-logging:.*"))
            include(dependency("commons-codec:commons-codec:.*"))
        }
        mergeServiceFiles()
    }

    jar {
        dependsOn(shadowJar)
    }
}
