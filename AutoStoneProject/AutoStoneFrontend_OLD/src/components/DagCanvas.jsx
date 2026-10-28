import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  Background,
  Controls,
  MiniMap,
  MarkerType,
  ReactFlow,
  ReactFlowProvider,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { jobApi } from "../api/api.js";
import {
  deriveAutosysState,
  AUTOSYS_STATE_META,
  AUTOSYS_STATE_ORDER,
  miniMapColor,
} from "../monitoring/statusPresentation.js";

/* ================================================================
   AutoSys Flow tab v4 - box-in-box nesting + real cross-box edges.

   Why v4: the /flow endpoint only returns an edge when BOTH ends are
   direct children of the same BOX, which drops almost every
   box-to-box dependency in real JIL (a box's condition very commonly
   points at a job nested inside a *different* box). /jobs/{id}/relations
   has no such filtering - it returns a job's true parents/children,
   each carrying its own boxId - so for every currently-visible box or
   job we also call relations() and resolve each neighbour up its
   boxId chain (via GET /jobs/{id}) until it lands on a currently
   visible sibling, then draw the edge there. Same principle AutoSys
   itself uses: a dependency on a hidden job renders as a line to
   the box that currently contains it.
   No backend changes - only additional calls to endpoints that
   already exist.
   ================================================================ */

const NODE_D = 34;
const BOX_ICON = 34;
const BOX_HEADER = 20;
const BOX_PAD = 16;
const GAP_X = 14;
const GAP_Y = 50;
const MIN_BOX_W = BOX_ICON + BOX_PAD * 2;
const MAX_ANCESTOR_HOPS = 8;

/* ---------------------------------------------------------------
   Node renderers - icon only, name in title tooltip
   --------------------------------------------------------------- */
function JobIconNode({ data }) {
  const meta = AUTOSYS_STATE_META[deriveAutosysState(data)];
  return (
    <div
      className={`asx-job ${data.__selected ? "is-selected" : ""} ${data.__dim ? "is-dim" : ""} ${data.__emphasize ? "is-emphasized" : ""} ${deriveAutosysState(data) === "RUNNING" ? "is-pulsing" : ""}`}
      style={{ "--asx-color": meta.color }}
      title={`${data.name} · ${meta.label}${data.hostName ? " · " + data.hostName : ""}`}
      onClick={(e) => { e.stopPropagation(); data.__onSelect?.(data); }}
      onContextMenu={(e) => { e.preventDefault(); e.stopPropagation(); data.__onContext?.(data, e); }}
    >
      {meta.icon}
    </div>
  );
}

function BoxIconNode({ data }) {
  const meta = AUTOSYS_STATE_META[deriveAutosysState(data)];
  const loading = data.__loadingChildren;
  return (
    <div
      className={`asx-box-icon ${data.__selected ? "is-selected" : ""} ${data.__dim ? "is-dim" : ""}`}
      style={{ "--asx-color": meta.color }}
      title={`${data.name} · BOX · ${meta.label} · click to expand`}
      onClick={(e) => { e.stopPropagation(); data.__onToggle?.(data); }}
      onContextMenu={(e) => { e.preventDefault(); e.stopPropagation(); data.__onContext?.(data, e); }}
    >
      {loading ? "…" : "▦"}
    </div>
  );
}

function BoxGroupNode({ data }) {
  const meta = AUTOSYS_STATE_META[deriveAutosysState(data)];
  return (
    <div className="asx-box-group" style={{ "--asx-color": meta.color, width: data.__w, height: data.__h }}>
      <div
        className="asx-box-group-head"
        onClick={(e) => { e.stopPropagation(); data.__onToggle?.(data); }}
        title={`${data.name} · BOX · ${meta.label} · click to collapse`}
      >
        <span className="asx-box-group-toggle">▾</span>
        <span className="asx-box-group-name">{data.name}</span>
      </div>
    </div>
  );
}

const nodeTypes = { job: JobIconNode, box: BoxIconNode, boxGroup: BoxGroupNode };

/* ---------------------------------------------------------------
   Compound layout (same approach as before - footprint-aware,
   recursive, level-based within each box).
   --------------------------------------------------------------- */
