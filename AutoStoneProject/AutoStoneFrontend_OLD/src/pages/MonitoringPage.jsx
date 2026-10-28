import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { dashboardApi, eventApi, executionApi, jobApi, monitoringApi } from "../api/api.js";
import DagCanvas from "../components/DagCanvas.jsx";
import { STATUS_PRESENTATION, stateClass, labelOf, iconOf, ACTIVE_STATUSES, STATUS_ORDER } from "../monitoring/statusPresentation.js";

/* Just the 3 tabs real AutoSys shows: Summary | Jobs and Alerts | Flow. */
const TABS = ["summary", "jobsAlerts", "flow"];
const TAB_LABEL = { summary: "Summary", jobsAlerts: "Jobs and Alerts", flow: "Flow" };
const ACTIVE = ACTIVE_STATUSES;
function stateOf(status) { return stateClass(status); }

function StatusDot({ status, size = "normal" }) { return <span className={`monitor-status-dot ${stateOf(status)} ${size}`} title={labelOf(status)}>{iconOf(status)}</span>; }

function TreeNode({ node, depth, expanded, onToggle, onSelect, onOpenBox, selectedId, childrenMap }) {
  const isBox = node.jobType === "BOX";
  const children = childrenMap.get(String(node.id)) || [];
  const open = expanded.has(String(node.id));
  return <div>
    <div className={`monitor-tree-row ${selectedId === node.id ? "selected" : ""}`} style={{ paddingLeft: 8 + depth * 15 }} onContextMenu={e => { e.preventDefault(); onSelect(node, e); }}>
      {isBox ? <button className="tree-expander" onClick={e => { e.stopPropagation(); onToggle(node); }}>{open ? "▾" : "▸"}</button> : <span className="tree-expander spacer">•</span>}
      <button className="tree-node-main" onClick={() => onSelect(node)} onDoubleClick={() => isBox && onOpenBox(node)} title={isBox ? `${node.name} (double-click to open as root)` : node.name}><StatusDot status={node.status} /><span>{node.name}</span>{isBox && <em>BOX</em>}</button>
      <span className="tree-node-count">{isBox ? children.length : ""}</span>
    </div>
    {isBox && open && children.map(child => <TreeNode key={child.id} node={child} depth={depth + 1} expanded={expanded} onToggle={onToggle} onSelect={onSelect} onOpenBox={onOpenBox} selectedId={selectedId} childrenMap={childrenMap} />)}
  </div>;
}

function ActionMenu({ menu, onClose, onAction, onOpen, onEdit, onCopy, onLogs, onHistory }) {
  if (!menu) return null;
  const j = menu.job;
  const status = String(j.status || "CREATED").toUpperCase();
  const active = ACTIVE.includes(status);
  const terminal = ["SUCCESS", "FAILED", "KILLED", "STOPPED", "TIMEOUT", "SKIPPED", "CANCELLED"].includes(status);
  const holdAction = Boolean(j.hold) || Boolean(j.runtimeHold);
  const items = [
    ["Open Job", onOpen, false], ["Edit Job", onEdit, false], ["Copy Job", onCopy, false], ["View Logs", onLogs, false], ["View Output", onLogs, false], ["View History", onHistory, false], ["", null, true],
    [j.jobType === "BOX" ? "Start BOX" : "Start", () => onAction("start"), !["CREATED","STOPPED","SUCCESS","FAILED","KILLED","TIMEOUT","SKIPPED","CANCELLED"].includes(status)],
    ...(!j.jobType || j.jobType !== "BOX" ? [["Force Start", () => onAction("forceStart"), active || holdAction || j.ice || j.noExecution]] : []),
    [status === "PAUSED" ? "Resume" : "Pause", () => onAction(status === "PAUSED" ? "resume" : "pause"), !["RUNNING","PAUSED"].includes(status)],
    [j.jobType === "BOX" ? "Stop BOX" : "Stop", () => onAction("stop"), !active],
    [j.jobType === "BOX" ? "Kill BOX" : "Kill", () => onAction("kill"), !active],
    [j.jobType === "BOX" ? "Restart BOX" : "Restart / Rerun", () => onAction("restart"), !terminal],
    ["", null, true],
    [holdAction ? "Unhold" : (j.jobType === "BOX" ? "Hold BOX" : "Hold"), () => onAction(holdAction ? "offHold" : "hold"), holdAction ? false : terminal],
    ...(status === "WAITING" || status === "QUEUED" ? [["Skip", () => onAction("skip"), false]] : []),
    ...(!terminal ? [["Mark Success", () => onAction("markSuccess"), false], ["Mark Failure", () => onAction("markFailure"), false]] : []),
  ];
  return <div className="monitor-context" style={{ left: menu.x, top: menu.y }} onClick={e => e.stopPropagation()}>
    <div className="context-title"><StatusDot status={j.status}/><span>{j.name}</span></div>
    {items.map(([text, fn, disabled], i) => text ? <button key={i} disabled={disabled} onClick={() => { fn?.(); onClose(); }}>{text}</button> : <div className="context-sep" key={i} />)}
  </div>;
}

