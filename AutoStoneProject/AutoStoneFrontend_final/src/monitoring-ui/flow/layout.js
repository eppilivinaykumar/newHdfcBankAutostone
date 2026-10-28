/* ================================================================
   Compound top-down layout for the Flow canvas.
   Nodes are grouped by `level` (server-computed longest-path depth)
   and placed left to right within each level using each node's real
   footprint width - a collapsed job/box is a small fixed square/
   circle, an *expanded* box's footprint is however big its own
   recursively-laid-out children turn out to be, so an expanded box
   pushes its siblings aside exactly like the real product does.
   Pure layout math - no React, no fetching, easy to reason about
   and unit-test on its own.
   ================================================================ */

export const NODE_D = 42;
export const BOX_ICON = 42;
export const BOX_HEADER = 18; // clearance reserved above a box for its floating label pill
export const BOX_PAD = 16;
export const GAP_X = 16;
export const GAP_Y = 58;
const MIN_BOX_W = BOX_ICON + BOX_PAD * 2;

/**
 * @param nodes   flow nodes at this level (each has id, level, jobType)
 * @param edges   flow edges at this level ({from,to,...})
 * @param expandedBoxes  Set<string> of box ids currently expanded
 * @param childCache     Map<string, {status, data:{nodes,edges}}> per expanded box
 * @returns {width, height, positions: Map<id,{x,y,w,h}>, footprints: Map<id,footprint>}
 */
export function layoutCompound(nodes, edges, expandedBoxes, childCache) {
  const footprints = new Map();

  function footprintOf(n) {
    const id = String(n.id);
    if (footprints.has(id)) return footprints.get(id);
    let fp;
    if (n.jobType === "BOX" && expandedBoxes.has(id)) {
      const child = childCache.get(id);
      if (child && child.status === "ready" && child.data.nodes?.length) {
        const sub = layoutCompound(child.data.nodes, child.data.edges, expandedBoxes, childCache);
        fp = { w: Math.max(sub.width + BOX_PAD * 2, MIN_BOX_W), h: sub.height + BOX_PAD * 2, isBox: true, expanded: true, sub };
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
  top = Math.max(top - GAP_Y, NODE_D);

  levels.forEach((l) => {
    const row = byLevel.get(l);
    const rowW = row.reduce((s, n) => s + footprintOf(n).w + GAP_X, -GAP_X);
    const offset = Math.max(0, (maxRowW - rowW) / 2);
    row.forEach((n) => { const p = positions.get(String(n.id)); p.x += offset; });
  });

  return { width: maxRowW, height: top, positions, footprints };
}
