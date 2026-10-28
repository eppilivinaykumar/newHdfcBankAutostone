import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { dashboardApi, monitoringApi } from "../api/api.js";
import { STATUS_PRESENTATION, labelOf } from "../monitoring/statusPresentation.js";

const states = ["RUNNING", "SUCCESS", "FAILED", "KILLED", "WAITING", "PAUSED", "STOPPED", "CREATED", "RETRY_WAIT"];

export default function DashboardPage() {
  const [jobs, setJobs] = useState([]);
  const [summary, setSummary] = useState(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState("");
  const eventRef = useRef(null);

  const refresh = useCallback(async (showLoading = false) => {
    if (showLoading) setLoading(true);
    try {
      setError("");
      const [j, s] = await Promise.all([dashboardApi.getStatus(), dashboardApi.getSummary()]);
      setJobs(j || []); setSummary(s);
    } catch (e) { setError(e.message); }
    finally { setLoading(false); }
  }, []);

  useEffect(() => { refresh(true); const t = setInterval(() => refresh(false), 5000); return () => clearInterval(t); }, [refresh]);
  useEffect(() => {
    const url = monitoringApi.streamUrl();
    try {
      const es = new EventSource(url); eventRef.current = es;
      es.addEventListener("snapshot", () => refresh(false));
      es.onerror = () => {};
      return () => { es.close(); eventRef.current = null; };
    } catch { return undefined; }
  }, [refresh]);

  const filteredJobs = useMemo(() => {
    const q = search.trim().toLowerCase();
    return q ? jobs.filter(j => `${j.name} ${j.hostName || ""} ${j.jobType || ""} ${j.boxId || ""}`.toLowerCase().includes(q)) : jobs;
  }, [jobs, search]);
  const upcoming = useMemo(() => filteredJobs.filter(j => j.triggerType === "CRON" && j.enabled).slice(0, 8), [filteredJobs]);
  const value = state => summary?.[{ SUCCESS: "successful", RUNNING: "running", QUEUED: "queued", FAILED: "failed", PAUSED: "paused", STOPPED: "stopped", KILLED: "killed", WAITING: "waiting", CREATED: "inactive", RETRY_WAIT: "restarted" }[state]] ?? filteredJobs.filter(j => j.status === state).length;

  return <div className="legacy-page">
    <div className="page-titlebar"><div><div className="crumb">Dashboard</div><h1>Workload Automation Dashboard</h1></div><div className="detail-actions"><Link className="legacy-btn primary" to="/jobs/new">New Job</Link><button className="legacy-btn" onClick={() => refresh(true)}>{loading ? "Refreshing..." : "Refresh"}</button></div></div>
    {error && <div className="legacy-error">{error}</div>}
    <div className="filter-row"><input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search jobs, BOXes, servers..." /></div>
    <div className="summary-strip"><Summary label="Total Jobs" value={summary?.totalJobs ?? filteredJobs.filter(j => j.jobType === "JOB").length} /><Summary label="Total BOXes" value={summary?.totalBoxes ?? filteredJobs.filter(j => j.jobType === "BOX").length} state="SUCCESS" /><Summary label="Executing" value={summary?.executing ?? 0} state="RUNNING" />{states.map(s => <Summary key={s} label={labelOf(s)} value={value(s)} state={s} />)}</div>
    <div className="legacy-grid two">
      <section className="legacy-panel"><div className="panel-head">Job / BOX Status Summary</div><div className="bar-list">{states.map(s => <div className="bar-row" key={s}><span>{labelOf(s)}</span><div className="bar-track"><div className={`bar-fill ${s.toLowerCase()}`} style={{ width: `${(summary?.totalJobs || filteredJobs.length) ? Math.max(2, value(s) / (summary?.totalJobs || filteredJobs.length) * 100) : 2}%` }} /></div><b>{value(s)}</b></div>)}</div></section>
      <section className="legacy-panel"><div className="panel-head">Upcoming Schedules</div>{upcoming.length ? <table className="legacy-table"><thead><tr><th>Job Name</th><th>Cron</th><th>Machine</th><th>Status</th></tr></thead><tbody>{upcoming.map(j => <tr key={j.id}><td><Link to={`/jobs/${j.id}`}>{j.name}</Link></td><td>{j.cronExpression || "—"}</td><td>{j.hostName || "-"}</td><td>{labelOf(j.status)}</td></tr>)}</tbody></table> : <div className="empty-legacy">No enabled cron jobs found.</div>}</section>
    </div>
    <section className="legacy-panel"><div className="panel-head">Current Workload</div><table className="legacy-table"><thead><tr><th>Name</th><th>Type</th><th>BOX</th><th>Status</th><th>Start</th><th>End</th><th>Duration</th><th>Exit</th></tr></thead><tbody>{filteredJobs.slice(0, 100).map(j => <tr key={j.id}><td><Link to={`/jobs/${j.id}`}>{j.name}</Link></td><td>{j.jobType}</td><td>{j.boxId || "—"}</td><td><span className={`state-badge ${STATUS_PRESENTATION[j.status]?.className || "never"}`}>{labelOf(j.status)}</span></td><td>{j.startTime ? new Date(j.startTime).toLocaleString() : "—"}</td><td>{j.endTime ? new Date(j.endTime).toLocaleString() : "—"}</td><td>{j.durationSeconds != null ? `${Math.floor(j.durationSeconds / 60)}m ${j.durationSeconds % 60}s` : "—"}</td><td>{j.exitCode ?? "—"}</td></tr>)}</tbody></table>{filteredJobs.length > 100 && <div className="empty-legacy">Showing first 100 of {filteredJobs.length} matching objects.</div>}</section>
    <section className="legacy-panel"><div className="panel-head">Enterprise Views</div><div className="dashboard-links"><Link to="/monitoring">Monitoring / Flow</Link><Link to="/jobs">Job Inventory</Link><Link to="/quick-view">Quick View</Link><Link to="/servers">Servers</Link><Link to="/alert-policies">Alert Policies</Link><Link to="/workflows">Workflows</Link><Link to="/import-jil">Import JIL</Link></div></section>
  </div>;
}
function Summary({ label, value, state }) { return <div className={`summary-card ${state ? state.toLowerCase() : ""}`}><span>{label}</span><strong>{value}</strong></div>; }
