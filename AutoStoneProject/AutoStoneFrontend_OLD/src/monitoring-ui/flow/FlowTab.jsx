import { useCallback, useMemo, useRef, useState, useEffect } from "react";
import { Background, Controls, MiniMap, MarkerType, ReactFlow, ReactFlowProvider } from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { useFlowGraph } from "./useFlowGraph.js";
import { layoutCompound } from "./layout.js";
import { nodeTypes } from "./nodes.jsx";
import { deriveState, STATE, STATE_ORDER } from "../statusModel.js";

function flatten(nodes, edges, layout, parentId, expandedBoxes, childCache, callbacks, ctx, out) {
  nodes.forEach((n) => {
    const id = String(n.id);
    const p = layout.positions.get(id);
    const fp = layout.footprints.get(id);
    const isExpandedBox = fp.isBox && fp.expanded;
    const base = { id, position: { x: p.x, y: p.y }, parentId: parentId || undefined, extent: parentId ? "parent" : undefined, draggable: false, selectable: true };
    if (isExpandedBox) {
      out.nodes.push({ ...base, type: "boxGroup", style: { width: fp.w, height: fp.h }, data: { ...n, __w: fp.w, __h: fp.h, __onToggle: callbacks.onToggle }, zIndex: 0 });
      const sub = fp.sub;
      const child = childCache.get(id);
      const shifted = { positions: new Map([...sub.positions].map(([cid, cp]) => [cid, { ...cp, x: cp.x + 16, y: cp.y + 22 }])), footprints: sub.footprints };
      flatten(child.data.nodes, child.data.edges, shifted, id, expandedBoxes, childCache, callbacks, ctx, out);
      const hasIncoming = new Set((child.data.edges || []).map((e) => String(e.to)));
      (child.data.nodes || []).forEach((c) => { if (!hasIncoming.has(String(c.id))) out.edges.push({ from: id, to: c.id, synthetic: true }); });
    } else {
      out.nodes.push({
        ...base,
        type: fp.isBox ? "box" : "job",
        data: { ...n, __selected: String(n.id) === String(ctx.highlightId), __dim: ctx.dimFn(n), __emphasize: ctx.emphasizeFn(n), __loading: fp.loading, __onToggle: callbacks.onToggle, __onSelect: callbacks.onSelect, __onContext: callbacks.onContext },
        zIndex: 5,
      });
    }
  });
  (edges || []).forEach((e) => out.edges.push(e));
}

function Toolbar({ search, setSearch, statusFilter, setStatusFilter, onZoomIn, onZoomOut, onFit, overviewOn, setOverviewOn, onPrint, liveOn, clock, nodeCount, edgeCount }) {
  return (
    <div className="asm-toolbar">
      <div className="asm-toolbar-icons">
        <button title="Zoom in" onClick={onZoomIn}>+</button>
        <button title="Zoom out" onClick={onZoomOut}>−</button>
        <button title="Fit to screen" onClick={onFit}>⤢</button>
        <button title="Toggle overview" className={overviewOn ? "active" : ""} onClick={() => setOverviewOn((v) => !v)}>▣</button>
        <button title="Print flow" onClick={onPrint}>🖶</button>
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

  const { baseFlow, loading, error, expandedBoxes, childCache, resolvedEdges, allVisible, toggleBox } = useFlowGraph(boxId, refreshToken, highlightId);

  useEffect(() => { setInternalSelected(null); }, [boxId]);
  useEffect(() => { if (highlightId != null) setInternalSelected(String(highlightId)); }, [highlightId]);
  useEffect(() => { const id = setInterval(() => setClock(new Date().toLocaleTimeString()), 1000); return () => clearInterval(id); }, []);

  const neighbors = useMemo(() => {
    if (!internalSelected) return null;
    const preds = new Set(), succs = new Set();
    allVisible.edges.forEach((e) => {
      if (String(e.to) === String(internalSelected)) preds.add(String(e.from));
      if (String(e.from) === String(internalSelected)) succs.add(String(e.to));
    });
    return { preds, succs };
  }, [internalSelected, allVisible]);

  const q = search.trim().toLowerCase();
  const dimFn = useCallback((n) => {
    const id = String(n.id);
    if ((q && !String(n.name).toLowerCase().includes(q)) || (statusFilter !== "ALL" && deriveState(n) !== statusFilter)) return true;
    if (neighbors && internalSelected && id !== String(internalSelected) && !neighbors.preds.has(id) && !neighbors.succs.has(id)) return true;
    return false;
  }, [q, statusFilter, neighbors, internalSelected]);
  const emphasizeFn = useCallback((n) => Boolean(neighbors && (neighbors.preds.has(String(n.id)) || neighbors.succs.has(String(n.id)))), [neighbors]);

  const onSelect = useCallback((node) => { setInternalSelected(String(node.id)); onChanged?.(node); }, [onChanged]);
  const onContext = useCallback((node, e) => { setInternalSelected(String(node.id)); onContextMenu?.(node, e); }, [onContextMenu]);

  const { rfNodes, rfEdgesRaw } = useMemo(() => {
    if (!baseFlow) return { rfNodes: [], rfEdgesRaw: [] };
    const layout = layoutCompound(baseFlow.nodes || [], baseFlow.edges || [], expandedBoxes, childCache);
    const out = { nodes: [], edges: [] };
    flatten(baseFlow.nodes || [], baseFlow.edges || [], layout, null, expandedBoxes, childCache, { onToggle: toggleBox, onSelect, onContext }, { highlightId: internalSelected, dimFn, emphasizeFn }, out);
    const outIds = new Set(out.nodes.map((n) => n.id));
    const seen = new Set(out.edges.map((e) => `${e.from}:${e.to}`));
    resolvedEdges.forEach((e) => { const k = `${e.from}:${e.to}`; if (outIds.has(String(e.from)) && outIds.has(String(e.to)) && !seen.has(k)) { out.edges.push(e); seen.add(k); } });
    return { rfNodes: out.nodes, rfEdgesRaw: out.edges };
  }, [baseFlow, expandedBoxes, childCache, internalSelected, dimFn, emphasizeFn, toggleBox, onSelect, onContext, resolvedEdges]);

  const rfEdges = useMemo(() => rfEdgesRaw.map((e, i) => {
    const isNeighborEdge = neighbors && (String(e.to) === String(internalSelected) || String(e.from) === String(internalSelected));
    const targetNode = allVisible.nodes.find((n) => String(n.id) === String(e.to));
    const stroke = isNeighborEdge ? "#1f9d55" : e.synthetic ? "#c3cbd1" : e.crossBox ? "#8a63c9" : "#5e6c78";
    return {
      id: `${e.from}-${e.to}-${i}`, source: String(e.from), target: String(e.to),
      label: e.synthetic ? "" : (e.conditionType || "s()"),
      type: "smoothstep",
      animated: targetNode ? deriveState(targetNode) === "RUNNING" : false,
      markerEnd: { type: MarkerType.ArrowClosed, width: 16, height: 16, color: stroke },
      style: { strokeWidth: isNeighborEdge ? 2.4 : 1.4, stroke, strokeDasharray: e.synthetic ? "3 3" : e.crossBox ? "5 3" : undefined },
      labelStyle: { fontSize: 7, fontWeight: 600, fill: "#5e6c78" },
      labelBgStyle: { fill: "#ffffff", fillOpacity: 0.85 },
      labelBgPadding: [1, 1],
      zIndex: isNeighborEdge ? 20 : 1,
    };
  }), [rfEdgesRaw, neighbors, internalSelected, allVisible]);

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
