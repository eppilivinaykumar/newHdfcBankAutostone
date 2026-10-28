import { useCallback, useEffect, useMemo, useState } from "react";
import {
  Background,
  Controls,
  Handle,
  MiniMap,
  MarkerType,
  Position,
  ReactFlow,
  ReactFlowProvider,
  useEdgesState,
  useNodesState,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { jobApi } from "../api/api.js";
import { stateClass, labelOf, iconOf, miniMapColor } from "../monitoring/statusPresentation.js";

function MonitorNode({ data, selected }) {
  const status = stateClass(data.status);
  const isBox = data.jobType === "BOX";

  return (
    <div className={`autostone-flow-node ${isBox ? "is-box" : ""} ${status} ${selected ? "is-selected" : ""}`}>
      <Handle type="target" position={Position.Left} className="autostone-flow-handle" />
      <div className="autostone-flow-node-inner">
        <span className="autostone-flow-icon">{iconOf(data.status)}</span>
        <span className="autostone-flow-name" title={data.name}>{data.name}</span>
        <span className="autostone-flow-meta">{isBox ? "BOX" : data.jobType || "JOB"}</span>
        <span className="autostone-flow-status">{labelOf(data.status)}</span>
      </div>
      <Handle type="source" position={Position.Right} className="autostone-flow-handle" />
    </div>
  );
}

function BoxNode({ data, selected }) {
  return (
    <div className={`autostone-flow-box-node ${stateClass(data.status)} ${selected ? "is-selected" : ""}`}>
      <Handle type="target" position={Position.Left} className="autostone-flow-handle" />
      <div className="autostone-flow-box-title">{data.name}</div>
      <div className="autostone-flow-box-subtitle">BOX · {labelOf(data.status)}</div>
      <Handle type="source" position={Position.Right} className="autostone-flow-handle" />
    </div>
  );
}

const nodeTypes = { monitor: MonitorNode, box: BoxNode };

function buildLayout(nodes) {
  const byLevel = new Map();
  nodes.forEach((node) => {
    const level = Number.isFinite(Number(node.level)) ? Number(node.level) : 0;
    if (!byLevel.has(level)) byLevel.set(level, []);
    byLevel.get(level).push(node);
  });

  const levels = [...byLevel.keys()].sort((a, b) => a - b);
  const positions = new Map();
  const X = 235;
  const Y = 125;
  const left = 80;
  const top = 80;

  levels.forEach((level, levelIndex) => {
    const rows = byLevel.get(level).slice().sort((a, b) => String(a.name).localeCompare(String(b.name)));
    rows.forEach((node, rowIndex) => {
      positions.set(String(node.id), { x: left + levelIndex * X, y: top + rowIndex * Y });
    });
  });

  nodes.forEach((node, index) => {
    const id = String(node.id);
    if (!positions.has(id)) {
      positions.set(id, {
        x: left + (index % Math.max(1, levels.length)) * X,
        y: top + Math.floor(index / Math.max(1, levels.length)) * Y,
      });
    }
  });

  return positions;
}

function Graph({ graph, selectedId, onSelect, onOpenBox, onContextMenu }) {
  const normalizedNodes = useMemo(
    () => (graph?.nodes || []).map((node) => ({
      ...node,
      id: String(node.id),
      boxId: node.boxId == null ? null : String(node.boxId),
    })),
    [graph],
  );

  const normalizedEdges = useMemo(
    () => (graph?.edges || []).map((edge, index) => ({
      ...edge,
      id: String(edge.id || `${edge.from}-${edge.to}-${index}`),
      source: String(edge.from),
      target: String(edge.to),
      label: edge.conditionType || "SUCCESS",
    })),
    [graph],
  );

  const positions = useMemo(() => buildLayout(normalizedNodes), [normalizedNodes]);

  const initialNodes = useMemo(() => normalizedNodes.map((node) => {
    const p = positions.get(node.id) || { x: 0, y: 0 };
    const isBox = node.jobType === "BOX";
    return {
      id: node.id,
      type: isBox ? "box" : "monitor",
      position: p,
      data: node,
      sourcePosition: Position.Right,
      targetPosition: Position.Left,
      draggable: true,
      selectable: true,
    };
  }), [normalizedNodes, positions]);

  const initialEdges = useMemo(() => normalizedEdges.map((edge) => ({
    id: edge.id,
    source: edge.source,
    target: edge.target,
    label: edge.label,
    type: "smoothstep",
    animated: false,
    markerEnd: { type: MarkerType.ArrowClosed, width: 14, height: 14 },
    style: { strokeWidth: 1.6 },
    labelStyle: { fontSize: 9, fontWeight: 600 },
    labelBgStyle: { fill: "#ffffff", fillOpacity: 0.9 },
    labelBgPadding: [3, 2],
  })), [normalizedEdges]);

  const [nodes, setNodes, onNodesChange] = useNodesState(initialNodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState(initialEdges);

  useEffect(() => setNodes(initialNodes), [initialNodes, setNodes]);
  useEffect(() => setEdges(initialEdges), [initialEdges, setEdges]);

  const onNodeClick = useCallback((event, node) => {
    event.stopPropagation();
    onSelect?.(node.data);
  }, [onSelect]);

  const onNodeDoubleClick = useCallback((event, node) => {
    event.stopPropagation();
    onSelect?.(node.data);
    if (node.data?.jobType === "BOX") onOpenBox?.(node.data);
  }, [onOpenBox, onSelect]);

  const onNodeContextMenu = useCallback((event, node) => {
    event.preventDefault();
    event.stopPropagation();
    onSelect?.(node.data);
    onContextMenu?.(node.data, event);
  }, [onContextMenu, onSelect]);

  const miniMapNodeColor = useCallback((node) => miniMapColor(node.data?.status), []);

  return (
    <div className="autostone-react-flow">
      <ReactFlow
        nodes={nodes.map((node) => ({ ...node, selected: node.id === String(selectedId) }))}
        edges={edges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onNodeClick={onNodeClick}
        onNodeDoubleClick={onNodeDoubleClick}
        onNodeContextMenu={onNodeContextMenu}
        nodeTypes={nodeTypes}
        fitView
        fitViewOptions={{ padding: 0.2, maxZoom: 1.15 }}
        minZoom={0.08}
        maxZoom={2.5}
        panOnDrag
        selectionOnDrag
        nodesDraggable
        nodesConnectable={false}
        zoomOnScroll
        zoomOnPinch
        proOptions={{ hideAttribution: true }}
      >
        <Background gap={20} size={1} />
        <MiniMap nodeColor={miniMapNodeColor} pannable zoomable />
        <Controls showInteractive={false} />
      </ReactFlow>
    </div>
  );
}

export default function DagCanvas({ boxId, jobs, refreshToken, onChanged, onOpenBox, onContextMenu }) {
  const [flow, setFlow] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [selectedId, setSelectedId] = useState(boxId == null ? null : String(boxId));

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    jobApi.flow(boxId ?? null, { mode: "BOX", depth: 1 })
      .then((data) => {
        if (!cancelled) {
          setFlow(data);
          setSelectedId(String(boxId));
        }
      })
      .catch((err) => {
        if (!cancelled) setError(err.message || "Unable to load workflow flow");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => { cancelled = true; };
  }, [boxId, refreshToken]);

  const fallback = useMemo(() => {
    if (flow || !jobs?.length) return null;
    // Root view = every job with no parent BOX, not only BOX-type jobs - a standalone
    // top-level JOB must still show up here (mirrors the backend's getRootFlow fix).
    const visible = boxId == null ? jobs.filter((job) => job.boxId == null) : jobs.filter((job) => String(job.boxId) === String(boxId));
    return { nodes: visible.map((job) => ({ ...job, id: job.id, level: 0 })), edges: [] };
  }, [flow, jobs, boxId]);

  const graph = flow || fallback;
  const normalizedCount = graph?.nodes?.length || 0;
  const edgeCount = graph?.edges?.length || 0;

  if (loading) return <div className="empty-state">Loading workflow flow…</div>;
  if (error) return <div className="banner banner-error">Flow API error: {error}</div>;
  if (!graph?.nodes?.length) return <div className="empty-state">No jobs in this flow.</div>;

  return (
    <div className="dag-canvas-wrap">
      <div className="d-flex align-items-center justify-content-between gap-2 px-2 py-2 border-bottom bg-light small">
        <span><strong>Live dependency flow</strong> · {normalizedCount} objects · {edgeCount} dependencies</span>
        <span className="text-secondary">Drag nodes · scroll to zoom · right-click for actions</span>
      </div>
      <div className="flow-status-legend px-2 py-2">
        {["RUNNING", "SUCCESS", "FAILED", "KILLED", "PAUSED", "WAITING", "RETRY_WAIT"].map((status) => (
          <span key={status}><i className={`legend-flow-dot ${stateClass(status)}`} />{labelOf(status)}</span>
        ))}
      </div>
      <Graph
        graph={graph}
        selectedId={selectedId}
        onSelect={(node) => {
          setSelectedId(String(node.id));
          onChanged?.(node);
        }}
        onOpenBox={onOpenBox}
        onContextMenu={(node, event) => onContextMenu?.(node, event)}
      />
    </div>
  );
}

export function DagCanvasWithProvider(props) {
  return <ReactFlowProvider><DagCanvas {...props} /></ReactFlowProvider>;
}
