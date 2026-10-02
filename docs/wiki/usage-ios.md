# Usage — iOS (Native / Swift)

[![iOS](https://img.shields.io/badge/iOS-000000.png?style=flat&logo=apple&logoColor=white)](usage-ios.md)

Ghost generates a pre-compiled **XCFramework** that Swift consumes as a regular Apple framework. Kotlin/Native has no `ServiceLoader`, so since 1.4.0 the Ghost Gradle plugin links every `@GhostSerialization` model to its serializer at compile time (a Kotlin compiler plugin plus associated objects): `Ghost.deserialize` works on iOS with no registration code.

Start with the shared-module setup in the [Quick Start](quick-start.md), then add the XCFramework export below.

---

## 1. Create a KMP Module with XCFramework Export

```kotlin
// shared/build.gradle.kts
plugins {
    kotlin("multiplatform")
    id("com.google.devtools.ksp") version "2.3.12"
    id("com.ghostserializer.ghost") version "1.4.0"
}

kotlin {
    val xcf = XCFramework("SharedUtils")
    iosArm64 {
        binaries.framework {
            baseName = "SharedUtils"
            xcf.add(this)
            export("com.ghostserializer:ghost-serialization:1.4.0")
        }
    }
    iosSimulatorArm64 {
        binaries.framework {
            baseName = "SharedUtils"
            xcf.add(this)
            export("com.ghostserializer:ghost-serialization:1.4.0")
        }
    }

    sourceSets {
        commonMain.dependencies {
            api("com.ghostserializer:ghost-api:1.4.0")
            api("com.ghostserializer:ghost-serialization:1.4.0")
        }
    }
}

ksp {
    arg("ghost.moduleName", "shared_utils")
    // Optional for byte-only modules: reduce generated code per DTO.
    // arg("ghost.textChannel", "false")
}
```

---

## 2. Annotate Models in `commonMain`

```kotlin
// shared/src/commonMain/kotlin/model/User.kt
import com.ghost.serialization.GhostSerialization

@GhostSerialization
data class User(
    val id: Long,
    val name: String,
    val email: String,
    val roles: List<String>
)

@GhostSerialization
data class Product(
    val id: String,
    val name: String,
    val price: Double,
    val inStock: Boolean
)
```

---

## 3. Optional: Prewarm Through a Swift-Accessible Bridge

Serializers resolve lazily on first use, with no setup. To pay that cost at launch instead, or to use the name-based APIs (`getSerializerByName`, `getAllSerializers`), register the KSP-generated registry once. `prewarm()` can only warm the modules it knows about:

```kotlin
// shared/src/iosMain/kotlin/GhostBridge.kt
import com.ghost.serialization.Ghost

object GhostBridge {
    fun prewarm() {
        // Register the KSP-generated registry for this module
        Ghost.addRegistry(GhostModuleRegistry_shared_utils())
        Ghost.prewarm()
    }
}
```

> [!NOTE]
> The registry class name is derived from `ghost.moduleName`. If you set `arg("ghost.moduleName", "shared_utils")`, the generated class is `GhostModuleRegistry_shared_utils`. Call `prewarm()` once at app launch — typically in `AppDelegate` or the SwiftUI `@main` entry point.

> [!IMPORTANT]
> Automatic registration needs the `com.ghostserializer.ghost` Gradle plugin. Without it, or with `ghost { autoRegistration.set(false) }`, the `addRegistry` call above is **required**. The compiler plugin is built for Kotlin 2.2.21; on another Kotlin version the build logs a warning, and if Native compilation fails, disable `autoRegistration` and register manually.

---

## 4. Build and Import the XCFramework

```bash
./gradlew :shared:assembleSharedUtilsReleaseXCFramework
```

Then in Xcode:
1. Drag the `.xcframework` folder into your project navigator.
2. In **Target → General → Frameworks, Libraries, and Embedded Content**, set it to **Embed & Sign**.

---

## 5. Use in Swift

```swift
import SharedUtils

// MARK: — App Startup (AppDelegate or @main)
GhostBridge.shared.prewarm()

// MARK: — Deserialize
let jsonString = """
{"id": 1, "name": "Alice", "email": "alice@example.com", "roles": ["admin"]}
"""
let user = Ghost.shared.deserialize(User.self, from: jsonString)

// MARK: — Serialize
let json: String = Ghost.shared.serialize(user)
```

---

## 6. Networking with Alamofire

Ghost integrates cleanly with Alamofire. Create a `ResponseSerializer` that feeds raw data bytes directly to Ghost:

```swift
import Alamofire
import SharedUtils

struct GhostResponseSerializer<T>: ResponseSerializer {
    func serialize(request: URLRequest?, response: HTTPURLResponse?,
                   data: Data?, error: Error?) throws -> T {
        guard let data = data else { throw AFError.responseSerializationFailed(reason: .inputDataNilOrZeroLength) }
        // Feed raw UTF-8 bytes — avoids UTF-8→UTF-16 String conversion
        return Ghost.shared.deserializeBytes(T.self, from: data)
    }
}

// Usage
AF.request("https://api.example.com/users/1")
  .response(responseSerializer: GhostResponseSerializer<User>()) { response in
      switch response.result {
      case .success(let user): print(user.name)
      case .failure(let error): print(error)
      }
  }
```

> [!TIP]
> For a full step-by-step guide and optimized networking integration with Alamofire, see the [iOS Test App Repository](https://github.com/juanchurtado1991/ghost-ios-test-app).

---

## Polymorphic Sealed Classes on iOS

Polymorphism works identically across all platforms — sealed classes defined in `commonMain` are serialized with the same discriminator logic:

```kotlin
// commonMain
@GhostSerialization
sealed class ApiEvent {
    @GhostSerialization
    data class UserCreated(val userId: String, val email: String) : ApiEvent()

    @GhostFallback
    @GhostSerialization
    data class Unknown(val raw: String = "unknown") : ApiEvent()
}
```

```swift
// Swift
let event = Ghost.shared.deserialize(ApiEvent.self, from: jsonString)
```

---

← [Back to README](../../README.md) | [Installation →](installation.md) | [KMP Guide →](usage-kmp.md)
