import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar

allprojects {
    group = "com.ghostserializer"
    version = "1.1.1"

    // Load local.properties if it exists
    val localProperties = java.util.Properties()
    val localPropertiesFile = project.rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localProperties.load(localPropertiesFile.inputStream())
        localProperties.forEach { (key, value) ->
            project.extensions.extraProperties.set(key as String, value)
        }
    }
}

subprojects {
    val isPublishable = name.startsWith("ghost") && 
                       !name.contains("sample") && 
                       !name.contains("benchmark") && 
                       !name.contains("integration-test")

    if (isPublishable) {
        apply(plugin = "maven-publish")
        apply(plugin = "signing")
        apply(plugin = "org.jetbrains.dokka")

        // Create Javadoc JAR using Dokka 2.x
        val dokkaJavadocJar = tasks.register<Jar>("dokkaJavadocJar") {
            archiveClassifier.set("javadoc")
            val dokkaHtmlTask = tasks.matching { it.name == "dokkaHtml" }
            from(dokkaHtmlTask)
        }

        afterEvaluate {
            // JVM-only modules
            if (!plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")) {
                configure<JavaPluginExtension> {
                    withSourcesJar()
                }

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

            // Publication configuration
            configure<PublishingExtension> {
                publications.withType<MavenPublication>().configureEach {
                    artifact(dokkaJavadocJar)

                    if (name == "kotlinMultiplatform" && project.name == "ghost-serialization") {
                        artifactId = "ghost-serialization"
                    }

                    pom {
                        val rawName = project.name.removePrefix("ghost-")
                        val displayName = if (rawName == "serialization") {
                            "Ghost Serialization"
                        } else {
                            "Ghost Serialization ${rawName.split("-").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }}"
                        }
                        
                        name.set(displayName)
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
                                name.set("Juan Carlos Hurtado Giammattei")
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
            }

            // Signing configuration
            configure<SigningExtension> {
                val keyId = project.findProperty("signing.keyId") as String?
                val password = project.findProperty("signing.password") as String?
                val secretKeyRingFile = project.findProperty("signing.secretKeyRingFile") as String?
                
                if (keyId != null && password != null && secretKeyRingFile != null) {
                    sign(extensions.getByType<PublishingExtension>().publications)
                }
            }

            tasks.withType<AbstractPublishToMaven>().configureEach {
                dependsOn(dokkaJavadocJar)
            }

            val signingTasks = tasks.withType<Sign>()
            tasks.withType<AbstractPublishToMaven>().configureEach {
                mustRunAfter(signingTasks)
            }
        }
    }
}
