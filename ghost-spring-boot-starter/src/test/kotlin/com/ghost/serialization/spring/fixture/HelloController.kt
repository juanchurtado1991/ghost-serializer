package com.ghost.serialization.spring.fixture

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class HelloController {

    @GetMapping("/hello")
    fun getHello(): HelloMessage = HelloMessage(id = 1, name = "ghost")

    @PostMapping("/hello")
    fun postHello(@RequestBody message: HelloMessage): HelloMessage =
        message.copy(name = message.name.uppercase())

    @com.ghost.serialization.annotations.GhostStrict
    @PostMapping("/strict")
    fun postStrict(@RequestBody message: HelloMessage): HelloMessage = message

    @PostMapping("/strict-param")
    fun postStrictParam(@RequestBody @com.ghost.serialization.annotations.GhostStrict message: HelloMessage): HelloMessage =
        message

    @com.ghost.serialization.annotations.GhostCoerce
    @PostMapping("/coerce")
    fun postCoerce(@RequestBody message: HelloMessage): HelloMessage = message

    @GetMapping("/hello-list")
    fun getHelloList(): List<HelloMessage> = listOf(HelloMessage(id = 1, name = "ghost"))

    @PostMapping("/hello-list")
    fun postHelloList(@RequestBody messages: List<HelloMessage>): List<HelloMessage> =
        messages.map { it.copy(name = it.name.uppercase()) }

    @GetMapping("/hello-set")
    fun getHelloSet(): Set<HelloMessage> = setOf(HelloMessage(id = 1, name = "ghost"))

    @PostMapping("/hello-set")
    fun postHelloSet(@RequestBody messages: Set<HelloMessage>): Set<HelloMessage> =
        messages.map { it.copy(name = it.name.uppercase()) }.toSet()

    @GetMapping("/hello-map")
    fun getHelloMap(): Map<String, HelloMessage> =
        mapOf("a" to HelloMessage(id = 1, name = "ghost"))

    @PostMapping("/hello-map")
    fun postHelloMap(@RequestBody messages: Map<String, HelloMessage>): Map<String, HelloMessage> =
        messages.mapValues { (_, v) -> v.copy(name = v.name.uppercase()) }
}
