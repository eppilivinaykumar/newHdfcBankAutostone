import { useEffect, useState } from "react";
import { alertPolicyApi } from "../api/api.js";

export default function AlertPoliciesPage(){
  const [policy,setPolicy]=useState(null); const [error,setError]=useState(""); const [saved,setSaved]=useState(false); const [busy,setBusy]=useState(false);
  useEffect(()=>{alertPolicyApi.getDefault().then(setPolicy).catch(e=>setError(e.message));},[]);
  function update(k,v){setPolicy(p=>({...p,[k]:v}));setSaved(false);}
  async function save(){setBusy(true);setError("");try{setPolicy(await alertPolicyApi.update(policy));setSaved(true)}catch(e){setError(e.message)}finally{setBusy(false)}}
  if(!policy)return <div className="legacy-page"><div className="legacy-error">{error||"Loading alert policy..."}</div></div>;
  return <div className="legacy-page"><div className="page-titlebar"><div><div className="crumb">Monitoring / Alert Policies</div><h1>Alert Policies</h1></div><button className="legacy-btn primary" onClick={save} disabled={busy}>{busy?"Saving...":"Save Policy"}</button></div>{error&&<div className="legacy-error">{error}</div>}{saved&&<div className="legacy-success">Policy saved successfully.</div>}
    <section className="legacy-panel"><div className="panel-head">Default Job Alert Policy</div><div className="policy-grid">
      <label><input type="checkbox" checked={!!policy.enabled} onChange={e=>update("enabled",e.target.checked)}/> Policy enabled</label>
      <label><input type="checkbox" checked={!!policy.alertOnFailure} onChange={e=>update("alertOnFailure",e.target.checked)}/> Alert on failure</label>
      <label><input type="checkbox" checked={!!policy.alertOnTimeout} onChange={e=>update("alertOnTimeout",e.target.checked)}/> Alert on timeout</label>
      <label><input type="checkbox" checked={!!policy.alertOnServerUnavailable} onChange={e=>update("alertOnServerUnavailable",e.target.checked)}/> Alert on server unavailable</label>
      <label>Maximum run time (minutes)<input type="number" min="1" value={policy.maxRunMinutes} onChange={e=>update("maxRunMinutes",Number(e.target.value))}/></label>
    </div><div className="field-hint">Policy configuration is persisted in AutoStone. Notification transport (email/SNMP/enterprise incident integration) is a separate integration point.</div></section>
  </div>;
}
