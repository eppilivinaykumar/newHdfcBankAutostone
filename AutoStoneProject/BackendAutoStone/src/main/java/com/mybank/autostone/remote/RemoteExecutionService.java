package com.mybank.autostone.remote;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.mybank.autostone.config.RemoteExecutionProperties;
import com.mybank.autostone.entity.ExecutionType;
import com.mybank.autostone.entity.Job;
import com.mybank.autostone.entity.JobExecution;
import com.mybank.autostone.util.ShellUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * Single point of contact for making something actually happen on a machine, remote or local.
 *
 * Dispatch is fire-and-forget: the wrapper script backgrounds the real command with setsid,
 * writes its own PID immediately, and this method returns as soon as that PID line comes back
 * over the (short-lived) SSH exec channel - it does NOT block for the job's runtime. Completion
 * arrives later as an inbound POST to /internal/job-callback (from the wrapper's trailing curl,
 * or from this same class's local-exec watcher thread for LOCAL_SCRIPT jobs).
 *
 * Every caller (EventService for manual start/stop/pause/resume, the Quartz job for scheduled
 * triggers, DependencyResolverService when releasing downstream jobs) goes through this one
 * class, so there is exactly one place that knows how to reach a machine. Swapping the wrapper
 * transport for a real remote agent later only changes the inside of execSsh/execLocal.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RemoteExecutionService {

    private final RemoteExecutionProperties props;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    // ------------------------------------------------------------------
    // Dispatch (START)
    // ------------------------------------------------------------------

    public boolean checkConnectivity(String host) {
        if (isLocalHost(host)) return true;
        Session session = null;
        try {
            JSch jsch = new JSch();
            jsch.addIdentity(props.getSshKeyPath());
            session = jsch.getSession(props.getSshUser(), host, props.getSshPort());
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(Math.min(props.getConnectTimeoutMs(), 5000));
            return session.isConnected();
        } catch (Exception e) {
            return false;
        } finally {
            if (session != null && session.isConnected()) session.disconnect();
        }
    }

    public RemoteCommandResult dispatchStart(Job job, JobExecution exec) {
        if (exec == null || exec.getRunId() == null) {
            return new RemoteCommandResult(false, null, null,
                    "Cannot dispatch job " + (job == null ? "?" : job.getName()) + ": execution has no run id");
        }
        String runId = exec.getRunId().toString();

        if (isLocal(job)) {
            return execLocalStart(job, runId, exec.getId());
        }

        // A JOB that is not LOCAL_SCRIPT and has no host_name would otherwise fall through
        // to execSsh() with a null host, which JSch turns into an opaque "SSH error to
        // null: ..." failure. Fail with a clear, actionable message instead.
        if (job.getHostName() == null || job.getHostName().isBlank()) {
            return new RemoteCommandResult(false, null, null,
                    "Job '" + job.getName() + "' has no host_name configured and its execution_type is not LOCAL_SCRIPT; "
                            + "set a host or switch execution_type to LOCAL_SCRIPT before starting it");
        }

        String callbackUrl = props.getCallbackBaseUrl() + "/internal/job-callback";
        String command = ShellUtil.quote(props.getWrapperPath()) + " start "
                + ShellUtil.quote(runId) + " "
                + job.getId() + " "
                + exec.getId() + " "
                + ShellUtil.quote(callbackUrl) + " "
                + Math.max(0, job.getTimeoutSeconds() == null ? 0 : job.getTimeoutSeconds()) + " "
                + ShellUtil.quote(job.getWorkingDirectory() == null ? "" : job.getWorkingDirectory()) + " "
                + ShellUtil.quote(buildCommand(job)) + " "
                + ShellUtil.quote(props.getCallbackSecretFile());

        log.info("Dispatching job {} ({}) to {}", job.getId(), job.getName(), job.getHostName());
        return execSsh(job.getHostName(), command);
    }

    // ------------------------------------------------------------------
    // Signal (STOP / KILL / PAUSE / RESUME) - always keyed off the PID
    // captured at dispatch time, never re-derived from the Job row
    // ------------------------------------------------------------------

    public RemoteCommandResult sendSignal(JobExecution exec, RemoteSignal signal) {
        if (exec.getPid() == null) {
            return new RemoteCommandResult(false, null, null,
                    "No PID recorded for execution " + exec.getId() + " - cannot send " + signal);
        }
        String pid = String.valueOf(exec.getPid());
        if (exec.getHost() == null || isLocalHost(exec.getHost())) {
            return signalLocal(pid, signal);
        }

        String command = ShellUtil.quote(props.getWrapperPath()) + " signal " + pid + " " + signal.posixSignal();
        log.info("Sending {} to pid {} on {} (execution {})", signal, exec.getPid(), exec.getHost(), exec.getId());
        return execSsh(exec.getHost(), command);
    }

    // ------------------------------------------------------------------
    // SSH transport (JSch) - short-lived connection, one exec channel
    // ------------------------------------------------------------------

    private RemoteCommandResult execSsh(String host, String command) {
        Session session = null;
        ChannelExec channel = null;
        try {
            JSch jsch = new JSch();
            jsch.addIdentity(props.getSshKeyPath());

            session = jsch.getSession(props.getSshUser(), host, props.getSshPort());
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(props.getConnectTimeoutMs());

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);

            ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
            ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
            channel.setOutputStream(outBuf);
            channel.setErrStream(errBuf);
            channel.connect(props.getConnectTimeoutMs());

            // the wrapper is designed to return almost immediately (it backgrounds the real
            // work); a generous-but-bounded wait here just guards against a hung SSH channel
            long deadline = System.currentTimeMillis() + 15_000;
            while (!channel.isClosed()) {
                if (System.currentTimeMillis() > deadline) {
                    return new RemoteCommandResult(false, null, outBuf.toString(StandardCharsets.UTF_8),
                            "Timed out waiting for wrapper script to return on " + host);
                }
                Thread.sleep(50);
            }

            int exitStatus = channel.getExitStatus();
            String out = outBuf.toString(StandardCharsets.UTF_8);
            String err = errBuf.toString(StandardCharsets.UTF_8);

            if (exitStatus != 0) {
                return new RemoteCommandResult(false, null, out,
                        "Wrapper exited " + exitStatus + " on " + host + (err.isBlank() ? "" : (": " + err.trim())));
            }
            return new RemoteCommandResult(true, extractPid(out), out, null, extractLogPath(out));

        } catch (Exception e) {
            log.warn("SSH call to {} failed: {}", host, e.getMessage());
            return new RemoteCommandResult(false, null, null, "SSH error to " + host + ": " + e.getMessage());
        } finally {
            if (channel != null && channel.isConnected()) channel.disconnect();
            if (session != null && session.isConnected()) session.disconnect();
        }
    }

    private String extractLogPath(String wrapperOutput) {
        if (wrapperOutput == null) return null;
        for (String line : wrapperOutput.split("\\R")) {
            line = line.trim();
            if (line.startsWith("LOG:")) return line.substring(4).trim();
        }
        return null;
    }

    private String buildCommand(Job job) {
        String cmd = job.getCommand() == null ? "" : job.getCommand();
        if (job.getArguments() != null && !job.getArguments().isBlank()) cmd += " " + job.getArguments();

        // Preserve the common AutoSys execution attributes imported from JIL.
        // The profile is sourced before the command, and AUTO_JOB_NAME is exported so
        // paths such as /tmp/$AUTO_JOB_NAME.out behave as they do in AutoSys.
        StringBuilder wrapped = new StringBuilder();
        wrapped.append("export AUTO_JOB_NAME=").append(ShellUtil.quote(job.getName())).append("; ");
        if (job.getProfile() != null && !job.getProfile().isBlank()) {
            wrapped.append("source ").append(ShellUtil.quote(job.getProfile())).append(" && ");
        }
        wrapped.append(cmd);
        if (job.getStdOutFile() != null && !job.getStdOutFile().isBlank()) {
            wrapped.append(" > ").append(job.getStdOutFile());
        }
        if (job.getStdErrFile() != null && !job.getStdErrFile().isBlank()) {
            wrapped.append(" 2> ").append(job.getStdErrFile());
        }
        return wrapped.toString();
    }

    private String extractPid(String wrapperOutput) {
        if (wrapperOutput == null) return null;
        for (String line : wrapperOutput.split("\\R")) {
            line = line.trim();
            if (line.startsWith("PID:")) {
                String pid = line.substring("PID:".length()).trim();
                return "UNKNOWN".equals(pid) ? null : pid;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Local execution path (Job.executionType == LOCAL_SCRIPT, or
    // hostName is one of autostone.remote.local-hostnames) - no SSH hop,
    // but completion still round-trips through the same callback endpoint
    // so there is exactly one "job finished" code path in the system.
    // ------------------------------------------------------------------

    private RemoteCommandResult execLocalStart(Job job, String runId, Long executionId) {
        try {
            String logPath = "/tmp/autostone_local_" + runId + "_" + job.getId() + ".log";
            String command = buildCommand(job);
            if (job.getTimeoutSeconds() != null && job.getTimeoutSeconds() > 0) {
                command = "timeout --signal=TERM --kill-after=10s " + job.getTimeoutSeconds() + "s bash -c " + ShellUtil.quote(command);
            }
            // Start a dedicated session so STOP/KILL can target the complete process tree
            // using the same process-group semantics as the remote wrapper.
            ProcessBuilder pb = new ProcessBuilder("setsid", "bash", "-c", command);
            if (job.getWorkingDirectory() != null && !job.getWorkingDirectory().isBlank()) pb.directory(new File(job.getWorkingDirectory()));
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.to(new File(logPath)));
            Process process = pb.start();
            String pid = String.valueOf(process.pid());

            Thread watcher = new Thread(() -> {
                try {
                    int exit = process.waitFor();
                    postCallback(runId, job.getId(), executionId, exit);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }, "autostone-local-watch-job-" + job.getId());
            watcher.setDaemon(true);
            watcher.start();

            return new RemoteCommandResult(true, pid, "PID:" + pid + "\nLOG:" + logPath, null, logPath);
        } catch (Exception e) {
            log.warn("Local exec of job {} failed: {}", job.getId(), e.getMessage());
            return new RemoteCommandResult(false, null, null, "Local exec error: " + e.getMessage());
        }
    }

    private RemoteCommandResult signalLocal(String pidStr, RemoteSignal signal) {
        try {
            long pid = Long.parseLong(pidStr);
            Optional<ProcessHandle> ph = ProcessHandle.of(pid);
            if (ph.isEmpty() || !ph.get().isAlive()) {
                return new RemoteCommandResult(false, null, null, "No such local process: " + pid);
            }
            // The tracked PID is the session leader. Signal the whole process group first
            // so descendants (including timeout/bash children) cannot survive an operator action.
            ProcessBuilder groupSignal = new ProcessBuilder("kill", "-s", signal.posixSignal(), "--", "-" + pidStr);
            Process proc = groupSignal.start();
            int rc = proc.waitFor();
            if (rc != 0) {
                // Fallback for environments where negative process-group signalling is unavailable.
                new ProcessBuilder("kill", "-s", signal.posixSignal(), pidStr).start().waitFor();
            }
            return new RemoteCommandResult(true, pidStr, "OK", null);
        } catch (Exception e) {
            return new RemoteCommandResult(false, null, null, "Local signal error: " + e.getMessage());
        }
    }

    private void postCallback(String runId, Long jobId, Long executionId, int exitCode) {
        try {
            String body = "{\"runId\":\"" + runId + "\",\"jobId\":" + jobId + ",\"executionId\":" + executionId + ",\"exitCode\":" + exitCode + "}";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(props.getCallbackBaseUrl() + "/internal/job-callback"))
                    .header("Content-Type", "application/json")
                    .header("X-AutoStone-Callback-Secret", props.getCallbackSecret())
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<Void> resp = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
            if (resp.statusCode() >= 300) {
                log.warn("Local job {} callback returned status {}", jobId, resp.statusCode());
            }
        } catch (Exception e) {
            log.error("Failed to post local completion callback for job {}: {}", jobId, e.getMessage());
        }
    }

    public String readExecutionLog(JobExecution exec) {
        return readExecutionLog(exec, 200_000);
    }

    /** Returns only the tail of a potentially large execution log. */
    public String readExecutionLog(JobExecution exec, int maxChars) {
        String path = exec.getLogPath();
        if (path == null || path.isBlank()) return "";
        int safeMax = Math.max(1_000, Math.min(maxChars, 1_000_000));
        try {
            if (exec.getHost() == null || isLocalHost(exec.getHost())) {
                java.nio.file.Path p = java.nio.file.Path.of(path);
                if (!java.nio.file.Files.exists(p)) return "";
                String value = java.nio.file.Files.readString(p);
                return value.length() <= safeMax ? value : value.substring(value.length() - safeMax);
            }
            RemoteCommandResult r = execSsh(exec.getHost(), "tail -c " + safeMax + " " + ShellUtil.quote(path));
            return r.isSuccess() ? (r.getRawOutput() == null ? "" : r.getRawOutput()) : "ERROR: " + r.getErrorMessage();
        } catch (Exception e) { return "ERROR: " + e.getMessage(); }
    }

    // ------------------------------------------------------------------

    private boolean isLocal(Job job) {
        return job.getExecutionType() == ExecutionType.LOCAL_SCRIPT || isLocalHost(job.getHostName());
    }

    private boolean isLocalHost(String host) {
        return host != null && props.getLocalHostnames().contains(host);
    }
}