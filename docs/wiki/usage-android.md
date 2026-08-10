# Usage — Android

[![Android](https://img.shields.io/badge/Android-3DDC84.png?style=flat&logo=android&logoColor=white)](usage-android.md)

This guide covers integrating Ghost Serializer in an Android app or library as an incremental optimization. Existing Gson, Moshi, or KotlinX Serialization models can remain unchanged while selected `@GhostSerialization` DTOs use generated Ghost code.

New project? Start with the [Ghost Serializer Quick Start](quick-start.md).

---

## 1. Apply the Gradle Plugin

The Ghost Gradle plugin automatically adds runtime dependencies and wires the KSP compiler. **KSP must already be applied.**

```kotlin
// build.gradle.kts (app module)
plugins {
    id("com.android.application")
    id("com.google.devtools.ksp") version "2.3.10"
    id("com.ghostserializer.ghost") version "1.3.0"
}

// Recommended for Retrofit/OkHttp/Ktor byte-first modules: omit native String
// reader/writer generation (~4 KB smaller per DTO). Keep default true if models
// also parse large in-memory Strings. See Advanced Features §5.
ksp {
    arg("ghost.textChannel", "false")
}
```

The plugin detects Android, adds `ghost-api` and `ghost-serialization`, and registers `ghost-compiler` on the `ksp` configuration.

---

## 2. Annotate Your Models

```kotlin
import com.ghost.serialization.GhostSerialization

@GhostSerialization
data class User(
    val id: Long,
    val name: String,
    val email: String,
    val roles: List<String>,
    val address: Address?
)

@GhostSerialization
data class Address(
    val street: String,
    val city: String,
    val country: String
)
```

---

## 3. Serialize and Deserialize

```kotlin
import com.ghost.serialization.Ghost

// Deserialize from JSON string
val user: User = Ghost.deserialize(jsonString)

// Deserialize from ByteArray (e.g., from OkHttp response body) — recommended
val user: User = Ghost.deserialize(responseBodyBytes)

// Serialize to String
val json: String = Ghost.encodeToString(user)

// Serialize to ByteArray
val bytes: ByteArray = Ghost.encodeToBytes(user)
```

> [!IMPORTANT]
> **Byte-First**: For network responses (OkHttp, Retrofit), always feed bytes directly. This avoids the unnecessary UTF-8→UTF-16→UTF-8 round-trip:
> ```kotlin
> // ✅ Optimal — zero-copy bytes fed directly to Ghost
> val user: User = Ghost.deserialize(response.body().bytes())
>
> // ⚠️ Suboptimal — forces unnecessary String allocation
> val user: User = Ghost.deserialize(response.body().string())
> ```

---

## 4. Flat vs. Streaming Deserialization (Okio)

When deserializing from a `BufferedSource`, choose the mode based on payload size:

| Mode | API | When to use |
|:---|:---|:---|
| **Flat** | `Ghost.deserialize(source)` | Default. Standard API responses < 10 MB. Fastest. |
| **Streaming** | `Ghost.deserializeStreaming(source)` | Large files / unpredictable size. True O(1) memory. |

> ⚠️ Flat deserialization loads the entire stream into memory. Peak RAM ≈ **2× payload size**. Use streaming for payloads that could exceed available memory.

---

## 5. Retrofit Integration

```kotlin
// build.gradle.kts
dependencies {
    implementation(libs.ghost.retrofit)
}
```

```kotlin
val retrofit = Retrofit.Builder()
    .baseUrl("https://api.example.com/")
    // Ghost handles registered @GhostSerialization DTOs.
    .addConverterFactory(GhostConverterFactory.create())
    // Your existing converter remains the fallback for every other type.
    .addConverterFactory(GsonConverterFactory.create())
    .build()

val api = retrofit.create(UserApi::class.java)
```

### Declarative Per-Endpoint Configuration

```kotlin
interface UserApi {
    // Default: lenient mode (maximum speed)
    @GET("users/lenient")
    suspend fun getStandardUsers(): List<User>

    // Strict: validates comma placement, rejects trailing commas
    @GhostStrict
    @GET("users/strict")
    suspend fun getStrictUsers(): List<User>

    // Coerce: parses stringified values ("42", "true") into primitives
    @GhostCoerce
    @GET("users/coerce")
    suspend fun getCoercedUsers(): List<User>
}
```

> [!TIP]
> For a full Android integration example including Retrofit and Room, see the [Ghost Android Test App](https://github.com/juanchurtado1991/ghost-android-test-app).

---

## 6. Lists, Sets, and Nullable Fields

```kotlin
// Collections are handled automatically (wire format for Set is a JSON array)
val users: List<User> = Ghost.deserialize(jsonArrayString)
val unique: Set<User> = Ghost.deserialize(jsonArrayString)
// Retrofit response types List<User> / Set<User> / Map<String, User> unwrap when User is registered

// Nullable fields work as expected
@GhostSerialization
data class Profile(
    val bio: String?,       // null if missing in JSON
    val avatarUrl: String?  // null if missing in JSON
)
```

---

## 7. Resilience & Anti-Explosion

### Polymorphic Fallbacks (`@GhostFallback`)

```kotlin
@GhostSerialization
sealed class DeviceEvent {
    @GhostFallback
    @GhostSerialization
    data class Unknown(val raw: String = "unknown") : DeviceEvent()
}
```

### Field Resilience (`@GhostResilient`)

```kotlin
@GhostSerialization
data class UserConfig(
    @GhostResilient
    val theme: Theme?,       // null if server sends unknown theme
    @GhostResilient
    val retryCount: Int = 3  // stays 3 if server sends malformed data
)
```

### Boolean Coercion

```kotlin
val user = Ghost.deserialize<User>(json) {
    it.coerceBooleans = true
}
```

### Strict Mode

```kotlin
val user = Ghost.deserialize<User>(json) {
    it.strictMode = true // RFC 8259 syntax validation + unknown key rejection
}
```

---

## 8. Custom Field Decoders

```kotlin
@GhostSerialization
data class LegacyUser(
    val id: Int,
    @GhostDecoder(LegacyUtils::class, "parseDate")
    @GhostEncoder(LegacyUtils::class, "writeDate")
    val birthDate: Long // Receives "15-05-2026", stores Long
)

object LegacyUtils {
    fun parseDate(reader: GhostJsonReader): Long {
        val raw = reader.nextString()
        return someDateParser(raw)
    }

    fun writeDate(writer: GhostJsonFlatWriter, value: Long) {
        writer.value(someDateFormatter(value))
    }
}
```

---

← [Back to README](../../README.md) | [Installation →](installation.md) | [KMP Guide →](usage-kmp.md)