function layoutCompound(nodes, edges, expandedBoxes, childCache) {
  const footprints = new Map();

  function footprintOf(n) {
    const id = String(n.id);
    if (footprints.has(id)) return footprints.get(id);
    let fp;
    if (n.jobType === "BOX" && expandedBoxes.has(id)) {
      const child = childCache.get(id);
      if (child && child.status === "ready" && child.data.nodes?.length) {
        const sub = layoutCompound(child.data.nodes, child.data.edges, expandedBoxes, childCache);
        fp = { w: Math.max(sub.width + BOX_PAD * 2, MIN_BOX_W), h: sub.height + BOX_HEADER + BOX_PAD, isBox: true, expanded: true, sub, childFlow: child.data };
      } else {
        fp = { w: BOX_ICON, h: BOX_ICON, isBox: true, expanded: false, loading: child?.status === "loading" };
      }
    } else if (n.jobType === "BOX") {
      fp = { w: BOX_ICON, h: BOX_ICON, isBox: true, expanded: false };
    } else {
      fp = { w: NODE_D, h: NODE_D, isBox: false };
    }
    footprints.set(id, fp);
    return fp;
  }
  nodes.forEach(footprintOf);

  const byLevel = new Map();
  nodes.forEach((n) => { const l = n.level || 0; if (!byLevel.has(l)) byLevel.set(l, []); byLevel.get(l).push(n); });
  const levels = [...byLevel.keys()].sort((a, b) => a - b);
  levels.forEach((l) => byLevel.get(l).sort((a, b) => String(a.name).localeCompare(String(b.name))));

  const positions = new Map();
  let top = 0;
  let maxRowW = 0;
  levels.forEach((l) => {
    const row = byLevel.get(l);
    let x = 0, rowH = 0;
    row.forEach((n) => {
      const fp = footprintOf(n);
      positions.set(String(n.id), { x, y: top, w: fp.w, h: fp.h });
      x += fp.w + GAP_X;
      rowH = Math.max(rowH, fp.h);
    });
    x -= GAP_X;
    maxRowW = Math.max(maxRowW, x);
    top += rowH + GAP_Y;
  });
  top -= GAP_Y;
  if (top < 0) top = 0;

  levels.forEach((l) => {
    const row = byLevel.get(l);
    const rowW = row.reduce((s, n) => s + footprintOf(n).w + GAP_X, -GAP_X);
    const offset = Math.max(0, (maxRowW - rowW) / 2);
    row.forEach((n) => { const p = positions.get(String(n.id)); p.x += offset; });
  });

  return { width: maxRowW, height: Math.max(top, NODE_D), positions, footprints };
}

function flatten(nodes, edges, layout, parentId, expandedBoxes, childCache, callbacks, ctx, out) {
  nodes.forEach((n) => {
    const id = String(n.id);
    const p = layout.positions.get(id);
    const fp = layout.footprints.get(id);
    const isExpandedBox = fp.isBox && fp.expanded;
    const base = {
      id,
      position: { x: p.x, y: p.y },
      parentId: parentId || undefined,
      extent: parentId ? "parent" : undefined,
      draggable: false,
      selectable: true,
    };
    if (isExpandedBox) {
      out.nodes.push({ ...base, type: "boxGroup", style: { width: fp.w, height: fp.h }, data: { ...n, __w: fp.w, __h: fp.h, __onToggle: callbacks.onToggle }, zIndex: 0 });
      const sub = fp.sub;
      const child = childCache.get(id);
      const shifted = { positions: new Map([...sub.positions].map(([cid, cp]) => [cid, { ...cp, x: cp.x + BOX_PAD, y: cp.y + BOX_HEADER }])), footprints: sub.footprints };
      flatten(child.data.nodes, child.data.edges, shifted, id, expandedBoxes, childCache, callbacks, ctx, out);
      const hasIncoming = new Set((child.data.edges || []).map((e) => String(e.to)));
      (child.data.nodes || []).forEach((c) => { if (!hasIncoming.has(String(c.id))) out.edges.push({ from: id, to: c.id, synthetic: true }); });
    } else {
      out.nodes.push({
        ...base,
        type: fp.isBox ? "box" : "job",
        data: {
          ...n,
          __selected: String(n.id) === String(ctx.highlightId),
          __dim: ctx.dimFn(n),
          __emphasize: ctx.emphasizeFn(n),
          __loadingChildren: fp.loading,
          __onToggle: callbacks.onToggle,
          __onSelect: callbacks.onSelect,
          __onContext: callbacks.onContext,
        },
        zIndex: 5,
      });
    }
  });
  (edges || []).forEach((e) => out.edges.push(e));
}

