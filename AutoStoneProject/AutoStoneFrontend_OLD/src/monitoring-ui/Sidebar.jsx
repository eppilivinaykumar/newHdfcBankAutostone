import StatusDot from "./StatusDot.jsx";

function TreeRow({ node, depth, expanded, onToggle, onSelect, onOpenBox, selectedId, childrenMap }) {
  const isBox = node.jobType === "BOX";
  const children = childrenMap.get(String(node.id)) || [];
  const open = expanded.has(String(node.id));
  return (
    <div>
      <div className={`asm-tree-row ${selectedId === node.id ? "is-selected" : ""}`} style={{ paddingLeft: 8 + depth * 14 }} onContextMenu={(e) => { e.preventDefault(); onSelect(node, e); }}>
        {isBox ? <button className="asm-tree-caret" onClick={(e) => { e.stopPropagation(); onToggle(node); }}>{open ? "▾" : "▸"}</button> : <span className="asm-tree-caret spacer">•</span>}
        <button className="asm-tree-label" onClick={() => onSelect(node)} onDoubleClick={() => isBox && onOpenBox(node)} title={isBox ? `${node.name} (double-click to open as root)` : node.name}>
          <StatusDot job={node} size="small" /><span>{node.name}</span>{isBox && <em>BOX</em>}
        </button>
        {isBox && <span className="asm-tree-count">{children.length}</span>}
      </div>
      {isBox && open && children.map((c) => <TreeRow key={c.id} node={c} depth={depth + 1} expanded={expanded} onToggle={onToggle} onSelect={onSelect} onOpenBox={onOpenBox} selectedId={selectedId} childrenMap={childrenMap} />)}
    </div>
  );
}

export default function Sidebar({ jobs, expanded, onToggle, onSelect, onOpenBox, selectedId, childrenMap, search, setSearch, autoRefresh, setAutoRefresh, lastRefresh, onRefresh, rootPage, page, setPage }) {
  return (
    <aside className="asm-sidebar">
      <div className="asm-sidebar-tools">
        <button onClick={() => setAutoRefresh((v) => !v)}>{autoRefresh ? "●" : "○"} Auto Refresh</button>
        <span>{lastRefresh?.toLocaleTimeString() || "—"}</span>
      </div>
      <div className="asm-sidebar-title">▾ AutoStone <button onClick={onRefresh}>↻</button></div>
      <div className="asm-sidebar-search"><input placeholder="Search jobs / boxes" value={search} onChange={(e) => { setPage(0); setSearch(e.target.value); }} /></div>
      <div className="asm-sidebar-tree">
        {jobs.map((j) => <TreeRow key={j.id} node={j} depth={0} expanded={expanded} onToggle={onToggle} onSelect={onSelect} onOpenBox={onOpenBox} selectedId={selectedId} childrenMap={childrenMap} />)}
        {!jobs.length && <div className="asm-empty small">No jobs match.</div>}
      </div>
      <div className="asm-sidebar-foot">
        <span>{rootPage?.totalElements || 0} top-level objects</span>
        <span><button disabled={!page} onClick={() => setPage((p) => Math.max(0, p - 1))}>‹</button> {page + 1}/{rootPage?.totalPages || 1} <button disabled={rootPage?.last} onClick={() => setPage((p) => p + 1)}>›</button></span>
      </div>
    </aside>
  );
}
