import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { dashboardApi, eventApi, executionApi, jobApi, monitoringApi } from "../api/api.js";
import { deriveState } from "./statusModel.js";
import Sidebar from "./Sidebar.jsx";
import SummaryTab from "./SummaryTab.jsx";
import JobsAlertsTab from "./JobsAlertsTab.jsx";
import FlowTab from "./flow/FlowTab.jsx";
import { ActionBar, ContextMenu, Modal, HistoryTable } from "./ActionParts.jsx";
import "./monitoring.css";

const TABS = [["summary", "Summary"], ["jobsAlerts", "Jobs and Alerts"], ["flow", "Flow"]];

export default function MonitoringPage() {
  const navigate = useNavigate();
  const [jobs, setJobs] = useState([]);
  const [summary, setSummary] = useState(null);
  const [alerts, setAlerts] = useState([]);
  const [selected, setSelected] = useState(null);
  const [tab, setTab] = useState("summary");
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [expanded, setExpanded] = useState(new Set());
  const [childrenMap, setChildrenMap] = useState(new Map());
  const [rootPage, setRootPage] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [autoRefresh, setAutoRefresh] = useState(true);
  const [lastRefresh, setLastRefresh] = useState(null);
  const [menu, setMenu] = useState(null);
  const [selectedIds, setSelectedIds] = useState(new Set());
  const [modal, setModal] = useState(null);
  const [historyRows, setHistoryRows] = useState([]);
  const [logs, setLogs] = useState(null);
  const [viewSearch, setViewSearch] = useState("");
  const [page, setPage] = useState(0);
  const [flowKey, setFlowKey] = useState(0);
  const [boxPath, setBoxPath] = useState([]);
  const eventRef = useRef(null);

  const loadRoots = useCallback(async (showBusy = false) => {
    if (showBusy) setBusy(true);
    try {
      const [rootData, sum, alertData] = await Promise.all([
        monitoringApi.jobs({ search, status: statusFilter, rootsOnly: true, page, size: 500 }),
        dashboardApi.getSummary(),
        monitoringApi.getAlerts().catch(() => []),
      ]);
      setRootPage(rootData); setJobs(rootData.content || []); setSummary(sum); setAlerts(alertData || []); setLastRefresh(new Date());
      setSelected((old) => (old && (rootData.content || []).some((j) => j.id === old.id) ? (rootData.content || []).find((j) => j.id === old.id) : old || rootData.content?.[0] || null));
    } catch (e) { setError(e.message || "Unable to load monitoring data"); }
    finally { if (showBusy) setBusy(false); }
  }, [search, statusFilter, page]);

  const loadChildren = useCallback(async (box) => {
    try {
      const data = await monitoringApi.jobs({ boxId: box.id, page: 0, size: 1000 });
      setChildrenMap((m) => { const n = new Map(m); n.set(String(box.id), data.content || []); return n; });
    } catch (e) { setError(e.message); }
  }, []);

  useEffect(() => { loadRoots(true); }, [loadRoots]);
  useEffect(() => { if (!autoRefresh) return undefined; const id = setInterval(() => loadRoots(false), 5000); return () => clearInterval(id); }, [autoRefresh, loadRoots]);
  useEffect(() => { const h = () => setMenu(null); window.addEventListener("click", h); return () => window.removeEventListener("click", h); }, []);

  useEffect(() => {
    const url = monitoringApi.streamUrl(); if (!url) return undefined;
    try {
      const es = new EventSource(url); eventRef.current = es;
      es.addEventListener("snapshot", (e) => {
        try {
          const payload = JSON.parse(e.data); const incoming = payload.jobs || [];
          const byId = new Map(incoming.map((j) => [String(j.id), j]));
          setJobs((old) => old.map((j) => (byId.get(String(j.id)) ? { ...j, ...byId.get(String(j.id)) } : j)));
          setChildrenMap((old) => { const n = new Map(old); n.forEach((list, key) => n.set(key, list.map((j) => (byId.get(String(j.id)) ? { ...j, ...byId.get(String(j.id)) } : j)))); return n; });
          setSelected((old) => (old && byId.has(String(old.id)) ? { ...old, ...byId.get(String(old.id)) } : old));
          setBoxPath((path) => path.map((box) => (byId.has(String(box.id)) ? { ...box, ...byId.get(String(box.id)) } : box)));
          dashboardApi.getSummary().then(setSummary).catch(() => {});
          setLastRefresh(new Date());
        } catch { /* ignore malformed stream event */ }
      });
      es.onerror = () => {};
      return () => { es.close(); eventRef.current = null; };
    } catch { return undefined; }
  }, []);

  const allVisible = useMemo(() => {
    const list = [...jobs]; childrenMap.forEach((v) => list.push(...v));
    const map = new Map(); list.forEach((j) => map.set(String(j.id), j)); return [...map.values()];
  }, [jobs, childrenMap]);
  const filtered = useMemo(() => allVisible.filter((j) => (statusFilter === "ALL" || deriveState(j) === statusFilter) && (!viewSearch || `${j.name} ${j.hostName || ""}`.toLowerCase().includes(viewSearch.toLowerCase()))), [allVisible, viewSearch, statusFilter]);
  const counts = useMemo(() => {
    const c = { total: allVisible.length, running: 0, success: 0, failed: 0, killed: 0, hold: 0, waiting: 0, inactive: 0, restarted: 0 };
    const map = { RUNNING: "running", SUCCESS: "success", FAILURE: "failed", TERMINATED: "killed", ON_HOLD: "hold", ACTIVATED: "waiting", INACTIVE: "inactive", RESTART: "restarted" };
    allVisible.forEach((j) => { const key = map[deriveState(j)]; if (key) c[key] += 1; });
    return c;
  }, [allVisible]);

  const toggleSidebarBox = async (box) => {
    const key = String(box.id);
    if (expanded.has(key)) setExpanded((s) => { const n = new Set(s); n.delete(key); return n; });
    else { await loadChildren(box); setExpanded((s) => new Set(s).add(key)); }
  };
  const selectJob = (job, e) => { setSelected(job); if (e) setMenu({ x: Math.min(e.clientX, innerWidth - 280), y: Math.min(e.clientY, innerHeight - 480), job }); };
  const openBox = useCallback((box) => {
    if (!box || box.jobType !== "BOX") return;
    setSelected(box); loadChildren(box);
    setBoxPath((path) => { const idx = path.findIndex((it) => String(it.id) === String(box.id)); return idx >= 0 ? path.slice(0, idx + 1) : [...path, box]; });
    setTab("flow");
  }, [loadChildren]);
  const backBox = useCallback(() => setBoxPath((path) => path.slice(0, -1)), []);
  const parentBox = useCallback(() => setBoxPath((path) => (path.length > 1 ? path.slice(0, -1) : [])), []);
  const currentBox = boxPath.length ? boxPath[boxPath.length - 1] : null;
  const refresh = () => { setFlowKey((x) => x + 1); loadRoots(true); };

  const runAction = async (action, job = selected) => {
    if (!job) return;
    if (["kill", "stop"].includes(action) && !window.confirm(`${action === "kill" ? "Kill" : "Stop"} ${job.name}?`)) return;
    setBusy(true); setMenu(null);
    try { await eventApi[action](job.id); await loadRoots(false); if (action === "restart") setTab("flow"); }
    catch (e) { setError(e.message || `Unable to execute ${action}`); }
    finally { setBusy(false); }
  };
  const openHistory = async (job) => { setModal("history"); setHistoryRows(await executionApi.byJob(job.id)); };
  const openLogs = async (job) => {
    const rows = await executionApi.byJob(job.id); const latest = rows?.[0];
    if (!latest) { setLogs({ stdout: "No execution history.", stderr: "" }); setModal("logs"); return; }
    try { setLogs(await executionApi.logs(latest.id)); } catch (e) { setLogs({ stdout: e.message, stderr: "" }); }
    setModal("logs");
  };
  const copyJob = async (job) => {
    try {
      const full = await jobApi.get(job.id);
      const copy = {
        name: `${full.name}_COPY_${Date.now()}`, description: full.description || null, jobType: full.jobType, triggerType: "MANUAL", cronExpression: null, timezone: full.timezone || "UTC",
        scheduleStartTime: null, scheduleEndTime: null, misfirePolicy: full.misfirePolicy || "FIRE_NOW", conditionExpression: null, command: full.command || null, hostName: full.hostName || null, boxId: null,
        dependsOnJobIds: [], enabled: true, executionType: full.executionType || "SSH_SCRIPT", retryCount: full.retryCount || 0, retryIntervalSeconds: full.retryIntervalSeconds || 60,
        timeoutSeconds: full.timeoutSeconds || 0, workingDirectory: full.workingDirectory || null, arguments: full.arguments || null, concurrencyPolicy: full.concurrencyPolicy || "ALLOW",
      };
      await jobApi.create(copy); await loadRoots(false);
    } catch (e) { setError(e.message); }
  };
  const bulk = async (action) => {
    if (!selectedIds.size) return;
    setBusy(true);
    try {
      const r = await eventApi.bulk([...selectedIds], action);
      setError(r.failed ? `${r.succeeded} succeeded, ${r.failed} failed: ${r.errors?.join(" | ")}` : "");
      setSelectedIds(new Set()); await loadRoots(false);
    } catch (e) { setError(e.message); }
    finally { setBusy(false); }
  };
  const openParentBox = async (job) => {
    if (!job?.boxId) return;
    try { const parent = allVisible.find((j) => String(j.id) === String(job.boxId)) || await jobApi.get(job.boxId); openBox(parent); }
    catch (e) { setError(e.message); }
  };

  const cards = [["Total", counts.total, "inactive"], ["Running", counts.running, "running"], ["Success", counts.success, "success"], ["Failed", counts.failed, "failed"], ["Killed", counts.killed, "killed"], ["On Hold", counts.hold, "hold"], ["Waiting", counts.waiting, "waiting"], ["Restarted", counts.restarted, "restarted"]];

  return (
    <div className="asm-shell">
      <Sidebar
        jobs={jobs} expanded={expanded} onToggle={toggleSidebarBox} onSelect={selectJob} onOpenBox={openBox}
        selectedId={selected?.id} childrenMap={childrenMap} search={search} setSearch={setSearch}
        autoRefresh={autoRefresh} setAutoRefresh={setAutoRefresh} lastRefresh={lastRefresh} onRefresh={refresh}
        rootPage={rootPage} page={page} setPage={setPage}
      />

      <main className="asm-main">
        <div className="asm-topbar">
          <div><b>Monitoring</b><span> / Views</span></div>
          <div className="asm-topbar-actions">
            <button onClick={refresh} disabled={busy}>↻ Refresh</button>
            <button onClick={() => setAutoRefresh((v) => !v)}>{autoRefresh ? "Live" : "Paused"}</button>
          </div>
        </div>
        <div className="asm-tabs">{TABS.map(([key, lbl]) => <button key={key} className={tab === key ? "active" : ""} onClick={() => setTab(key)}>{lbl}</button>)}</div>
        {error && <div className="asm-error">{error}<button onClick={() => setError("")}>×</button></div>}

        <div className="asm-content">
          <div className="asm-summary-strip">{cards.map(([n, v, cls]) => <div className={`asm-card ${cls}`} key={n}><span>{n}</span><strong>{v}</strong></div>)}</div>
          {selected && <ActionBar job={selected} onRun={(a) => runAction(a)} />}

          {tab === "summary" && <SummaryTab summary={summary} alerts={alerts} counts={counts} jobs={filtered} onSelect={setSelected} />}
          {tab === "jobsAlerts" && (
            <JobsAlertsTab jobs={filtered} alerts={alerts} selectedIds={selectedIds} setSelectedIds={setSelectedIds} onSelect={setSelected}
              onOpenParent={openParentBox} onBulk={bulk} viewSearch={viewSearch} setViewSearch={setViewSearch}
              statusFilter={statusFilter} setStatusFilter={setStatusFilter} />
          )}
          {tab === "flow" && (
            <div className="asm-flow-panel">
              <div className="asm-flow-panel-head">
                <div className="asm-flow-panel-nav">
                  <button disabled={!currentBox} onClick={backBox}>← Back</button>
                  <button disabled={!currentBox || boxPath.length < 2} onClick={parentBox}>↑ Parent BOX</button>
                  <span>Monitoring</span>
                  {boxPath.map((box) => <span key={box.id} className="asm-breadcrumb">→ {box.name}</span>)}
                </div>
                <span>{currentBox ? `Current BOX: ${currentBox.name}` : "Root / Main BOXes"}</span>
              </div>
              <FlowTab key={`${flowKey}-${currentBox?.id ?? "ROOT"}`} boxId={currentBox?.id ?? null} refreshToken={lastRefresh?.getTime()} highlightId={selected?.id ?? null} onChanged={(node) => setSelected(node)} onContextMenu={(job, e) => selectJob(job, e)} />
            </div>
          )}
        </div>
      </main>

      <ContextMenu menu={menu} onClose={() => setMenu(null)} onAction={runAction} onOpen={() => navigate(`/jobs/${menu?.job?.id}`)} onEdit={() => navigate(`/jobs/${menu?.job?.id}/edit`)} onCopy={() => copyJob(menu.job)} onLogs={() => openLogs(menu.job)} onHistory={() => openHistory(menu.job)} />
      {modal === "logs" && <Modal title={`Execution Output — ${selected?.name || "Job"}`} onClose={() => setModal(null)} wide><pre className="asm-log">{logs?.stdout || logs?.output || ""}{logs?.stderr ? `\n\n--- STDERR ---\n${logs.stderr}` : ""}</pre></Modal>}
      {modal === "history" && <Modal title={`Execution History — ${selected?.name || "Job"}`} onClose={() => setModal(null)} wide><HistoryTable rows={historyRows} /></Modal>}
    </div>
  );
}
