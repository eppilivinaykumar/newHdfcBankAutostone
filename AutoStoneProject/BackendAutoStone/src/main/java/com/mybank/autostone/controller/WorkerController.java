package com.mybank.autostone.controller;

import com.mybank.autostone.dto.WorkerResponse;
import com.mybank.autostone.service.WorkerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workers")
@RequiredArgsConstructor
public class WorkerController {
    private final WorkerService workerService;
    @GetMapping
    public List<WorkerResponse> list() { return workerService.list(); }
}
