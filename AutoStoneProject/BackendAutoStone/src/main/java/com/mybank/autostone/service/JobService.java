package com.mybank.autostone.service;

import com.mybank.autostone.dto.JobRequest;
import com.mybank.autostone.dto.JobSummaryResponse;
import com.mybank.autostone.entity.*;
import com.mybank.autostone.repository.JobDependencyRepository;
import com.mybank.autostone.repository.JobExecutionRepository;
import com.mybank.autostone.repository.JobRepository;
import com.mybank.autostone.scheduler.QuartzSchedulerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.quartz.CronExpression;
import com.mybank.autostone.util.ConditionEvaluator;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
public class JobService {
    private final JobRepository jobRepository;
    private final JobDependencyRepository jobDependencyRepository;
    private final JobExecutionRepository jobExecutionRepository;
    private final QuartzSchedulerService quartzSchedulerService;
    private final com.mybank.autostone.repository.JobDefinitionLogRepository definitionLogRepository;

    @Transactional
    public JobSummaryResponse createJob(JobRequest req) {
        validateRequest(req);
        if (jobRepository.existsByName(req.getName().trim())) {
            throw new IllegalArgumentException("A job with name '" + req.getName().trim() + "' already exists");
        }
        Job box = resolveBox(req);
        validateBoxParent(box, null);
        Job job = fromRequest(req, box);
        Job saved = jobRepository.save(job);
        addDependencies(saved, req.getDependsOnJobIds());
        addConditionDependencies(saved);
        validateNoCycle(saved.getId());
        scheduleAfterCommit(saved);
        writeDefinitionLog(saved, "CREATE");
        return toSummary(saved);
    }

    @Transactional
    public JobSummaryResponse updateJob(Long id, JobRequest req) {
        validateRequest(req);
        Job job = findJob(id);
        if (jobRepository.findByName(req.getName().trim()).filter(existing -> !existing.getId().equals(id)).isPresent()) {
            throw new IllegalArgumentException("A job with name '" + req.getName().trim() + "' already exists");
        }
        if (req.getBoxId() != null && req.getBoxId().equals(id)) {
            throw new IllegalArgumentException("A job cannot belong to itself as a box");
        }
        Job box = resolveBox(req);
        validateBoxParent(box, id);
        int nextVersion = job.getVersion() == null ? 2 : job.getVersion() + 1;
        job.setVersion(nextVersion);
        job.setName(req.getName().trim()); job.setDescription(req.getDescription());
        job.setJobType(JobType.valueOf(req.getJobType())); job.setTriggerType(TriggerType.valueOf(req.getTriggerType()));
        job.setCronExpression(req.getCronExpression()); job.setConditionExpression(req.getConditionExpression());
        job.setTimezone(req.getTimezone() == null || req.getTimezone().isBlank() ? "UTC" : req.getTimezone());
        job.setScheduleStartTime(parseDateTime(req.getScheduleStartTime(), "scheduleStartTime"));
        job.setScheduleEndTime(parseDateTime(req.getScheduleEndTime(), "scheduleEndTime"));
        job.setMisfirePolicy(parseMisfire(req.getMisfirePolicy()));
        job.setCommand(req.getCommand()); job.setHostName(req.getHostName()); job.setBox(box);
        job.setEnabled(req.getEnabled() == null || req.getEnabled());
        job.setExecutionType(req.getExecutionType() == null ? ExecutionType.SSH_SCRIPT : ExecutionType.valueOf(req.getExecutionType()));
        job.setRetryCount(req.getRetryCount() == null ? 0 : Math.max(0, req.getRetryCount()));
        job.setRetryIntervalSeconds(req.getRetryIntervalSeconds() == null ? 60 : Math.max(1, req.getRetryIntervalSeconds()));
        job.setTimeoutSeconds(req.getTimeoutSeconds() == null ? 0 : Math.max(0, req.getTimeoutSeconds()));
        job.setWorkingDirectory(req.getWorkingDirectory()); job.setArguments(req.getArguments());
        job.setConcurrencyPolicy(req.getConcurrencyPolicy() == null ? ConcurrencyPolicy.ALLOW : ConcurrencyPolicy.valueOf(req.getConcurrencyPolicy()));
        job.setUpdatedAt(LocalDateTime.now());
        jobDependencyRepository.deleteAll(jobDependencyRepository.findByJobId(id));
        Job saved = jobRepository.save(job);
        addDependencies(saved, req.getDependsOnJobIds());
        addConditionDependencies(saved);
        validateNoCycle(saved.getId());
        quartzSchedulerService.unscheduleJob(saved.getId());
        scheduleAfterCommit(saved);
        writeDefinitionLog(saved, "UPDATE");
        return toSummary(saved);
    }

