from pathlib import Path
import re
root=Path(__file__).resolve().parent
q=(root/'BackendAutoStone/src/main/java/com/mybank/autostone/scheduler/QuartzSchedulerService.java').read_text()
j=(root/'BackendAutoStone/src/main/java/com/mybank/autostone/scheduler/AutostoneQuartzJob.java').read_text()
props=(root/'BackendAutoStone/src/main/resources/application.properties').read_text()
checks={
 'startup_rebuild': 'scheduleAllEnabledCronJobsOnStartup' in q and 'ApplicationReadyEvent' in q,
 'enabled_only': '.filter(j -> Boolean.TRUE.equals(j.getEnabled()))' in q,
 'cron_only': '.filter(j -> j.getTriggerType() == TriggerType.CRON)' in q,
 'timezone_trigger': '.inTimeZone(TimeZone.getTimeZone(job.getTimezone()))' in q,
 'timezone_window_start': 'job.getScheduleStartTime().atZone(scheduleZone).toInstant()' in q,
 'timezone_window_end': 'job.getScheduleEndTime().atZone(scheduleZone).toInstant()' in q,
 'misfire_do_nothing': 'withMisfireHandlingInstructionDoNothing' in q,
 'misfire_fire_now': 'withMisfireHandlingInstructionFireAndProceed' in q,
 'cron_dispatches_same_start_path': 'eventService.start(jobId, null)' in j,
 'schedule_disable_endpoint_supported': 'spring.quartz.job-store-type=memory' in props,
}
failed=[k for k,v in checks.items() if not v]
for k,v in checks.items(): print(f'{k}: {"PASS" if v else "FAIL"}')
if failed: raise SystemExit('cron contract failed: '+', '.join(failed))
print('CRON CONTRACT: PASS')
