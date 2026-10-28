import { useCallback, useMemo, useRef, useState, useEffect } from "react";
import { Background, Controls, MiniMap, MarkerType, ReactFlow, ReactFlowProvider } from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { useFlowGraph } from "./useFlowGraph.js";
import { layoutCompound } from "./layout.js";
import { nodeTypes } from "./nodeTypes.js";
import { deriveState, STATE, STATE_ORDER } from "../statusModel.js";

function flatten(nodes, edges, layout, parentId, expandedBoxes, childCache, callbacks, ctx, out) {
  nodes.forEach((n) => {
    const id = String(n.id);
    const p = layout.positions.get(id);
    const fp = layout.footprints.get(id);
    const isExpandedBox = fp.isBox && fp.expanded;
    const base = { id, position: { x: p.x, y: p.y }, parentId: parentId || undefined, extent: parentId ? "parent" : undefined, draggable: false, selectable: true };
    if (isExpandedBox) {
      out.nodes.push({
        ...base, type: "boxGroup", style: { width: fp.w, height: fp.h },
        data: { ...n, __w: fp.w, __h: fp.h, __selected: String(n.id) === String(ctx.highlightId), __onToggle: callbacks.onToggle, __onSelect: callbacks.onSelect },
        zIndex: 0,
      });
      const sub = fp.sub;
      const child = childCache.get(id);
      // Layout may mark a BOX as expanded while its child request is loading.
      // Never dereference child.data until the request is ready.
      if (child?.status === "ready" && child.data && sub) {
        const shifted = { positions: new Map([...sub.positions].map(([cid, cp]) => [cid, { ...cp, x: cp.x + 16, y: cp.y + 16 }])), footprints: sub.footprints };
        flatten(child.data.nodes || [], child.data.edges || [], shifted, id, expandedBoxes, childCache, callbacks, ctx, out);
        const hasIncoming = new Set((child.data.edges || []).map((e) => String(e.to)));
        (child.data.nodes || []).forEach((c) => { if (!hasIncoming.has(String(c.id))) out.edges.push({ from: id, to: c.id, synthetic: true }); });
      }
    } else {
      out.nodes.push({
        ...base,
        type: fp.isBox ? "box" : "job",
        data: { ...n, __selected: String(n.id) === String(ctx.highlightId), __dim: ctx.dimFn(n), __loading: fp.loading, __onToggle: callbacks.onToggle, __onSelect: callbacks.onSelect, __onContext: callbacks.onContext },
        zIndex: 5,
      });
    }
  });
  (edges || []).forEach((e) => out.edges.push(e));
}

function Toolbar({ search, setSearch, statusFilter, setStatusFilter, onZoomIn, onZoomOut, onFit, overviewOn, setOverviewOn, onPrint, onExpandAll, onCollapseAll, onRefresh, liveOn, clock, nodeCount, edgeCount }) {
  return (
    <div className="asm-toolbar">
      <div className="asm-toolbar-icons">
        <button title="Refresh flow" onClick={onRefresh}>↻</button>
        <button title="Zoom in" onClick={onZoomIn}>+</button>
        <button title="Zoom out" onClick={onZoomOut}>−</button>
        <button title="Fit to screen" onClick={onFit}>⤢</button>
        <button title="Toggle overview" className={overviewOn ? "active" : ""} onClick={() => setOverviewOn((v) => !v)}>▣</button>
        <button title="Print flow" onClick={onPrint}>🖶</button>
        <span className="asm-toolbar-divider" />
        <button className="asm-toolbar-text-btn" title="Expand every BOX" onClick={onExpandAll}>Expand All</button>
        <button className="asm-toolbar-text-btn" title="Collapse every BOX" onClick={onCollapseAll}>Collapse All</button>
      </div>
      <div className="asm-toolbar-meta">
        <span className={`asm-live-dot ${liveOn ? "on" : "off"}`} />
        <span>{nodeCount} objects · {edgeCount} dependencies</span>
      </div>
      <div className="asm-toolbar-search">
        <input placeholder="Search job…" value={search} onChange={(e) => setSearch(e.target.value)} />
        <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
          <option value="ALL">Select by status</option>
          {STATE_ORDER.map((s) => <option key={s} value={s}>{STATE[s].label}</option>)}
        </select>
        <span className="asm-clock">{clock}</span>
      </div>
    </div>
  );
}

function Legend() {
  return (
    <div className="asm-legend">
      {STATE_ORDER.map((s) => <span key={s}><i style={{ background: STATE[s].color }} />{STATE[s].label}</span>)}
      <span><i className="asm-legend-line" />Cross-BOX dependency</span>
    </div>
  );
}

