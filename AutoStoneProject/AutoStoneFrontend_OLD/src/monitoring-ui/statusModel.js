/* ================================================================
   Fresh status model for the rebuilt Monitoring UI.
   Deliberately independent of /monitoring/statusPresentation.js
   (which Dashboard and Workflows still use) so this section has no
   shared-file coupling to anything outside itself, per "write the
   monitoring tab from scratch."
   ================================================================ */

export const STATE = {
  RUNNING:    { label: "Running",     icon: "▶", color: "#1f9d55", bg: "#e7f7ee" },
  SUCCESS:    { label: "Success",     icon: "✓", color: "#1470b9", bg: "#eaf4fb" },
  FAILURE:    { label: "Failure",     icon: "✕", color: "#d93632", bg: "#fdeceb" },
  TERMINATED: { label: "Terminated",  icon: "■", color: "#c9701c", bg: "#fdf1e6" },
  ON_ICE:     { label: "On Ice",      icon: "❄", color: "#2fa3c9", bg: "#eaf7fb" },
  ON_HOLD:    { label: "On Hold",     icon: "II", color: "#d1a021", bg: "#fdf8e8" },
  ACTIVATED:  { label: "Activated",   icon: "◔", color: "#8a63c9", bg: "#f4efFb" },
  RESTART:    { label: "Restarting",  icon: "↻", color: "#7650a7", bg: "#f3edf9" },
  ON_NOEXEC:  { label: "No-Exec",     icon: "⊘", color: "#7b8791", bg: "#f2f3f4" },
  INACTIVE:   { label: "Inactive",    icon: "○", color: "#8b959d", bg: "#f4f5f6" },
};
export const STATE_ORDER = ["RUNNING", "SUCCESS", "FAILURE", "TERMINATED", "ON_ICE", "ON_HOLD", "ACTIVATED", "RESTART", "ON_NOEXEC", "INACTIVE"];
export const ACTIVE_STATUSES = ["RUNNING", "QUEUED", "WAITING", "PAUSED", "RETRY_WAIT"];
export const TERMINAL_STATUSES = ["SUCCESS", "FAILED", "KILLED", "STOPPED", "TIMEOUT", "SKIPPED", "CANCELLED"];

/** job: any object carrying status + hold/ice/noExecution/runtimeHold flags
    (a FlowNodeResponse, a JobSummaryResponse, a monitoring row - they all do). */
export function deriveState(job) {
  if (!job) return "INACTIVE";
  if (job.ice) return "ON_ICE";
  if (job.hold || job.runtimeHold) return "ON_HOLD";
  if (job.noExecution) return "ON_NOEXEC";
  switch (String(job.status || "CREATED").toUpperCase()) {
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
export function meta(job) { return STATE[deriveState(job)] || STATE.INACTIVE; }
export function label(job) { return meta(job).label; }
export function icon(job) { return meta(job).icon; }
export function color(job) { return meta(job).color; }