function Modal({ title, children, onClose, wide = false }) { return <div className="monitor-modal-backdrop" onMouseDown={onClose}><div className={`monitor-modal ${wide ? "wide" : ""}`} onMouseDown={e => e.stopPropagation()}><div className="monitor-modal-head"><b>{title}</b><button onClick={onClose}>×</button></div>{children}</div></div>; }

export default function MonitoringPage() {
  const navigate = useNavigate();
  const [jobs, setJobs] = useState([]); const [summary, setSummary] = useState(null); const [alerts, setAlerts] = useState([]);
  const [selected, setSelected] = useState(null); const [tab, setTab] = useState("summary"); const [search, setSearch] = useState(""); const [status, setStatus] = useState("ALL");
  const [expanded, setExpanded] = useState(new Set()); const [childrenMap, setChildrenMap] = useState(new Map()); const [rootPage, setRootPage] = useState(null);
  const [busy, setBusy] = useState(false); const [error, setError] = useState(""); const [autoRefresh, setAutoRefresh] = useState(true); const [lastRefresh, setLastRefresh] = useState(null);
  const [menu, setMenu] = useState(null); const [selectedIds, setSelectedIds] = useState(new Set()); const [modal, setModal] = useState(null); const [historyRows, setHistoryRows] = useState([]); const [logs, setLogs] = useState(null);
  const [viewSearch, setViewSearch] = useState(""); const [page, setPage] = useState(0); const [flowKey, setFlowKey] = useState(0); const eventRef = useRef(null);
  const [boxPath, setBoxPath] = useState([]);

  const loadRoots = useCallback(async (showBusy = false) => {
    if (showBusy) setBusy(true);
    try {
      const [rootData, sum, alertData] = await Promise.all([monitoringApi.jobs({ search, status, rootsOnly: true, page, size: 500 }), dashboardApi.getSummary(), monitoringApi.getAlerts().catch(() => [])]);
      setRootPage(rootData); setJobs(rootData.content || []); setSummary(sum); setAlerts(alertData || []); setLastRefresh(new Date());
      setSelected(old => old && (rootData.content || []).some(j => j.id === old.id) ? (rootData.content || []).find(j => j.id === old.id) : old || rootData.content?.[0] || null);
    } catch (e) { setError(e.message || "Unable to load monitoring data"); } finally { if (showBusy) setBusy(false); }
  }, [search, status, page]);

  const loadChildren = useCallback(async (box) => {
    try {
      const data = await monitoringApi.jobs({ boxId: box.id, page: 0, size: 1000 });
      setChildrenMap(m => { const n = new Map(m); n.set(String(box.id), data.content || []); return n; });
    } catch (e) { setError(e.message); }
  }, []);

  useEffect(() => { loadRoots(true); }, [loadRoots]);
  useEffect(() => {
    if (!autoRefresh) return undefined;
    const id = setInterval(() => loadRoots(false), 5000); return () => clearInterval(id);
  }, [autoRefresh, loadRoots]);
  useEffect(() => {
    const handler = () => setMenu(null); window.addEventListener("click", handler); return () => window.removeEventListener("click", handler);
  }, []);
  useEffect(() => {
    const url = monitoringApi.streamUrl(); if (!url) return undefined;
    try {
      const es = new EventSource(url); eventRef.current = es;
      es.addEventListener("snapshot", e => {
        try {
          const payload = JSON.parse(e.data); const incoming = payload.jobs || [];
          const byId = new Map(incoming.map(j => [String(j.id), j]));
          setJobs(old => old.map(j => byId.get(String(j.id)) ? { ...j, ...byId.get(String(j.id)) } : j));
          setChildrenMap(old => { const n = new Map(old); n.forEach((list, key) => n.set(key, list.map(j => byId.get(String(j.id)) ? { ...j, ...byId.get(String(j.id)) } : j))); return n; });
          setSelected(old => old && byId.has(String(old.id)) ? { ...old, ...byId.get(String(old.id)) } : old);
          setBoxPath(path => path.map(box => byId.has(String(box.id)) ? { ...box, ...byId.get(String(box.id)) } : box));
          dashboardApi.getSummary().then(setSummary).catch(() => {});
          setLastRefresh(new Date());
        } catch { /* ignore malformed stream event */ }
      });
      es.onerror = () => { /* polling remains the fallback */ };
      return () => { es.close(); eventRef.current = null; };
    } catch { return undefined; }
  }, []);

  const allVisible = useMemo(() => {
    const list = [...jobs]; childrenMap.forEach(v => list.push(...v)); const map = new Map(); list.forEach(j => map.set(String(j.id), j)); return [...map.values()];
  }, [jobs, childrenMap]);
  const filtered = useMemo(() => allVisible.filter(j => (status === "ALL" || String(j.status || "CREATED").toUpperCase() === status) && (!viewSearch || `${j.name} ${j.hostName || ""}`.toLowerCase().includes(viewSearch.toLowerCase()))), [allVisible, viewSearch, status]);
  const counts = useMemo(() => { const c = { total: allVisible.length, running: 0, success: 0, failed: 0, killed: 0, hold: 0, waiting: 0, inactive: 0, restarted: 0 }; allVisible.forEach(j => { const s = stateOf(j.status); c[s] = (c[s] || 0) + 1; }); return c; }, [allVisible]);

  const toggleBox = async box => { const key = String(box.id); if (expanded.has(key)) setExpanded(s => { const n = new Set(s); n.delete(key); return n; }); else { await loadChildren(box); setExpanded(s => new Set(s).add(key)); } };
  const selectJob = (job, e) => { setSelected(job); if (e) setMenu({ x: Math.min(e.clientX, innerWidth - 280), y: Math.min(e.clientY, innerHeight - 480), job }); };
  const openBox = useCallback((box) => {
    if (!box || box.jobType !== "BOX") return;
    setSelected(box);
    loadChildren(box);
    setBoxPath(path => {
      const idx = path.findIndex(item => String(item.id) === String(box.id));
      if (idx >= 0) return path.slice(0, idx + 1);
      return [...path, box];
    });
    setTab("flow");
  }, [loadChildren]);
  const backBox = useCallback(() => setBoxPath(path => path.slice(0, -1)), []);
  const parentBox = useCallback(() => setBoxPath(path => path.length > 1 ? path.slice(0, -1) : []), []);
  const currentBox = boxPath.length ? boxPath[boxPath.length - 1] : null;
  const refresh = () => { setFlowKey(x => x + 1); loadRoots(true); };

  const runAction = async (action, job = selected) => {
    if (!job) return; if (["kill", "stop"].includes(action) && !window.confirm(`${action === "kill" ? "Kill" : "Stop"} ${job.name}?`)) return;
    setBusy(true); setMenu(null);
    try { await eventApi[action](job.id); await loadRoots(false); if (action === "restart") setTab("flow"); }
    catch (e) { setError(e.message || `Unable to execute ${action}`); } finally { setBusy(false); }
  };
  const openHistory = async job => { setModal("history"); setHistoryRows(await executionApi.byJob(job.id)); };
  const openLogs = async job => { const rows = await executionApi.byJob(job.id); const latest = rows?.[0]; if (!latest) { setLogs({ stdout: "No execution history.", stderr: "" }); setModal("logs"); return; } try { setLogs(await executionApi.logs(latest.id)); } catch (e) { setLogs({ stdout: e.message, stderr: "" }); } setModal("logs"); };
  const copyJob = async job => { try { const full = await jobApi.get(job.id); const copy = {
    name:`${full.name}_COPY_${Date.now()}`, description:full.description || null, jobType:full.jobType, triggerType:"MANUAL", cronExpression:null, timezone:full.timezone || "UTC",
    scheduleStartTime:null, scheduleEndTime:null, misfirePolicy:full.misfirePolicy || "FIRE_NOW", conditionExpression:null, command:full.command || null, hostName:full.hostName || null, boxId:null,
    dependsOnJobIds:[], enabled:true, executionType:full.executionType || "SSH_SCRIPT", retryCount:full.retryCount || 0, retryIntervalSeconds:full.retryIntervalSeconds || 60,
    timeoutSeconds:full.timeoutSeconds || 0, workingDirectory:full.workingDirectory || null, arguments:full.arguments || null, concurrencyPolicy:full.concurrencyPolicy || "ALLOW"
  }; await jobApi.create(copy); await loadRoots(false); } catch (e) { setError(e.message); } };
  const bulk = async action => { if (!selectedIds.size) return; setBusy(true); try { const r = await eventApi.bulk([...selectedIds], action); setError(r.failed ? `${r.succeeded} succeeded, ${r.failed} failed: ${r.errors?.join(" | ")}` : ""); setSelectedIds(new Set()); await loadRoots(false); } catch (e) { setError(e.message); } finally { setBusy(false); } };

  const cards = [["Total", counts.total, "inactive"], ["Running", counts.running, "running"], ["Success", counts.success, "success"], ["Failed", counts.failed, "failed"], ["Killed", counts.killed, "killed"], ["On Hold", counts.hold, "hold"], ["Waiting", counts.waiting, "waiting"], ["Restarted", counts.restarted, "restarted"]];

  return <div className="monitor-shell">
    <aside className="monitor-sidebar">
      <div className="monitor-side-tabs"><span className="active">Views</span><Link to="/servers">Servers</Link><Link to="/alert-policies">Alert Policies</Link></div>
      <div className="monitor-side-tools"><button onClick={() => setAutoRefresh(v => !v)}>{autoRefresh ? "●" : "○"} Auto Refresh</button><span>{lastRefresh?.toLocaleTimeString() || "—"}</span></div>
      <div className="monitor-tree-title">▾ AutoStone <button onClick={refresh}>↻</button></div>
      <div className="monitor-search"><input placeholder="Search jobs / boxes" value={search} onChange={e => { setPage(0); setSearch(e.target.value); }} /></div>
      <div className="monitor-tree-scroll">{jobs.map(j => <TreeNode key={j.id} node={j} depth={0} expanded={expanded} onToggle={toggleBox} onSelect={selectJob} onOpenBox={openBox} selectedId={selected?.id} childrenMap={childrenMap} />)}{!jobs.length && <div className="monitor-empty">No jobs match.</div>}</div>
      <div className="monitor-side-foot"><span>{rootPage?.totalElements || 0} top-level objects</span><span><button disabled={!page} onClick={() => setPage(p=>Math.max(0,p-1))}>‹</button> {page+1}/{rootPage?.totalPages||1} <button disabled={rootPage?.last} onClick={() => setPage(p=>p+1)}>›</button></span></div>
    </aside>

    <main className="monitor-main">
      <div className="monitor-topbar"><div><b>Monitoring</b><span> / Views</span></div><div className="monitor-top-actions"><button onClick={refresh} disabled={busy}>↻ Refresh</button><button onClick={() => setAutoRefresh(v => !v)}>{autoRefresh ? "Live" : "Paused"}</button></div></div>
      <div className="monitor-viewtabs">{TABS.map(t => <button key={t} className={tab === t ? "active" : ""} onClick={() => setTab(t)}>{TAB_LABEL[t]}</button>)}</div>
      {error && <div className="monitor-error">{error}<button onClick={() => setError("")}>×</button></div>}
      <div className="monitor-content">
        <div className="monitor-summary-strip">{cards.map(([name, value, cls]) => <div className={`monitor-card ${cls}`} key={name}><span>{name}</span><strong>{value}</strong></div>)}</div>
        {selected && <div className="monitor-actionbar"><div className="monitor-selected"><StatusDot status={selected.status}/><div><b>{selected.name}</b><small>{selected.jobType} · {labelOf(selected.status)}</small></div></div><div className="monitor-actions"><button onClick={() => runAction("start")} disabled={!(["CREATED","STOPPED","SUCCESS","FAILED","KILLED","TIMEOUT","SKIPPED","CANCELLED"].includes(String(selected.status || "CREATED").toUpperCase()))}>▶ Start</button><button onClick={() => runAction("forceStart")} disabled={selected.jobType === "BOX" || ACTIVE.includes(selected.status) || selected.hold || selected.ice || selected.noExecution}>Force Start</button><button onClick={() => runAction("stop")} disabled={!ACTIVE.includes(selected.status)}>■ Stop</button><button onClick={() => runAction("kill")} disabled={!ACTIVE.includes(selected.status)}>Kill</button><button onClick={() => runAction("restart")} disabled={!(["SUCCESS","FAILED","KILLED","STOPPED","TIMEOUT","SKIPPED","CANCELLED"].includes(String(selected.status || "").toUpperCase()))}>↻ Restart</button><button onClick={() => runAction("skip")} disabled={!(["WAITING","QUEUED"].includes(String(selected.status || "").toUpperCase()))}>Skip</button><button onClick={() => runAction(selected.hold ? "offHold" : "hold")}>{selected.hold ? "Unhold" : "Hold"}</button><button onClick={() => runAction("markSuccess")}>Mark Success</button><button onClick={() => runAction("markFailure")}>Mark Failure</button></div></div>}

        {tab === "summary" && <SummaryView summary={summary} alerts={alerts} counts={counts} jobs={filtered} onSelect={setSelected} />}
        {tab === "jobsAlerts" && <JobsAndAlertsView jobs={filtered} alerts={alerts} selectedIds={selectedIds} setSelectedIds={setSelectedIds} onSelect={setSelected} onOpenParent={async (job) => { if (!job?.boxId) return; try { const parent = allVisible.find(j => String(j.id) === String(job.boxId)) || await jobApi.get(job.boxId); openBox(parent); } catch (e) { setError(e.message); } }} onBulk={bulk} viewSearch={viewSearch} setViewSearch={setViewSearch} status={status} setStatus={setStatus} />}
        {tab === "flow" && <div className="monitor-flow-panel"><div className="flow-panel-head"><div className="d-flex align-items-center gap-2 flex-wrap"><button className="btn btn-sm btn-outline-secondary" disabled={!currentBox} onClick={backBox}>← Back</button><button className="btn btn-sm btn-outline-secondary" disabled={!currentBox || boxPath.length < 2} onClick={parentBox}>↑ Parent BOX</button><span>Monitoring</span>{boxPath.map(box => <span key={box.id} className="breadcrumb-box">→ {box.name}</span>)}</div><span>{currentBox ? `Current BOX: ${currentBox.name}` : "Root / Main BOXes"}</span></div><DagCanvas key={`${flowKey}-${currentBox?.id ?? "ROOT"}`} boxId={currentBox?.id ?? null} refreshToken={lastRefresh?.getTime()} highlightId={selected?.id ?? null} onChanged={(node) => setSelected(node)} onContextMenu={(job, e) => selectJob(job, e)} /></div>}
      </div>
    </main>

    <ActionMenu menu={menu} onClose={() => setMenu(null)} onAction={runAction} onOpen={() => navigate(`/jobs/${menu?.job?.id}`)} onEdit={() => navigate(`/jobs/${menu?.job?.id}/edit`)} onCopy={() => copyJob(menu.job)} onLogs={() => openLogs(menu.job)} onHistory={() => openHistory(menu.job)} />
    {modal === "logs" && <Modal title={`Execution Output — ${selected?.name || "Job"}`} onClose={() => setModal(null)} wide><pre className="monitor-log">{logs?.stdout || logs?.output || ""}{logs?.stderr ? `\n\n--- STDERR ---\n${logs.stderr}` : ""}</pre></Modal>}
    {modal === "history" && <Modal title={`Execution History — ${selected?.name || "Job"}`} onClose={() => setModal(null)} wide><HistoryTable rows={historyRows} /></Modal>}
  </div>;
}