function FlowInner({ boxId, refreshToken, onChanged, onContextMenu, highlightId }) {
  const [internalSelected, setInternalSelected] = useState(null);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [overviewOn, setOverviewOn] = useState(true);
  const [clock, setClock] = useState(() => new Date().toLocaleTimeString());
  const rfInstance = useRef(null);
  const wrapRef = useRef(null);

  const { baseFlow, loading, error, expandedBoxes, childCache, resolvedEdges, allVisible, toggleBox, expandAll, collapseAll, refreshNow } = useFlowGraph(boxId, refreshToken, highlightId);

  useEffect(() => { setInternalSelected(null); }, [boxId]);
  useEffect(() => { if (highlightId != null) setInternalSelected(String(highlightId)); }, [highlightId]);
  useEffect(() => { const id = setInterval(() => setClock(new Date().toLocaleTimeString()), 1000); return () => clearInterval(id); }, []);

  const q = search.trim().toLowerCase();
  const dimFn = useCallback((n) => {
    if (q && !String(n.name).toLowerCase().includes(q)) return true;
    if (statusFilter !== "ALL" && deriveState(n) !== statusFilter) return true;
    return false;
  }, [q, statusFilter]);

  const onSelect = useCallback((node) => { setInternalSelected(String(node.id)); onChanged?.(node); }, [onChanged]);
  const onContext = useCallback((node, e) => { setInternalSelected(String(node.id)); onContextMenu?.(node, e); }, [onContextMenu]);

  const { rfNodes, rfEdgesRaw } = useMemo(() => {
    if (!baseFlow) return { rfNodes: [], rfEdgesRaw: [] };
    const layout = layoutCompound(baseFlow.nodes || [], baseFlow.edges || [], expandedBoxes, childCache);
    const out = { nodes: [], edges: [] };
    flatten(baseFlow.nodes || [], baseFlow.edges || [], layout, null, expandedBoxes, childCache, { onToggle: toggleBox, onSelect, onContext }, { highlightId: internalSelected, dimFn }, out);
    const outIds = new Set(out.nodes.map((n) => n.id));
    const seen = new Set(out.edges.map((e) => `${e.from}:${e.to}`));
    resolvedEdges.forEach((e) => { const k = `${e.from}:${e.to}`; if (outIds.has(String(e.from)) && outIds.has(String(e.to)) && !seen.has(k)) { out.edges.push(e); seen.add(k); } });

    // ancestor-box highlight: whenever something is selected, light up the
    // border of every box that contains it, at any nesting depth - this is
    // how you tell which box something belongs to now that box names/labels
    // aren't shown on the chart at all.
    if (internalSelected) {
      const parentOf = new Map(out.nodes.map((n) => [n.id, n.parentId]));
      const ancestors = new Set();
      let cur = parentOf.get(String(internalSelected));
      while (cur) { ancestors.add(cur); cur = parentOf.get(cur); }
      out.nodes.forEach((n) => { if (n.type === "boxGroup" && ancestors.has(n.id)) n.data = { ...n.data, __boxHighlight: true }; });
    }
    return { rfNodes: out.nodes, rfEdgesRaw: out.edges };
  }, [baseFlow, expandedBoxes, childCache, internalSelected, dimFn, toggleBox, onSelect, onContext, resolvedEdges]);

  const rfEdges = useMemo(() => rfEdgesRaw.map((e, i) => {
    const targetNode = allVisible.nodes.find((n) => String(n.id) === String(e.to));
    const stroke = e.synthetic ? "#c3cbd1" : e.crossBox ? "#8a63c9" : "#5e6c78";
    return {
      id: `${e.from}-${e.to}-${i}`, source: String(e.from), target: String(e.to),
      type: "smoothstep",
      animated: targetNode ? deriveState(targetNode) === "RUNNING" : false,
      markerEnd: { type: MarkerType.ArrowClosed, width: 16, height: 16, color: stroke },
      style: { strokeWidth: 1.4, stroke, strokeDasharray: e.synthetic ? "3 3" : e.crossBox ? "5 3" : undefined },
      zIndex: 1,
    };
  }), [rfEdgesRaw, allVisible]);

  const onPaneClick = useCallback(() => setInternalSelected(null), []);
  const miniMapNodeColor = useCallback((node) => STATE[deriveState(node.data)]?.color || STATE.INACTIVE.color, []);
  const onPrint = () => { wrapRef.current?.classList.add("asm-print"); window.print(); setTimeout(() => wrapRef.current?.classList.remove("asm-print"), 300); };

  if (loading) return <div className="asm-empty">Loading workflow flow…</div>;
  if (error) return <div className="asm-banner-error">Flow API error: {error}</div>;
  if (!rfNodes.length) return <div className="asm-empty">No jobs in this flow.</div>;

  return (
    <div className="asm-flow-wrap" ref={wrapRef}>
      <Toolbar search={search} setSearch={setSearch} statusFilter={statusFilter} setStatusFilter={setStatusFilter}
        onZoomIn={() => rfInstance.current?.zoomIn?.()} onZoomOut={() => rfInstance.current?.zoomOut?.()} onFit={() => rfInstance.current?.fitView?.({ padding: 0.2 })}
        overviewOn={overviewOn} setOverviewOn={setOverviewOn} onPrint={onPrint}
        onExpandAll={expandAll} onCollapseAll={collapseAll} onRefresh={refreshNow}
        liveOn={Boolean(refreshToken)} clock={clock} nodeCount={rfNodes.length} edgeCount={rfEdges.length} />
      <Legend />
      <div className="asm-canvas">
        <ReactFlow
          nodes={rfNodes} edges={rfEdges} nodeTypes={nodeTypes}
          onInit={(inst) => { rfInstance.current = inst; }}
          onPaneClick={onPaneClick}
          fitView fitViewOptions={{ padding: 0.25, maxZoom: 1.3 }}
          minZoom={0.08} maxZoom={3}
          panOnDrag nodesDraggable={false} nodesConnectable={false}
          zoomOnScroll zoomOnPinch
          proOptions={{ hideAttribution: true }}
        >
          <Background gap={22} size={1} color="#eef1f3" />
          {overviewOn && <MiniMap nodeColor={miniMapNodeColor} pannable zoomable />}
          <Controls showInteractive={false} showZoom={false} showFitView={false} />
        </ReactFlow>
      </div>
    </div>
  );
}

export default function FlowTab(props) {
  return <ReactFlowProvider><FlowInner {...props} /></ReactFlowProvider>;
}
