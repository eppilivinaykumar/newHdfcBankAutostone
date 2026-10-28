export const STATUS_PRESENTATION = {
  RUNNING: { className: "running", label: "Running", icon: "Ⅱ", miniMap: "#198754" },
  SUCCESS: { className: "success", label: "Success", icon: "✓", miniMap: "#0d6efd" },
  FAILED: { className: "failed", label: "Failed", icon: "×", miniMap: "#dc3545" },
  KILLED: { className: "killed", label: "Killed", icon: "×", miniMap: "#fd7e14" },
  PAUSED: { className: "paused", label: "Paused", icon: "Ⅱ", miniMap: "#ffc107" },
  WAITING: { className: "waiting", label: "Waiting", icon: "…", miniMap: "#adb5bd" },
  QUEUED: { className: "queued", label: "Queued", icon: "…", miniMap: "#9ec5fe" },
  CREATED: { className: "inactive", label: "Inactive", icon: "○", miniMap: "#6c757d" },
  STOPPED: { className: "inactive", label: "Stopped", icon: "■", miniMap: "#6c757d" },
  HALTED: { className: "killed", label: "Halted", icon: "×", miniMap: "#fd7e14" },
  TIMEOUT: { className: "failed", label: "Failed", icon: "!", miniMap: "#dc3545" },
  CANCELLED: { className: "killed", label: "Cancelled", icon: "×", miniMap: "#fd7e14" },
  SKIPPED: { className: "inactive", label: "Skipped", icon: "○", miniMap: "#6c757d" },
  RETRY_WAIT: { className: "restarted", label: "Restarting", icon: "↻", miniMap: "#6f42c1" },
  RESTARTED: { className: "restarted", label: "Restarted", icon: "↻", miniMap: "#6f42c1" },
};
export const ACTIVE_STATUSES = ["RUNNING", "QUEUED", "WAITING", "PAUSED", "RETRY_WAIT"];
export const STATUS_ORDER = ["RUNNING", "SUCCESS", "FAILED", "KILLED", "PAUSED", "WAITING", "QUEUED", "CREATED", "RETRY_WAIT"];
export function stateClass(status) { return STATUS_PRESENTATION[String(status || "CREATED").toUpperCase()]?.className || "inactive"; }
export function labelOf(status) { return STATUS_PRESENTATION[String(status || "CREATED").toUpperCase()]?.label || "Inactive"; }
export function iconOf(status) { return STATUS_PRESENTATION[String(status || "CREATED").toUpperCase()]?.icon || "○"; }
export function miniMapColor(status) { return STATUS_PRESENTATION[String(status || "CREATED").toUpperCase()]?.miniMap || STATUS_PRESENTATION.CREATED.miniMap; }

/* ================================================================
   AutoSys-accurate combined state (Flow tab only).
   Real AutoSys derives what an operator sees from EXECUTION STATUS
   plus the independent ICE / HOLD / NO-EXEC flags a job can carry
   at any time - e.g. a RUNNING job can still be flagged ON_ICE for
   its *next* run. deriveAutosysState() reproduces that precedence.
   Additive only - nothing above this line is modified or removed.
   ================================================================ */
export const AUTOSYS_STATE_META = {
  RUNNING:    { label: "Running",     icon: "▶", color: "#198754", bg: "#e5f6ee" },
  STARTING:   { label: "Starting",    icon: "◐", color: "#198754", bg: "#e5f6ee" },
  SUCCESS:    { label: "Success",     icon: "✓", color: "#0d6efd", bg: "#f2f7ff" },
  FAILURE:    { label: "Failure",     icon: "✕", color: "#dc3545", bg: "#fff5f5" },
  TERMINATED: { label: "Terminated",  icon: "■", color: "#fd7e14", bg: "#fff1e5" },
  ON_ICE:     { label: "On Ice",      icon: "❄", color: "#36a7d7", bg: "#eaf7fd" },
  ON_HOLD:    { label: "On Hold",     icon: "II", color: "#ffc107", bg: "#fffaf0" },
  ACTIVATED:  { label: "Activated",   icon: "◔", color: "#7758b8", bg: "#f5f1fc" },
  RESTART:    { label: "Restarting",  icon: "↻", color: "#6f42c1", bg: "#faf6ff" },
  ON_NOEXEC:  { label: "No-Exec",     icon: "⊘", color: "#7b8791", bg: "#f4f5f6" },
  INACTIVE:   { label: "Inactive",    icon: "○", color: "#6c757d", bg: "#f5f5f5" },
};
export const AUTOSYS_STATE_ORDER = ["RUNNING", "SUCCESS", "FAILURE", "TERMINATED", "ON_ICE", "ON_HOLD", "ACTIVATED", "RESTART", "ON_NOEXEC", "INACTIVE"];

/** job: a FlowNodeResponse-shaped object (status, hold, ice, noExecution, runtimeHold). */
export function deriveAutosysState(job) {
  if (!job) return "INACTIVE";
  if (job.ice) return "ON_ICE";
  if (job.hold || job.runtimeHold) return "ON_HOLD";
  if (job.noExecution) return "ON_NOEXEC";
  const s = String(job.status || "CREATED").toUpperCase();
  switch (s) {
    case "RUNNING": return "RUNNING";
    case "SUCCESS": return "SUCCESS";
    case "FAILED":
    case "TIMEOUT": return "FAILURE";
    case "KILLED":
    case "CANCELLED":
    case "HALTED":
    case "STOPPED": return "TERMINATED";
    case "RETRY_WAIT": return "RESTART";
    case "QUEUED":
    case "WAITING": return "ACTIVATED";
    case "SKIPPED":
    case "CREATED":
    default: return "INACTIVE";
  }
}
export function autosysMeta(job) { return AUTOSYS_STATE_META[deriveAutosysState(job)] || AUTOSYS_STATE_META.INACTIVE; }