/* ---------------------------------------------------------------
   Toolbar + legend
   --------------------------------------------------------------- */
function Toolbar({ search, setSearch, statusFilter, setStatusFilter, onZoomIn, onZoomOut, onFit, overviewOn, setOverviewOn, onPrint, liveOn, clock, nodeCount, edgeCount }) {
  return (
    <div className="asx-toolbar">
      <div className="asx-toolbar-icons">
        <button title="Zoom in" onClick={onZoomIn}>+</button>
        <button title="Zoom out" onClick={onZoomOut}>−</button>
        <button title="Fit to screen" onClick={onFit}>⤢</button>
        <button title="Toggle overview" className={overviewOn ? "active" : ""} onClick={() => setOverviewOn((v) => !v)}>▣</button>
        <button title="Print flow" onClick={onPrint}>🖶</button>
      </div>
      <div className="asx-toolbar-meta">
        <span className={`asx-live-dot ${liveOn ? "on" : "off"}`} />
        <span>{nodeCount} objects · {edgeCount} dependencies</span>
      </div>
      <div className="asx-toolbar-search">
        <input placeholder="Search job…" value={search} onChange={(e) => setSearch(e.target.value)} />
        <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
          <option value="ALL">Select by status</option>
          {AUTOSYS_STATE_ORDER.map((s) => <option key={s} value={s}>{AUTOSYS_STATE_META[s].label}</option>)}
        </select>
        <span className="asx-clock">{clock}</span>
      </div>
    </div>
  );
}

function Legend() {
  return (
    <div className="asx-legend">
      {AUTOSYS_STATE_ORDER.map((s) => {
        const meta = AUTOSYS_STATE_META[s];
        return <span key={s}><i style={{ background: meta.color }} />{meta.label}</span>;
      })}
      <span><span className="asx-legend-line" />Cross-BOX dependency</span>
    </div>
  );
}

/* ---------------------------------------------------------------
   Main component
   --------------------------------------------------------------- */
