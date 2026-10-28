/**
 * Canonical Monitoring UI state presentation.
 *
 * Backend execution states remain authoritative.  ACTIVE and ACTIVATED are
 * presentation states mapped from existing backend states: QUEUED means the
 * execution is active/eligible for dispatch; WAITING means it has been
 * activated into the current run but is waiting for a dependency/condition.
 * STARTING is reserved for a backend STARTING value if a future dispatcher
 * exposes it; the current dispatcher uses QUEUED -> RUNNING atomically.
 */
export const STATE = {
  ACTIVE:     { label: "Active",     icon: "●", color: "#7b4fc6", bg: "#f3edfb" },
  ACTIVATED:  { label: "Activated",  icon: "◔", color: "#2f80c9", bg: "#edf6ff" },
  STARTING:   { label: "Starting",   icon: "◐", color: "#00a6c7", bg: "#eafaff" },
  RUNNING:    { label: "Running",    icon: "▶", color: "#1f9d55", bg: "#e7f7ee" },
  PAUSED:     { label: "Paused",     icon: "Ⅱ", color: "#d1a021", bg: "#fdf8e8" },
  STOPPED:    { label: "Stopped",    icon: "■", color: "#8b3d3d", bg: "#faeeee" },
  KILLED:     { label: "Killed",     icon: "✕", color: "#d93632", bg: "#fdeceb" },
  HOLD:       { label: "Hold",       icon: "II", color: "#e68a16", bg: "#fff3e3" },
  SUCCESS:    { label: "Success",    icon: "✓", color: "#1470b9", bg: "#eaf4fb" },
  FAILED:     { label: "Failed",     icon: "✕", color: "#d93632", bg: "#fdeceb" },
  TIMEOUT:    { label: "Timeout",    icon: "!", color: "#a83a8f", bg: "#fbeefa" },
  SKIPPED:    { label: "Skipped",    icon: "↷", color: "#6f7880", bg: "#f1f3f5" },
  CANCELLED:  { label: "Cancelled",  icon: "×", color: "#7b8791", bg: "#f2f3f4" },
  HALTED:     { label: "Halted",     icon: "⏸", color: "#795548", bg: "#f5eeeb" },
  ON_ICE:     { label: "On Ice",     icon: "❄", color: "#2fa3c9", bg: "#eaf7fb" },
  ON_NOEXEC:  { label: "No-Exec",    icon: "⊘", color: "#7b8791", bg: "#f2f3f4" },
  RESTART:    { label: "Restarting", icon: "↻", color: "#7650a7", bg: "#f3edf9" },
  INACTIVE:   { label: "Inactive",   icon: "○", color: "#8b959d", bg: "#f4f5f6" },
};

export const STATE_ORDER = [
  "ACTIVE", "ACTIVATED", "STARTING", "RUNNING", "PAUSED", "STOPPED",
  "KILLED", "HOLD", "SUCCESS", "FAILED", "TIMEOUT", "SKIPPED",
  "CANCELLED", "HALTED", "ON_ICE", "RESTART", "ON_NOEXEC", "INACTIVE"
];

// Backend states that represent an execution participating in the current run.
export const ACTIVE_STATUSES = ["QUEUED", "WAITING", "RUNNING", "PAUSED", "RETRY_WAIT"];
export const TERMINAL_STATUSES = ["SUCCESS", "FAILED", "KILLED", "STOPPED", "TIMEOUT", "SKIPPED", "CANCELLED"];

/**
 * Convert the authoritative backend state + flags to the exact UI vocabulary.
 * Important: actual execution status wins over job-level hold flags, so a
 * running/paused process can never be painted as HOLD by stale configuration.
 */
export function deriveState(job) {
  if (!job) return "INACTIVE";
  const s = String(job.status || "CREATED").toUpperCase();

  // Process/execution states always have precedence.
  switch (s) {
    case "RUNNING": return "RUNNING";
    case "PAUSED": return "PAUSED";
    case "STOPPED": return "STOPPED";
    case "KILLED": return "KILLED";
    case "FAILED": return "FAILED";
    case "TIMEOUT": return "TIMEOUT";
    case "SUCCESS": return "SUCCESS";
    case "SKIPPED": return "SKIPPED";
    case "CANCELLED": return "CANCELLED";
    case "HALTED": return "HALTED";
    case "RETRY_WAIT": return "RESTART";
    case "QUEUED": return "ACTIVE";
    case "WAITING": return (job.hold || job.runtimeHold) ? "HOLD" : "ACTIVATED";
    case "STARTING": return "STARTING";
    default: break;
  }

  if (job.ice) return "ON_ICE";
  if (job.hold || job.runtimeHold) return "HOLD";
  if (job.noExecution) return "ON_NOEXEC";
  return "INACTIVE";
}

export function meta(job) { return STATE[deriveState(job)] || STATE.INACTIVE; }
export function label(job) { return meta(job).label; }
export function icon(job) { return meta(job).icon; }
export function color(job) { return meta(job).color; }
