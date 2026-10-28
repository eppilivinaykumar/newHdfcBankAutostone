import StatusDot from "./StatusDot.jsx";
import { label, STATE, STATE_ORDER } from "./statusModel.js";

export default function JobsAlertsTab({ jobs, alerts, selectedIds, setSelectedIds, onSelect, onOpenParent, onBulk, viewSearch, setViewSearch, statusFilter, setStatusFilter }) {
  const toggle = (id) => setSelectedIds((s) => { const n = new Set(s); n.has(id) ? n.delete(id) : n.add(id); return n; });
  return (
    <div className="asm-grid-2">
      <section className="asm-panel">
        <div className="asm-search-toolbar">
          <input autoFocus value={viewSearch} onChange={(e) => setViewSearch(e.target.value)} placeholder="Search by job name, host, box, trigger..." />
          <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
            <option value="ALL">All statuses</option>
            {STATE_ORDER.map((s) => <option key={s} value={s}>{STATE[s].label}</option>)}
          </select>
        </div>
        <div className="asm-panel-title">Jobs <span>{jobs.length} objects · {selectedIds.size} selected</span></div>
        <table className="asm-table">
          <thead><tr><th></th><th>Job</th><th>Type</th><th>Status</th><th>Box</th><th>Host</th><th>Trigger</th></tr></thead>
          <tbody>
            {jobs.map((j) => (
              <tr key={j.id}>
                <td><input type="checkbox" checked={selectedIds.has(j.id)} onChange={() => toggle(j.id)} /></td>
                <td style={{ paddingLeft: j.jobType === "BOX" ? 8 : 24 }} onClick={() => onSelect(j)}><StatusDot job={j} />{j.name}</td>
                <td>{j.jobType}</td><td>{label(j)}</td>
                <td>{j.boxId ? <button className="asm-link" onClick={(e) => { e.stopPropagation(); onOpenParent(j); }}>{j.boxId}</button> : "—"}</td>
                <td>{j.hostName || "—"}</td><td>{j.triggerType || "MANUAL"}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {selectedIds.size > 0 && (
          <div className="asm-bulkbar">
            <b>{selectedIds.size} jobs selected</b>
            <button onClick={() => onBulk("start")}>Bulk Start</button>
            <button onClick={() => onBulk("kill")}>Bulk Kill</button>
            <button onClick={() => onBulk("restart")}>Bulk Restart</button>
            <button onClick={() => onBulk("hold")}>Bulk Hold</button>
          </div>
        )}
      </section>
      <div className="asm-panel">
        <div className="asm-panel-title">Alerts <span>{alerts.length}</span></div>
        {alerts.map((a) => <div className="asm-alert-row" key={`${a.executionId}-${a.occurredAt}`}><StatusDot job={a} /><span><b>{a.jobName}</b><small>{a.type} · {a.message || "Alert"}</small></span></div>)}
        {!alerts.length && <div className="asm-empty">No active alerts.</div>}
      </div>
    </div>
  );
}
