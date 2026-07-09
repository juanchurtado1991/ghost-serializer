# GitHub Packages

[![Packages](https://img.shields.io/badge/GitHub_Packages-1.2.7+-blue.png?style=flat&logo=github&logoColor=white)](https://github.com/juanchurtado1991/ghost-serializer/packages)

Ghost is published to Maven Central, but we also maintain a mirror on [GitHub Packages](https://github.com/juanchurtado1991/ghost-serializer/packages) for CI/CD redundancy.

| Version | Where to resolve |
|:---|:---|
| **`1.2.7+`** | GitHub Packages (this guide) |
| **`1.2.7+`** | [Maven Central](https://central.sonatype.com/search?q=g:com.ghostserializer) (primary) |

---

## Consumer setup

### 1. Credentials

Create a [GitHub PAT](https://github.com/settings/tokens) with **`read:packages`**.

Store in `~/.gradle/gradle.properties` or project `local.properties` (**do not commit**):

```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=ghp_xxxxxxxxxxxx
```

**CI (GitHub Actions)** — `GITHUB_ACTOR` + `GITHUB_TOKEN` with `packages: read` are enough when the package lives in the same org/user.

### 2. `settings.gradle.kts`

Register GitHub Packages in **`pluginManagement`** (Gradle plugin) and **`dependencyResolutionManagement`** (libraries):

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/juanchurtado1991/ghost-serializer")
            credentials {
                username = providers.gradleProperty("gpr.user")
                    .orElse(providers.environmentVariable("GITHUB_ACTOR"))
                    .get()
                password = providers.gradleProperty("gpr.key")
                    .orElse(providers.environmentVariable("GITHUB_TOKEN"))
                    .get()
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/juanchurtado1991/ghost-serializer")
            credentials {
                username = providers.gradleProperty("gpr.user")
                    .orElse(providers.environmentVariable("GITHUB_ACTOR"))
                    .get()
                password = providers.gradleProperty("gpr.key")
                    .orElse(providers.environmentVariable("GITHUB_TOKEN"))
                    .get()
            }
        }
        mavenCentral()
        google()
    }
}
```

### 3. Version catalog

```toml
[versions]
ghost = "1.2.7"
ksp   = "2.1.10-1.0.31"

[libraries]
ghost-api           = { module = "com.ghostserializer:ghost-api", version.ref = "ghost" }
ghost-serialization = { module = "com.ghostserializer:ghost-serialization", version.ref = "ghost" }
ghost-compiler      = { module = "com.ghostserializer:ghost-compiler", version.ref = "ghost" }
ghost-ktor          = { module = "com.ghostserializer:ghost-ktor", version.ref = "ghost" }
ghost-retrofit      = { module = "com.ghostserializer:ghost-retrofit", version.ref = "ghost" }

[plugins]
ghost = { id = "com.ghostserializer.ghost", version.ref = "ghost" }
```

### 4. Gradle plugin

```kotlin
plugins {
    id("com.google.devtools.ksp") version "2.1.10-1.0.31"
    id("com.ghostserializer.ghost") version "1.2.7"
}
```

Coordinates are unchanged: `com.ghostserializer:*:1.2.7`.

### Transitive dependencies

Libraries such as **core-kmp** that depend on Ghost `1.2.7` still require the GitHub Packages repository in **`settings.gradle.kts`**, even if your app does not declare Ghost directly.

---

## Maintainer: publish to GitHub Packages

From **macOS** (includes iOS klibs). Requires PAT with **`write:packages`** and **`read:packages`**.

Publishing uses [Vanniktech Maven Publish](https://github.com/vanniktech/gradle-maven-publish-plugin) with **empty Javadoc jars** to reduce artifact file count for future Maven Central releases.

```properties
# local.properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=ghp_xxxxxxxxxxxx
signing.keyId=...
signing.password=...
signing.secretKeyRingFile=...
```

```bash
./gradlew publishToGitHubPackages --no-parallel --rerun-tasks
```

Verify: [github.com/juanchurtado1991/ghost-serializer/packages](https://github.com/juanchurtado1991/ghost-serializer/packages)

Maven Central publish (`publishToSonatype`) remains configured for when Sonatype monthly limits reset.



← [Installation](installation.md) · [Back to README](../../README.md)
