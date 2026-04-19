import org.gradle.api.publish.maven.MavenPublication

// coordinates
allprojects {
    group = "com.ghostserializer"
    version = "1.1.0"
}

subprojects {
    // Only publish library modules
    if (name.startsWith("serialization") && name != "serialization-sample") {
        apply(plugin = "maven-publish")
        apply(plugin = "signing")

        // Specialized for JVM-only projects like the compiler
        afterEvaluate {
            if (!plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")) {
                configure<PublishingExtension> {
                    publications {
                        val javaComponent = components.findByName("java")
                        if (javaComponent != null) {
                            create<MavenPublication>("maven") {
                                from(javaComponent)
                                artifactId = project.name
                            }
                        }
                    }
                }
            }
        }

        configure<PublishingExtension> {
            publications.withType<MavenPublication>().configureEach {
                // Ensure the core module is named 'serialization'
                if (name == "kotlinMultiplatform" && project.name == "serialization") {
                    artifactId = "serialization"
                }

                pom {
                    name.set("Ghost Serialization ${project.name}")
                    description.set("Industrial-grade, zero-allocation serialization engine for Kotlin Multiplatform.")
                    url.set("https://github.com/juanchurtado1991/GhostSerialization")
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    developers {
                        developer {
                            id.set("ghost")
                            name.set("Ghost Serialization Team")
                            email.set("juan1402.91@gmail.com")
                        }
                    }
                    scm {
                        connection.set("scm:git:git://github.com/juanchurtado1991/GhostSerialization.git")
                        developerConnection.set("scm:git:ssh://github.com/juanchurtado1991/GhostSerialization.git")
                        url.set("https://github.com/juanchurtado1991/GhostSerialization")
                    }
                }
            }
            repositories {
                maven {
                    name = "OSSRH"
                    url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
                    credentials {
                        username = project.findProperty("sonatypeUsername") as String?
                        password = project.findProperty("sonatypePassword") as String?
                    }
                }
            }
        }

        // Signing logic
        configure<SigningExtension> {
            val keyId = project.findProperty("signing.keyId") as String?
            val password = project.findProperty("signing.password") as String?
            val secretKeyRingFile = project.findProperty("signing.secretKeyRingFile") as String?
            
            if (keyId != null && password != null && secretKeyRingFile != null) {
                sign(extensions.getByType<PublishingExtension>().publications)
            }
        }
    }
}
