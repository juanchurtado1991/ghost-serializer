import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar

// Coordinates and Project Info
allprojects {
    group = "com.ghostserializer"
    version = "1.1.0"

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

// Configuration for the Nexus Publishing plugin (Staging Management)
extensions.findByName("nexusPublishing")?.let { _ ->
    val configureAction: Action<Any> = Action {
        val repositoriesMethod = this.javaClass.getMethod("repositories", Action::class.java)
        repositoriesMethod.invoke(this, Action<Any> {
            val sonatypeMethod = this.javaClass.getMethod("sonatype", Action::class.java)
            sonatypeMethod.invoke(this, Action<Any> {
                val setNexusUrl = this.javaClass.getMethod("getNexusUrl")
                val setSnapshotUrl = this.javaClass.getMethod("getSnapshotRepositoryUrl")
                val getUsername = this.javaClass.getMethod("getUsername")
                val getPassword = this.javaClass.getMethod("getPassword")
                val getPackageGroup = this.javaClass.getMethod("getPackageGroup")

                (setNexusUrl.invoke(this) as Property<java.net.URI>).set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
                (setSnapshotUrl.invoke(this) as Property<java.net.URI>).set(uri("https://ossrh-staging-api.central.sonatype.com/content/repositories/snapshots/"))
                (getUsername.invoke(this) as Property<String>).set(project.findProperty("sonatypeUsername") as String?)
                (getPassword.invoke(this) as Property<String>).set(project.findProperty("sonatypePassword") as String?)
                (getPackageGroup.invoke(this) as Property<String>).set("com.ghostserializer")
            })
        })
    }
    project.extensions.configure("nexusPublishing", configureAction)
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
            from(tasks.named("dokkaHtml"))
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
                    // Attach Dokka Javadoc JAR
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

            // Fix for implicit dependencies and signing order
            tasks.withType<AbstractPublishToMaven>().configureEach {
                dependsOn(dokkaJavadocJar)
            }
            
            // Resolve signing order issues
            val signingTasks = tasks.withType<Sign>()
            tasks.withType<AbstractPublishToMaven>().configureEach {
                mustRunAfter(signingTasks)
            }
        }
    }
}
