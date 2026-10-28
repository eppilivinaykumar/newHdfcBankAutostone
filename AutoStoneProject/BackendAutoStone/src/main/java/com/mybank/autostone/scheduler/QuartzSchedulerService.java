package com.mybank.autostone.scheduler;

import com.mybank.autostone.entity.Job;
import com.mybank.autostone.entity.TriggerType;
import com.mybank.autostone.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.List;
import java.util.TimeZone;

/**
 * Sole owner of Quartz trigger registration. We use Quartz's default in-memory RAMJobStore
 * (schedules are lost on app restart) rather than a JDBC job store - that's fine because
 * scheduleAllEnabledCronJobsOnStartup() re-derives every trigger from the `job` table on
 * every boot anyway, so the DB (not Quartz's own store) stays the single source of truth
 * for "what should be scheduled".
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuartzSchedulerService {

    private static final String GROUP = "autostone";

    private final Scheduler scheduler;
    private final JobRepository jobRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void scheduleAllEnabledCronJobsOnStartup() {
        List<Job> cronJobs = jobRepository.findAll().stream()
                .filter(j -> j.getTriggerType() == TriggerType.CRON)
                .filter(j -> Boolean.TRUE.equals(j.getEnabled()))
                .filter(j -> j.getCronExpression() != null && !j.getCronExpression().isBlank())
                .toList();

        cronJobs.forEach(this::scheduleCronJob);
        log.info("Registered {} cron job(s) with Quartz on startup", cronJobs.size());
    }

    public void scheduleCronJob(Job job) {
        try {
            JobKey jobKey = JobKey.jobKey("job-" + job.getId(), GROUP);

            JobDetail detail = JobBuilder.newJob(AutostoneQuartzJob.class)
                    .withIdentity(jobKey)
                    .usingJobData("jobId", job.getId())
                    .storeDurably()
                    .build();

            CronScheduleBuilder schedule = CronScheduleBuilder.cronSchedule(job.getCronExpression());
            if (job.getMisfirePolicy() == com.mybank.autostone.entity.MisfirePolicy.DO_NOTHING) {
                schedule = schedule.withMisfireHandlingInstructionDoNothing();
            } else {
                schedule = schedule.withMisfireHandlingInstructionFireAndProceed();
            }
            if (job.getTimezone() != null && !job.getTimezone().isBlank()) {
                schedule = schedule.inTimeZone(TimeZone.getTimeZone(job.getTimezone()));
            }
            TriggerBuilder<CronTrigger> triggerBuilder = TriggerBuilder.newTrigger()
                    .withIdentity("trigger-" + job.getId(), GROUP)
                    .forJob(detail)
                    .withSchedule(schedule);
            ZoneId scheduleZone = ZoneId.of(job.getTimezone() == null || job.getTimezone().isBlank() ? "UTC" : job.getTimezone());
            if (job.getScheduleStartTime() != null) {
                triggerBuilder.startAt(java.util.Date.from(job.getScheduleStartTime().atZone(scheduleZone).toInstant()));
            }
            if (job.getScheduleEndTime() != null) {
                triggerBuilder.endAt(java.util.Date.from(job.getScheduleEndTime().atZone(scheduleZone).toInstant()));
            }
            CronTrigger trigger = triggerBuilder.build();

            if (scheduler.checkExists(jobKey)) {
                scheduler.deleteJob(jobKey); // reschedule cleanly rather than layering triggers
            }
            scheduler.scheduleJob(detail, trigger);
            log.info("Scheduled job {} ({}) with cron '{}'", job.getId(), job.getName(), job.getCronExpression());
        } catch (SchedulerException e) {
            log.error("Failed to schedule job {}: {}", job.getId(), e.getMessage(), e);
        }
    }

    /**
     * Cancels the NEXT scheduled fire only. This is schedule control, not execution control -
     * a run already dispatched and in flight is completely untouched by this call. Maps to
     * the "EXHAUST"-style semantics from the design discussion: let the current run finish,
     * just don't fire again.
     */
    public void unscheduleJob(Long jobId) {
        try {
            JobKey jobKey = JobKey.jobKey("job-" + jobId, GROUP);
            if (scheduler.checkExists(jobKey)) {
                scheduler.deleteJob(jobKey);
                log.info("Unscheduled job {}", jobId);
            }
        } catch (SchedulerException e) {
            log.error("Failed to unschedule job {}: {}", jobId, e.getMessage(), e);
        }
    }
}