    public JobSummaryResponse get(Long id) { return toSummary(findJob(id)); }

    @Transactional(readOnly = true)
    public com.mybank.autostone.dto.JobRelationsResponse getRelations(Long id) {
        Job job = findJob(id);
        List<JobSummaryResponse> parents = jobDependencyRepository.findByJobId(id).stream()
                .map(d -> toSummary(d.getDependsOnJob())).toList();
        List<JobSummaryResponse> children = jobDependencyRepository.findByDependsOnJobId(id).stream()
                .map(d -> toSummary(d.getJob())).toList();
        return com.mybank.autostone.dto.JobRelationsResponse.builder().parents(parents).children(children).build();
    }
    public List<JobSummaryResponse> getAllJobs() { return jobRepository.findAll().stream().map(this::toSummary).toList(); }

    @Transactional
    public void delete(Long id) {
        Job job = findJob(id);
        if (jobExecutionRepository.findFirstByJobIdAndStatusInOrderByIdDesc(id, List.of(JobStatus.RUNNING, JobStatus.QUEUED, JobStatus.WAITING, JobStatus.PAUSED, JobStatus.RETRY_WAIT)).isPresent())
            throw new IllegalStateException("Cannot delete job while it has an active execution");
        jobDependencyRepository.deleteAll(jobDependencyRepository.findByJobId(id));
        jobDependencyRepository.deleteAll(jobDependencyRepository.findByDependsOnJobId(id));
        quartzSchedulerService.unscheduleJob(id);
        jobRepository.delete(job);
    }

    private Job fromRequest(JobRequest r, Job box) {
        return Job.builder().name(r.getName().trim()).description(r.getDescription()).jobType(JobType.valueOf(r.getJobType()))
                .triggerType(TriggerType.valueOf(r.getTriggerType())).cronExpression(r.getCronExpression())
                .timezone(r.getTimezone() == null || r.getTimezone().isBlank() ? "UTC" : r.getTimezone())
                .scheduleStartTime(parseDateTime(r.getScheduleStartTime(), "scheduleStartTime"))
                .scheduleEndTime(parseDateTime(r.getScheduleEndTime(), "scheduleEndTime"))
                .misfirePolicy(parseMisfire(r.getMisfirePolicy()))
                .conditionExpression(r.getConditionExpression()).command(r.getCommand()).hostName(r.getHostName()).box(box)
                .enabled(r.getEnabled() == null || r.getEnabled())
                .executionType(r.getExecutionType() == null ? ExecutionType.SSH_SCRIPT : ExecutionType.valueOf(r.getExecutionType()))
                .retryCount(r.getRetryCount() == null ? 0 : Math.max(0, r.getRetryCount()))
                .retryIntervalSeconds(r.getRetryIntervalSeconds() == null ? 60 : Math.max(1, r.getRetryIntervalSeconds()))
                .timeoutSeconds(r.getTimeoutSeconds() == null ? 0 : Math.max(0, r.getTimeoutSeconds()))
                .workingDirectory(r.getWorkingDirectory()).arguments(r.getArguments())
                .concurrencyPolicy(r.getConcurrencyPolicy() == null ? ConcurrencyPolicy.ALLOW : ConcurrencyPolicy.valueOf(r.getConcurrencyPolicy()))
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
    }

