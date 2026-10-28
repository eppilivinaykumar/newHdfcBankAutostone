package com.mybank.autostone;

import com.mybank.autostone.entity.*;
import com.mybank.autostone.repository.JobDependencyRepository;
import com.mybank.autostone.repository.JobExecutionRepository;
import com.mybank.autostone.repository.JobRepository;
import com.mybank.autostone.service.AlertService;
import com.mybank.autostone.service.DependencyResolverService;
import com.mybank.autostone.service.ExecutionCoordinatorService;
import com.mybank.autostone.service.ExecutionStateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DependencyResolverSkipSemanticsTest {
    @Mock JobRepository jobRepository;
    @Mock JobDependencyRepository dependencyRepository;
    @Mock JobExecutionRepository executionRepository;
    @Mock ExecutionCoordinatorService executionCoordinator;
    @Mock AlertService alertService;
    @Mock ExecutionStateService stateService;
    @InjectMocks DependencyResolverService resolver;

    @Test
    void skippedMiddleJobDoesNotReleaseDownstreamWhileItsPrerequisiteIsRunning() {
        UUID run = UUID.randomUUID();
        Job a = job(1L, "A", null);
        Job b = job(2L, "B", "s(A)");
        Job c = job(3L, "C", "s(B)");
        JobExecution aRun = exec(a, run, JobStatus.RUNNING);
        JobExecution bRun = exec(b, run, JobStatus.SKIPPED);
        JobExecution cRun = exec(c, run, JobStatus.WAITING);

        when(jobRepository.findById(3L)).thenReturn(Optional.of(c));
        when(jobRepository.findByName("B")).thenReturn(Optional.of(b));
        when(jobRepository.findByName("A")).thenReturn(Optional.of(a));
        when(executionRepository.findFirstByJobIdAndWorkflowRunIdOrderByIdDesc(3L, run)).thenReturn(Optional.of(cRun));
        when(executionRepository.findFirstByJobIdAndWorkflowRunIdOrderByIdDesc(2L, run)).thenReturn(Optional.of(bRun));
        when(executionRepository.findFirstByJobIdAndWorkflowRunIdOrderByIdDesc(1L, run)).thenReturn(Optional.of(aRun));
        when(stateService.isRuntimeHeld(cRun)).thenReturn(false);
        when(dependencyRepository.findByJobId(3L)).thenReturn(List.of(dep(c, b)));
        when(dependencyRepository.findByJobId(2L)).thenReturn(List.of(dep(b, a)));

        resolver.tryActivate(run, 3L);

        verify(stateService, never()).transition(eq(cRun), any(), anyString());
        verify(executionCoordinator, never()).dispatchIfEligible(any());
    }

    @Test
    void skippedMiddleJobReleasesDownstreamAfterItsPrerequisiteSucceeds() {
        UUID run = UUID.randomUUID();
        Job a = job(1L, "A", null);
        Job b = job(2L, "B", "s(A)");
        Job c = job(3L, "C", "s(B)");
        JobExecution aRun = exec(a, run, JobStatus.SUCCESS);
        JobExecution bRun = exec(b, run, JobStatus.SKIPPED);
        JobExecution cRun = exec(c, run, JobStatus.WAITING);

        when(jobRepository.findById(3L)).thenReturn(Optional.of(c));
        when(jobRepository.findByName("B")).thenReturn(Optional.of(b));
        when(jobRepository.findByName("A")).thenReturn(Optional.of(a));
        when(executionRepository.findFirstByJobIdAndWorkflowRunIdOrderByIdDesc(3L, run)).thenReturn(Optional.of(cRun));
        when(executionRepository.findFirstByJobIdAndWorkflowRunIdOrderByIdDesc(2L, run)).thenReturn(Optional.of(bRun));
        when(executionRepository.findFirstByJobIdAndWorkflowRunIdOrderByIdDesc(1L, run)).thenReturn(Optional.of(aRun));
        when(stateService.isRuntimeHeld(cRun)).thenReturn(false);
        when(dependencyRepository.findByJobId(3L)).thenReturn(List.of(dep(c, b)));
        when(dependencyRepository.findByJobId(2L)).thenReturn(List.of(dep(b, a)));

        resolver.tryActivate(run, 3L);

        verify(stateService).transition(cRun, JobStatus.QUEUED, "Dependency satisfied; queued for execution");
        verify(executionCoordinator).dispatchIfEligible(cRun);
    }

    private static Job job(Long id, String name, String condition) {
        return Job.builder().id(id).name(name).jobType(JobType.JOB).conditionExpression(condition)
                .hold(false).ice(false).noExecution(false).build();
    }
    private static JobExecution exec(Job job, UUID run, JobStatus status) {
        return JobExecution.builder().id(job.getId()).job(job).runId(run).workflowRunId(run)
                .status(status).runtimeHold(false).attempt(1).build();
    }
    private static JobDependency dep(Job child, Job parent) {
        return JobDependency.builder().job(child).dependsOnJob(parent).build();
    }
}
