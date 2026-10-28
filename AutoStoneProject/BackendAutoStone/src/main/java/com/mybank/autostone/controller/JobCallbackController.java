package com.mybank.autostone.controller;

import com.mybank.autostone.dto.JobCallbackRequest;
import com.mybank.autostone.service.DependencyResolverService;
import com.mybank.autostone.config.RemoteExecutionProperties;
import org.springframework.web.bind.annotation.RequestHeader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Not part of the public API - only reachable by the wrapper script's curl (running on a
 * trusted remote host) and this backend's own local-exec watcher. If this backend is ever
 * exposed beyond a private network, put this behind network-level restrictions or a shared
 * secret header, since it currently trusts whatever runId/jobId/exitCode it's handed.
 */
@Slf4j
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class JobCallbackController {

    private final DependencyResolverService dependencyResolverService;
    private final RemoteExecutionProperties properties;

    @PostMapping("/job-callback")
    public ResponseEntity<Void> callback(@RequestBody JobCallbackRequest request,
                                         @RequestHeader(value = "X-AutoStone-Callback-Secret", required = false) String secret) {
        if (request == null || request.getRunId() == null || request.getJobId() == null || request.getExecutionId() == null) {
            return ResponseEntity.badRequest().build();
        }
        if (properties.getCallbackSecret() == null || properties.getCallbackSecret().isBlank()) {
            log.error("Execution callback secret is not configured; rejecting callback for execution {}", request.getExecutionId());
            return ResponseEntity.status(503).build();
        }
        if (secret == null || !java.security.MessageDigest.isEqual(
                properties.getCallbackSecret().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                secret.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            log.warn("Rejected unauthorized execution callback for job {} execution {}", request.getJobId(), request.getExecutionId());
            return ResponseEntity.status(401).build();
        }
        log.info("Received job callback: {}", request);
        try {
            boolean processed = dependencyResolverService.handleCompletion(request.getRunId(), request.getJobId(), request.getExecutionId(), request.getExitCode(), request.getStatus());
            return processed ? ResponseEntity.ok().build() : ResponseEntity.status(409).build();
        } catch (java.util.NoSuchElementException e) {
            log.warn("Rejected callback: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            log.warn("Rejected invalid callback for execution {}: {}", request.getExecutionId(), e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException e) {
            log.warn("Rejected stale callback for execution {}: {}", request.getExecutionId(), e.getMessage());
            return ResponseEntity.status(409).build();
        }
    }
}