function DagCanvasInner({ boxId, refreshToken, onChanged, onContextMenu, highlightId }) {
  const [baseFlow, setBaseFlow] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [internalSelected, setInternalSelected] = useState(null);

  const [expandedBoxes, setExpandedBoxes] = useState(new Set());
  const [childCache, setChildCache] = useState(new Map());

  // relations()-based cross-box edge resolution
  const [relCache, setRelCache] = useState(new Map());   // jobId -> {parents:[JobSummary], children:[JobSummary]}
  const [boxOfCache, setBoxOfCache] = useState(new Map()); // jobId -> boxId (or null), lazily fetched via GET /jobs/{id}
  const [resolvedEdges, setResolvedEdges] = useState([]);

  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [overviewOn, setOverviewOn] = useState(true);
  const [clock, setClock] = useState(() => new Date().toLocaleTimeString());

  const rfInstance = useRef(null);
  const wrapRef = useRef(null);

  useEffect(() => { setExpandedBoxes(new Set()); setChildCache(new Map()); setInternalSelected(null); setRelCache(new Map()); setBoxOfCache(new Map()); setResolvedEdges([]); }, [boxId]);
  useEffect(() => { if (highlightId != null) setInternalSelected(String(highlightId)); }, [highlightId]);
  useEffect(() => { const id = setInterval(() => setClock(new Date().toLocaleTimeString()), 1000); return () => clearInterval(id); }, []);

  useEffect(() => {
    let cancelled = false;
    setLoading(true); setError(null);
    jobApi.flow(boxId ?? null, { mode: "BOX", depth: 1 })
      .then((data) => { if (!cancelled) setBaseFlow(data); })
      .catch((err) => { if (!cancelled) setError(err.message || "Unable to load workflow flow"); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [boxId, refreshToken]);

  const loadBoxChildren = useCallback((box) => {
    const key = String(box.id);
    setChildCache((m) => { const n = new Map(m); n.set(key, { status: "loading" }); return n; });
    jobApi.flow(box.id, { mode: "BOX", depth: 1 })
      .then((data) => setChildCache((m) => { const n = new Map(m); n.set(key, { status: "ready", data }); return n; }))
      .catch((err) => setChildCache((m) => { const n = new Map(m); n.set(key, { status: "error", error: err.message }); return n; }));
  }, []);

  const toggleBox = useCallback((box) => {
    const key = String(box.id);
    setExpandedBoxes((s) => {
      const n = new Set(s);
      if (n.has(key)) n.delete(key);
      else { n.add(key); if (!childCache.has(key)) loadBoxChildren(box); }
      return n;
    });
  }, [childCache, loadBoxChildren]);

  /* --- auto-reveal: when highlightId points at a node hidden inside a
     collapsed box, walk its boxId ancestry (GET /jobs/{id}) up to a
     currently-visible root node and expand every box along the way,
     instead of re-rooting the whole view onto it. --- */
  useEffect(() => {
    if (highlightId == null || !baseFlow) return;
    let cancelled = false;
    (async () => {
      const rootIds = new Set((baseFlow.nodes || []).map((n) => String(n.id)));
      let cur = String(highlightId);
      if (rootIds.has(cur)) return; // already visible at root, nothing to expand
      const path = [];
      let depth = 0;
      let localBoxOf = boxOfCache;
      while (!rootIds.has(cur) && depth < MAX_ANCESTOR_HOPS) {
        let bId = localBoxOf.get(cur);
        if (bId === undefined) {
          try { const j = await jobApi.get(cur); bId = j.boxId == null ? null : String(j.boxId); }
          catch { bId = null; }
          if (cancelled) return;
          localBoxOf = new Map(localBoxOf); localBoxOf.set(cur, bId);
          setBoxOfCache((prev) => { const n = new Map(prev); n.set(cur, bId); return n; });
        }
        if (bId == null) break;
        path.unshift(bId);
        cur = bId;
        depth++;
      }
      if (cancelled || !path.length) return;
      setExpandedBoxes((prev) => { const n = new Set(prev); path.forEach((id) => n.add(id)); return n; });
      path.forEach((id) => { if (!childCache.has(id)) loadBoxChildren({ id }); });
    })();
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [highlightId, baseFlow]);

  /* --- cross-box edges: for every currently-visible "sibling group"
     (the root set, and each expanded box's own children set), fetch
     relations() for every member and resolve each neighbour up its
     boxId chain until it lands on a sibling in that same group. --- */
  const siblingGroups = useMemo(() => {
    const groups = [];
    if (baseFlow) groups.push(new Set((baseFlow.nodes || []).map((n) => String(n.id))));
    childCache.forEach((c, bId) => { if (expandedBoxes.has(bId) && c.status === "ready") groups.push(new Set((c.data.nodes || []).map((n) => String(n.id)))); });
    return groups;
  }, [baseFlow, childCache, expandedBoxes]);

  useEffect(() => {
    if (!siblingGroups.length) return;
    let cancelled = false;
    (async () => {
      const allIds = new Set();
      siblingGroups.forEach((g) => g.forEach((id) => allIds.add(id)));
      const toFetch = [...allIds].filter((id) => !relCache.has(id));
      if (toFetch.length) {
        const results = await Promise.all(toFetch.map((id) => jobApi.relations(id).then((r) => [id, r]).catch(() => [id, { parents: [], children: [] }])));
        if (cancelled) return;
        setRelCache((prev) => { const n = new Map(prev); results.forEach(([id, r]) => n.set(id, r)); return n; });
        return; // resolution runs on the next pass once relCache updates
      }
      const boxFetchNeeded = new Set();
      const resolveToSibling = (neighbor, groupIds) => {
        let curId = String(neighbor.id);
        if (groupIds.has(curId)) return curId;
        let curBox = neighbor.boxId == null ? null : String(neighbor.boxId);
        let depth = 0;
        while (curBox && depth < MAX_ANCESTOR_HOPS) {
          if (groupIds.has(curBox)) return curBox;
          if (!boxOfCache.has(curBox)) { boxFetchNeeded.add(curBox); return null; }
          curBox = boxOfCache.get(curBox);
          depth++;
        }
        return null;
      };
      const resolved = [];
      siblingGroups.forEach((groupIds) => {
        groupIds.forEach((id) => {
          const rel = relCache.get(id);
          if (!rel) return;
          (rel.parents || []).forEach((p) => { const r = resolveToSibling(p, groupIds); if (r && r !== id) resolved.push({ from: r, to: id, conditionType: "s()", crossBox: true }); });
          (rel.children || []).forEach((c) => { const r = resolveToSibling(c, groupIds); if (r && r !== id) resolved.push({ from: id, to: r, conditionType: "s()", crossBox: true }); });
        });
      });
      if (boxFetchNeeded.size) {
        const results = await Promise.all([...boxFetchNeeded].map((id) => jobApi.get(id).then((j) => [id, j.boxId == null ? null : String(j.boxId)]).catch(() => [id, null])));
        if (cancelled) return;
        setBoxOfCache((prev) => { const n = new Map(prev); results.forEach(([id, b]) => n.set(id, b)); return n; });
        return; // resolution runs again next pass
      }
      if (!cancelled) setResolvedEdges(resolved);
    })();
    return () => { cancelled = true; };
  }, [siblingGroups, relCache, boxOfCache]);

  const allVisible = useMemo(() => {
    const nodes = []; const edges = [];
    function walk(flow) {
      (flow.nodes || []).forEach((n) => {
        nodes.push(n);
        if (n.jobType === "BOX" && expandedBoxes.has(String(n.id))) {
          const c = childCache.get(String(n.id));
          if (c?.status === "ready") walk(c.data);
        }
      });
      (flow.edges || []).forEach((e) => edges.push(e));
    }
    if (baseFlow) walk(baseFlow);
    resolvedEdges.forEach((e) => edges.push(e));
    return { nodes, edges };
  }, [baseFlow, expandedBoxes, childCache, resolvedEdges]);

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
    const matchesSearch = q && !String(n.name).toLowerCase().includes(q);
    const matchesStatus = statusFilter !== "ALL" && deriveAutosysState(n) !== statusFilter;
    if (matchesSearch || matchesStatus) return true;
    if (neighbors && internalSelected && id !== String(internalSelected) && !neighbors.preds.has(id) && !neighbors.succs.has(id)) return true;
    return false;
  }, [q, statusFilter, neighbors, internalSelected]);
  const emphasizeFn = useCallback((n) => {
    const id = String(n.id);
    return Boolean(neighbors && (neighbors.preds.has(id) || neighbors.succs.has(id)));
  }, [neighbors]);

  const onSelect = useCallback((node) => { setInternalSelected(String(node.id)); onChanged?.(node); }, [onChanged]);
  const onContext = useCallback((node, e) => { setInternalSelected(String(node.id)); onContextMenu?.(node, e); }, [onContextMenu]);

  const { rfNodes, rfEdgesRaw } = useMemo(() => {
    if (!baseFlow) return { rfNodes: [], rfEdgesRaw: [] };
    const layout = layoutCompound(baseFlow.nodes || [], baseFlow.edges || [], expandedBoxes, childCache);
    const out = { nodes: [], edges: [] };
    const ctx = { highlightId: internalSelected, dimFn, emphasizeFn };
    const callbacks = { onToggle: toggleBox, onSelect, onContext };
    flatten(baseFlow.nodes || [], baseFlow.edges || [], layout, null, expandedBoxes, childCache, callbacks, ctx, out);

    const outIds = new Set(out.nodes.map((n) => n.id));
    const seen = new Set(out.edges.map((e) => `${e.from}:${e.to}`));
    resolvedEdges.forEach((e) => {
      const key = `${e.from}:${e.to}`;
      if (outIds.has(String(e.from)) && outIds.has(String(e.to)) && !seen.has(key)) { out.edges.push(e); seen.add(key); }
    });
    return { rfNodes: out.nodes, rfEdgesRaw: out.edges };
  }, [baseFlow, expandedBoxes, childCache, internalSelected, dimFn, emphasizeFn, toggleBox, onSelect, onContext, resolvedEdges]);

  const rfEdges = useMemo(() => rfEdgesRaw.map((e, i) => {
    const isNeighborEdge = neighbors && (String(e.to) === String(internalSelected) || String(e.from) === String(internalSelected));
    const targetNode = allVisible.nodes.find((n) => String(n.id) === String(e.to));
    return {
      id: `${e.from}-${e.to}-${i}`,
      source: String(e.from),
      target: String(e.to),
      label: e.synthetic ? "" : (e.conditionType || "s()"),
      type: "smoothstep",
      animated: targetNode ? deriveAutosysState(targetNode) === "RUNNING" : false,
      markerEnd: { type: MarkerType.ArrowClosed, width: 16, height: 16, color: isNeighborEdge ? "#18a56b" : e.synthetic ? "#c3cbd1" : e.crossBox ? "#7758b8" : "#5e6c78" },
      style: {
        strokeWidth: isNeighborEdge ? 2.4 : 1.4,
        stroke: isNeighborEdge ? "#18a56b" : e.synthetic ? "#c3cbd1" : e.crossBox ? "#7758b8" : "#5e6c78",
        strokeDasharray: e.synthetic ? "3 3" : e.crossBox ? "5 3" : undefined,
      },
      labelStyle: { fontSize: 7, fontWeight: 600, fill: "#5e6c78" },
      labelBgStyle: { fill: "#ffffff", fillOpacity: 0.85 },
      labelBgPadding: [1, 1],
      zIndex: isNeighborEdge ? 20 : 1,
    };
  }), [rfEdgesRaw, neighbors, internalSelected, allVisible]);

  const onPaneClick = useCallback(() => setInternalSelected(null), []);
  const onZoomIn = () => rfInstance.current?.zoomIn?.();
  const onZoomOut = () => rfInstance.current?.zoomOut?.();
  const onFit = () => rfInstance.current?.fitView?.({ padding: 0.2 });
  const onPrint = () => { wrapRef.current?.classList.add("asx-print-mode"); window.print(); setTimeout(() => wrapRef.current?.classList.remove("asx-print-mode"), 300); };
  const miniMapNodeColor = useCallback((node) => miniMapColor(node.data?.status), []);

  if (loading) return <div className="empty-state">Loading workflow flow…</div>;
  if (error) return <div className="banner banner-error">Flow API error: {error}</div>;
  if (!rfNodes.length) return <div className="empty-state">No jobs in this flow.</div>;

  return (
    <div className="asx-flow-wrap" ref={wrapRef}>
      <Toolbar
        search={search} setSearch={setSearch}
        statusFilter={statusFilter} setStatusFilter={setStatusFilter}
        onZoomIn={onZoomIn} onZoomOut={onZoomOut} onFit={onFit}
        overviewOn={overviewOn} setOverviewOn={setOverviewOn}
        onPrint={onPrint}
        liveOn={Boolean(refreshToken)}
        clock={clock}
        nodeCount={rfNodes.length}
        edgeCount={rfEdges.length}
      />
      <Legend />
      <div className="asx-canvas">
        <ReactFlow
          nodes={rfNodes}
          edges={rfEdges}
          nodeTypes={nodeTypes}
          onInit={(inst) => { rfInstance.current = inst; }}
          onPaneClick={onPaneClick}
          fitView
          fitViewOptions={{ padding: 0.25, maxZoom: 1.3 }}
          minZoom={0.08}
          maxZoom={3}
          panOnDrag
          nodesDraggable={false}
          nodesConnectable={false}
          zoomOnScroll
          zoomOnPinch
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

export default function DagCanvas(props) {
  return (
    <ReactFlowProvider>
      <DagCanvasInner {...props} />
    </ReactFlowProvider>
  );
}

export function DagCanvasWithProvider(props) {
  return <DagCanvas {...props} />;
}