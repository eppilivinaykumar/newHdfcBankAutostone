import { meta } from "./statusModel.js";

export default function StatusDot({ job, size = "normal" }) {
  const m = meta(job);
  return <span className={`asm-dot ${size}`} style={{ background: m.color }} title={m.label}>{m.icon}</span>;
}
