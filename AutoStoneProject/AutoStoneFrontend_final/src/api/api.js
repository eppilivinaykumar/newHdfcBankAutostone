const BASE_URL = (import.meta.env.VITE_API_BASE_URL || "").replace(/\/$/, "");

function bodyMessage(status, fallback) {
  if (status === 401) return "Authentication is required for this AutoStone operation.";
  if (status === 403) return "You are not authorized to perform this AutoStone operation.";
  return fallback;
}

async function request(path, options = {}) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), options.timeoutMs || 15000);
  try {
    const fetchOptions = { ...options };
    delete fetchOptions.timeoutMs;
    let res;
    try {
      res = await fetch(`${BASE_URL}${path}`, {
        ...fetchOptions,
        signal: controller.signal,
        headers: { Accept: "application/json", ...(fetchOptions.body ? { "Content-Type": "application/json" } : {}), ...(fetchOptions.headers || {}) },
      });
    } catch (networkError) {
      if (networkError?.name === "AbortError") throw networkError;
      throw new Error("AutoStone backend is unreachable. Check the backend port, proxy and network connection.", { cause: networkError });
    }
    if (!res.ok) {
      let message = `${res.status} ${res.statusText}`;
      try {
        const body = await res.json();
        if (body?.message) message = body.message;
        else if (body?.error) message = body.error;
      } catch { /* response may not be JSON */ }
      if (res.status === 401 || res.status === 403) {
        message = bodyMessage(res.status, message);
      }
      throw new Error(message);
    }
    if (res.status === 204) return null;
    const text = await res.text();
    return text ? JSON.parse(text) : null;
  } catch (err) {
    if (err.name === "AbortError") throw new Error("Request timed out. Check that AutoStone backend is running.", { cause: err });
    throw err;
  } finally {
    clearTimeout(timeout);
  }
}

const json = (method, body) => ({ method, body: JSON.stringify(body) });

export const jobApi = {
  getAll: () => request("/api/jobs"),
  get: (id) => request(`/api/jobs/${id}`),
  create: (payload) => request("/api/jobs", json("POST", payload)),
  update: (id, payload) => request(`/api/jobs/${id}`, json("PUT", payload)),
  remove: (id) => request(`/api/jobs/${id}`, { method: "DELETE" }),
  relations: (id) => request(`/api/jobs/${id}/relations`),
  flow: (id, options = {}) => id == null
    ? request(`/api/jobs/flow?mode=${encodeURIComponent(options.mode || "BOX")}&depth=${encodeURIComponent(options.depth || 1)}`)
    : request(`/api/jobs/${id}/flow?mode=${encodeURIComponent(options.mode || "BOX")}&depth=${encodeURIComponent(options.depth || 1)}`),
};

export const healthApi = { get: () => request("/api/health") };

export const dashboardApi = {
  getStatus: () => request("/api/dashboard"),
  getSummary: () => request("/api/dashboard/summary"),
};

export const executionApi = {
  byJob: (jobId) => request(`/api/jobs/${jobId}/executions`),
  get: (id) => request(`/api/executions/${id}`),
  logs: (id, maxChars = 200000) => request(`/api/executions/${id}/logs?maxChars=${maxChars}`),
};

export const eventApi = {
  // All execution controls use the same /event API used by the Monitoring context menu.
  start: (jobId, payload) => request(`/event/start/${jobId}`, { ...json("POST", payload || {}) }),
  stop: (jobId, payload) => request(`/event/stop/${jobId}`, { ...json("POST", payload || {}) }),
  pause: (jobId, payload) => request(`/event/pause/${jobId}`, { ...json("POST", payload || {}) }),
  resume: (jobId, payload) => request(`/event/resume/${jobId}`, { ...json("POST", payload || {}) }),
  restart: (jobId, payload) => request(`/event/restart/${jobId}`, { ...json("POST", payload || {}) }),
  forceStart: (jobId) => request(`/event/force-start/${jobId}`, { method: "POST" }),
  hold: (jobId) => request(`/event/hold/${jobId}`, { method: "POST" }),
  offHold: (jobId) => request(`/event/off-hold/${jobId}`, { method: "POST" }),
  ice: (jobId) => request(`/event/ice/${jobId}`, { method: "POST" }),
  offIce: (jobId) => request(`/event/off-ice/${jobId}`, { method: "POST" }),
  noExecution: (jobId) => request(`/event/no-execution/${jobId}`, { method: "POST" }),
  offNoExecution: (jobId) => request(`/event/off-no-execution/${jobId}`, { method: "POST" }),
  releaseResources: (jobId) => request(`/event/release-resources/${jobId}`, { method: "POST" }),
  kill: (jobId) => request(`/event/kill/${jobId}`, { method: "POST" }),
  sendEvent: (jobId, event) => request(`/event/send-event/${jobId}`, json("POST", { event })),
  cancelEvent: (jobId) => request(`/event/cancel-event/${jobId}`, { method: "POST" }),
  skip: (jobId, payload) => request(`/event/skip/${jobId}`, { ...json("POST", payload || {}) }),
  markSuccess: (jobId, payload) => request(`/event/mark-success/${jobId}`, { ...json("POST", payload || {}) }),
  markFailure: (jobId, payload) => request(`/event/mark-failure/${jobId}`, { ...json("POST", payload || {}) }),
  bulk: (jobIds, action, triggeredBy = "MONITORING") => request("/event/bulk", { ...json("POST", { jobIds, action, triggeredBy }) }),
  enableSchedule: (jobId) => request(`/event/schedule/enable/${jobId}`, { method: "POST" }),
  disableSchedule: (jobId) => request(`/event/schedule/disable/${jobId}`, { method: "POST" }),
};

export const workerApi = { getAll: () => request("/api/workers") };

export const monitoringApi = {
  getAlerts: () => request("/api/monitoring/alerts"),
  jobs: ({ search = "", status = "ALL", boxId = null, rootsOnly = false, page = 0, size = 250 } = {}) => {
    const params = new URLSearchParams({ search, status, rootsOnly: String(rootsOnly), page: String(page), size: String(size) });
    if (boxId != null) params.set("boxId", String(boxId));
    return request(`/api/monitoring/jobs?${params.toString()}`);
  },
  timeline: (jobId = null, limit = 500) => request(`/api/monitoring/timeline?limit=${limit}${jobId == null ? "" : `&jobId=${jobId}`}`),
  streamUrl: () => `${BASE_URL}/api/monitoring/stream`,
};

export const alertPolicyApi = {
  getDefault: () => request("/api/alert-policies/default"),
  update: (payload) => request("/api/alert-policies/default", json("PUT", payload)),
};

export const jilApi = {
  validate: (jilText) => request("/api/jil/validate", json("POST", { jilText })),
  importJil: (jilText) => request("/api/jil/import", json("POST", { jilText })),
};
