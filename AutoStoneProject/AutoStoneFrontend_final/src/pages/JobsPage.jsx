import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { dashboardApi, eventApi } from "../api/api.js";

const statuses = ["RUNNING", "QUEUED", "SUCCESS", "FAILED", "WAITING", "PAUSED", "STOPPED", "TIMEOUT"];

export default function JobsPage() {
  const [jobs, setJobs] = useState([]);
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("ALL");
  const [type, setType] = useState("ALL");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(null);

  const load = useCallback(async () => {
    try { setError(""); setJobs(await dashboardApi.getStatus()); }
    catch (e) { setError(e.message); }
  }, []);

  useEffect(() => { load(); const timer = setInterval(load, 4000); return () => clearInterval(timer); }, [load]);

  const filtered = useMemo(() => jobs.filter(j => {
    const q = query.trim().toLowerCase();
    return (!q || j.name.toLowerCase().includes(q) || (j.hostName || "").toLowerCase().includes(q))
      && (status === "ALL" || (j.status || "NEVER_RUN") === status)
      && (type === "ALL" || j.jobType === type);
  }), [jobs, query, status, type]);

  async function run(id, action) {
    setBusy(`${id}:${action}`);
    try { await eventApi[action](id); await load(); }
    catch (e) { setError(e.message); }
    finally { setBusy(null); }
  }

  return <div className="legacy-page">
    <div className="page-titlebar">
      <div><div className="crumb">Workload / Jobs</div><h1>Jobs</h1></div>
      <div className="detail-actions"><Link className="legacy-btn primary" to="/jobs/new">New Job</Link><button className="legacy-btn" onClick={load}>Refresh</button></div>
    </div>
    {error && <div className="legacy-error">{error}</div>}
    <div className="filter-row">
      <input value={query} onChange={e => setQuery(e.target.value)} placeholder="Search name or server" />
      <select value={status} onChange={e => setStatus(e.target.value)}><option value="ALL">All statuses</option>{statuses.map(s => <option key={s}>{s}</option>)}<option value="NEVER_RUN">NEVER RUN</option></select>
      <select value={type} onChange={e => setType(e.target.value)}><option value="ALL">All types</option><option>JOB</option><option>BOX</option></select>
    </div>
    <section className="legacy-panel"><div className="panel-head">Job Inventory <span className="right-link">{filtered.length} of {jobs.length}</span></div>
      <table className="legacy-table"><thead><tr><th>Name</th><th>Type</th><th>Server</th><th>Trigger</th><th>Status</th><th>Enabled</th><th>Actions</th></tr></thead>
        <tbody>{filtered.map(j => <tr key={j.id}>
          <td><Link to={`/jobs/${j.id}`}>{j.name}</Link></td><td>{j.jobType}</td><td>{j.hostName || "-"}</td><td>{j.triggerType}{j.cronExpression ? ` · ${j.cronExpression}` : ""}</td>
          <td><span className={`state-badge ${(j.status || "never").toLowerCase()}`}>{j.status || "NEVER RUN"}</span></td><td>{j.enabled ? "YES" : "NO"}</td>
          <td><div className="action-buttons compact-actions"><button disabled={busy} onClick={() => run(j.id, "start")}>Run</button><button disabled={busy || !["RUNNING","PAUSED","QUEUED"].includes(j.status)} onClick={() => run(j.id, "stop")}>Stop</button><Link to={`/jobs/${j.id}`}>Details</Link></div></td>
        </tr>)}{!filtered.length && <tr><td colSpan="7"><div className="empty-legacy">No jobs match the current filters.</div></td></tr>}</tbody>
      </table>
    </section>
  </div>;
}
