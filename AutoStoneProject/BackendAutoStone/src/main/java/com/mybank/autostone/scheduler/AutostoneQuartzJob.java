package com.mybank.autostone.scheduler;

import com.mybank.autostone.service.EventService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Fired by Quartz on each cron tick. Deliberately thin: it just calls EventService.start(),
 * the exact same entry point a manual "Start" click in the UI goes through. This means a
 * cron-triggered run and a manually-triggered run are indistinguishable once dispatched -
 * same run_id creation, same RemoteExecutionService path, same dependency-release logic.
 */
@Slf4j
public class AutostoneQuartzJob implements Job {

    @Autowired
    private EventService eventService;

    @Override
    public void execute(JobExecutionContext context) {
        Long jobId = context.getJobDetail().getJobDataMap().getLong("jobId");
        log.info("Quartz trigger fired for job {}", jobId);
        try {
            eventService.start(jobId, null);
        } catch (Exception e) {
            // swallow here - Quartz would otherwise log a raw stack trace and, depending on
            // config, could unschedule the trigger on a misfire; we'd rather it try again
            // next tick and rely on our own logs to surface the problem
            log.error("Quartz-triggered start failed for job {}: {}", jobId, e.getMessage(), e);
        }
    }
}
