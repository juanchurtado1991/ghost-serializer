package com.ghost.serialization.spring.reactivefixture

import com.ghost.serialization.spring.fixture.YamlProfileMessage
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api/reactive/yaml")
class YamlProfileReactiveController {

    @GetMapping("/profile", produces = ["application/yaml"])
    fun getProfile(): Mono<YamlProfileMessage> =
        Mono.just(YamlProfileMessage(id = 1, name = "ghost"))

    @PostMapping("/profile", consumes = ["application/yaml"], produces = ["application/yaml"])
    fun postProfile(@RequestBody message: Mono<YamlProfileMessage>): Mono<YamlProfileMessage> =
        message.map { it.copy(name = it.name.uppercase()) }
}
