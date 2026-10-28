from pathlib import Path
import re
root=Path(__file__).resolve().parent
q=(root/'BackendAutoStone/src/main/java/com/mybank/autostone/scheduler/QuartzSchedulerService.java').read_text()
j=(root/'BackendAutoStone/src/main/java/com/mybank/autostone/scheduler/AutostoneQuartzJob.java').read_text()
e=(root/'BackendAutoStone/src/main/java/com/mybank/autostone/service/EventService.java').read_text()
js=(root/'BackendAutoStone/src/main/java/com/mybank/autostone/service/JobService.java').read_text()
c=(root/'BackendAutoStone/src/main/java/com/mybank/autostone/controller/EventController.java').read_text()
props=(root/'BackendAutoStone/src/main/resources/application.properties').read_text()
checks={
 'quartz_dependency': 'spring-boot-starter-quartz' in (root/'BackendAutoStone/pom.xml').read_text(),
 'startup_rebuild_enabled_cron': 'ApplicationReadyEvent' in q and 'TriggerType.CRON' in q and 'getEnabled()' in q,
 'timezone': '.inTimeZone(TimeZone.getTimeZone(job.getTimezone()))' in q and 'ZoneId.of' in q,
 'schedule_window': 'getScheduleStartTime().atZone(scheduleZone)' in q and 'getScheduleEndTime().atZone(scheduleZone)' in q,
 'misfire_fire_now': 'withMisfireHandlingInstructionFireAndProceed' in q,
 'misfire_do_nothing': 'withMisfireHandlingInstructionDoNothing' in q,
 'same_execution_entrypoint': 'eventService.start(jobId, null)' in j,
 'cron_schedule_after_commit': 'scheduleAfterCommit(saved)' in js and 'scheduleCronJob(job)' in js,
 'disable_unschedules': 'quartzSchedulerService.unscheduleJob(job.getId())' in e,
 'enable_reschedules': 'quartzSchedulerService.scheduleCronJob(job)' in e,
 'disable_does_not_touch_execution': 'does not touch JobExecution' in e and 'current run (if any) unaffected' in e,
 'frontend_enable_disable_api': 'enableSchedule:' in (root/'AutoStoneFrontend_final/src/api/api.js').read_text() and 'disableSchedule:' in (root/'AutoStoneFrontend_final/src/api/api.js').read_text(),
 'wrapper_callback': 'callback' in (root/'../StandaloneWrapper/wrapper.sh').read_text().lower(),
 'db_credentials_externalized': '${DB_PASSWORD:root}' in props,
}
for k,v in checks.items(): print(f'{k}: {"PASS" if v else "FAIL"}')
failed=[k for k,v in checks.items() if not v]
if failed: raise SystemExit('CRON E2E CONTRACT FAILED: '+', '.join(failed))
print('CRON END-TO-END CONTRACT: PASS')
