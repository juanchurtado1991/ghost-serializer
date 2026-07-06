import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar

allprojects {
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

        val dokkaJavadocJar = tasks.register<Jar>("dokkaJavadocJar") {
            archiveClassifier.set("javadoc")

            val dokkaTask = tasks.named("dokkaGenerate")
            dependsOn(dokkaTask)
            from(dokkaTask.map { it.outputs.files })
        }

        tasks.matching { it.name == "dokkaGenerate" }.configureEach {
            dependsOn(tasks.matching { it.name.startsWith("ksp") })
        }


        afterEvaluate {
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
                        description.set("Production-ready, zero-allocation serialization engine for Kotlin Multiplatform.")
                        url.set(PublishConstants.GITHUB_REPO_URL)
                        
                        licenses {
                            license {
                                name.set("The Apache License, Version 2.0")
                                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                            }
                        }
                        developers {
                            developer {
                                id.set("ghost")
                                name.set("Juan Hurtado")
                                email.set("juan1402.91@gmail.com")
                            }
                        }
                        scm {
                            connection.set("scm:git:git://github.com/juanchurtado1991/GhostSerialization.git")
                            developerConnection.set("scm:git:ssh://github.com/juanchurtado1991/GhostSerialization.git")
                            url.set(PublishConstants.GITHUB_REPO_URL)
                        }
                    }
                }

                repositories {
                    maven {
                        name = PublishConstants.GITHUB_PACKAGES_REPO_NAME
                        url = uri(PublishConstants.GITHUB_PACKAGES_URL)
                        credentials {
                            username = project.findProperty(PublishConstants.GPR_USER_PROPERTY) as String?
                                ?: System.getenv(PublishConstants.GITHUB_ACTOR_ENV)
                                ?: ""
                            password = project.findProperty(PublishConstants.GPR_TOKEN_PROPERTY) as String?
                                ?: System.getenv(PublishConstants.GITHUB_TOKEN_ENV)
                                ?: ""
                        }
                    }
                }
            }

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

            rootProject.tasks.named(PublishConstants.PUBLISH_TO_GITHUB_PACKAGES_TASK).configure {
                dependsOn(tasks.named("publishAllPublicationsToGitHubPackagesRepository"))
            }
        }
    }
}

private object PublishConstants {
    const val GITHUB_REPO_URL = "https://github.com/juanchurtado1991/GhostSerialization"
    const val GITHUB_PACKAGES_URL = "https://maven.pkg.github.com/juanchurtado1991/GhostSerialization"
    const val GITHUB_PACKAGES_REPO_NAME = "GitHubPackages"
    const val GPR_USER_PROPERTY = "gpr.user"
    const val GPR_TOKEN_PROPERTY = "gpr.key"
    const val GITHUB_ACTOR_ENV = "GITHUB_ACTOR"
    const val GITHUB_TOKEN_ENV = "GITHUB_TOKEN"
    const val PUBLISH_TO_GITHUB_PACKAGES_TASK = "publishToGitHubPackages"
}
