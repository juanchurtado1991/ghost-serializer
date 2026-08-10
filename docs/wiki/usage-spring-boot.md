# Usage — Spring Boot

[![Spring Boot](https://img.shields.io/badge/Spring_Boot-6DB33F.png?style=flat&logo=spring&logoColor=white)](usage-spring-boot.md)

Ghost integrates with Spring Boot via the `ghost-spring-boot-starter`. It adds generated JSON serialization for annotated hot-path DTOs in **Spring MVC** and **Spring WebFlux** without replacing Jackson: unsupported and unannotated controller types continue through the existing Spring codecs. No manual bean wiring is needed.

For the cross-platform setup flow, see the [Ghost Serializer Quick Start](quick-start.md).

---

## 1. Add the Starter

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.ghostserializer:ghost-spring-boot-starter:1.3.0")
}
```

The starter registers these ahead of the default codecs:
- `GhostHttpMessageConverter` for Spring MVC JSON (`application/json`)
- `GhostYamlHttpMessageConverter` for Spring MVC YAML (`application/yaml`)
- `GhostProtoHttpMessageConverter` for proto3 JSON (auto-detected via `isProto`)
- `GhostReactiveEncoder` + `GhostReactiveDecoder` for Spring WebFlux JSON
- `GhostYamlReactiveEncoder` + `GhostYamlReactiveDecoder` for WebFlux YAML
- `GhostProtoReactiveEncoder` + `GhostProtoReactiveDecoder` for WebFlux proto3 JSON

They accept DTOs backed by a generated Ghost serializer and decline other types, preserving Jackson as the fallback.

Integration tests in this repo run against **Spring Boot 3.4.5** (`@SpringBootTest` + MockMvc with KSP-generated DTOs).

> [!WARNING]
> **Large HTTP bodies (e.g. 50–100 MB):** Ghost does **not** cap JSON body size. Enforce limits at the edge (nginx, API gateway) or in Spring (`spring.codec.max-in-memory-size`, WebFlux codecs). Ghost targets typical API payloads (KB to a few MB).

---

## 2. Annotate Your DTOs

```kotlin
import com.ghost.serialization.GhostSerialization

@GhostSerialization
data class CreateUserRequest(
    val username: String,
    val email: String,
    val role: UserRole
)

@GhostSerialization
data class UserResponse(
    val id: Long,
    val username: String,
    val createdAt: String
)
```

KSP generates the serializer at compile time. Annotated types skip Jackson for that payload; Jackson (and any other Spring codecs) still handle everything else.

---

## 3. Use in Controllers

```kotlin
@RestController
@RequestMapping("/users")
class UserController(private val userService: UserService) {

    @PostMapping
    fun createUser(@RequestBody request: CreateUserRequest): UserResponse {
        return userService.create(request)
    }

    @GetMapping("/{id}")
    fun getUser(@PathVariable id: Long): UserResponse {
        return userService.findById(id)
    }
}
```

Ghost handles serialization and deserialization transparently. Spring's content negotiation, validation, and error handling work unchanged.

### Collection request/response bodies

Top-level `List<T>`, `Set<T>`, and `Map<String, V>` are unwrapped when the element/value type has a Ghost serializer (JSON and YAML converters, MVC and WebFlux). Element types must be registered the same way as a direct body DTO:

```kotlin
@GetMapping("/users")
fun listUsers(): List<UserResponse> = userService.findAll()

@PostMapping("/users/batch")
fun createBatch(@RequestBody users: List<CreateUserRequest>): List<UserResponse> =
    users.map { userService.create(it) }
```

Without a registered element serializer, Spring falls through to Jackson for that type (same as a bare unregistered DTO).

---

## 4. Declarative Controller Customization

Use `@GhostStrict` and `@GhostCoerce` at the controller class, method, or `@RequestBody` parameter level for granular parsing control:

```kotlin
@RestController
@RequestMapping("/users")
class UserController(private val userService: UserService) {

    // Strict: validates comma placement, rejects unknown fields
    @PostMapping("/strict")
    fun createUserStrict(
        @RequestBody @GhostStrict request: CreateUserRequest
    ): UserResponse = userService.create(request)

    // Coerce: parses stringified primitives ("42", "true") automatically
    @GhostCoerce
    @PostMapping("/coerce")
    fun createUserCoerced(
        @RequestBody request: CreateUserRequest
    ): UserResponse = userService.create(request)
}
```

---

## 5. WebFlux (Reactive)

Ghost works identically with reactive controllers. The `GhostReactiveDecoder` / `GhostReactiveEncoder` are registered automatically by the starter:

```kotlin
@RestController
@RequestMapping("/reactive/users")
class ReactiveUserController(private val userService: ReactiveUserService) {

    @PostMapping
    fun createUser(@RequestBody request: Mono<CreateUserRequest>): Mono<UserResponse> {
        return request.flatMap { userService.create(it) }
    }

    @GetMapping
    fun listUsers(): Flux<UserResponse> {
        return userService.findAll()
    }
}
```

---

## 7. YAML endpoints

Use `consumes` / `produces` when an endpoint should speak YAML:

```kotlin
@PostMapping("/config", consumes = ["application/yaml"], produces = ["application/yaml"])
fun updateConfig(@RequestBody config: ConfigDto): ConfigDto = service.save(config)
```

When both JSON and YAML converters are registered, Ghost orders JSON ahead of YAML for content negotiation — constrain media types on mixed endpoints so Spring picks the format you intend.

`@GhostStrict` and `@GhostCoerce` on `@RequestBody` apply to YAML bodies the same way they do for JSON.

→ **[Full YAML guide →](usage-yaml.md)**

---

## 6. Manual Configuration (optional)

If you need explicit control over the converter bean:

```kotlin
@Configuration
class GhostConfig {
    @Bean
    fun ghostMessageConverter(): GhostHttpMessageConverter {
        return GhostHttpMessageConverter()
    }
}
```

---

## Platform Limits & Memory

Ghost separates **parser safety** from **HTTP body size policy**. Body size is the infrastructure's responsibility:

| Layer | How to limit |
|:---|:---|
| Spring WebFlux | `spring.codec.max-in-memory-size=10MB` in `application.properties` |
| Spring WebMVC | `spring.mvc.async.request-timeout` + reverse proxy |
| Reverse proxy | `client_max_body_size` (nginx) / API gateway rules |

Ghost's per-parser limits (`maxCollectionSize`, `maxDepth`) remain active on all deserialization paths regardless of transport.

> [!TIP]
> For a high-performance Spring Boot implementation example, see the [Ghost Spring Boot Test App](https://github.com/juanchurtado1991/ghost-spring-boot-test-app).

---

← [Back to README](../../README.md) | [Installation →](installation.md) | [Android Guide →](usage-android.md)
