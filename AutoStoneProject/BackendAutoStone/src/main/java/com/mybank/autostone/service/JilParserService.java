package com.mybank.autostone.service;

import com.mybank.autostone.dto.JilImportResult;
import com.mybank.autostone.entity.Job;
import com.mybank.autostone.entity.JobDependency;
import com.mybank.autostone.entity.JobType;
import com.mybank.autostone.entity.TriggerType;
import com.mybank.autostone.repository.JobDependencyRepository;
import com.mybank.autostone.repository.JobRepository;
import com.mybank.autostone.scheduler.QuartzSchedulerService;
import com.mybank.autostone.util.ConditionEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Imports real AutoSys-style JIL into AutoStone.
 *
 * The importer deliberately follows a two-phase model:
 *   1) parse + validate the complete file without writing job rows;
 *   2) when requested, save all definitions and relationships in one transaction.
 *
 * The common AutoSys attributes are retained on the Job entity instead of silently
 * throwing them away. AutoStone-specific cron_expression is also accepted for jobs
 * that already use a Quartz expression.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JilParserService {

    private final JobRepository jobRepository;
    private final JobDependencyRepository jobDependencyRepository;
    private final QuartzSchedulerService quartzSchedulerService;

    private static final Pattern INSERT_JOB = Pattern.compile(
            "^insert_job\\s*:\\s*(\\S+)(?:\\s+job_type\\s*:\\s*(\\w+))?\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern KEY_VALUE = Pattern.compile("^([^:]+?)\\s*:\\s*(.*)$");

    private record JilJobDraft(
            String name,
            String jobType,
            String description,
            String command,
            String hostName,
            String boxName,
            String conditionExpr,
            String cronExpression,
            String owner,
            String permission,
            String application,
            String groupName,
            String runCalendarName,
            String startTimes,
            Boolean dateConditions,
            Boolean autoHold,
            Boolean alarmIfFail,
            Boolean alarmIfTerminated,
            Boolean sendNotification,
            Integer maxRunAlarmMin,
            Integer avgRuntime,
            String profile,
            String stdOutFile,
            String stdErrFile,
            Integer nRetrys,
            Boolean hold,
            Boolean ice,
            Boolean noExecution,
            Integer timeoutSeconds
    ) {}

    @Transactional
    public JilImportResult validateAndMaybeSave(String jilText, boolean save) {
        List<String> errors = new ArrayList<>();
        if (jilText == null || jilText.isBlank()) {
            return result(false, List.of("No JIL text provided"), List.of());
        }

        List<JilJobDraft> drafts = parseBlocks(jilText, errors);
        if (!errors.isEmpty()) return result(false, errors, List.of());

        Set<String> namesInBatch = new HashSet<>();
        Map<String, JilJobDraft> draftsByName = new HashMap<>();
        for (JilJobDraft d : drafts) {
            if (!namesInBatch.add(d.name())) errors.add("Duplicate job name '" + d.name() + "' within this import");
            draftsByName.put(d.name(), d);
        }
        for (JilJobDraft d : drafts) validateDraft(d, namesInBatch, draftsByName, errors);
        if (!errors.isEmpty()) return result(false, errors, List.of());

        List<String> names = drafts.stream().map(JilJobDraft::name).toList();
        if (!save) return result(true, List.of(), names);

        try {
            List<String> created = saveDrafts(drafts);
            return result(true, List.of(), created);
        } catch (RuntimeException e) {
            // The exception is intentionally converted to an import result for the UI, so
            // explicitly mark the surrounding transaction rollback-only. Otherwise Spring
            // would see a normal return and commit partially imported definitions.
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            log.error("JIL import rolled back: {}", e.getMessage(), e);
            return result(false, List.of("Import failed and was rolled back: " + e.getMessage()), List.of());
        }
    }

    private JilImportResult result(boolean success, List<String> errors, List<String> names) {
        return JilImportResult.builder().success(success).errors(errors).createdJobNames(names).build();
    }

    private List<JilJobDraft> parseBlocks(String text, List<String> errors) {
        List<List<String>> blocks = new ArrayList<>();
        List<String> current = null;
        for (String line : text.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.toLowerCase(Locale.ROOT).startsWith("insert_job")) {
                if (current != null) blocks.add(current);
                current = new ArrayList<>();
            }
            if (current != null) current.add(line);
        }
        if (current != null) blocks.add(current);
        if (blocks.isEmpty()) {
            errors.add("No 'insert_job:' blocks found in the provided text");
            return List.of();
        }

        List<JilJobDraft> drafts = new ArrayList<>();
        for (List<String> block : blocks) {
            try { drafts.add(parseBlock(block)); }
            catch (IllegalArgumentException e) { errors.add(e.getMessage()); }
        }
        return drafts;
    }

    private JilJobDraft parseBlock(List<String> lines) {
        String header = lines.get(0).trim();
        Matcher headerMatcher = INSERT_JOB.matcher(header);
        if (!headerMatcher.matches()) throw new IllegalArgumentException("Malformed insert_job header: '" + header + "'");

        String name = headerMatcher.group(1).trim();
        String inlineType = headerMatcher.group(2);
        Map<String, String> attrs = new LinkedHashMap<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty() || line.startsWith("#") || line.equals("-")) continue;
            Matcher kv = KEY_VALUE.matcher(line);
            if (!kv.matches()) continue;
            String key = kv.group(1).trim().toLowerCase(Locale.ROOT);
            attrs.put(key, stripQuotes(kv.group(2).trim()));
        }

        String rawType = inlineType == null || inlineType.isBlank() ? attrs.get("job_type") : inlineType;
        if (rawType == null || rawType.isBlank()) throw new IllegalArgumentException("Job '" + name + "': job_type is required");
        String jobType = switch (rawType.trim().toUpperCase(Locale.ROOT)) {
            case "C", "CMD" -> "JOB";
            case "B", "BOX" -> "BOX";
            default -> throw new IllegalArgumentException("Job '" + name + "': unknown job_type '" + rawType + "' (expected c/cmd or b/box)");
        };

        return new JilJobDraft(
                name,
                jobType,
                attrs.get("description"),
                attrs.get("command"),
                attrs.get("machine"),
                attrs.get("box_name"),
                attrs.get("condition"),
                attrs.get("cron_expression"),
                attrs.get("owner"),
                attrs.get("permission"),
                attrs.get("application"),
                attrs.get("group"),
                attrs.get("run_calendar"),
                attrs.get("start_times"),
                parseBoolean(attrs.get("date_conditions"), "date_conditions", name),
                parseBoolean(attrs.get("auto_hold"), "auto_hold", name),
                parseBoolean(attrs.get("alarm_if_fail"), "alarm_if_fail", name),
                parseBoolean(attrs.get("alarm_if_terminated"), "alarm_if_terminated", name),
                parseBoolean(attrs.get("send_notification"), "send_notification", name),
                parseInteger(attrs.get("max_run_alarm"), "max_run_alarm", name),
                parseInteger(attrs.get("avg_runtime"), "avg_runtime", name),
                attrs.get("profile"),
                attrs.get("std_out_file"),
                attrs.get("std_err_file"),
                parseInteger(attrs.get("n_retrys"), "n_retrys", name),
                parseBoolean(attrs.get("hold"), "hold", name),
                parseBoolean(attrs.get("ice"), "ice", name),
                parseBoolean(attrs.get("no_execution"), "no_execution", name),
                parseInteger(attrs.get("timeout"), "timeout", name)
        );
    }

    private String stripQuotes(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) return value.substring(1, value.length() - 1);
        return value;
    }

    private Boolean parseBoolean(String value, String field, String jobName) {
        if (value == null || value.isBlank()) return null;
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "y", "yes", "true", "1" -> true;
            case "n", "no", "false", "0" -> false;
            default -> throw new IllegalArgumentException("Job '" + jobName + "': " + field + " must be y/n, yes/no, true/false, or 1/0");
        };
    }

    private Integer parseInteger(String value, String field, String jobName) {
        if (value == null || value.isBlank()) return null;
        try { return Integer.valueOf(value.trim()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Job '" + jobName + "': " + field + " must be an integer"); }
    }

    private void validateDraft(JilJobDraft d, Set<String> namesInBatch, Map<String, JilJobDraft> draftsByName, List<String> errors) {
        if (jobRepository.existsByName(d.name())) errors.add("Job '" + d.name() + "' already exists");

        if ("JOB".equals(d.jobType()) && (d.command() == null || d.command().isBlank()))
            errors.add("Job '" + d.name() + "' is job_type CMD but has no command");
        if ("BOX".equals(d.jobType()) && d.command() != null && !d.command().isBlank())
            errors.add("BOX '" + d.name() + "' cannot have a command");

        if (Boolean.TRUE.equals(d.autoHold()) && (d.boxName() == null || d.boxName().isBlank())) {
            errors.add("Job '" + d.name() + "': auto_hold is valid only for a job inside a BOX");
        }

        if (d.boxName() != null && !d.boxName().isBlank()) {
            boolean dbBox = jobRepository.findByName(d.boxName()).map(j -> j.getJobType() == JobType.BOX).orElse(false);
            boolean batchBox = Optional.ofNullable(draftsByName.get(d.boxName())).map(x -> "BOX".equals(x.jobType())).orElse(false);
            if (!dbBox && !batchBox) errors.add("Job '" + d.name() + "' references box_name '" + d.boxName() + "' which is not an existing BOX");
        }

        if (d.conditionExpr() != null && !d.conditionExpr().isBlank()) {
            try {
                for (String ref : ConditionEvaluator.extractReferencedJobNames(d.conditionExpr())) {
                    if (!jobRepository.existsByName(ref) && !namesInBatch.contains(ref)) errors.add("Job '" + d.name() + "' condition references unknown job '" + ref + "'");
                }
            } catch (IllegalArgumentException e) { errors.add("Job '" + d.name() + "' has an invalid condition: " + e.getMessage()); }
        }

        if (d.cronExpression() != null && !d.cronExpression().isBlank() && !CronExpression.isValidExpression(d.cronExpression()))
            errors.add("Job '" + d.name() + "' has an invalid cron_expression: '" + d.cronExpression() + "'");

        if (d.startTimes() != null && !d.startTimes().isBlank()) {
            try {
                String cron = autoSysCron(d);
                if (cron == null && (d.cronExpression() == null || d.cronExpression().isBlank()))
                    errors.add("Job '" + d.name() + "' uses start_times/run_calendar that AutoStone cannot schedule yet. Supported built-in calendars: MONTH_END, DAILY");
                else if (cron != null && !CronExpression.isValidExpression(cron))
                    errors.add("Job '" + d.name() + "' produced an invalid Quartz schedule from start_times/run_calendar: " + cron);
            } catch (IllegalArgumentException e) {
                errors.add("Job '" + d.name() + "': " + e.getMessage());
            }
        }
        if (d.maxRunAlarmMin() != null && d.maxRunAlarmMin() < 0) errors.add("Job '" + d.name() + "': max_run_alarm cannot be negative");
        if (d.avgRuntime() != null && d.avgRuntime() < 0) errors.add("Job '" + d.name() + "': avg_runtime cannot be negative");
        if (d.nRetrys() != null && d.nRetrys() < 0) errors.add("Job '" + d.name() + "': n_retrys cannot be negative");
        if (d.timeoutSeconds() != null && d.timeoutSeconds() < 0) errors.add("Job '" + d.name() + "': timeout cannot be negative");
    }

    /**
     * Converts the demonstrated AutoSys calendar/time pair to Quartz.
     * MONTH_END at 19:00 -> 0 0 19 L * ?
     */
    private String autoSysCron(JilJobDraft d) {
        if (d.startTimes() == null || d.startTimes().isBlank()) return null;
        String[] times = d.startTimes().split(",");
        if (times.length != 1) throw new IllegalArgumentException("Multiple start_times are not supported by the current single-trigger Quartz model; import one time per job");
        String[] hm = times[0].trim().split(":");
        if (hm.length != 2) throw new IllegalArgumentException("start_times must use HH:mm, for example 19:00");
        int hour;
        int minute;
        try { hour = Integer.parseInt(hm[0]); minute = Integer.parseInt(hm[1]); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("start_times must use HH:mm, for example 19:00"); }
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) throw new IllegalArgumentException("start_times must use a valid 24-hour time");

        String calendar = d.runCalendarName() == null ? "" : d.runCalendarName().trim().toUpperCase(Locale.ROOT);
        boolean dateConditions = Boolean.TRUE.equals(d.dateConditions());
        if (!dateConditions) return null;
        return switch (calendar) {
            case "MONTH_END" -> String.format("0 %d %d L * ?", minute, hour);
            case "DAILY" -> String.format("0 %d %d * * ?", minute, hour);
            default -> null;
        };
    }

    public List<String> saveDrafts(List<JilJobDraft> drafts) {
        Map<String, Job> byName = new LinkedHashMap<>();
        for (JilJobDraft d : drafts) {
            boolean isBox = "BOX".equals(d.jobType());
            String cron = d.cronExpression();
            if (cron == null || cron.isBlank()) cron = autoSysCron(d);
            TriggerType triggerType = d.conditionExpr() != null && !d.conditionExpr().isBlank()
                    ? TriggerType.DEPENDENCY
                    : cron != null && !cron.isBlank() ? TriggerType.CRON : TriggerType.MANUAL;

            Job job = Job.builder()
                    .name(d.name())
                    .description(d.description())
                    .jobType(isBox ? JobType.BOX : JobType.JOB)
                    .triggerType(triggerType)
                    .cronExpression(cron)
                    .conditionExpression(d.conditionExpr())
                    .command(isBox ? null : d.command())
                    .hostName(isBox ? null : d.hostName())
                    .owner(d.owner())
                    .permission(d.permission())
                    .applicationName(d.application())
                    .groupName(d.groupName())
                    .runCalendarName(d.runCalendarName())
                    .startTimes(d.startTimes())
                    .dateConditions(d.dateConditions())
                    .autoHold(Boolean.TRUE.equals(d.autoHold()))
                    .alarmIfFail(d.alarmIfFail())
                    .alarmIfTerminated(d.alarmIfTerminated())
                    .sendNotification(d.sendNotification())
                    .maxRunAlarmMin(d.maxRunAlarmMin())
                    .avgRuntime(d.avgRuntime())
                    .profile(d.profile())
                    .stdOutFile(d.stdOutFile())
                    .stdErrFile(d.stdErrFile())
                    .nRetrys(d.nRetrys())
                    .hold(Boolean.TRUE.equals(d.hold())).ice(Boolean.TRUE.equals(d.ice())).noExecution(Boolean.TRUE.equals(d.noExecution()))
                    .timeoutSeconds(d.timeoutSeconds() == null ? 0 : d.timeoutSeconds())
                    .retryCount(d.nRetrys() == null ? 0 : Math.max(0, d.nRetrys()))
                    .enabled(true)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            byName.put(d.name(), jobRepository.save(job));
        }

        for (JilJobDraft d : drafts) {
            Job job = byName.get(d.name());
            if (d.boxName() != null && !d.boxName().isBlank()) {
                Job box = byName.containsKey(d.boxName()) ? byName.get(d.boxName()) : jobRepository.findByName(d.boxName()).orElseThrow();
                if (box.getJobType() != JobType.BOX) throw new IllegalArgumentException("box_name '" + d.boxName() + "' is not a BOX");
                job.setBox(box);
                jobRepository.save(job);
            }
            if (d.conditionExpr() != null && !d.conditionExpr().isBlank()) {
                for (String parentName : ConditionEvaluator.extractReferencedJobNames(d.conditionExpr())) {
                    Job parent = byName.containsKey(parentName) ? byName.get(parentName) : jobRepository.findByName(parentName).orElseThrow();
                    if (!parent.getId().equals(job.getId())) {
                        jobDependencyRepository.save(JobDependency.builder().job(job).dependsOnJob(parent).build());
                    }
                }
            }
        }

        validateImportedBoxHierarchy(byName.values());
        validateImportedGraph(byName.values());
        List<Job> cronJobs = byName.values().stream()
                .filter(job -> job.getTriggerType() == TriggerType.CRON && Boolean.TRUE.equals(job.getEnabled()))
                .toList();
        if (!cronJobs.isEmpty() && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { cronJobs.forEach(quartzSchedulerService::scheduleCronJob); }
            });
        } else {
            cronJobs.forEach(quartzSchedulerService::scheduleCronJob);
        }
        log.info("JIL import created {} job(s): {}", byName.size(), byName.keySet());
        return new ArrayList<>(byName.keySet());
    }

    private void validateImportedBoxHierarchy(Collection<Job> jobs) {
        for (Job job : jobs) {
            Set<Long> seen = new HashSet<>();
            Job cursor = job;
            while (cursor != null && cursor.getBox() != null) {
                if (!seen.add(cursor.getId())) throw new IllegalArgumentException("Circular BOX hierarchy detected near job '" + job.getName() + "'");
                cursor = cursor.getBox();
            }
        }
    }

    private void validateImportedGraph(Collection<Job> jobs) {
        Set<Long> visiting = new HashSet<>(), visited = new HashSet<>();
        for (Job job : jobs) if (hasCycle(job.getId(), visiting, visited))
            throw new IllegalArgumentException("Circular dependency detected in JIL import near job '" + job.getName() + "'");
    }

    private boolean hasCycle(Long id, Set<Long> visiting, Set<Long> visited) {
        if (visited.contains(id)) return false;
        if (!visiting.add(id)) return true;
        for (JobDependency dependency : jobDependencyRepository.findByJobId(id))
            if (hasCycle(dependency.getDependsOnJob().getId(), visiting, visited)) return true;
        visiting.remove(id);
        visited.add(id);
        return false;
    }
}
