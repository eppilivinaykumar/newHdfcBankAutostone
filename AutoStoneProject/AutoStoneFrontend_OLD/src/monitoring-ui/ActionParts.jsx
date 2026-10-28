import StatusDot from "./StatusDot.jsx";
import { label, ACTIVE_STATUSES, TERMINAL_STATUSES } from "./statusModel.js";

export function ActionBar({ job, onRun }) {
  if (!job) return null;
  const status = String(job.status || "CREATED").toUpperCase();
  const active = ACTIVE_STATUSES.includes(status);
  const startable = ["CREATED", "STOPPED", "SUCCESS", "FAILED", "KILLED", "TIMEOUT", "SKIPPED", "CANCELLED"].includes(status);
  const restartable = TERMINAL_STATUSES.includes(status);
  return (
    <div className="asm-actionbar">
      <div className="asm-actionbar-selected"><StatusDot job={job} /><div><b>{job.name}</b><small>{job.jobType} · {label(job)}</small></div></div>
      <div className="asm-actionbar-buttons">
        <button onClick={() => onRun("start")} disabled={!startable}>▶ Start</button>
        <button onClick={() => onRun("forceStart")} disabled={job.jobType === "BOX" || active || job.hold || job.ice || job.noExecution}>Force Start</button>
        <button onClick={() => onRun("stop")} disabled={!active}>■ Stop</button>
        <button onClick={() => onRun("kill")} disabled={!active}>Kill</button>
        <button onClick={() => onRun("restart")} disabled={!restartable}>↻ Restart</button>
        <button onClick={() => onRun("skip")} disabled={!["WAITING", "QUEUED"].includes(status)}>Skip</button>
        <button onClick={() => onRun(job.hold ? "offHold" : "hold")}>{job.hold ? "Unhold" : "Hold"}</button>
        <button onClick={() => onRun("markSuccess")}>Mark Success</button>
        <button onClick={() => onRun("markFailure")}>Mark Failure</button>
      </div>
    </div>
  );
}

export function ContextMenu({ menu, onClose, onAction, onOpen, onEdit, onCopy, onLogs, onHistory }) {
  if (!menu) return null;
  const j = menu.job;
  const status = String(j.status || "CREATED").toUpperCase();
  const active = ACTIVE_STATUSES.includes(status);
  const terminal = TERMINAL_STATUSES.includes(status);
  const holdAction = Boolean(j.hold) || Boolean(j.runtimeHold);
  const items = [
    ["Open Job", onOpen, false], ["Edit Job", onEdit, false], ["Copy Job", onCopy, false], ["View Logs", onLogs, false], ["View Output", onLogs, false], ["View History", onHistory, false], ["", null, true],
    [j.jobType === "BOX" ? "Start BOX" : "Start", () => onAction("start"), !["CREATED", "STOPPED", "SUCCESS", "FAILED", "KILLED", "TIMEOUT", "SKIPPED", "CANCELLED"].includes(status)],
    ...(j.jobType !== "BOX" ? [["Force Start", () => onAction("forceStart"), active || holdAction || j.ice || j.noExecution]] : []),
    [status === "PAUSED" ? "Resume" : "Pause", () => onAction(status === "PAUSED" ? "resume" : "pause"), !["RUNNING", "PAUSED"].includes(status)],
    [j.jobType === "BOX" ? "Stop BOX" : "Stop", () => onAction("stop"), !active],
    [j.jobType === "BOX" ? "Kill BOX" : "Kill", () => onAction("kill"), !active],
    [j.jobType === "BOX" ? "Restart BOX" : "Restart / Rerun", () => onAction("restart"), !terminal],
    ["", null, true],
    [holdAction ? "Unhold" : (j.jobType === "BOX" ? "Hold BOX" : "Hold"), () => onAction(holdAction ? "offHold" : "hold"), holdAction ? false : terminal],
    ...(status === "WAITING" || status === "QUEUED" ? [["Skip", () => onAction("skip"), false]] : []),
    ...(!terminal ? [["Mark Success", () => onAction("markSuccess"), false], ["Mark Failure", () => onAction("markFailure"), false]] : []),
  ];
  return (
    <div className="asm-context" style={{ left: menu.x, top: menu.y }} onClick={(e) => e.stopPropagation()}>
      <div className="asm-context-title"><StatusDot job={j} /><span>{j.name}</span></div>
      {items.map(([text, fn, disabled], i) => text ? <button key={i} disabled={disabled} onClick={() => { fn?.(); onClose(); }}>{text}</button> : <div className="asm-context-sep" key={i} />)}
    </div>
  );
}

export function Modal({ title, children, onClose, wide = false }) {
  return (
    <div className="asm-modal-backdrop" onMouseDown={onClose}>
      <div className={`asm-modal ${wide ? "wide" : ""}`} onMouseDown={(e) => e.stopPropagation()}>
        <div className="asm-modal-head"><b>{title}</b><button onClick={onClose}>×</button></div>
        {children}
      </div>
    </div>
  );
}

export function HistoryTable({ rows }) {
  return (
    <table className="asm-table">
      <thead><tr><th>Execution</th><th>Status</th><th>Attempt</th><th>Start</th><th>End</th><th>Host</th><th>Message</th></tr></thead>
      <tbody>{rows?.map((r) => <tr key={r.id}><td>{r.id}</td><td><StatusDot job={r} />{label(r)}</td><td>{r.attempt}</td><td>{r.startTime ? new Date(r.startTime).toLocaleString() : "—"}</td><td>{r.endTime ? new Date(r.endTime).toLocaleString() : "—"}</td><td>{r.host || "—"}</td><td>{r.message || ""}</td></tr>)}</tbody>
    </table>
  );
}
