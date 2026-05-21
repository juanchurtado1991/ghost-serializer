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
}
