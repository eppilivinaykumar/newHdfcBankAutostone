package com.mybank.autostone.repository;

import com.mybank.autostone.entity.JobExecution;
import com.mybank.autostone.entity.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobExecutionRepository extends JpaRepository<JobExecution, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from JobExecution e join fetch e.job where e.id = :id")
    Optional<JobExecution> findByIdForUpdate(@Param("id") Long id);
    @Query("select e from JobExecution e where e.id in (select max(e2.id) from JobExecution e2 where e2.job.id in :jobIds group by e2.job.id)")
    List<JobExecution> findLatestForJobIds(@Param("jobIds") List<Long> jobIds);

    @Query("select e from JobExecution e where e.id in (select max(e2.id) from JobExecution e2 group by e2.job.id)")
    List<JobExecution> findLatestPerJob();

    @Query("select e from JobExecution e where e.id in (select max(e2.id) from JobExecution e2 where e2.job.id in :jobIds and e2.runId = :runId group by e2.job.id)")
    List<JobExecution> findLatestForJobIdsAndRunId(@Param("jobIds") List<Long> jobIds, @Param("runId") UUID runId);

    @Query("select e from JobExecution e where e.job.id=:jobId and e.status in :statuses order by e.id desc")
    List<JobExecution> findActive(@Param("jobId") Long jobId, @Param("statuses") List<JobStatus> statuses);
    default Optional<JobExecution> findFirstByJobIdAndStatusInOrderByIdDesc(Long jobId, List<JobStatus> statuses) { return findActive(jobId,statuses).stream().findFirst(); }

    @Query("select e from JobExecution e where e.job.id=:jobId order by e.id desc")
    List<JobExecution> findLatestForJob(@Param("jobId") Long jobId);
    default Optional<JobExecution> findFirstByJobIdOrderByIdDesc(Long jobId) { return findLatestForJob(jobId).stream().findFirst(); }
    default List<JobExecution> findByJobIdOrderByIdDesc(Long jobId) { return findLatestForJob(jobId); }

    @Query("select e from JobExecution e where e.job.id=:jobId and e.runId=:runId order by e.id desc")
    List<JobExecution> findForRun(@Param("jobId") Long jobId, @Param("runId") UUID runId);
    default Optional<JobExecution> findFirstByJobIdAndRunIdOrderByIdDesc(Long jobId, UUID runId) { return findForRun(jobId,runId).stream().findFirst(); }

    List<JobExecution> findByRunId(UUID runId);
    @Query("select e from JobExecution e where e.job.id=:jobId and e.workflowRunId=:workflowRunId order by e.id desc")
    List<JobExecution> findForWorkflowRun(@Param("jobId") Long jobId, @Param("workflowRunId") UUID workflowRunId);
    default Optional<JobExecution> findFirstByJobIdAndWorkflowRunIdOrderByIdDesc(Long jobId, UUID workflowRunId) { return findForWorkflowRun(jobId, workflowRunId).stream().findFirst(); }

    @Query("select e from JobExecution e where e.job.id in :jobIds and e.workflowRunId = :workflowRunId and e.id in (select max(e2.id) from JobExecution e2 where e2.job.id in :jobIds and e2.workflowRunId = :workflowRunId group by e2.job.id)")
    List<JobExecution> findLatestForJobIdsAndWorkflowRunId(@Param("jobIds") List<Long> jobIds, @Param("workflowRunId") UUID workflowRunId);

    List<JobExecution> findByWorkflowRunId(UUID workflowRunId);

    @Query("select e from JobExecution e where e.workflowRunId=:workflowRunId and e.status=:status")
    List<JobExecution> findByWorkflowRunIdAndStatus(@Param("workflowRunId") UUID workflowRunId, @Param("status") JobStatus status);
    List<JobExecution> findTop5000ByOrderByIdDesc();
    List<JobExecution> findTop5000ByJob_IdOrderByIdDesc(Long jobId);
    List<JobExecution> findByIdGreaterThanOrderByIdAsc(Long id);
    List<JobExecution> findByRunIdAndStatus(UUID runId, JobStatus status);

    @Query("select e from JobExecution e where e.job.id=:jobId and e.status=:status order by e.id asc")
    List<JobExecution> findQueued(@Param("jobId") Long jobId, @Param("status") JobStatus status);
    default Optional<JobExecution> findFirstByJobIdAndStatusOrderByIdAsc(Long jobId, JobStatus status) { return findQueued(jobId,status).stream().findFirst(); }
}
