import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { jobApi } from "../api/api.js";

const emptyForm = {
  name: "", description: "", jobType: "JOB", triggerType: "MANUAL", cronExpression: "", conditionExpression: "",
  command: "", hostName: "", boxId: "", dependsOnJobIds: [], enabled: true, executionType: "SSH_SCRIPT",
  retryCount: 0, retryIntervalSeconds: 60, timeoutSeconds: 0, workingDirectory: "", arguments: "", concurrencyPolicy: "ALLOW", timezone: "UTC", scheduleStartTime: "", scheduleEndTime: "", misfirePolicy: "FIRE_NOW",
};

export default function JobFormPage() {
  const navigate = useNavigate();
  const { id } = useParams();
  const editing = Boolean(id);
  const [form, setForm] = useState(emptyForm);
  const [existingJobs, setExistingJobs] = useState([]);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    jobApi.getAll().then(setExistingJobs).catch(e => setError(`Could not load existing jobs: ${e.message}`));
    if (id) jobApi.get(id).then(j => setForm({
      ...emptyForm, name:j.name || "", description:j.description || "", jobType:j.jobType || "JOB", triggerType:j.triggerType || "MANUAL",
      cronExpression:j.cronExpression || "", timezone:j.timezone || "UTC", scheduleStartTime:j.scheduleStartTime || "", scheduleEndTime:j.scheduleEndTime || "",
      misfirePolicy:j.misfirePolicy || "FIRE_NOW", conditionExpression:j.conditionExpression || "", command:j.command || "", hostName:j.hostName || "",
      boxId:j.boxId || "", dependsOnJobIds:j.dependsOnJobIds || [], enabled:j.enabled !== false, executionType:j.executionType || "SSH_SCRIPT",
      retryCount:j.retryCount || 0, retryIntervalSeconds:j.retryIntervalSeconds || 60, timeoutSeconds:j.timeoutSeconds || 0,
      workingDirectory:j.workingDirectory || "", arguments:j.arguments || "", concurrencyPolicy:j.concurrencyPolicy || "ALLOW"
    })).catch(e => setError(`Could not load job: ${e.message}`));
  }, [id]);
  const boxes = existingJobs.filter(j => j.jobType === "BOX");
  const candidateParents = existingJobs.filter(j => j.name !== form.name.trim());
  const update = (field, value) => setForm(f => ({ ...f, [field]: value }));

  function toggleDependency(jobId) {
    setForm(f => ({ ...f, dependsOnJobIds: f.dependsOnJobIds.includes(jobId) ? f.dependsOnJobIds.filter(id => id !== jobId) : [...f.dependsOnJobIds, jobId] }));
  }

  function validateForm() {
    const name = form.name.trim();
    if (!name) return "Job name is required.";
    if (!/^[A-Za-z0-9_.-]+$/.test(name)) return "Job name may contain only letters, numbers, dot, underscore and hyphen.";
    if (form.jobType === "JOB" && !form.command.trim()) return "Command / Script is required for a JOB.";
    if (form.jobType === "JOB" && form.executionType === "SSH_SCRIPT" && !form.hostName.trim()) return "Execution Server is required for SSH_SCRIPT jobs.";
    if (form.triggerType === "CRON" && !form.cronExpression.trim()) return "Quartz Cron expression is required for CRON jobs.";
    if (form.triggerType === "DEPENDENCY" && !form.dependsOnJobIds.length && !form.conditionExpression.trim()) return "Select at least one dependency or enter a condition expression.";
    if (Number(form.retryCount) < 0 || Number(form.timeoutSeconds) < 0) return "Retry count and timeout cannot be negative.";
    if (Number(form.retryIntervalSeconds) < 1) return "Retry interval must be at least 1 second.";
    if (form.scheduleStartTime && form.scheduleEndTime && form.scheduleEndTime < form.scheduleStartTime) return "Schedule end must be after schedule start.";
    return "";
  }

  async function submit(e) {
    e.preventDefault(); setError(""); setSuccess(false);
    const validationError = validateForm();
    if (validationError) { setError(validationError); return; }
    setSubmitting(true);
    const payload = {
      ...form,
      name: form.name.trim(), description: form.description.trim() || null,
      cronExpression: form.triggerType === "CRON" ? form.cronExpression.trim() : null,
      conditionExpression: form.conditionExpression.trim() || null,
      command: form.jobType === "BOX" ? null : form.command.trim(),
      hostName: form.jobType === "BOX" ? null : form.hostName.trim() || null,
      boxId: form.boxId ? Number(form.boxId) : null,
      dependsOnJobIds: form.triggerType === "DEPENDENCY" ? form.dependsOnJobIds : [],
      retryCount: Number(form.retryCount) || 0, retryIntervalSeconds: Number(form.retryIntervalSeconds) || 60,
      timeoutSeconds: Number(form.timeoutSeconds) || 0,
    };
    try { if (editing) await jobApi.update(id, payload); else await jobApi.create(payload); setSuccess(true); setTimeout(() => navigate("/monitoring"), 600); }
    catch (err) { setError(err.message); }
    finally { setSubmitting(false); }
  }

  return <div className="legacy-page">
    <div className="page-titlebar"><div><div className="crumb">Workload / New Job</div><h1>{editing ? "Edit Job" : "Create Job"}</h1></div><button className="legacy-btn" onClick={() => navigate(-1)}>Cancel</button></div>
    {error && <div className="legacy-error">{error}</div>}{success && <div className="legacy-success">Job created successfully.</div>}
    <form className="form-card enterprise-form" onSubmit={submit}>
      <section><h2>General</h2><div className="field-row"><Field label="Job Name"><input value={form.name} onChange={e=>update("name",e.target.value)} required placeholder="JOB_F" /></Field><Field label="Job Type"><select value={form.jobType} onChange={e=>setForm(f=>({...f,jobType:e.target.value,boxId:f.boxId,hostName:e.target.value==="BOX"?"":f.hostName,command:e.target.value==="BOX"?"":f.command}))}><option>JOB</option><option>BOX</option></select></Field></div>
      <Field label="Description"><input value={form.description} onChange={e=>update("description",e.target.value)} placeholder="Business purpose" /></Field>
      </section>
      <section><h2>Scheduling & Dependencies</h2><div className="field-row"><Field label="Trigger Type"><select value={form.triggerType} onChange={e=>update("triggerType",e.target.value)}><option>MANUAL</option><option>CRON</option><option>DEPENDENCY</option><option>EVENT</option></select></Field><Field label="Parent BOX / Workflow"><select value={form.boxId} onChange={e=>update("boxId",e.target.value)}><option value="">None</option>{boxes.map(b=><option key={b.id} value={b.id}>{b.name}</option>)}</select></Field></div>
      {form.triggerType === "CRON" && <><div className="field-row"><Field label="Quartz Cron"><input value={form.cronExpression} onChange={e=>update("cronExpression",e.target.value)} placeholder="0 10 * * * ?" /></Field><Field label="Time Zone"><input value={form.timezone} onChange={e=>update("timezone",e.target.value)} placeholder="Asia/Kolkata" /></Field></div><div className="field-row"><Field label="Schedule Start (ISO)"><input value={form.scheduleStartTime} onChange={e=>update("scheduleStartTime",e.target.value)} placeholder="2026-08-24T10:00:00" /></Field><Field label="Schedule End (ISO)"><input value={form.scheduleEndTime} onChange={e=>update("scheduleEndTime",e.target.value)} placeholder="2026-12-31T23:59:59" /></Field></div><Field label="Misfire Policy"><select value={form.misfirePolicy} onChange={e=>update("misfirePolicy",e.target.value)}><option>FIRE_NOW</option><option>DO_NOTHING</option></select><Hint>Quartz schedule is persisted in AutoStone and reconstructed on restart.</Hint></Field></>}
      {form.triggerType === "DEPENDENCY" && <Field label="Depends On"><div className="check-grid">{candidateParents.map(j=><label key={j.id}><input type="checkbox" checked={form.dependsOnJobIds.includes(j.id)} onChange={()=>toggleDependency(j.id)}/>{j.name}</label>)}{!candidateParents.length && <Hint>Select a box or create parent jobs first.</Hint>}</div></Field>}
      <Field label="Condition Expression"><input value={form.conditionExpression} onChange={e=>update("conditionExpression",e.target.value)} placeholder="s(JOB_A) AND s(JOB_B)" /></Field>
      </section>
      {form.jobType === "JOB" && <section><h2>Execution</h2><div className="field-row"><Field label="Execution Type"><select value={form.executionType} onChange={e=>update("executionType",e.target.value)}><option>SSH_SCRIPT</option><option>LOCAL_SCRIPT</option></select></Field><Field label="Execution Server"><input value={form.hostName} onChange={e=>update("hostName",e.target.value)} placeholder="linux-prod-01" /></Field></div>
        <Field label="Command / Script"><textarea value={form.command} onChange={e=>update("command",e.target.value)} required placeholder="/opt/scripts/job.sh" /></Field>
        <div className="field-row"><Field label="Working Directory"><input value={form.workingDirectory} onChange={e=>update("workingDirectory",e.target.value)} placeholder="/opt/scripts" /></Field><Field label="Arguments"><input value={form.arguments} onChange={e=>update("arguments",e.target.value)} placeholder="--date ${RUN_DATE}" /></Field></div>
      </section>}
      <section><h2>Reliability & Concurrency</h2><div className="field-row three"><Field label="Retry Count"><input type="number" min="0" value={form.retryCount} onChange={e=>update("retryCount",e.target.value)} /></Field><Field label="Retry Interval (sec)"><input type="number" min="1" value={form.retryIntervalSeconds} onChange={e=>update("retryIntervalSeconds",e.target.value)} /></Field><Field label="Timeout (sec)"><input type="number" min="0" value={form.timeoutSeconds} onChange={e=>update("timeoutSeconds",e.target.value)} /></Field></div><Field label="Concurrency Policy"><select value={form.concurrencyPolicy} onChange={e=>update("concurrencyPolicy",e.target.value)}><option>ALLOW</option><option>DISALLOW</option><option>QUEUE</option></select></Field></section>
      <div className="form-actions"><button className="legacy-btn primary" type="submit" disabled={submitting}>{submitting ? (editing ? "Saving..." : "Creating...") : (editing ? "Save Changes" : "Create Job")}</button><button className="legacy-btn" type="button" onClick={()=>setForm(emptyForm)} disabled={submitting}>Reset</button></div>
    </form>
  </div>;
}
function Field({label,children}){return <div className="field"><label>{label}</label>{children}</div>}
function Hint({children}){return <div className="field-hint">{children}</div>}