function SummaryView({ summary, alerts, counts, jobs, onSelect }) { return <div className="monitor-grid-two"><section className="monitor-panel"><div className="monitor-panel-title">Status overview</div><div className="status-overview">{["running","success","failed","killed","hold","waiting","inactive","restarted"].map(s => <div key={s}><StatusDot status={s === "hold" ? "PAUSED" : s.toUpperCase()}/><span>{s[0].toUpperCase()+s.slice(1)}</span><b>{counts[s] || 0}</b></div>)}</div><div className="monitor-panel-title">Current workload</div><table className="monitor-table"><thead><tr><th>Job</th><th>Type</th><th>Status</th><th>Host</th></tr></thead><tbody>{jobs.slice(0,30).map(j => <tr key={j.id} onClick={() => onSelect(j)}><td>{j.name}</td><td>{j.jobType}</td><td><StatusDot status={j.status}/> {labelOf(j.status)}</td><td>{j.hostName || "—"}</td></tr>)}</tbody></table></section><section><div className="monitor-panel"><div className="monitor-panel-title">Execution summary</div><div className="summary-big">{summary?.totalJobs ?? counts.total}<small> monitored jobs</small></div><div className="summary-lines"><span>Running <b>{summary?.running ?? counts.running}</b></span><span>Success <b>{summary?.successful ?? counts.success}</b></span><span>Failed <b>{summary?.failed ?? counts.failed}</b></span><span>Scheduled <b>{summary?.scheduled ?? 0}</b></span></div></div><div className="monitor-panel"><div className="monitor-panel-title">Latest alerts</div>{alerts.slice(0,10).map(a => <div className="alert-row" key={`${a.executionId}-${a.occurredAt}`}><StatusDot status={a.status}/><span><b>{a.jobName}</b><small>{a.type} · {a.message || "Alert"}</small></span></div>)}{!alerts.length && <div className="monitor-empty">No active alerts.</div>}</div></section></div>; }