    private void validateBoxParent(Job box, Long currentId) {
        if (box == null || currentId == null) return;
        Set<Long> seen = new HashSet<>();
        Job cursor = box;
        while (cursor != null) {
            if (!seen.add(cursor.getId())) throw new IllegalArgumentException("Circular BOX hierarchy detected");
            if (cursor.getId().equals(currentId)) throw new IllegalArgumentException("A BOX cannot contain itself or one of its ancestors");
            cursor = cursor.getBox();
        }
    }

    private void addDependencies(Job saved, List<Long> ids) {
        if (ids == null) return;
        for (Long parentId : new LinkedHashSet<>(ids)) {
            if (saved.getId().equals(parentId)) throw new IllegalArgumentException("A job cannot depend on itself");
            Job parent = jobRepository.findById(parentId).orElseThrow(() -> new NoSuchElementException("Dependency job not found: " + parentId));
            jobDependencyRepository.save(JobDependency.builder().job(saved).dependsOnJob(parent).build());
        }
    }

    private void validateNoCycle(Long start) {
        Set<Long> visiting = new HashSet<>(), visited = new HashSet<>();
        if (hasCycle(start, visiting, visited)) throw new IllegalArgumentException("Circular dependency detected");
    }
    private boolean hasCycle(Long id, Set<Long> visiting, Set<Long> visited) {
        if (!visiting.add(id)) return true;
        if (visited.contains(id)) { visiting.remove(id); return false; }
        for (JobDependency d : jobDependencyRepository.findByJobId(id)) if (hasCycle(d.getDependsOnJob().getId(), visiting, visited)) return true;
        visiting.remove(id); visited.add(id); return false;
    }

