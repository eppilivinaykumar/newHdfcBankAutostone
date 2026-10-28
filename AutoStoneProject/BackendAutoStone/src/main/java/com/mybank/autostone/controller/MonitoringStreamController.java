package com.mybank.autostone.controller;

import com.mybank.autostone.service.MonitoringStreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/monitoring")
@RequiredArgsConstructor
public class MonitoringStreamController {
    private final MonitoringStreamService streamService;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() { return streamService.connect(); }
}
