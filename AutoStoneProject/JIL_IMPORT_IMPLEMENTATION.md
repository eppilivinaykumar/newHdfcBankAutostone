# AutoStone AutoSys JIL import

The Import JIL page accepts the AutoSys-style syntax used by the supplied example.

Example:

```text
insert_job: TEST_CBSGD_MONTHLY_EOD_B    job_type: box
owner: atsappsup@HBAUTOUTATAPPRI
group: MONTHLY_NDC
application: CBSG
permission: gx,wx
alarm_if_fail: n
alarm_if_terminated: y
date_conditions: y
start_times: "19:00"
run_calendar: MONTH_END
send_notification: n

insert_job: TEST_CBSGD.M.ATS_ACTIVE_C    job_type: cmd
machine: HBAUTOUTATAPPRI
box_name: TEST_CBSGD_MONTHLY_EOD_B
profile: /home/atsappsup/autoscp/ats.atsappsup.profile
std_out_file: /tmp/$AUTO_JOB_NAME.out
std_err_file: /tmp/$AUTO_JOB_NAME.err
command: $UCMDPATH/EODActive.sh CBSG TEST_CBSGD_MONTHLY_EOD_B
```

## Validation

`POST /api/jil/validate` parses and validates the complete JIL without saving job rows.
The importer validates duplicate names, job type, commands, BOX references, dependency
references/cycles, numeric values and schedule syntax.

## Import

`POST /api/jil/import` repeats validation and saves the complete batch in one transaction.
Jobs are created first, then `box_name` and condition dependencies are resolved after all
IDs exist. A failure rolls back the import.

## Scheduling

The demonstrated AutoSys schedule:

```text
date_conditions: y
start_times: "19:00"
run_calendar: MONTH_END
```

is converted to the Quartz expression:

```text
0 0 19 L * ?
```

`run_calendar_name`, `start_times` and the original scheduling flags are retained on the
Job definition. The current single-trigger Quartz model supports the built-in `MONTH_END`
and `DAILY` mappings. Other named/custom AutoSys calendars should be implemented as
calendar definitions before they are scheduled automatically.

## Execution metadata

The importer preserves the common AutoSys execution attributes on the Job entity,
including owner, permission, application, group, machine, profile, stdout/stderr paths,
retry count, alarms and average runtime.

The execution command builder sources the imported profile (when supplied), exports
`AUTO_JOB_NAME`, and applies imported stdout/stderr paths. This allows common paths such
as `/tmp/$AUTO_JOB_NAME.out` to work during execution.

## BOX / nested BOX import semantics

- `job_type: box` creates a BOX definition; it is not treated as a command job.
- `box_name: PARENT_BOX` stores the direct parent in `t_as_job_definition.box_code`. A BOX can itself have a `box_name`, so nested BOX levels are preserved.
- `condition: s(JOB_NAME)` is stored as the job condition and materialized into `t_as_job_condition` dependency rows.
- `date_conditions: y` + `start_times: "HH:mm"` + `run_calendar: MONTH_END` maps to the current Quartz cron model for the BOX.
- `auto_hold: y` is parsed and stored as the AutoSys definition flag. When the containing BOX enters a new execution, that child execution starts ON_HOLD/PAUSED until an OFF_HOLD action releases it.
- Import remains two-phase: `/api/jil/validate` parses and validates without writes; `/api/jil/import` revalidates and persists the complete batch transactionally.
