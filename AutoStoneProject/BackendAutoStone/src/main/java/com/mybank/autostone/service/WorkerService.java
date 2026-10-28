package com.mybank.autostone.service;

import com.mybank.autostone.dto.WorkerResponse;
import com.mybank.autostone.entity.JobStatus;
import com.mybank.autostone.repository.JobRepository;
import com.mybank.autostone.repository.JobExecutionRepository;
import com.mybank.autostone.remote.RemoteExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkerService {
    private final JobRepository jobRepository;
    private final RemoteExecutionService remoteExecutionService;
    private final JobExecutionRepository executionRepository;
    private final AlertService alertService;

    /**
     * The Servers page polls this every 10s. Each distinct host needs a live SSH
     * connectivity check, and checking them one at a time serially means the request's
     * latency grows with the number of hosts (each check can take up to a few seconds
     * before timing out on an unreachable host). Running the checks concurrently keeps
     * total latency close to that of the single slowest host instead of their sum.
     */
    public List<WorkerResponse> list() {
        Map<String, List<com.mybank.autostone.entity.Job>> grouped = jobRepository.findAll().stream()
                .filter(j -> j.getHostName() != null && !j.getHostName().isBlank())
                .collect(Collectors.groupingBy(com.mybank.autostone.entity.Job::getHostName));

        List<CompletableFuture<WorkerResponse>> futures = grouped.entrySet().stream().map(e ->
                CompletableFuture.supplyAsync(() -> {
                    String host = e.getKey();
                    boolean reachable = remoteExecutionService.checkConnectivity(host);
                    if (!reachable) alertService.onServerUnavailable(host, "SSH connection unavailable");
                    long running = e.getValue().stream()
                            .filter(j -> executionRepository.findFirstByJobIdAndStatusInOrderByIdDesc(j.getId(), List.of(JobStatus.RUNNING)).isPresent())
                            .count();
                    return WorkerResponse.builder().host(host).jobCount(e.getValue().size()).runningCount(running)
                            .status(reachable ? "AVAILABLE" : "UNAVAILABLE")
                            .detail(reachable ? "SSH authentication/connection available" : "SSH connection unavailable").build();
                })).toList();

        return futures.stream().map(CompletableFuture::join).toList();
    }
}
