package com.mybank.autostone.controller;

import com.mybank.autostone.entity.AlertPolicy;
import com.mybank.autostone.repository.AlertPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/alert-policies")
@RequiredArgsConstructor
public class AlertPolicyController {
    private final AlertPolicyRepository repository;

    @GetMapping("/default")
    public AlertPolicy getDefault() {
        return repository.findAll().stream().findFirst().orElseGet(() -> repository.save(AlertPolicy.builder().updatedAt(LocalDateTime.now()).build()));
    }

    @PutMapping("/default")
    public AlertPolicy update(@RequestBody AlertPolicy request) {
        AlertPolicy policy = getDefault();
        policy.setEnabled(Boolean.TRUE.equals(request.getEnabled()));
        policy.setAlertOnFailure(Boolean.TRUE.equals(request.getAlertOnFailure()));
        policy.setAlertOnTimeout(Boolean.TRUE.equals(request.getAlertOnTimeout()));
        policy.setAlertOnServerUnavailable(Boolean.TRUE.equals(request.getAlertOnServerUnavailable()));
        policy.setMaxRunMinutes(Math.max(1, request.getMaxRunMinutes() == null ? 30 : request.getMaxRunMinutes()));
        policy.setUpdatedAt(LocalDateTime.now());
        return repository.save(policy);
    }
}
