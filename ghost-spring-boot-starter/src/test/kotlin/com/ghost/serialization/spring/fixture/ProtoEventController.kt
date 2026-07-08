package com.ghost.serialization.spring.fixture

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/proto")
class ProtoEventController {

    @GetMapping("/event")
    fun getEvent(): ProtoEventMessage = ProtoEventMessage(device_id = Long.MAX_VALUE, label = "sensor-1")

    @PostMapping("/event")
    fun postEvent(@RequestBody event: ProtoEventMessage): ProtoEventMessage = event
}
