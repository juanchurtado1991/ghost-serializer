package com.ghost.serialization.spring.reactivefixture

import com.ghost.serialization.spring.fixture.ProtoEventMessage
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api/reactive/proto")
class ProtoEventReactiveController {

    @GetMapping("/event")
    fun getEvent(): Mono<ProtoEventMessage> =
        Mono.just(ProtoEventMessage(device_id = Long.MAX_VALUE, label = "sensor-1"))

    @PostMapping("/event")
    fun postEvent(@RequestBody event: Mono<ProtoEventMessage>): Mono<ProtoEventMessage> = event
}
