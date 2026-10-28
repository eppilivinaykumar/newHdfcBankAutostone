import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { jilApi } from "../api/api.js";

const SAMPLE = `insert_job: TEST_CBSGB_MONTHLY_EOD_B    job_type: box
owner: atsappsup@HBAUTOUTATAPPRI
group: MONTHLY_BCP1
application: CBSG
permission: gx,wx
alarm_if_fail: n
alarm_if_terminated: y
date_conditions: y
start_times: "19:00"
run_calendar: MONTH_END
send_notification: n

insert_job: TEST_CBSGB.M.ATS_ACTIVE_C    job_type: cmd
description: "This job activates the flow"
machine: HBAUTOUATAPPRI_vr
owner: atsappsup@HBAUTOUTATAPPRI
box_name: TEST_CBSGB_MONTHLY_EOD_B
group: MONTHLY_BCP1
application: CBSG
permission: gx
max_run_alarm: 10
alarm_if_fail: y
alarm_if_terminated: y
avg_runtime: 22
profile: /home/atsappsup/autoscp/ats.atsappsup.profile
send_notification: n
std_out_file: /tmp/$AUTO_JOB_NAME.out
std_err_file: /tmp/$AUTO_JOB_NAME.err
command: $UCMDPATH/EODActive.sh CBSG TEST_CBSGB_MONTHLY_EOD_B

insert_job: TEST_CBSGB.M.ATS_START_C    job_type: cmd
description: "This job is start Job, to initiate CBSG flow beyond this Job"
machine: HBAUTOUATAPPRI_vr
condition: s(TEST_CBSGB.M.ATS_ACTIVE_C)
owner: atsappsup@HBAUTOUTATAPPRI
box_name: TEST_CBSGB_MONTHLY_EOD_B
group: MONTHLY_BCP1
application: CBSG
permission: gx
max_run_alarm: 10
alarm_if_fail: y
alarm_if_terminated: y
auto_hold: y
profile: /home/atsappsup/autoscp/ats.atsappsup.profile
send_notification: n
std_out_file: /tmp/$AUTO_JOB_NAME.out
std_err_file: /tmp/$AUTO_JOB_NAME.err
command: $UCMDPATH/EODStart.sh CBSG TEST_CBSGB_MONTHLY_EOD_B

insert_job: TEST_CBSGB_MONTHLY_PREEOD_RMAN_DMP_MVMT_DC_B    job_type: box
condition: s(TEST_CBSGB.M.ATS_START_C)
owner: atsappsup@HBAUTOUTATAPPRI
box_name: TEST_CBSGB_MONTHLY_EOD_B
group: MONTHLY_BCP1
application: CBSG
permission: gx,wx
alarm_if_fail: y
alarm_if_terminated: y
auto_hold: y
send_notification: n

insert_job: TEST_CBSGB_M_UNMNT_OLD_CUR_DUMP_PRE_DC_C    job_type: cmd
description: "PreEOD: Unmount Current and old mount points"
machine: hbglodgddrbl
owner: root@hbglodgddrbl
box_name: TEST_CBSGB_MONTHLY_PREEOD_RMAN_DMP_MVMT_DC_B
group: MONTHLY_BCP1
application: CBSG
permission: gx,wx
max_run_alarm: 10
alarm_if_fail: y
alarm_if_terminated: y
profile: /dumps/scripts/ats.root.profile
send_notification: n
std_out_file: /tmp/$AUTO_JOB_NAME.out
std_err_file: /tmp/$AUTO_JOB_NAME.err
command: sh -x $UCMDPATH2/fcgl_umount_old_cur_pre_dmp.sh
`;

export default function ImportJilPage() {
  const navigate = useNavigate();
  const [jilText, setJilText] = useState("");
  const [result, setResult] = useState(null); // { success, errors, createdJobNames }
  const [validated, setValidated] = useState(false); // true once a clean validate has run against the current text
  const [busy, setBusy] = useState(false);
  const [imported, setImported] = useState(false);

  async function handleValidate() {
    setBusy(true);
    setResult(null);
    setImported(false);
    try {
      const res = await jilApi.validate(jilText);
      setResult(res);
      setValidated(res.success);
    } catch (err) {
      setResult({ success: false, errors: [err.message], createdJobNames: [] });
      setValidated(false);
    } finally {
      setBusy(false);
    }
  }

  async function handleImport() {
    setBusy(true);
    try {
      const res = await jilApi.importJil(jilText);
      setResult(res);
      if (res.success) {
        setImported(true);
        setTimeout(() => navigate("/jobs"), 900);
      } else {
        setValidated(false);
      }
    } catch (err) {
      setResult({ success: false, errors: [err.message], createdJobNames: [] });
      setValidated(false);
    } finally {
      setBusy(false);
    }
  }

  function handleTextChange(value) {
    setJilText(value);
    setValidated(false); // any edit invalidates the previous preview
    setResult(null);
  }

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Import JIL</h1>
          <div className="sub">Paste AutoSys JIL text — validate first, then import BOX + jobs</div>
        </div>
        <button className="btn" onClick={() => handleTextChange(SAMPLE)}>
          Load sample
        </button>
      </div>

      <textarea
        className="jil-textarea"
        placeholder="insert_job: JOB_A\njob_type: c&#10;command: /opt/scripts/job_a.sh&#10;machine: remote-host-01&#10;..."
        value={jilText}
        onChange={(e) => handleTextChange(e.target.value)}
        spellCheck={false}
      />

      <div className="form-actions" style={{ border: "none", paddingTop: 16 }}>
        <button className="btn" onClick={handleValidate} disabled={busy || jilText.trim() === ""}>
          {busy && !validated ? "Validating…" : "Validate"}
        </button>
        <button
          className="btn btn-primary"
          onClick={handleImport}
          disabled={busy || !validated}
          title={!validated ? "Run Validate first and resolve any errors" : ""}
        >
          {busy && validated ? "Importing…" : "Import"}
        </button>
      </div>

      {result && !result.success && (
        <div className="panel" style={{ marginTop: 8 }}>
          <h2 style={{ color: "var(--status-failed)" }}>
            {result.errors.length} issue{result.errors.length === 1 ? "" : "s"} found
          </h2>
          <ul className="error-list">
            {result.errors.map((e, i) => <li key={i}>{e}</li>)}
          </ul>
        </div>
      )}

      {result && result.success && !imported && (
        <div className="panel" style={{ marginTop: 8 }}>
          <h2>Ready to import</h2>
          <div className="sub" style={{ marginBottom: 10 }}>
            {result.createdJobNames.length} job{result.createdJobNames.length === 1 ? "" : "s"} will be created
          </div>
          {result.createdJobNames.map((name) => (
            <span key={name} className="job-chip">{name}</span>
          ))}
        </div>
      )}

      {imported && (
        <div className="banner banner-success" style={{ marginTop: 8 }}>
          Import successful — redirecting to Job Monitoring…
        </div>
      )}
    </div>
  );
}
