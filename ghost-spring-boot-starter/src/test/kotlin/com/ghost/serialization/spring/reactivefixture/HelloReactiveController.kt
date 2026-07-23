package com.ghost.serialization.spring.reactivefixture

import com.ghost.serialization.spring.fixture.HelloMessage
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api/reactive")
class HelloReactiveController {

    @GetMapping("/hello")
    fun getHello(): Mono<HelloMessage> = Mono.just(HelloMessage(id = 1, name = "ghost"))

    @PostMapping("/hello")
    fun postHello(@RequestBody message: Mono<HelloMessage>): Mono<HelloMessage> =
        message.map { it.copy(name = it.name.uppercase()) }

    @GetMapping("/hello-stream", produces = ["application/x-ndjson"])
    fun getHelloStream(): Flux<HelloMessage> =
        Flux.just(HelloMessage(1, "a"), HelloMessage(2, "b"))

    @PostMapping("/hello-stream", consumes = ["application/x-ndjson"], produces = ["application/x-ndjson"])
    fun postHelloStream(@RequestBody messages: Flux<HelloMessage>): Flux<HelloMessage> =
        messages.map { it.copy(name = it.name.uppercase()) }
}