    private void validateRequest(JobRequest r) {
        if (r == null) throw new IllegalArgumentException("Request body is required");
        if (r.getName() == null || r.getName().isBlank()) throw new IllegalArgumentException("Job name is required");

        JobType type = parseEnum(JobType.class, r.getJobType(), "jobType");
        TriggerType trigger = parseEnum(TriggerType.class, r.getTriggerType(), "triggerType");

        if (type == JobType.JOB && (r.getCommand() == null || r.getCommand().isBlank())) {
            throw new IllegalArgumentException("command is required for JOB");
        }
        if (type == JobType.BOX && r.getCommand() != null && !r.getCommand().isBlank()) {
            throw new IllegalArgumentException("BOX jobs cannot have a command");
        }
        if (type == JobType.BOX && r.getExecutionType() != null && !r.getExecutionType().isBlank()
                && !ExecutionType.SSH_SCRIPT.name().equals(r.getExecutionType())) {
            throw new IllegalArgumentException("BOX jobs cannot use an execution type");
        }

        if (trigger == TriggerType.CRON && (r.getCronExpression() == null || r.getCronExpression().isBlank())) {
            throw new IllegalArgumentException("cronExpression is required for CRON");
        }
        if (r.getCronExpression() != null && !r.getCronExpression().isBlank()
                && trigger == TriggerType.CRON && !CronExpression.isValidExpression(r.getCronExpression())) {
            throw new IllegalArgumentException("Invalid Quartz cron expression: " + r.getCronExpression());
        }
        if (trigger == TriggerType.DEPENDENCY
                && (r.getDependsOnJobIds() == null || r.getDependsOnJobIds().isEmpty())
                && (r.getConditionExpression() == null || r.getConditionExpression().isBlank())) {
            throw new IllegalArgumentException("DEPENDENCY jobs require at least one parent job or a condition expression");
        }

        if (r.getConditionExpression() != null && !r.getConditionExpression().isBlank()) {
            ConditionEvaluator.extractReferencedJobNames(r.getConditionExpression());
        }

        if (r.getRetryCount() != null && r.getRetryCount() < 0) throw new IllegalArgumentException("retryCount cannot be negative");
        if (r.getRetryIntervalSeconds() != null && r.getRetryIntervalSeconds() < 1) throw new IllegalArgumentException("retryIntervalSeconds must be at least 1");
        if (r.getTimeoutSeconds() != null && r.getTimeoutSeconds() < 0) throw new IllegalArgumentException("timeoutSeconds cannot be negative");

        if (r.getTimezone() != null && !r.getTimezone().isBlank()) {
            try { ZoneId.of(r.getTimezone()); }
            catch (Exception e) { throw new IllegalArgumentException("Invalid timezone: " + r.getTimezone()); }
        }

        LocalDateTime scheduleStart = parseDateTime(r.getScheduleStartTime(), "scheduleStartTime");
        LocalDateTime scheduleEnd = parseDateTime(r.getScheduleEndTime(), "scheduleEndTime");
        if (scheduleStart != null && scheduleEnd != null && scheduleEnd.isBefore(scheduleStart)) {
            throw new IllegalArgumentException("scheduleEndTime cannot be before scheduleStartTime");
        }

        if (r.getMisfirePolicy() != null && !r.getMisfirePolicy().isBlank()) parseEnum(MisfirePolicy.class, r.getMisfirePolicy(), "misfirePolicy");
        if (r.getExecutionType() != null && !r.getExecutionType().isBlank()) parseEnum(ExecutionType.class, r.getExecutionType(), "executionType");
        if (r.getConcurrencyPolicy() != null && !r.getConcurrencyPolicy().isBlank()) parseEnum(ConcurrencyPolicy.class, r.getConcurrencyPolicy(), "concurrencyPolicy");
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        try { return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT)); }
        catch (Exception e) {
            throw new IllegalArgumentException("Unsupported " + field + ": " + value);
        }
    }

    private Job resolveBox(JobRequest req) {
        if (req.getBoxId() == null) {
            if (JobType.BOX.name().equalsIgnoreCase(req.getJobType())) return null;
            return null;
        }
        Job box = jobRepository.findById(req.getBoxId())
                .orElseThrow(() -> new NoSuchElementException("Box not found: " + req.getBoxId()));
        if (box.getJobType() != JobType.BOX) {
            throw new IllegalArgumentException("Job " + req.getBoxId() + " is not a BOX");
        }
        return box;
    }

    private void addConditionDependencies(Job job) {
        String expression = job.getConditionExpression();
        if (expression == null || expression.isBlank()) return;

        for (String name : ConditionEvaluator.extractReferencedJobNames(expression)) {
            Job parent = jobRepository.findByName(name)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Condition references unknown job '" + name + "'"));
            if (parent.getId().equals(job.getId())) {
                throw new IllegalArgumentException("A job cannot depend on itself: " + job.getName());
            }
            boolean alreadyLinked = jobDependencyRepository.findByJobId(job.getId()).stream()
                    .anyMatch(d -> d.getDependsOnJob().getId().equals(parent.getId()));
            if (!alreadyLinked) {
                jobDependencyRepository.save(JobDependency.builder().job(job).dependsOnJob(parent).build());
            }
        }
    }

    private void writeDefinitionLog(Job job, String operator) {
        if (job.getId() == null) return;
        String snapshot = "{\"name\":\"" + escape(job.getName()) + "\",\"type\":\"" + job.getJobType()
                + "\",\"trigger\":\"" + job.getTriggerType() + "\",\"command\":\"" + escape(job.getCommand()) + "\",\"host\":\"" + escape(job.getHostName()) + "\"}";
        definitionLogRepository.save(JobDefinitionLog.builder().code(job.getId()).version(job.getVersion() == null ? 1 : job.getVersion())
                .jobName(job.getName()).jobType(job.getJobType().name()).command(job.getCommand())
                .boxCode(job.getBox() == null ? null : job.getBox().getId()).definitionSnapshot(snapshot)
                .operator(operator).operateTime(LocalDateTime.now()).build());
    }

    private String escape(String value) { return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\""); }

    private LocalDateTime parseDateTime(String value, String field) {
        if (value == null || value.isBlank()) return null;
        try { return LocalDateTime.parse(value); }
        catch (Exception e) { throw new IllegalArgumentException(field + " must be ISO local date-time, for example 2026-08-24T10:00:00"); }
    }

    private MisfirePolicy parseMisfire(String value) {
        if (value == null || value.isBlank()) return MisfirePolicy.FIRE_NOW;
        try { return MisfirePolicy.valueOf(value); }
        catch (Exception e) { throw new IllegalArgumentException("Unsupported misfirePolicy: " + value); }
    }

    private Job findJob(Long id) { return jobRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Job not found: " + id)); }

    private void scheduleAfterCommit(Job job) {
        if (job.getTriggerType() != TriggerType.CRON || !Boolean.TRUE.equals(job.getEnabled())) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { quartzSchedulerService.scheduleCronJob(job); }
            });
        } else {
            quartzSchedulerService.scheduleCronJob(job);
        }
    }

    private JobSummaryResponse toSummary(Job job) {
        JobExecution latest = jobExecutionRepository.findFirstByJobIdOrderByIdDesc(job.getId()).orElse(null);
        return JobSummaryResponse.builder().id(job.getId()).name(job.getName()).jobType(job.getJobType().name())
                .boxId(job.getBox() != null ? job.getBox().getId() : null).dependsOnJobIds(jobDependencyRepository.findByJobId(job.getId()).stream().map(d -> d.getDependsOnJob().getId()).toList()).triggerType(job.getTriggerType() != null ? job.getTriggerType().name() : null)
                .cronExpression(job.getCronExpression()).timezone(job.getTimezone()).scheduleStartTime(job.getScheduleStartTime() == null ? null : job.getScheduleStartTime().toString()).scheduleEndTime(job.getScheduleEndTime() == null ? null : job.getScheduleEndTime().toString()).misfirePolicy(job.getMisfirePolicy() == null ? null : job.getMisfirePolicy().name()).description(job.getDescription()).command(job.getCommand()).hostName(job.getHostName())
                .enabled(job.getEnabled()).status(latest == null ? null : latest.getStatus().name()).retryCount(job.getRetryCount())
                .owner(job.getOwner()).permission(job.getPermission()).applicationName(job.getApplicationName()).groupName(job.getGroupName())
                .runCalendarName(job.getRunCalendarName()).startTimes(job.getStartTimes()).dateConditions(job.getDateConditions())
                .alarmIfFail(job.getAlarmIfFail()).alarmIfTerminated(job.getAlarmIfTerminated()).sendNotification(job.getSendNotification())
                .maxRunAlarmMin(job.getMaxRunAlarmMin()).avgRuntime(job.getAvgRuntime()).profile(job.getProfile())
                .stdOutFile(job.getStdOutFile()).stdErrFile(job.getStdErrFile()).nRetrys(job.getNRetrys())
                .retryIntervalSeconds(job.getRetryIntervalSeconds()).timeoutSeconds(job.getTimeoutSeconds())
                .executionType(job.getExecutionType() == null ? null : job.getExecutionType().name())
                .concurrencyPolicy(job.getConcurrencyPolicy() == null ? null : job.getConcurrencyPolicy().name())
                .conditionExpression(job.getConditionExpression()).hold(Boolean.TRUE.equals(job.getHold())).autoHold(Boolean.TRUE.equals(job.getAutoHold())).runtimeHold(jobExecutionRepository.findFirstByJobIdOrderByIdDesc(job.getId()).map(e -> Boolean.TRUE.equals(e.getRuntimeHold())).orElse(false)).ice(Boolean.TRUE.equals(job.getIce()))
                .noExecution(Boolean.TRUE.equals(job.getNoExecution())).workingDirectory(job.getWorkingDirectory()).arguments(job.getArguments())
                .version(job.getVersion()).build();
    }
}
