import StatusDot from "./StatusDot.jsx";
import { label } from "./statusModel.js";

export default function SummaryTab({ summary, alerts, counts, jobs, onSelect }) {
  return (
    <div className="asm-grid-2">
      <section className="asm-panel">
        <div className="asm-panel-title">Status overview</div>
        <div className="asm-status-overview">
          {["running", "success", "failed", "killed", "hold", "waiting", "inactive", "restarted"].map((s) => (
            <div key={s}><StatusDot job={{ status: s === "hold" ? "PAUSED" : s.toUpperCase() }} /><span>{s[0].toUpperCase() + s.slice(1)}</span><b>{counts[s] || 0}</b></div>
          ))}
        </div>
        <div className="asm-panel-title">Current workload</div>
        <table className="asm-table">
          <thead><tr><th>Job</th><th>Type</th><th>Status</th><th>Host</th></tr></thead>
          <tbody>{jobs.slice(0, 30).map((j) => <tr key={j.id} onClick={() => onSelect(j)}><td>{j.name}</td><td>{j.jobType}</td><td><StatusDot job={j} /> {label(j)}</td><td>{j.hostName || "—"}</td></tr>)}</tbody>
        </table>
      </section>
      <section>
        <div className="asm-panel">
          <div className="asm-panel-title">Execution summary</div>
          <div className="asm-big-number">{summary?.totalJobs ?? counts.total}<small> monitored jobs</small></div>
          <div className="asm-summary-lines">
            <span>Running <b>{summary?.running ?? counts.running}</b></span>
            <span>Success <b>{summary?.successful ?? counts.success}</b></span>
            <span>Failed <b>{summary?.failed ?? counts.failed}</b></span>
            <span>Scheduled <b>{summary?.scheduled ?? 0}</b></span>
          </div>
        </div>
        <div className="asm-panel">
          <div className="asm-panel-title">Latest alerts</div>
          {alerts.slice(0, 10).map((a) => <div className="asm-alert-row" key={`${a.executionId}-${a.occurredAt}`}><StatusDot job={a} /><span><b>{a.jobName}</b><small>{a.type} · {a.message || "Alert"}</small></span></div>)}
          {!alerts.length && <div className="asm-empty">No active alerts.</div>}
        </div>
      </section>
    </div>
  );
}
