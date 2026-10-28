import { useCallback, useEffect, useState } from "react";
import { dashboardApi } from "../api/api.js";
import DagCanvas from "../components/DagCanvas.jsx";
import JobNode from "../components/JobNode.jsx";

export default function WorkflowsPage() {
  const [jobs, setJobs] = useState([]);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    setError(null);
    try {
      setJobs(await dashboardApi.getStatus());
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const boxes = jobs.filter((j) => j.jobType === "BOX");
  const standaloneJobs = jobs.filter((j) => j.jobType === "JOB" && !j.boxId);

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Workflows</h1>
          <div className="sub">Dependency chains and boxes — status reflects the last refresh</div>
        </div>
        <button className="btn" onClick={refresh} disabled={loading}>
          {loading ? "Refreshing…" : "Refresh"}
        </button>
      </div>

      {error && <div className="banner banner-error">{error}</div>}

      {!loading && jobs.length === 0 && !error && (
        <div className="empty-state">No jobs yet — create one to get started</div>
      )}

      {boxes.map((box) => {
        const childJobs = jobs.filter((j) => String(j.boxId) === String(box.id));
        return (
          <div className="box-group" key={box.id}>
            <div className="box-group-header">
              <span className="box-name">{box.name}</span>
              <JobNode job={box} onChanged={refresh} />
            </div>
            <DagCanvas jobId={box.id} jobs={childJobs} onChanged={refresh} />
          </div>
        );
      })}

      {standaloneJobs.length > 0 && (
        <>
          <div className="page-header" style={{ marginBottom: 12 }}>
            <div className="sub" style={{ fontSize: 12 }}>Standalone jobs (not part of a box)</div>
          </div>
          <div className="standalone-row">
            {standaloneJobs.map((job) => (
              <JobNode key={job.id} job={job} onChanged={refresh} />
            ))}
          </div>
        </>
      )}
    </div>
  );
}
