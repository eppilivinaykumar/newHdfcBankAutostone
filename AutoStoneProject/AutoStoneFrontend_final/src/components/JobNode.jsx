import { useState } from "react";
import { eventApi } from "../api/api.js";
import { ACTIVE_STATUSES, STATUS_PRESENTATION, labelOf, stateClass } from "../monitoring/statusPresentation.js";

const TERMINAL = ["SUCCESS", "FAILED", "KILLED", "STOPPED", "TIMEOUT", "SKIPPED", "CANCELLED"];
const ACTION_LABEL = { start: "Start", stop: "Stop", pause: "Pause", resume: "Resume", restart: "Restart" };

function availableActions(status) {
  const s = String(status || "CREATED").toUpperCase();
  if (s === "RUNNING") return ["stop", "pause"];
  if (s === "PAUSED") return ["resume", "stop"];
  if (ACTIVE_STATUSES.includes(s)) return ["stop"];
  if (TERMINAL.includes(s)) return ["restart"];
  return ["start"];
}

export default function JobNode({ job, onChanged }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const status = String(job.status || "CREATED").toUpperCase();
  const presentation = STATUS_PRESENTATION[status] || STATUS_PRESENTATION.CREATED;

  async function runAction(action) {
    setBusy(true);
    setError(null);
    try {
      await eventApi[action](job.id);
      await onChanged();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className={`job-node ${stateClass(status)}`}>
      <div className="job-name">{job.name}</div>
      <div className="job-meta">
        {job.triggerType}
        {job.triggerType === "CRON" && job.cronExpression ? ` · ${job.cronExpression}` : ""}
      </div>

      <span className={`status-pill ${presentation.className}`}>
        {labelOf(status)}
      </span>

      {error && <div className="field-hint failed">{error}</div>}

      <div className="actions">
        {availableActions(status).map((action) => (
          <button
            key={action}
            className={action === "stop" ? "btn btn-danger" : "btn"}
            disabled={busy}
            onClick={() => runAction(action)}
          >
            {ACTION_LABEL[action]}
          </button>
        ))}
      </div>
    </div>
  );
}
