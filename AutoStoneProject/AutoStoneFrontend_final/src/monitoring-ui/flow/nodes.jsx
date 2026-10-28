import { Handle, Position } from "@xyflow/react";
import { meta, deriveState } from "../statusModel.js";

export function JobIconNode({ data }) {
  const m = meta(data);
  const cls = ["asm-job", data.__selected && "is-selected", data.__dim && "is-dim", deriveState(data) === "RUNNING" && "is-pulsing"].filter(Boolean).join(" ");
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

/** An expanded BOX: a literal dashed-border rectangle - no name/status
    text on it at all, just the shape (per feedback: "circles, arrows
    and lines are good enough"). A small unlabeled caret in the corner
    is the only affordance, for collapsing it back. The border itself
    lights up (via __boxHighlight) whenever the currently-selected job
    or box lives inside it, at any nesting depth - that's how you tell
    which box something belongs to now that names aren't shown.
    Children are separate React Flow nodes with parentId = this node's
    id, so RF positions and clips them inside automatically.
    Background stays fully transparent on purpose - edges render below
    the node layer, so any fill here would hide every line inside it. */
export function BoxGroupNode({ data }) {
  const m = meta(data);
  const cls = ["asm-box-group", data.__selected && "is-selected", (data.__boxHighlight && !data.__selected) && "is-ancestor"].filter(Boolean).join(" ");
  return (
    <div className={cls} style={{ "--asm-c": m.color, width: data.__w, height: data.__h }}
      onClick={(e) => { e.stopPropagation(); data.__onSelect?.(data); }}
      title={`${data.name} · BOX · ${m.label}`}
    >
      <button className="asm-box-group-toggle" onClick={(e) => { e.stopPropagation(); data.__onToggle?.(data); }} title="Collapse BOX">▾</button>
    </div>
  );
}

