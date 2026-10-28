import { Handle, Position } from "@xyflow/react";
import { meta, deriveState } from "../statusModel.js";

export function JobIconNode({ data }) {
  const m = meta(data);
  const cls = ["asm-job", data.__selected && "is-selected", data.__dim && "is-dim", data.__emphasize && "is-emphasized", deriveState(data) === "RUNNING" && "is-pulsing"].filter(Boolean).join(" ");
  return (
    <div className={cls} style={{ "--asm-c": m.color }}
      title={`${data.name} · ${m.label}${data.hostName ? " · " + data.hostName : ""}`}
      onClick={(e) => { e.stopPropagation(); data.__onSelect?.(data); }}
      onContextMenu={(e) => { e.preventDefault(); e.stopPropagation(); data.__onContext?.(data, e); }}
    >
      <Handle type="target" position={Position.Top} className="asm-handle" />
      {m.icon}
      <Handle type="source" position={Position.Bottom} className="asm-handle" />
    </div>
  );
}

export function BoxIconNode({ data }) {
  const m = meta(data);
  return (
    <div className={`asm-box-icon ${data.__selected ? "is-selected" : ""} ${data.__dim ? "is-dim" : ""}`} style={{ "--asm-c": m.color }}
      title={`${data.name} · BOX · ${m.label} · click to expand`}
      onClick={(e) => { e.stopPropagation(); data.__onToggle?.(data); }}
      onContextMenu={(e) => { e.preventDefault(); e.stopPropagation(); data.__onContext?.(data, e); }}
    >
      <Handle type="target" position={Position.Top} className="asm-handle" />
      {data.__loading ? "…" : "▦"}
      <Handle type="source" position={Position.Bottom} className="asm-handle" />
    </div>
  );
}

/** An expanded BOX: a literal border-only rectangle. Its children are
    separate React Flow nodes with parentId = this node's id, so RF
    positions and clips them inside automatically. Kept background
    fully transparent on purpose - edges render below the node layer,
    so any fill here would visually hide every line drawn inside it. */
export function BoxGroupNode({ data }) {
  const m = meta(data);
  return (
    <div className="asm-box-group" style={{ "--asm-c": m.color, width: data.__w, height: data.__h }}>
      <div className="asm-box-group-head" onClick={(e) => { e.stopPropagation(); data.__onToggle?.(data); }} title={`${data.name} · BOX · ${m.label} · click to collapse`}>
        <span className="asm-box-group-caret">▾</span>
        <span className="asm-box-group-name">{data.name}</span>
      </div>
    </div>
  );
}

export const nodeTypes = { job: JobIconNode, box: BoxIconNode, boxGroup: BoxGroupNode };
