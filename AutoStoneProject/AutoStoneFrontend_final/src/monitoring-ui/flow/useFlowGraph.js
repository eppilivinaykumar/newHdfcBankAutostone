import { useCallback, useEffect, useMemo, useState } from "react";
import { jobApi } from "../../api/api.js";

const MAX_ANCESTOR_HOPS = 8;

/* ================================================================
   All data-fetching and cross-box edge resolution for the Flow tab,
   isolated from rendering. Talks only to endpoints that already
   exist on the backend:
     - /jobs/{id}/flow    (a box's direct children + same-box edges)
     - /jobs/{id}/relations  (a job/box's TRUE parents/children -
       used because /flow silently drops any edge whose two ends
       aren't both direct children of the same box, which real JIL
       hits constantly: a box's condition very often names a job
       nested inside a *different* box)
     - /jobs/{id}          (used only to read a job's own boxId, to
       walk an ancestor chain when resolving cross-box edges or
       revealing a node the sidebar selected)
   ================================================================ */
export function useFlowGraph(rootBoxId, refreshToken, highlightId) {
  const [baseFlow, setBaseFlow] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [expandedBoxes, setExpandedBoxes] = useState(new Set());
  const [childCache, setChildCache] = useState(new Map());   // boxId -> {status, data}
  const [relCache, setRelCache] = useState(new Map());       // jobId -> {parents, children}
  const [boxOfCache, setBoxOfCache] = useState(new Map());   // jobId -> boxId|null
  const [resolvedEdges, setResolvedEdges] = useState([]);

  useEffect(() => {
    setExpandedBoxes(new Set()); setChildCache(new Map());
    setRelCache(new Map()); setBoxOfCache(new Map()); setResolvedEdges([]);
  }, [rootBoxId]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true); setError(null);
    jobApi.flow(rootBoxId ?? null, { mode: "BOX", depth: 1 })
      .then((data) => { if (!cancelled) setBaseFlow(data); })
      .catch((err) => { if (!cancelled) setError(err.message || "Unable to load workflow flow"); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [rootBoxId, refreshToken]);

  /* Auto-refresh only ever re-fetched the outer/root flow - a job status
     change buried inside an already-expanded box never showed up until you
     collapsed and re-expanded it. Re-fetch every currently-expanded box's
     contents too, every time refreshToken ticks. */
  useEffect(() => {
    if (!expandedBoxes.size) return;
    let cancelled = false;
    Promise.all([...expandedBoxes].map((id) =>
      jobApi.flow(id, { mode: "BOX", depth: 1 }).then((data) => ({ id, data })).catch(() => null)
    )).then((results) => {
      if (cancelled) return;
      setChildCache((prev) => {
        const n = new Map(prev);
        results.forEach((r) => { if (r) n.set(r.id, { status: "ready", data: r.data }); });
        return n;
      });
    });
    return () => { cancelled = true; };
  }, [refreshToken]);

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

  const expandPath = useCallback((boxIds) => {
    setExpandedBoxes((prev) => { const n = new Set(prev); boxIds.forEach((id) => n.add(id)); return n; });
    boxIds.forEach((id) => { if (!childCache.has(id)) loadBoxChildren({ id }); });
  }, [childCache, loadBoxChildren]);

  /* auto-reveal: if the sidebar highlighted a node hidden inside a
     collapsed box, walk its boxId ancestry up to a visible root node
     and expand every box on that path - never re-root the view. */
  useEffect(() => {
    if (highlightId == null || !baseFlow) return;
    let cancelled = false;
    (async () => {
      const rootIds = new Set((baseFlow.nodes || []).map((n) => String(n.id)));
      let cur = String(highlightId);
      if (rootIds.has(cur)) return;
      const path = [];
      let depth = 0;
      let local = boxOfCache;
      while (!rootIds.has(cur) && depth < MAX_ANCESTOR_HOPS) {
        let bId = local.get(cur);
        if (bId === undefined) {
          try { const j = await jobApi.get(cur); bId = j.boxId == null ? null : String(j.boxId); }
          catch { bId = null; }
          if (cancelled) return;
          local = new Map(local); local.set(cur, bId);
          setBoxOfCache((prev) => { const n = new Map(prev); n.set(cur, bId); return n; });
        }
        if (bId == null) break;
        path.unshift(bId); cur = bId; depth++;
      }
      if (!cancelled && path.length) expandPath(path);
    })();
    return () => { cancelled = true; };
  }, [highlightId, baseFlow]);

  /* cross-box edges: for every currently-visible "sibling group" (the
     root set, and each expanded box's own children set), fetch
     relations() for every member and resolve each neighbour up its
     boxId chain until it lands on a sibling of that same group. */
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
        return;
      }
      const boxFetchNeeded = new Set();
      const resolveToSibling = (neighbor, groupIds) => {
        const curId = String(neighbor.id);
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
        return;
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

  const expandAll = useCallback(async () => {
    if (!baseFlow) return;
    const nextExpanded = new Set(expandedBoxes);
    const nextChildCache = new Map(childCache);
    async function recurse(boxNode) {
      const key = String(boxNode.id);
      let entry = nextChildCache.get(key);
      if (!entry || entry.status !== "ready") {
        try { const data = await jobApi.flow(boxNode.id, { mode: "BOX", depth: 1 }); entry = { status: "ready", data }; nextChildCache.set(key, entry); }
        catch (err) { nextChildCache.set(key, { status: "error", error: err.message }); return; }
      }
      nextExpanded.add(key);
      await Promise.all((entry.data.nodes || []).filter((n) => n.jobType === "BOX").map(recurse));
    }
    const rootBoxes = (baseFlow.nodes || []).filter((n) => n.jobType === "BOX");
    await Promise.all(rootBoxes.map(recurse));
    setExpandedBoxes(nextExpanded);
    setChildCache(nextChildCache);
  }, [baseFlow, expandedBoxes, childCache]);

  const collapseAll = useCallback(() => setExpandedBoxes(new Set()), []);

  const refreshNow = useCallback(async () => {
    setLoading(true);
    try {
      const data = await jobApi.flow(rootBoxId ?? null, { mode: "BOX", depth: 1 });
      setBaseFlow(data);
    } catch (err) { setError(err.message || "Unable to load workflow flow"); }
    finally { setLoading(false); }
    if (expandedBoxes.size) {
      const results = await Promise.all([...expandedBoxes].map((id) =>
        jobApi.flow(id, { mode: "BOX", depth: 1 }).then((data) => ({ id, data })).catch(() => null)
      ));
      setChildCache((prev) => {
        const n = new Map(prev);
        results.forEach((r) => { if (r) n.set(r.id, { status: "ready", data: r.data }); });
        return n;
      });
    }
  }, [rootBoxId, expandedBoxes]);

  return { baseFlow, loading, error, expandedBoxes, childCache, resolvedEdges, allVisible, toggleBox, expandAll, collapseAll, refreshNow };
}
