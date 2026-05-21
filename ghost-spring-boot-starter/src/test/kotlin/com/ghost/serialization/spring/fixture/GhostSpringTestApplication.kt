package com.ghost.serialization.spring.fixture

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class GhostSpringTestApplication

fun main(args: Array<String>) {
    runApplication<GhostSpringTestApplication>(*args)
}
