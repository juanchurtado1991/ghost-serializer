package com.ghost.serialization.spring.fixture

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/yaml")
class YamlProfileController {

    @GetMapping("/profile", produces = ["application/yaml"])
    fun getProfile(): YamlProfileMessage = YamlProfileMessage(id = 1, name = "ghost")

    @PostMapping("/profile", consumes = ["application/yaml"], produces = ["application/yaml"])
    fun postProfile(@RequestBody message: YamlProfileMessage): YamlProfileMessage =
        message.copy(name = message.name.uppercase())
}
