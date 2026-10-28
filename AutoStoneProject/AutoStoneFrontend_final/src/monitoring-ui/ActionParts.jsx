import StatusDot from "./StatusDot.jsx";
import { label, ACTIVE_STATUSES, TERMINAL_STATUSES } from "./statusModel.js";

const STARTABLE = ["CREATED", "STOPPED", "SUCCESS", "FAILED", "KILLED", "TIMEOUT", "SKIPPED", "CANCELLED"];
const HOLDABLE = ["WAITING", "QUEUED"];
const PROCESS_CONTROLLABLE = ["RUNNING", "PAUSED"];

function actionRules(job) {
  const status = String(job?.status || "CREATED").toUpperCase();
  const held = Boolean(job?.hold) || Boolean(job?.runtimeHold);
  const terminal = TERMINAL_STATUSES.includes(status);
  return {
    status,
    held,
    terminal,
    active: ACTIVE_STATUSES.includes(status),
    startable: STARTABLE.includes(status),
    holdable: HOLDABLE.includes(status) && !held,
    processControllable: PROCESS_CONTROLLABLE.includes(status),
  };
}

export function ActionBar({ job, onRun }) {
  if (!job) return null;
  const r = actionRules(job);
  return (
    <div className="asm-actionbar">
      <div className="asm-actionbar-selected"><StatusDot job={job} /><div><b>{job.name}</b><small>{job.jobType} · {label(job)}</small></div></div>
      <div className="asm-actionbar-buttons">
        <button onClick={() => onRun("start")} disabled={!r.startable}>▶ Start</button>
        {job.jobType !== "BOX" && <button onClick={() => onRun("forceStart")} disabled={r.active || r.held || job.ice || job.noExecution}>Force Start</button>}
        {r.status === "RUNNING" && <button onClick={() => onRun("pause")}>Ⅱ Pause</button>}
        {r.status === "PAUSED" && <button onClick={() => onRun("resume")}>▶ Resume</button>}
        <button onClick={() => onRun("stop")} disabled={!r.processControllable}>■ Stop</button>
        <button onClick={() => onRun("kill")} disabled={!r.processControllable}>✕ Kill</button>
        <button onClick={() => onRun("restart")} disabled={!r.terminal}>↻ Restart</button>
        <button onClick={() => onRun(r.held ? "offHold" : "hold")} disabled={!r.held && !r.holdable}>{r.held ? "Unhold" : "Hold"}</button>
        <button onClick={() => onRun("skip")} disabled={!HOLDABLE.includes(r.status)}>↷ Skip</button>
      </div>
    </div>
  );
}

export function ContextMenu({ menu, onClose, onAction, onOpen, onEdit, onCopy, onLogs, onHistory }) {
  if (!menu) return null;
  const j = menu.job;
  const r = actionRules(j);
  const items = [
    ["Open Job", onOpen, false], ["Edit Job", onEdit, false], ["Copy Job", onCopy, false],
    ["View Logs", onLogs, false], ["View Output", onLogs, false], ["View History", onHistory, false], ["", null, true],
    [j.jobType === "BOX" ? "Start BOX" : "Start", () => onAction("start"), !r.startable],
    ...(j.jobType !== "BOX" ? [["Force Start", () => onAction("forceStart"), r.active || r.held || j.ice || j.noExecution]] : []),
    [r.status === "PAUSED" ? "Resume" : "Pause", () => onAction(r.status === "PAUSED" ? "resume" : "pause"), !["RUNNING", "PAUSED"].includes(r.status)],
    [j.jobType === "BOX" ? "Stop BOX" : "Stop", () => onAction("stop"), !r.processControllable],
    [j.jobType === "BOX" ? "Kill BOX" : "Kill", () => onAction("kill"), !r.processControllable],
    [j.jobType === "BOX" ? "Restart BOX" : "Restart / Rerun", () => onAction("restart"), !r.terminal],
    ["", null, true],
    [r.held ? "Unhold" : (j.jobType === "BOX" ? "Hold BOX" : "Hold"), () => onAction(r.held ? "offHold" : "hold"), r.held ? false : !r.holdable],
    ["Skip", () => onAction("skip"), !HOLDABLE.includes(r.status)],
  ];
  return (
    <div className="asm-context" style={{ left: menu.x, top: menu.y }} onClick={(e) => e.stopPropagation()}>
      <div className="asm-context-title"><StatusDot job={j} /><span>{j.name}</span></div>
      {items.map(([text, fn, disabled], i) => text ? <button key={i} disabled={disabled} onClick={() => { fn?.(); onClose(); }}>{text}</button> : <div className="asm-context-sep" key={i} />)}
    </div>
  );
}

export function Modal({ title, children, onClose, wide = false }) {
  return <div className="asm-modal-backdrop" onMouseDown={onClose}><div className={`asm-modal ${wide ? "wide" : ""}`} onMouseDown={(e) => e.stopPropagation()}><div className="asm-modal-head"><b>{title}</b><button onClick={onClose}>×</button></div>{children}</div></div>;
}

export function HistoryTable({ rows }) {
  return <table className="asm-table"><thead><tr><th>Execution</th><th>Status</th><th>Attempt</th><th>Start</th><th>End</th><th>Host</th><th>Message</th></tr></thead><tbody>{rows?.map((r) => <tr key={r.id}><td>{r.id}</td><td><StatusDot job={r} />{label(r)}</td><td>{r.attempt}</td><td>{r.startTime ? new Date(r.startTime).toLocaleString() : "—"}</td><td>{r.endTime ? new Date(r.endTime).toLocaleString() : "—"}</td><td>{r.host || "—"}</td><td>{r.message || ""}</td></tr>)}</tbody></table>;
}