/* Replaces the old separate Tree / Dependency / BOX View / Timeline / Status / Search
   tabs with one combined view: the full job list (with bulk actions) plus alerts. */
function JobsAndAlertsView({ jobs, alerts, selectedIds, setSelectedIds, onSelect, onOpenParent, onBulk, viewSearch, setViewSearch, status, setStatus }) {
  const toggle = id => setSelectedIds(s => { const n = new Set(s); n.has(id) ? n.delete(id) : n.add(id); return n; });
  return <div className="monitor-grid-two">
    <section className="monitor-panel">
      <div className="search-view-toolbar"><input autoFocus value={viewSearch} onChange={e=>setViewSearch(e.target.value)} placeholder="Search by job name, host, box, trigger..."/><select value={status} onChange={e=>setStatus(e.target.value)}><option value="ALL">All statuses</option>{STATUS_ORDER.filter(s => STATUS_PRESENTATION[s]).map(s=><option key={s}>{s}</option>)}</select></div>
      <div className="monitor-panel-title">Jobs <span>{jobs.length} objects · {selectedIds.size} selected</span></div>
      <table className="monitor-table"><thead><tr><th></th><th>Job</th><th>Type</th><th>Status</th><th>Box</th><th>Host</th><th>Trigger</th></tr></thead><tbody>{jobs.map(j => <tr key={j.id}><td><input type="checkbox" checked={selectedIds.has(j.id)} onChange={() => toggle(j.id)} /></td><td style={{paddingLeft:j.jobType === "BOX" ? 8 : 28}} onClick={() => onSelect(j)}><StatusDot status={j.status}/>{j.name}</td><td>{j.jobType}</td><td>{labelOf(j.status)}</td><td>{j.boxId ? <button className="btn btn-sm btn-link p-0" onClick={(e)=>{e.stopPropagation(); onOpenParent(j);}}>{j.boxId}</button> : "—"}</td><td>{j.hostName || "—"}</td><td>{j.triggerType || "MANUAL"}</td></tr>)}</tbody></table>
      {selectedIds.size > 0 && <div className="bulkbar"><b>{selectedIds.size} jobs selected</b><button onClick={() => onBulk("start")}>Bulk Start</button><button onClick={() => onBulk("kill")}>Bulk Kill</button><button onClick={() => onBulk("restart")}>Bulk Restart</button><button onClick={() => onBulk("hold")}>Bulk Hold</button></div>}
    </section>
    <div className="monitor-panel">
      <div className="monitor-panel-title">Alerts <span>{alerts.length}</span></div>
      {alerts.map(a => <div className="alert-row" key={`${a.executionId}-${a.occurredAt}`}><StatusDot status={a.status}/><span><b>{a.jobName}</b><small>{a.type} · {a.message || "Alert"}</small></span></div>)}
      {!alerts.length && <div className="monitor-empty">No active alerts.</div>}
    </div>
  </div>;
}

function HistoryTable({ rows }) { return <table className="monitor-table"><thead><tr><th>Execution</th><th>Status</th><th>Attempt</th><th>Start</th><th>End</th><th>Host</th><th>Message</th></tr></thead><tbody>{rows?.map(r=><tr key={r.id}><td>{r.id}</td><td><StatusDot status={r.status}/>{labelOf(r.status)}</td><td>{r.attempt}</td><td>{r.startTime ? new Date(r.startTime).toLocaleString() : "—"}</td><td>{r.endTime ? new Date(r.endTime).toLocaleString() : "—"}</td><td>{r.host || "—"}</td><td>{r.message || ""}</td></tr>)}</tbody></table>; }